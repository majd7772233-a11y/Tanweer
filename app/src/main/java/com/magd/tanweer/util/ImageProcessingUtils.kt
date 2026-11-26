package com.magd.tanweer.util

import android.content.Context
import android.graphics.*
import android.util.Log
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class ProcessedPageResult(
    val file: File,
    val byteArray: ByteArray,
    val sizeBytes: Long,
    val sha256: String,
    val width: Int,
    val height: Int
)

enum class ImageQualityPreset(
    val maxDimension: Int,
    val initialQuality: Int,
    val maxTargetBytes: Int
) {
    ULTRA_COMPACT(maxDimension = 1280, initialQuality = 68, maxTargetBytes = 700_000),
    COMPACT(maxDimension = 1280, initialQuality = 68, maxTargetBytes = 700_000),
    BALANCED(maxDimension = 1800, initialQuality = 80, maxTargetBytes = 1_250_000),
    HIGH(maxDimension = 2400, initialQuality = 88, maxTargetBytes = 1_500_000),
    ORIGINAL(maxDimension = 3200, initialQuality = 95, maxTargetBytes = 3_000_000);

    companion object {
        fun fromString(value: String?): ImageQualityPreset = when (value?.uppercase()) {
            "ULTRA_COMPACT", "COMPACT" -> ULTRA_COMPACT
            "HIGH" -> HIGH
            "ORIGINAL" -> ORIGINAL
            else -> BALANCED
        }
    }
}

object ImageProcessingUtils {

    suspend fun loadAndProcessImage(
        context: Context,
        uri: Uri?,
        rawBitmap: Bitmap?,
        autoEnhance: Boolean = true,
        contrast: Float = 1.25f,
        brightness: Float = 0.05f,
        blackAndWhite: Boolean = false,
        rotationDegrees: Float = 0f,
        qualityMode: String? = null,
        maxDimension: Int? = null,
        quality: Int? = null
    ): ProcessedPageResult? = withContext(Dispatchers.IO) {
        val preset = ImageQualityPreset.fromString(
            qualityMode ?: context.getSharedPreferences("tanweer_prefs", Context.MODE_PRIVATE)
                .getString("image_quality", "BALANCED")
        )
        val targetMaxDim = maxDimension ?: preset.maxDimension
        val targetQuality = quality ?: preset.initialQuality
        val maxTargetBytes = preset.maxTargetBytes

        try {
            val initialBitmap: Bitmap = rawBitmap ?: (uri?.let { decodeSampledBitmapFromUri(context, it, targetMaxDim) })
                ?: return@withContext null
            var bitmap: Bitmap = initialBitmap

            // 1. Rotation if needed
            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap = rotated
                }
            }

            // 2. Resizing to maximum bound if necessary
            if (bitmap.width > targetMaxDim || bitmap.height > targetMaxDim) {
                val ratio = minOf(targetMaxDim.toFloat() / bitmap.width, targetMaxDim.toFloat() / bitmap.height)
                val newW = (bitmap.width * ratio).toInt()
                val newH = (bitmap.height * ratio).toInt()
                bitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            }

            // 3. Auto enhance / Whiteboard contrast / Black and white filter
            if (autoEnhance || blackAndWhite || contrast != 1.0f || brightness != 0.0f) {
                bitmap = applyColorFilters(bitmap, autoEnhance, contrast, brightness, blackAndWhite)
            }

            // 4. Compress to JPEG with user's configured quality preset
            var currentQuality = targetQuality
            var currentBitmap = bitmap
            var bytes: ByteArray

            while (true) {
                val stream = ByteArrayOutputStream()
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, stream)
                bytes = stream.toByteArray()

                if (bytes.size <= maxTargetBytes) {
                    break
                }

