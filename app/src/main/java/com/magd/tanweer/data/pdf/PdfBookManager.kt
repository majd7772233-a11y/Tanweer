package com.magd.tanweer.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed class PdfDownloadState {
    object Idle : PdfDownloadState()
    data class Downloading(val progressPercent: Float, val downloadedBytes: Long, val totalBytes: Long) : PdfDownloadState()
    data class Ready(val file: File, val pageCount: Int) : PdfDownloadState()
    data class Error(val message: String) : PdfDownloadState()
}

class PdfBookManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // 25% of available memory for rendered PDF Bitmaps LRU Cache
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 4

    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private var currentRenderer: PdfRenderer? = null
    private var currentPfd: ParcelFileDescriptor? = null
    private var currentBookId: String? = null
    private var pageCount: Int = 0

    fun getCachedFile(bookId: String): File {
        val dir = File(context.filesDir, "pdf_books").apply { mkdirs() }
        return File(dir, "${bookId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}.pdf")
    }

    fun isBookDownloaded(bookId: String): Boolean {
        val file = getCachedFile(bookId)
        return file.exists() && file.length() > 1024
    }

    fun downloadAndOpenBook(bookId: String, downloadUrl: String): Flow<PdfDownloadState> = flow {
        val targetFile = getCachedFile(bookId)

        // Check if already cached
        if (targetFile.exists() && targetFile.length() > 1024) {
            try {
                val pages = initRenderer(bookId, targetFile)
                emit(PdfDownloadState.Ready(targetFile, pages))
                return@flow
            } catch (e: Exception) {
                Log.w("PdfBookManager", "Cached file corrupted, redownloading", e)
                targetFile.delete()
            }
        }

        emit(PdfDownloadState.Downloading(0f, 0L, 0L))

        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Tanweer-Android-App")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                emit(PdfDownloadState.Error("فشل تحميل ملف الكتاب من الخادم (رمز الخطأ: ${response.code})"))
                return@flow
            }

            val body = response.body!!
            val totalBytes = body.contentLength().coerceAtLeast(1L)
            var downloadedBytes = 0L

            val inputStream: InputStream = body.byteStream()
            val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var lastEmitTime = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastEmitTime > 100 || downloadedBytes == totalBytes) {
                    lastEmitTime = now
                    val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    emit(PdfDownloadState.Downloading(progress, downloadedBytes, totalBytes))
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // Move temp file to permanent destination
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()

            val pages = initRenderer(bookId, targetFile)
            emit(PdfDownloadState.Ready(targetFile, pages))

        } catch (e: Exception) {
            Log.e("PdfBookManager", "Error downloading book PDF", e)
            emit(PdfDownloadState.Error(e.message ?: "حدث خطأ غير متوقع أثناء تحميل الكتاب"))
        }
    }.flowOn(Dispatchers.IO)

    @Synchronized
    private fun initRenderer(bookId: String, file: File): Int {
        if (currentBookId == bookId && currentRenderer != null) {
            return pageCount
        }

        closeRenderer()

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        currentPfd = pfd
        currentRenderer = renderer
        currentBookId = bookId
        pageCount = renderer.pageCount
        return pageCount
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 1080, targetHeight: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = currentRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null

        val cacheKey = "${currentBookId}_${pageIndex}_${targetWidth}x${targetHeight}"
        val cached = bitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return@withContext cached
        }

        try {
            synchronized(renderer) {
                val page = renderer.openPage(pageIndex)
                val srcWidth = page.width
                val srcHeight = page.height

                // Calculate scaled dimensions maintaining aspect ratio
                val scale = minOf(targetWidth.toFloat() / srcWidth, targetHeight.toFloat() / srcHeight).coerceAtLeast(1.5f)
                val renderWidth = (srcWidth * scale).toInt()
                val renderHeight = (srcHeight * scale).toInt()

                val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                // Draw white background before rendering
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                bitmapCache.put(cacheKey, bitmap)
                return@withContext bitmap
            }
        } catch (e: Exception) {
            Log.e("PdfBookManager", "Failed to render page $pageIndex", e)
            return@withContext null
        }
    }

    fun getPageCount(): Int = pageCount

    @Synchronized
    fun closeRenderer() {
        try {
            currentRenderer?.close()
            currentPfd?.close()
        } catch (e: Exception) {
            Log.w("PdfBookManager", "Error closing renderer", e)
        } finally {
            currentRenderer = null
            currentPfd = null
            currentBookId = null
            pageCount = 0
            bitmapCache.evictAll()
        }
    }
}
