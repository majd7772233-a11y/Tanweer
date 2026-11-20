package com.magd.tanweer.util

import android.content.Context
import android.util.Log
import android.util.AtomicFile
import com.magd.tanweer.data.model.ContentItem
import com.magd.tanweer.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL

data class TranscribedLessonRecord(
    val contentId: String,
    val subjectId: String,
    val subjectName: String,
    val title: String,
    val studyDate: String,
    val totalPages: Int,
    val completedPages: Set<Int> = emptySet(),
    val localImagePaths: Map<Int, String> = emptyMap(),
    val transcribedAt: Long = System.currentTimeMillis()
) {
    val isFullyCompleted: Boolean get() = totalPages > 0 && completedPages.size >= totalPages
    val isLocallyCached: Boolean get() = totalPages > 0 && localImagePaths.size >= totalPages
}

/**
 * Smart Student Notebook & Lesson Sharing Manager
 * Handles local offline transcription of board captures, caching of lesson imagery,
 * and peer-to-peer lesson sharing for missed lectures.
 */
object StudentNotebookManager {

    private const val TAG = "StudentNotebookManager"
    private const val FILE_NAME = "student_notebook_records.json"
    private const val DIR_NAME = "transcribed_lessons"

    private fun getStorageFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    fun getOfflineDir(context: Context): File {
        return File(context.filesDir, DIR_NAME).apply { mkdirs() }
    }

    private sealed class NotebookReadResult {
        data class Valid(val records: List<TranscribedLessonRecord>) : NotebookReadResult()
        data class Invalid(val error: Exception) : NotebookReadResult()
    }

