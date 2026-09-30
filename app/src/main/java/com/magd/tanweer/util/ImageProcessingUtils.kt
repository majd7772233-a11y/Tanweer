package com.magd.tanweer.util

import android.content.Context
import android.graphics.*
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
        maxDimension: Int = 1600,
        quality: Int = 82
    ): ProcessedPageResult? = withContext(Dispatchers.IO) {
        try {
            var bitmap: Bitmap? = rawBitmap ?: (uri?.let { decodeSampledBitmapFromUri(context, it, maxDimension) })
            if (bitmap == null) return@withContext null

            // 1. Rotation if needed
            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap = rotated
                }
            }

            // 2. Resizing to maximum bound if necessary
            if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = minOf(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
                val newW = (bitmap.width * ratio).toInt()
                val newH = (bitmap.height * ratio).toInt()
                bitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            }

            // 3. Auto enhance / Whiteboard contrast / Black and white filter
            if (autoEnhance || blackAndWhite || contrast != 1.0f || brightness != 0.0f) {
                bitmap = applyColorFilters(bitmap, autoEnhance, contrast, brightness, blackAndWhite)
            }

            // 4. Compress to JPEG with strict size guard (< 1.4MB)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            var bytes = stream.toByteArray()

            if (bytes.size > 1_400_000) {
                val reducedStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 68, reducedStream)
                bytes = reducedStream.toByteArray()
            }

            // 5. Calculate SHA-256 Checksum
            val checksum = calculateSha256(bytes)

            // 6. Save to app cache dir
            val cacheDir = File(context.cacheDir, "lesson_pages").apply { mkdirs() }
            val tempFile = File(cacheDir, "page_${System.currentTimeMillis()}_${checksum.take(8)}.jpg")
            FileOutputStream(tempFile).use { fos ->
                fos.write(bytes)
            }

            ProcessedPageResult(
                file = tempFile,
                byteArray = bytes,
                sizeBytes = bytes.size.toLong(),
                sha256 = checksum,
                width = bitmap.width,
                height = bitmap.height
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
