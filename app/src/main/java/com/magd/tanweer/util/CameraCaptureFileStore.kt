package com.magd.tanweer.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.magd.tanweer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

data class CameraCaptureTarget(
    val file: File,
    val contentUri: Uri
)

/**
 * Owns temporary full-resolution camera targets. Only files created inside the
 * dedicated cache/camera_captures directory can be resolved or deleted here.
 */
object CameraCaptureFileStore {
    private const val DIRECTORY_NAME = "camera_captures"
    private const val FILE_PREFIX = "capture_"
    private const val FILE_SUFFIX = ".jpg"
    private const val STALE_AFTER_MILLIS = 24L * 60L * 60L * 1000L

    fun create(context: Context, captureId: String = UUID.randomUUID().toString()): CameraCaptureTarget {
        val directory = captureDirectory(context)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Could not create camera capture directory")
        }

        cleanupStale(context)
        val safeId = captureId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(directory, "$FILE_PREFIX$safeId$FILE_SUFFIX")
        if (file.exists() || !file.createNewFile()) {
            throw IOException("Could not create a unique camera capture file")
        }

        return try {
            CameraCaptureTarget(
                file = file,
                contentUri = FileProvider.getUriForFile(
                    context,
                    providerAuthority(),
                    file
                )
            )
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    fun managedFile(context: Context, absolutePath: String?): File? {
        if (absolutePath.isNullOrBlank()) return null
        return try {
            val candidate = File(absolutePath).canonicalFile
            val directory = captureDirectory(context).canonicalFile
            if (
                candidate.parentFile == directory &&
                candidate.name.startsWith(FILE_PREFIX) &&
                candidate.name.endsWith(FILE_SUFFIX)
            ) candidate else null
        } catch (_: Exception) {
            null
        }
    }

    fun contentUri(context: Context, absolutePath: String?): Uri? {
        val file = managedFile(context, absolutePath) ?: return null
        if (!file.isFile) return null
        return runCatching {
            FileProvider.getUriForFile(context, providerAuthority(), file)
        }.getOrNull()
    }

    fun delete(context: Context, absolutePath: String?) {
        managedFile(context, absolutePath)?.takeIf { it.isFile }?.delete()
    }

    fun cleanupStale(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMillis: Long = STALE_AFTER_MILLIS
    ): Int {
        val directory = captureDirectory(context)
        if (!directory.isDirectory) return 0
        var deleted = 0
        directory.listFiles()?.forEach { file ->
            val managed = managedFile(context, file.absolutePath)
            if (managed != null && nowMillis - managed.lastModified() > maxAgeMillis) {
                if (managed.delete()) deleted++
            }
        }
        return deleted
    }

    /**
     * Copies the captured full-resolution image into the same private draft-source
     * storage used by the gallery flow. The cache capture is always removed afterwards.
     */
    suspend fun persistToDraft(
        context: Context,
        pageId: String,
        capturePath: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val file = managedFile(context, capturePath) ?: return@withContext null
            if (!file.isFile || file.length() <= 0L) return@withContext null
            val uri = contentUri(context, capturePath) ?: return@withContext null
            ImageProcessingUtils.persistDraftSource(
                context = context,
                pageId = pageId,
                sourceUri = uri,
                rawBitmap = null
            )
        } finally {
            delete(context, capturePath)
        }
    }

    private fun providerAuthority(): String = "${BuildConfig.APPLICATION_ID}.fileprovider"

    private fun captureDirectory(context: Context): File =
        File(context.cacheDir, DIRECTORY_NAME)
}
