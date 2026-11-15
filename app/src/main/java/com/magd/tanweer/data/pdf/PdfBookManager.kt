package com.magd.tanweer.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class PdfRenderDimensions(val width: Int, val height: Int)

internal fun calculatePdfRenderDimensions(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): PdfRenderDimensions {
    require(sourceWidth > 0 && sourceHeight > 0) { "PDF page dimensions must be positive" }
    require(targetWidth > 0 && targetHeight > 0) { "PDF target dimensions must be positive" }
    val scale = minOf(targetWidth.toFloat() / sourceWidth, targetHeight.toFloat() / sourceHeight)
    return PdfRenderDimensions(
        width = (sourceWidth * scale).toInt().coerceAtLeast(1),
        height = (sourceHeight * scale).toInt().coerceAtLeast(1)
    )
}

internal fun parsePdfContentRangeStart(header: String?): Long? {
    val match = Regex("^bytes\\s+(\\d+)-\\d+/(?:\\d+|\\*)$", RegexOption.IGNORE_CASE)
        .matchEntire(header?.trim().orEmpty()) ?: return null
    return match.groupValues[1].toLongOrNull()
}

internal fun pdfResponseAppendOffset(
    existingPartialBytes: Long,
    responseCode: Int,
    contentRangeStart: Long?
): Long? {
    if (responseCode == 200) return 0L
    if (responseCode == 206 && existingPartialBytes > 0L &&
        contentRangeStart == existingPartialBytes
    ) return existingPartialBytes
    return null
}