    private fun parseNotebookRecords(json: String): List<TranscribedLessonRecord> {
        val array = JSONArray(json)
        val records = mutableListOf<TranscribedLessonRecord>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val contentId = obj.getString("contentId").trim()
            if (contentId.isEmpty()) throw IOException("Notebook record has an empty content ID")
            val totalPages = obj.optInt("totalPages", 0)
            if (totalPages < 0) throw IOException("Notebook record has a negative page count")
            val completedPages = mutableSetOf<Int>()
            val completedArray = obj.optJSONArray("completedPages") ?: JSONArray()
            for (j in 0 until completedArray.length()) {
                val pageIndex = completedArray.getInt(j)
                // Text-only lessons still expose one "summary completed" checkbox (page 0).
                if (pageIndex >= 0 && pageIndex < totalPages.coerceAtLeast(1)) completedPages.add(pageIndex)
            }
            val imagePaths = mutableMapOf<Int, String>()
            val imageObject = obj.optJSONObject("localImagePaths") ?: JSONObject()
            val keys = imageObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val pageIndex = key.toIntOrNull()
                if (pageIndex != null && pageIndex >= 0 && pageIndex < totalPages) {
                    val path = imageObject.getString(key)
                    if (path.isNotBlank()) imagePaths[pageIndex] = path
                }
            }
            records.add(TranscribedLessonRecord(
                contentId = contentId,
                subjectId = obj.optString("subjectId", ""),
                subjectName = obj.optString("subjectName", ""),
                title = obj.optString("title", ""),
                studyDate = obj.optString("studyDate", ""),
                totalPages = totalPages,
                completedPages = completedPages,
                localImagePaths = imagePaths,
                transcribedAt = obj.optLong("transcribedAt", System.currentTimeMillis())
            ))
        }
        return records
    }

    private fun preserveCorruptNotebook(file: File) {
        val source = if (file.exists()) file else File(file.path + ".bak")
        if (!source.exists()) return
        val backup = File(file.parentFile, file.name + ".corrupt")
        if (backup.exists()) return
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(backup).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Could not preserve the unreadable notebook file", error)
        }
    }

    @Synchronized
    private fun readNotebookRecords(context: Context): NotebookReadResult {
        val file = getStorageFile(context)
        val atomicFile = AtomicFile(file)
        if (!file.exists() && !File(file.path + ".bak").exists()) {
            return NotebookReadResult.Valid(emptyList())
        }
        return try {
            val json = atomicFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            NotebookReadResult.Valid(parseNotebookRecords(json))
        } catch (error: Exception) {
            preserveCorruptNotebook(file)
            NotebookReadResult.Invalid(error)
        }
    }

    private fun encodeNotebookRecords(records: List<TranscribedLessonRecord>): String {
        val array = JSONArray()
        records.forEach { item ->
            val obj = JSONObject().apply {
                put("contentId", item.contentId)
                put("subjectId", item.subjectId)
                put("subjectName", item.subjectName)
                put("title", item.title)
                put("studyDate", item.studyDate)
                put("totalPages", item.totalPages)
                put("transcribedAt", item.transcribedAt)
                val completedArray = JSONArray()
                item.completedPages.forEach { completedArray.put(it) }
                put("completedPages", completedArray)
                val imageObject = JSONObject()
                item.localImagePaths.forEach { (page, path) -> imageObject.put(page.toString(), path) }
                put("localImagePaths", imageObject)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun writeNotebookRecordsAtomically(
        context: Context,
        records: List<TranscribedLessonRecord>
    ): Boolean {
        val json = try {
            encodeNotebookRecords(records).also { parseNotebookRecords(it) }
        } catch (error: Exception) {
            Log.e(TAG, "Notebook serialization validation failed", error)
            return false
        }
        val atomicFile = AtomicFile(getStorageFile(context))
        var stream: FileOutputStream? = null
        return try {
            stream = atomicFile.startWrite()
            stream.write(json.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            atomicFile.finishWrite(stream)
            true
        } catch (error: Exception) {
            stream?.let { runCatching { atomicFile.failWrite(it) } }
            Log.e(TAG, "Failed to atomically save notebook records", error)
            false
        }
    }

    @Synchronized
    fun getAllNotebookLessons(context: Context): List<TranscribedLessonRecord> {
        return when (val result = readNotebookRecords(context)) {
            is NotebookReadResult.Valid -> result.records
            is NotebookReadResult.Invalid -> {
                Log.e(TAG, "Notebook is unreadable; original data was preserved for recovery", result.error)
                emptyList()
            }
        }
    }

    @Synchronized
    fun getLessonRecord(context: Context, contentId: String): TranscribedLessonRecord? {
        return getAllNotebookLessons(context).find { it.contentId == contentId }
    }

    /**
     * Returns false when the existing notebook cannot be read or the atomic write fails.
     * An unreadable source is never treated as an empty notebook and is never overwritten.
     */
    @Synchronized
    fun saveLessonRecord(context: Context, record: TranscribedLessonRecord): Boolean {
        val loaded = readNotebookRecords(context)
        if (loaded is NotebookReadResult.Invalid) {
            Log.e(TAG, "Refusing to overwrite an unreadable notebook", loaded.error)
            return false
        }
        val current = (loaded as NotebookReadResult.Valid).records.toMutableList()
        val index = current.indexOfFirst { it.contentId == record.contentId }
        if (index >= 0) current[index] = record else current.add(0, record)
        return writeNotebookRecordsAtomically(context, current)
    }

    @Synchronized
    private fun persistCachedPageRecord(
        context: Context,
        content: ContentItem,
        pageIndex: Int,
        imageFile: File
    ): Boolean {
        val loaded = readNotebookRecords(context)
        if (loaded is NotebookReadResult.Invalid) {
            Log.e(TAG, "Refusing to attach a cached page to an unreadable notebook", loaded.error)
            return false
        }
        val records = (loaded as NotebookReadResult.Valid).records.toMutableList()
        val recordIndex = records.indexOfFirst { it.contentId == content.id }
        val existing = records.getOrNull(recordIndex) ?: TranscribedLessonRecord(
            contentId = content.id,
            subjectId = content.subjectId,
            subjectName = content.subjectName,
            title = content.title,
            studyDate = content.studyDate,
            totalPages = content.media.size
        )
        val validPaths = existing.localImagePaths.filterKeys { it in content.media.indices }
        val updated = existing.copy(
            totalPages = content.media.size,
            localImagePaths = validPaths + (pageIndex to imageFile.absolutePath)
        )
        if (recordIndex >= 0) records[recordIndex] = updated else records.add(0, updated)
        return writeNotebookRecordsAtomically(context, records)
    }

    @Synchronized
    private fun persistCachedPagesRecord(
        context: Context,
        content: ContentItem,
        pagePaths: Map<Int, String>
    ): Boolean {
        val loaded = readNotebookRecords(context)
        if (loaded is NotebookReadResult.Invalid) {
            Log.e(TAG, "Refusing to attach cached pages to an unreadable notebook", loaded.error)
            return false
        }
        val records = (loaded as NotebookReadResult.Valid).records.toMutableList()
        val recordIndex = records.indexOfFirst { it.contentId == content.id }
        val existing = records.getOrNull(recordIndex) ?: TranscribedLessonRecord(
            contentId = content.id,
            subjectId = content.subjectId,
            subjectName = content.subjectName,
            title = content.title,
            studyDate = content.studyDate,
            totalPages = content.media.size
        )
        val validPaths = existing.localImagePaths.filterKeys { it in content.media.indices }
        val newPaths = pagePaths.filterKeys { it in content.media.indices }
        val updated = existing.copy(
            totalPages = content.media.size,
            localImagePaths = validPaths + newPaths
        )
        if (recordIndex >= 0) records[recordIndex] = updated else records.add(0, updated)
        return writeNotebookRecordsAtomically(context, records)
    }

    @Synchronized
    fun markPageCompleted(context: Context, content: ContentItem, pageIndex: Int, isCompleted: Boolean): Boolean {
        val validPageCount = content.media.size.coerceAtLeast(1)
        if (pageIndex !in 0 until validPageCount) return false
        val existing = getLessonRecord(context, content.id) ?: TranscribedLessonRecord(
            contentId = content.id,
            subjectId = content.subjectId,
            subjectName = content.subjectName,
            title = content.title,
            studyDate = content.studyDate,
            totalPages = content.media.size
        )
        val updatedSet = existing.completedPages.toMutableSet().apply {
            if (isCompleted) add(pageIndex) else remove(pageIndex)
        }
        val updated = existing.copy(completedPages = updatedSet)
        return saveLessonRecord(context, updated)
    }

data class LessonCachingResult(
    val totalPages: Int,
    val successfulCount: Int,
    val failedCount: Int,
    val newlyDownloadedCount: Int,
    val isAllSucceeded: Boolean
) {
    val summaryMessage: String get() = when {
        isAllSucceeded -> "تم حفظ جميع لوحات الدرس ($totalPages لوحات) بنجاح للمذاكرة دون اتصال ✓"
        successfulCount > 0 -> "تم حفظ $successfulCount من أصل $totalPages لوحات بنجاح، بينما تعذر تنزيل $failedCount لوحات ⚠️"
        else -> "تعذر تنزيل لوحات الدرس، يرجى التأكد من اتصال الإنترنت ❌"
    }
}

    /**
     * Downloads one protected lesson page to the app-private cache.
     * Authorization is restricted to the configured Tanweer API origin.
     */
    suspend fun cacheLessonPageLocally(
        context: Context,
        content: ContentItem,
        pageIndex: Int
    ): Result<File> = withContext(Dispatchers.IO) {
        val mediaItem = content.media.getOrNull(pageIndex)
            ?: return@withContext Result.failure(IOException("صفحة الدرس المطلوبة غير موجودة"))
        if (readNotebookRecords(context) is NotebookReadResult.Invalid) {
            return@withContext Result.failure(IOException("ملف دفتر الطالب تالف؛ تم الاحتفاظ بنسخة للاستعادة ولا يمكن الكتابة فوقها"))
        }

        val offlineDir = getOfflineDir(context)
        val destination = File(offlineDir, "board_" + content.id + "_" + pageIndex + ".jpg")
        if (destination.exists() && destination.length() > 0) {
            val saved = persistCachedPageRecord(context, content, pageIndex, destination)
            return@withContext if (saved) Result.success(destination)
            else Result.failure(IOException("الصورة موجودة، لكن تعذر تحديث سجل دفتر الطالب بأمان"))
        }

        val partial = File(offlineDir, destination.name + ".part")
        var connection: java.net.HttpURLConnection? = null
        try {
            partial.delete()
            val source = mediaItem.url.trim()
            val remoteUrl = resolveRemoteMediaUrl(source)
            val localSource = resolveLocalMediaFile(source)

            if (remoteUrl != null) {
                connection = openAuthenticatedMediaConnection(remoteUrl, content.groupId)
                val status = connection!!.responseCode
                if (status !in 200..299) {
                    throw IOException("تعذر تنزيل صفحة الدرس (HTTP " + status + ")")
                }
                val mimeType = connection!!.contentType?.substringBefore(';')?.trim()?.lowercase()
                if (mimeType != null && !mimeType.startsWith("image/") &&
                    mimeType != "application/octet-stream"
                ) {
                    throw IOException("استجابة الخادم ليست صورة صالحة")
                }
                connection!!.inputStream.use { input ->
                    FileOutputStream(partial).use { output -> input.copyTo(output) }
                }
            } else if (localSource != null && localSource.exists() && localSource.length() > 0) {
                FileInputStream(localSource).use { input ->
                    FileOutputStream(partial).use { output -> input.copyTo(output) }
                }
            } else {
                throw IOException("مصدر صورة الدرس غير متاح")
            }

            if (!partial.exists() || partial.length() <= 0) {
                throw IOException("تم استلام ملف صورة فارغ")
            }
            if (destination.exists() && !destination.delete()) {
                throw IOException("تعذر استبدال نسخة الصورة المحلية")
            }
            if (!partial.renameTo(destination)) {
                partial.copyTo(destination, overwrite = true)
                partial.delete()
            }
            if (!destination.exists() || destination.length() <= 0) {
                throw IOException("فشل حفظ الصورة في التخزين المحلي")
            }

            val saved = persistCachedPageRecord(context, content, pageIndex, destination)
            if (!saved) throw IOException("تم تنزيل الصورة، لكن تعذر حفظ سجل الدفتر بأمان")
            Result.success(destination)
        } catch (error: Exception) {
            partial.delete()
            if (destination.exists() && destination.length() <= 0) destination.delete()
            Log.w(TAG, "Failed to load lesson page " + pageIndex + " for " + content.id + ": " + error.message, error)
            Result.failure(error)
        } finally {
            connection?.disconnect()
        }
    }
    /**
     * Downloads and permanently caches all whiteboard/lesson captures to local storage.
     * Respects section privacy by attaching the student's Authorization token and Group ID.
     */
    suspend fun cacheAllLessonPagesLocally(context: Context, content: ContentItem): Result<LessonCachingResult> = withContext(Dispatchers.IO) {
        val loaded = readNotebookRecords(context)
        if (loaded is NotebookReadResult.Invalid) {
            return@withContext Result.failure(IOException("ملف دفتر الطالب تالف؛ تم الاحتفاظ بنسخة للاستعادة ولا يمكن الكتابة فوقها"))
        }
        val existing = (loaded as NotebookReadResult.Valid).records.firstOrNull { it.contentId == content.id }
        val offlineDir = getOfflineDir(context)
        val existingRecord = existing ?: TranscribedLessonRecord(
            contentId = content.id,
            subjectId = content.subjectId,
            subjectName = content.subjectName,
            title = content.title,
            studyDate = content.studyDate,
            totalPages = content.media.size
        )

        val pathsMap = existingRecord.localImagePaths.toMutableMap()
        var newlyDownloadedCount = 0
        var failedCount = 0

        content.media.forEachIndexed { index, mediaItem ->
            val source = mediaItem.url.trim()
            val remoteUrl = resolveRemoteMediaUrl(source)
            val localSource = resolveLocalMediaFile(source)
            val destFile = File(offlineDir, "board_${content.id}_${index}.jpg")

            if (destFile.exists() && destFile.length() > 0) {
                pathsMap[index] = destFile.absolutePath
                return@forEachIndexed
            }

            val partialFile = File(offlineDir, "${destFile.name}.part")
            try {
                partialFile.delete()
                if (remoteUrl != null) {
                    val connection = openAuthenticatedMediaConnection(
                        remoteUrl = remoteUrl,
                        groupId = content.groupId
                    )
                    try {
                        val statusCode = connection.responseCode
                        if (statusCode !in 200..299) {
                            throw IOException("تعذر تنزيل الصورة (HTTP $statusCode)")
                        }
                        val mimeType = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
                        if (mimeType != null && !mimeType.startsWith("image/")) {
                            throw IOException("استجابة الخادم ليست ملف صورة صالحاً")
                        }
                        connection.inputStream.use { input ->
                            FileOutputStream(partialFile).use { output -> input.copyTo(output) }
                        }
                    } finally {
                        connection.disconnect()
                    }
                } else if (localSource != null && localSource.exists() && localSource.length() > 0) {
                    FileInputStream(localSource).use { input ->
                        FileOutputStream(partialFile).use { output -> input.copyTo(output) }
                    }
                } else {
                    throw IOException("ملف الصورة غير متاح")
                }

                if (!partialFile.exists() || partialFile.length() <= 0) {
                    throw IOException("تم استلام ملف صورة فارغ")
                }
                if (destFile.exists() && !destFile.delete()) {
                    throw IOException("تعذر استبدال نسخة الصورة المحلية")
                }
                if (!partialFile.renameTo(destFile)) {
                    partialFile.copyTo(destFile, overwrite = true)
                    partialFile.delete()
                }
                if (!destFile.exists() || destFile.length() <= 0) {
                    throw IOException("فشل حفظ الصورة في التخزين المحلي")
                }

                pathsMap[index] = destFile.absolutePath
                newlyDownloadedCount++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cache lesson image at index $index: ${e.message}", e)
                partialFile.delete()
                if (destFile.exists() && destFile.length() == 0L) destFile.delete()
                failedCount++
            }
        }

        if (!persistCachedPagesRecord(context, content, pathsMap)) {
            return@withContext Result.failure(IOException("تم تنزيل الصور، لكن تعذر حفظ سجل الدفتر بأمان"))
        }

        val totalPages = content.media.size
        val successfulCount = pathsMap.keys.count { it in content.media.indices }
        val isAllOk = totalPages > 0 && successfulCount >= totalPages && failedCount == 0
        val cachingResult = LessonCachingResult(
            totalPages = totalPages,
            successfulCount = successfulCount,
            failedCount = failedCount,
            newlyDownloadedCount = newlyDownloadedCount,
            isAllSucceeded = isAllOk
        )

        if (totalPages > 0 && successfulCount == 0) {
            Result.failure(Exception(cachingResult.summaryMessage))
        } else {
            Result.success(cachingResult)
        }
    }

    /**
     * Saves a lesson board image directly to the user's public device Gallery (Pictures/Tanweer)
     * using Android's MediaStore API so the student can access it outside the app.
     */
    suspend fun saveImageToGallery(
        context: Context,
        imageSource: String,
        title: String,
        pageIndex: Int,
        groupId: String = ""
    ): Result<android.net.Uri> = withContext(Dispatchers.IO) {
        var galleryUri: android.net.Uri? = null
        var connection: java.net.HttpURLConnection? = null
        try {
            val resolver = context.contentResolver
            val safeTitle = title.replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF]"), "_").take(30)
            val fileName = "Tanweer_${safeTitle}_P${pageIndex + 1}_${System.currentTimeMillis()}.jpg"
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Tanweer")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("تعذر إنشاء ملف الصورة في معرض الصور")
            galleryUri = uri

            resolver.openOutputStream(uri, "w")?.use { output ->
                val remoteUrl = resolveRemoteMediaUrl(imageSource.trim())
                if (remoteUrl != null) {
                    connection = openAuthenticatedMediaConnection(remoteUrl, groupId)
                    val statusCode = connection!!.responseCode
                    if (statusCode !in 200..299) {
                        throw IOException("تعذر تنزيل الصورة (HTTP $statusCode)")
                    }
                    val mimeType = connection!!.contentType?.substringBefore(';')?.trim()?.lowercase()
                    if (mimeType != null && !mimeType.startsWith("image/")) {
                        throw IOException("استجابة الخادم ليست صورة صالحة")
                    }
                    connection!!.inputStream.use { input -> input.copyTo(output) }
                } else {
                    val sourceFile = resolveLocalMediaFile(imageSource.trim())
                        ?: throw IOException("مصدر الصورة غير صالح")
                    if (!sourceFile.exists() || sourceFile.length() <= 0) {
                        throw IOException("ملف الصورة غير موجود على الجهاز")
                    }
                    FileInputStream(sourceFile).use { input -> input.copyTo(output) }
                }
            } ?: throw IOException("تعذر فتح مجرى الكتابة للصورة")

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val completedValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                }
                val changed = resolver.update(uri, completedValues, null, null)
                if (changed <= 0) throw IOException("تعذر إكمال حفظ الصورة في المعرض")
            }

            Result.success(uri)
        } catch (e: Exception) {
            galleryUri?.let { try { context.contentResolver.delete(it, null, null) } catch (_: Exception) {} }
            Log.e(TAG, "Failed saving image to gallery", e)
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun resolveRemoteMediaUrl(source: String): String? {
        if (source.startsWith("https://", ignoreCase = true) || source.startsWith("http://", ignoreCase = true)) {
            return source
        }
        if (source.startsWith("/api/", ignoreCase = true) || source.startsWith("api/", ignoreCase = true)) {
            return NetworkModule.resolveApiUrl(source)
        }
        return null
    }

    private fun resolveLocalMediaFile(source: String): File? {
        val normalized = if (source.startsWith("file://", ignoreCase = true)) {
            source.removePrefix("file://")
        } else {
            source
        }
        if (normalized.isBlank() || normalized.startsWith("content://", ignoreCase = true)) return null
        return try { File(normalized) } catch (_: Exception) { null }
    }

    private fun openAuthenticatedMediaConnection(
        remoteUrl: String,
        groupId: String
    ): java.net.HttpURLConnection {
        // Use the same live in-memory session token as Retrofit's auth interceptor.
        // The token is stored in Room and mirrored into NetworkModule, not SharedPreferences.
        val requestHeaders = buildMediaRequestHeaders(
            remoteUrl = remoteUrl,
            apiBaseUrl = NetworkModule.apiBaseUrl,
            authToken = NetworkModule.currentAuthToken(),
            groupId = groupId
        )
        val isTanweerApiOrigin = requestHeaders.containsKey("Authorization")

        return (URL(remoteUrl).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            requestMethod = "GET"
            useCaches = false
            // Do not forward bearer credentials to external hosts or through internal redirects.
            instanceFollowRedirects = !isTanweerApiOrigin
            setRequestProperty("Accept", "image/*")
            requestHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
        }
    }

    /**
     * Saves all whiteboard captures of a lesson directly to the user's Gallery (Pictures/Tanweer).
     */
    suspend fun saveAllImagesToGallery(
        context: Context,
        content: ContentItem
    ): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        val offlineDir = getOfflineDir(context)
        var savedCount = 0
        var failedCount = 0

        content.media.forEachIndexed { index, mediaItem ->
            val localFile = File(offlineDir, "board_${content.id}_${index}.jpg")
            val sourcePath = if (localFile.exists() && localFile.length() > 0) {
                localFile.absolutePath
            } else {
                mediaItem.url
            }

            val res = saveImageToGallery(
                context = context,
                imageSource = sourcePath,
                title = content.title,
                pageIndex = index,
                groupId = content.groupId
            )
            if (res.isSuccess) {
                savedCount++
            } else {
                failedCount++
            }
        }

        if (savedCount > 0) {
            Result.success(Pair(savedCount, failedCount))
        } else {
            Result.failure(Exception("تعذر حفظ اللوحات في المعرض، يرجى المحاولة لاحقاً"))
        }
    }

    /**
     * Clears all offline notebook records, transcribed lesson folders, and cached whiteboard images
     * to ensure total user data isolation upon logout or account switch.
     */
    @Synchronized
    fun clearAllUserData(context: Context) {
        try {
            // 1. Delete student_notebook_records.json
            val recordsFile = getStorageFile(context)
            if (recordsFile.exists()) {
                recordsFile.delete()
            }

            // 2. Delete transcribed_lessons directory and all cached board images
            val offlineDir = File(context.filesDir, DIR_NAME)
            if (offlineDir.exists()) {
                offlineDir.deleteRecursively()
            }

            // 3. Clear any temporary lesson files in cache directory
            val cacheLessonsDir = File(context.cacheDir, "lessons")
            if (cacheLessonsDir.exists()) {
                cacheLessonsDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing student notebook user data", e)
        }
    }
}