                // If still too large, step down quality first
                if (currentQuality > 40) {
                    currentQuality -= 10
                } else {
                    // If quality reached lower bound, scale down bitmap dimensions by 0.8x
                    val nextW = (currentBitmap.width * 0.8f).toInt().coerceAtLeast(300)
                    val nextH = (currentBitmap.height * 0.8f).toInt().coerceAtLeast(300)
                    if (nextW >= currentBitmap.width || nextH >= currentBitmap.height) {
                        break
                    }
                    currentBitmap = Bitmap.createScaledBitmap(currentBitmap, nextW, nextH, true)
                    currentQuality = 65
                }
            }

            // 5. Calculate SHA-256 Checksum
            val checksum = calculateSha256(bytes)

            // 6. Save to app files dir (pending_uploads) so pending uploads survive cache clearing
            val pendingDir = File(context.filesDir, "pending_uploads").apply { mkdirs() }
            val tempFile = File(pendingDir, "page_${System.currentTimeMillis()}_${checksum.take(8)}.jpg")
            FileOutputStream(tempFile).use { fos ->
                fos.write(bytes)
            }

            ProcessedPageResult(
                file = tempFile,
                byteArray = bytes,
                sizeBytes = bytes.size.toLong(),
                sha256 = checksum,
                width = currentBitmap.width,
                height = currentBitmap.height
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Copies picker content into app-private storage so Activity/process recreation
     * does not depend on a transient external URI grant.
     */
    suspend fun persistDraftSource(
        context: Context,
        pageId: String,
        sourceUri: Uri?,
        rawBitmap: Bitmap?
    ): Uri? = withContext(Dispatchers.IO) {
        val sourceFile = draftSourceFile(context, pageId)
        val partialFile = File(sourceFile.parentFile, sourceFile.name + ".part")
        try {
            sourceFile.parentFile?.mkdirs()
            if (sourceFile.isFile && sourceFile.length() > 0L && isValidImageFile(sourceFile)) {
                return@withContext Uri.fromFile(sourceFile)
            }

            sourceFile.delete()
            partialFile.delete()
            if (rawBitmap != null) {
                FileOutputStream(partialFile).use { output ->
                    if (!rawBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw IllegalStateException("Could not persist camera source bitmap")
                    }
                    output.fd.sync()
                }
            } else {
                val uri = sourceUri ?: return@withContext null
                val source = context.contentResolver.openInputStream(uri) ?: return@withContext null
                source.use { input ->
                    FileOutputStream(partialFile).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
            }

            if (!partialFile.isFile || partialFile.length() <= 0L || !isValidImageFile(partialFile)) {
                partialFile.delete()
                return@withContext null
            }
            if (!partialFile.renameTo(sourceFile)) {
                throw IllegalStateException("Could not finalize draft source media")
            }
            Uri.fromFile(sourceFile)
        } catch (error: Exception) {
            partialFile.delete()
            Log.w("ImageProcessingUtils", "Could not persist source media for draft page $pageId", error)
            null
        }
    }

    private fun isValidImageFile(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    fun restoreProcessedPageResult(filePath: String?): ProcessedPageResult? {
        if (filePath.isNullOrBlank()) return null
        val file = File(filePath)
        if (!file.isFile || file.length() <= 0L) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            val bytes = file.readBytes()
            ProcessedPageResult(
                file = file,
                byteArray = bytes,
                sizeBytes = bytes.size.toLong(),
                sha256 = calculateSha256(bytes),
                width = options.outWidth,
                height = options.outHeight
            )
        } catch (error: Exception) {
            Log.w("ImageProcessingUtils", "Could not restore processed draft page $filePath", error)
            null
        }
    }

    fun decodeProcessedPreview(filePath: String?, maxDimension: Int = 640): Bitmap? {
        if (filePath.isNullOrBlank() || maxDimension <= 0) return null
        val file = File(filePath)
        if (!file.isFile || file.length() <= 0L) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxDimension
            ) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (error: Exception) {
            Log.w("ImageProcessingUtils", "Could not decode processed draft preview $filePath", error)
            null
        }
    }
    fun deleteDraftSource(context: Context, sourceUri: Uri?) {
        if (sourceUri?.scheme != "file") return
        val sourceFile = sourceUri.path?.let(::File) ?: return
        val draftDirectory = File(context.filesDir, "scan_draft_sources")
        try {
            if (sourceFile.canonicalFile.parentFile == draftDirectory.canonicalFile &&
                sourceFile.name.endsWith(".source")
            ) {
                sourceFile.delete()
            }
        } catch (error: Exception) {
            Log.w("ImageProcessingUtils", "Could not delete draft source media", error)
        }
    }

    /**
     * Deletes a processed image only while it is still an unsent draft attachment.
     * Canonical-path validation prevents this helper from deleting arbitrary files.
     * Successfully submitted pages must not call this: an offline mutation may still
     * reference the pending_uploads file until the server confirms publication.
     */
    fun deleteProcessedDraftFile(context: Context, processedFilePath: String?) {
        if (processedFilePath.isNullOrBlank()) return
        try {
            val file = File(processedFilePath).canonicalFile
            val pendingDirectory = File(context.filesDir, "pending_uploads").canonicalFile
            if (
                file.parentFile == pendingDirectory &&
                file.name.startsWith("page_") &&
                file.name.endsWith(".jpg") &&
                file.isFile
            ) {
                file.delete()
            }
        } catch (error: Exception) {
            Log.w("ImageProcessingUtils", "Could not delete unsent processed draft page", error)
        }
    }

    fun deleteDraftPage(context: Context, sourceUri: Uri?, processedFilePath: String?) {
        deleteDraftSource(context, sourceUri)
        deleteProcessedDraftFile(context, processedFilePath)
    }

    private fun draftSourceFile(context: Context, pageId: String): File {
        val safeId = pageId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(File(context.filesDir, "scan_draft_sources"), "source_$safeId.source")
    }
    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        var input: InputStream? = null
        return try {
            input = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(input, null, options)
            input.close()

            // Calculate inSampleSize
            var sampleSize = 1
            if (options.outHeight > maxDim || options.outWidth > maxDim) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / sampleSize) >= maxDim && (halfWidth / sampleSize) >= maxDim) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            input = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } catch (e: Exception) {
            null
        } finally {
            input?.close()
        }
    }

    private fun applyColorFilters(
        src: Bitmap,
        autoEnhance: Boolean,
        contrast: Float,
        brightness: Float,
        blackAndWhite: Boolean
    ): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val colorMatrix = ColorMatrix()

        if (blackAndWhite) {
            // Convert to grayscale first
            colorMatrix.setSaturation(0f)
        }

        // Apply contrast and brightness adjustments:
        // C' = contrast * C + (brightness * 255)
        val effectiveContrast = if (autoEnhance) contrast * 1.15f else contrast
        val effectiveBrightness = if (autoEnhance) brightness + 0.05f else brightness

        val scale = effectiveContrast
        val translate = (effectiveBrightness * 255f) + (1f - scale) * 128f

        val cm = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )

        if (blackAndWhite) {
            colorMatrix.postConcat(cm)
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        } else {
            paint.colorFilter = ColorMatrixColorFilter(cm)
        }

        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