internal fun shouldResumePdfPartial(partialBytes: Long, validator: String?): Boolean =
    partialBytes > 0L && !validator.isNullOrBlank()

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

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    // Keep rendered pages within one eighth of the app heap and at most 64 MiB.
    private val cacheSize = (maxMemory / 8).coerceAtMost(64 * 1024)

    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private var currentRenderer: PdfRenderer? = null
    private var currentPfd: ParcelFileDescriptor? = null
    private var currentBookId: String? = null
    private var pageCount: Int = 0

    fun getCanonicalBookId(bookId: String): String {
        if (bookId.startsWith("book_")) {
            return bookId.lowercase().replace("-", "_")
        }
        val cleanName = bookId.substringBeforeLast(".pdf")
        val parts = cleanName.split("-", "_")
        if (parts.size >= 2) {
            val subjectPart = parts[0].trim()
            val gradePart = parts.getOrNull(1)?.toIntOrNull()
            val partNum = parts.getOrNull(2)?.toIntOrNull()
            if (gradePart != null) {
                val normSubj = com.magd.tanweer.util.BookCatalogResolver.normalizeSubjectKey(subjectPart)
                return com.magd.tanweer.util.BookCatalogResolver.getCanonicalBookId(gradePart, normSubj, partNum)
            }
        }
        return bookId.lowercase().replace("[^a-zA-Z0-9_-]".toRegex(), "_")
    }

    fun getCachedFile(bookId: String): File {
        val dir = File(context.filesDir, "pdf_books").apply { mkdirs() }
        val canonicalId = getCanonicalBookId(bookId)
        val canonicalFile = File(dir, "${canonicalId}.pdf")
        if (canonicalFile.exists() && canonicalFile.length() > 1024) {
            return canonicalFile
        }

        // Check fallback direct raw sanitized name
        val rawSanitized = bookId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val rawFile = File(dir, "${rawSanitized}.pdf")
        if (rawFile.exists() && rawFile.length() > 1024) {
            return rawFile
        }

        return canonicalFile
    }

    fun isBookDownloaded(bookId: String): Boolean {
        val file = getCachedFile(bookId)
        return file.exists() && file.length() > 1024
    }

    fun getBookFileSize(bookId: String): Long {
        val file = getCachedFile(bookId)
        return if (file.exists()) file.length() else 0L
    }

    private fun isManagedBookArtifact(file: File): Boolean {
        val name = file.name
        return name.endsWith(".pdf") || name.endsWith(".pdf.part") ||
            name.endsWith(".pdf.part.meta") ||
            (name.contains(".pdf") && name.contains(".corrupt-"))
    }

    fun getTotalBooksStorageBytes(): Long {
        val dir = File(context.filesDir, "pdf_books")
        if (!dir.exists()) return 0L
        return dir.listFiles()?.filter { it.isFile && isManagedBookArtifact(it) }
            ?.sumOf { it.length() } ?: 0L
    }

    private fun bookArtifactCandidates(bookId: String): List<File> {
        val dir = File(context.filesDir, "pdf_books")
        val canonicalName = "${getCanonicalBookId(bookId)}.pdf"
        val rawName = bookId.replace("[^a-zA-Z0-9_-]".toRegex(), "_") + ".pdf"
        val baseNames = setOf(canonicalName, rawName)
        return dir.listFiles()?.filter { file ->
            file.isFile && baseNames.any { base ->
                file.name == base || file.name == "$base.part" ||
                    file.name == "$base.part.meta" || file.name.startsWith("$base.corrupt-") ||
                    file.name.startsWith("$base.part.corrupt-")
            }
        } ?: emptyList()
    }

    fun deleteBookFile(bookId: String): Boolean {
        if (currentBookId == bookId || currentBookId == getCanonicalBookId(bookId)) closeRenderer()
        val artifacts = bookArtifactCandidates(bookId)
        if (artifacts.isEmpty()) return false
        var allDeleted = true
        artifacts.forEach { if (!it.delete()) allDeleted = false }
        return allDeleted
    }

    fun deleteAllDownloadedBooks(): Int {
        closeRenderer()
        val dir = File(context.filesDir, "pdf_books")
        if (!dir.exists()) return 0
        val files = dir.listFiles()?.filter { it.isFile && isManagedBookArtifact(it) } ?: emptyList()
        var deletedBooks = 0
        files.forEach { file ->
            val wasBook = file.name.endsWith(".pdf")
            if (file.delete() && wasBook) deletedBooks++
        }
        return deletedBooks
    }

    /**
     * Removes abandoned partial downloads and legacy cache temp files after a generous age.
     * Recent partial files are retained so a request with a matching validator can resume.
     */
    fun cleanupOrphanedDownloadFiles(maxAgeMillis: Long = 30L * 24 * 60 * 60 * 1000): Int {
        val now = System.currentTimeMillis()
        var removed = 0
        val bookDir = File(context.filesDir, "pdf_books")
        bookDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".pdf.part.meta")) {
                val partial = File(file.parentFile, file.name.removeSuffix(".meta"))
                if (!partial.exists() && file.delete()) removed++
            } else if (file.isFile && file.name.endsWith(".pdf.part") &&
                now - file.lastModified() > maxAgeMillis
            ) {
                val metadata = File(file.parentFile, file.name + ".meta")
                if (file.delete()) removed++
                if (metadata.exists() && metadata.delete()) removed++
            }
        }
        context.cacheDir.listFiles()?.filter {
            it.isFile && it.name.startsWith("temp_") && it.name.endsWith(".pdf") &&
                now - it.lastModified() > 24L * 60 * 60 * 1000
        }?.forEach { if (it.delete()) removed++ }
        return removed
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 بايت"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f غيغابايت", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f ميغابايت", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f كيلوبايت", kb)
            else -> "$bytes بايت"
        }
    }

    fun downloadAndOpenBook(bookId: String, downloadUrl: String): Flow<PdfDownloadState> = flow {
        cleanupOrphanedDownloadFiles()
        val targetFile = getCachedFile(bookId)
        val partialFile = File(targetFile.parentFile, targetFile.name + ".part")
        val validatorFile = File(targetFile.parentFile, targetFile.name + ".part.meta")

        if (targetFile.exists() && targetFile.length() > 1024) {
            try {
                val pages = initRenderer(bookId, targetFile)
                emit(PdfDownloadState.Ready(targetFile, pages))
                return@flow
            } catch (error: Exception) {
                Log.w("PdfBookManager", "Cached PDF is invalid; preserving it while downloading a replacement", error)
            }
        }

        // A complete partial left by process death can be promoted without downloading it again.
        if (partialFile.exists() && partialFile.length() > 1024 && hasPdfSignature(partialFile)) {
            try {
                val pages = verifyPdfFile(partialFile)
                if (currentBookId == bookId) closeRenderer()
                promoteVerifiedPdf(partialFile, targetFile)
                validatorFile.delete()
                val openedPages = initRenderer(bookId, targetFile)
                emit(PdfDownloadState.Ready(targetFile, if (openedPages > 0) openedPages else pages))
                return@flow
            } catch (error: Exception) {
                Log.d("PdfBookManager", "Partial PDF is not complete yet; continuing with a safe resume", error)
            }
        }

        var validator = if (validatorFile.exists()) {
            validatorFile.readText().trim().takeIf { it.isNotBlank() }
        } else null
        var existingBytes = if (partialFile.exists()) partialFile.length() else 0L
        if (!shouldResumePdfPartial(existingBytes, validator)) {
            if (partialFile.exists()) partialFile.delete()
            validatorFile.delete()
            existingBytes = 0L
            validator = null
        }

        emit(PdfDownloadState.Downloading(0f, existingBytes, 0L))
        try {
            val requestBuilder = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Tanweer-Android-App")
            if (existingBytes > 0L && !validator.isNullOrBlank()) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
                requestBuilder.header("If-Range", validator)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body ?: throw IOException("الخادم لم يرسل ملف الكتاب")
                if (response.code == 416 && existingBytes > 0L) {
                    partialFile.delete()
                    validatorFile.delete()
                    throw IOException("انتهت صلاحية جزء التنزيل السابق؛ أعد المحاولة لبدء تنزيل جديد")
                }
                if (!response.isSuccessful) {
                    throw IOException("فشل تحميل ملف الكتاب من الخادم (رمز الخطأ: ${response.code})")
                }

                val contentRangeStart = parsePdfContentRangeStart(response.header("Content-Range"))
                val appendOffset = pdfResponseAppendOffset(existingBytes, response.code, contentRangeStart)
                    ?: throw IOException("استجابة استكمال تنزيل الكتاب غير متوافقة؛ أعد المحاولة")
                val append = appendOffset > 0L

                val responseValidator = response.header("ETag")
                    ?.takeIf { it.isNotBlank() && !it.startsWith("W/", ignoreCase = true) }
                    ?: response.header("Last-Modified")?.takeIf { it.isNotBlank() }
                if (append && responseValidator != validator) {
                    partialFile.delete()
                    validatorFile.delete()
                    throw IOException("تعذر التحقق من تطابق نسخة الكتاب المستكملة؛ أعد التنزيل من البداية")
                }

                if (!append && partialFile.exists() && !partialFile.delete()) {
                    throw IOException("تعذر إعادة بدء تنزيل الكتاب")
                }

                if (responseValidator != null) {
                    FileOutputStream(validatorFile, false).use { metadata ->
                        metadata.write(responseValidator.toByteArray(Charsets.UTF_8))
                        metadata.fd.sync()
                    }
                } else {
                    validatorFile.delete()
                }

                val remainingBytes = body.contentLength()
                val totalBytes = if (remainingBytes >= 0L) appendOffset + remainingBytes else 0L
                val parentPath = partialFile.parentFile?.absolutePath
                    ?: throw IOException("مسار تخزين الكتاب غير صالح")
                if (remainingBytes >= 0L && StatFs(parentPath).availableBytes < remainingBytes) {
                    throw IOException("المساحة المتاحة لا تكفي لتنزيل الكتاب")
                }

                var downloadedBytes = appendOffset
                var lastEmitTime = 0L
                FileOutputStream(partialFile, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            if (StatFs(parentPath).availableBytes < bytesRead) {
                                throw IOException("نفدت مساحة التخزين أثناء تنزيل الكتاب")
                            }
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime > 100L ||
                                (totalBytes > 0L && downloadedBytes >= totalBytes)
                            ) {
                                lastEmitTime = now
                                val progress = if (totalBytes > 0L) {
                                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                emit(PdfDownloadState.Downloading(progress, downloadedBytes, totalBytes))
                            }
                        }
                    }
                    output.fd.sync()
                }
            }

            if (!partialFile.exists() || partialFile.length() <= 1024 || !hasPdfSignature(partialFile)) {
                preserveDamagedArtifact(partialFile)
                validatorFile.delete()
                throw IOException("الملف المستلم ليس ملف PDF صالحاً أو أنه غير مكتمل")
            }

            val pages = verifyPdfFile(partialFile)
            if (currentBookId == bookId) closeRenderer()
            promoteVerifiedPdf(partialFile, targetFile)
            validatorFile.delete()
            val openedPages = initRenderer(bookId, targetFile)
            emit(PdfDownloadState.Ready(targetFile, if (openedPages > 0) openedPages else pages))
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("PdfBookManager", "Error downloading book PDF", error)
            emit(PdfDownloadState.Error(error.message ?: "حدث خطأ غير متوقع أثناء تحميل الكتاب"))
        }
    }.flowOn(Dispatchers.IO)

    private fun hasPdfSignature(file: File): Boolean {
        if (!file.exists() || file.length() < 5L) return false
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(1024)
                val count = input.read(header)
                count > 0 && String(header, 0, count, Charsets.ISO_8859_1).contains("%PDF-")
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun verifyPdfFile(file: File): Int {
        if (file.length() <= 1024L || !hasPdfSignature(file)) {
            throw IOException("ملف PDF تالف أو غير مكتمل")
        }
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(descriptor)
            try {
                if (renderer.pageCount <= 0) throw IOException("ملف PDF لا يحتوي صفحات")
                return renderer.pageCount
            } finally {
                renderer.close()
            }
        } finally {
            descriptor.close()
        }
    }

    private fun preserveDamagedArtifact(file: File) {
        if (!file.exists()) return
        val backup = File(file.parentFile, file.name + ".corrupt-" + System.currentTimeMillis())
        if (!file.renameTo(backup)) {
            Log.w("PdfBookManager", "Could not preserve damaged PDF artifact: " + file.name)
        }
    }

    private fun promoteVerifiedPdf(partial: File, target: File) {
        var damagedBackup: File? = null
        if (target.exists()) {
            damagedBackup = File(target.parentFile, target.name + ".corrupt-" + System.currentTimeMillis())
            if (!target.renameTo(damagedBackup)) {
                throw IOException("تعذر حفظ نسخة الكتاب السابقة قبل الاستبدال")
            }
        }
        if (!partial.renameTo(target)) {
            damagedBackup?.let { it.renameTo(target) }
            throw IOException("تعذر نقل ملف PDF المكتمل إلى التخزين الدائم")
        }
    }

    @Synchronized
    private fun initRenderer(bookId: String, file: File): Int {
        if (currentBookId == bookId && currentRenderer != null) {
            return pageCount
        }

        closeRenderer()

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(pfd)
        } catch (error: Exception) {
            pfd.close()
            throw error
        }
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
                try {
                    val dimensions = calculatePdfRenderDimensions(
                        sourceWidth = page.width,
                        sourceHeight = page.height,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight
                    )
                    val bitmap = Bitmap.createBitmap(
                        dimensions.width,
                        dimensions.height,
                        Bitmap.Config.ARGB_8888
                    )
                    android.graphics.Canvas(bitmap).drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmapCache.put(cacheKey, bitmap)
                    return@withContext bitmap
                } finally {
                    page.close()
                }
            }
        } catch (e: Exception) {
            Log.e("PdfBookManager", "Failed to render page $pageIndex", e)
            return@withContext null
        }
    }

    fun getPageCount(): Int = pageCount

    @Synchronized
    fun closeRenderer() {
        val renderer = currentRenderer
        val descriptor = currentPfd
        try {
            if (renderer != null) {
                synchronized(renderer) {
                    renderer.close()
                    descriptor?.close()
                }
            } else {
                descriptor?.close()
            }
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
