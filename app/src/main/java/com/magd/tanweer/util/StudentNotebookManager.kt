package com.magd.tanweer.util

import android.content.Context
import android.util.Log
import com.magd.tanweer.data.model.ContentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
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

    @Synchronized
    fun getAllNotebookLessons(context: Context): List<TranscribedLessonRecord> {
        val file = getStorageFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<TranscribedLessonRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val completedPagesSet = mutableSetOf<Int>()
                val compArr = obj.optJSONArray("completedPages") ?: JSONArray()
                for (j in 0 until compArr.length()) {
                    completedPagesSet.add(compArr.getInt(j))
                }

                val imagePathsMap = mutableMapOf<Int, String>()
                val imgObj = obj.optJSONObject("localImagePaths") ?: JSONObject()
                val keys = imgObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    k.toIntOrNull()?.let { pageIdx ->
                        imagePathsMap[pageIdx] = imgObj.getString(k)
                    }
                }

                list.add(
                    TranscribedLessonRecord(
                        contentId = obj.getString("contentId"),
                        subjectId = obj.optString("subjectId", ""),
                        subjectName = obj.optString("subjectName", ""),
                        title = obj.optString("title", ""),
                        studyDate = obj.optString("studyDate", ""),
                        totalPages = obj.optInt("totalPages", 0),
                        completedPages = completedPagesSet,
                        localImagePaths = imagePathsMap,
                        transcribedAt = obj.optLong("transcribedAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read notebook records", e)
            emptyList()
        }
    }

    @Synchronized
    fun getLessonRecord(context: Context, contentId: String): TranscribedLessonRecord? {
        return getAllNotebookLessons(context).find { it.contentId == contentId }
    }

    @Synchronized
    fun saveLessonRecord(context: Context, record: TranscribedLessonRecord) {
        val current = getAllNotebookLessons(context).toMutableList()
        val index = current.indexOfFirst { it.contentId == record.contentId }
        if (index >= 0) {
            current[index] = record
        } else {
            current.add(0, record)
        }

        try {
            val array = JSONArray()
            current.forEach { item ->
                val obj = JSONObject().apply {
                    put("contentId", item.contentId)
                    put("subjectId", item.subjectId)
                    put("subjectName", item.subjectName)
                    put("title", item.title)
                    put("studyDate", item.studyDate)
                    put("totalPages", item.totalPages)
                    put("transcribedAt", item.transcribedAt)

                    val compArr = JSONArray()
                    item.completedPages.forEach { compArr.put(it) }
                    put("completedPages", compArr)

                    val imgObj = JSONObject()
                    item.localImagePaths.forEach { (k, v) -> imgObj.put(k.toString(), v) }
                    put("localImagePaths", imgObj)
                }
                array.put(obj)
            }
            getStorageFile(context).writeText(array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save notebook record", e)
        }
    }

    @Synchronized
    fun markPageCompleted(context: Context, content: ContentItem, pageIndex: Int, isCompleted: Boolean) {
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
        saveLessonRecord(context, updated)
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
     * Downloads and permanently caches all whiteboard/lesson captures to local storage.
     * Respects section privacy by attaching the student's Authorization token and Group ID.
     */
    suspend fun cacheAllLessonPagesLocally(context: Context, content: ContentItem): Result<LessonCachingResult> = withContext(Dispatchers.IO) {
        val offlineDir = getOfflineDir(context)
        val existing = getLessonRecord(context, content.id) ?: TranscribedLessonRecord(
            contentId = content.id,
            subjectId = content.subjectId,
            subjectName = content.subjectName,
            title = content.title,
            studyDate = content.studyDate,
            totalPages = content.media.size
        )

        val pathsMap = existing.localImagePaths.toMutableMap()
        var newlyDownloadedCount = 0
        var failedCount = 0

        val prefs = context.getSharedPreferences("tanweer_prefs", Context.MODE_PRIVATE)
        val authToken = prefs.getString("auth_token", "") ?: ""

        content.media.forEachIndexed { index, mediaItem ->
            val urlStr = mediaItem.url
            val effectiveUrl = when {
                urlStr.startsWith("http://") || urlStr.startsWith("https://") -> urlStr
                urlStr.startsWith("/") -> "https://tanweer.magd.workers.dev$urlStr"
                else -> urlStr
            }
            val destFile = File(offlineDir, "board_${content.id}_${index}.jpg")

            if (destFile.exists() && destFile.length() > 0) {
                pathsMap[index] = destFile.absolutePath
            } else if (effectiveUrl.startsWith("http://") || effectiveUrl.startsWith("https://")) {
                try {
                    val conn = (URL(effectiveUrl).openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 20000
                        if (authToken.isNotBlank()) {
                            setRequestProperty("Authorization", "Bearer $authToken")
                        }
                        if (content.groupId.isNotBlank()) {
                            setRequestProperty("X-Group-Id", content.groupId)
                        }
                    }
                    conn.inputStream.use { input ->
                        FileOutputStream(destFile).use { out ->
                            input.copyTo(out)
                        }
                    }
                    if (destFile.exists() && destFile.length() > 0) {
                        pathsMap[index] = destFile.absolutePath
                        newlyDownloadedCount++
                    } else {
                        destFile.delete()
                        failedCount++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to download board image at index $index: ${e.message}")
                    if (destFile.exists() && destFile.length() == 0L) {
                        destFile.delete()
                    }
                    failedCount++
                }
            } else if (File(effectiveUrl).exists()) {
                pathsMap[index] = effectiveUrl
            } else {
                failedCount++
            }
        }

        val updatedRecord = existing.copy(localImagePaths = pathsMap)
        saveLessonRecord(context, updatedRecord)

        val totalPages = content.media.size
        val successfulCount = pathsMap.size
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
                ?: return@withContext Result.failure(Exception("تعذر إنشاء مسار الصورة في معرض الصور"))

            val prefs = context.getSharedPreferences("tanweer_prefs", Context.MODE_PRIVATE)
            val authToken = prefs.getString("auth_token", "") ?: ""

            resolver.openOutputStream(uri)?.use { out ->
                if (imageSource.startsWith("http://") || imageSource.startsWith("https://")) {
                    val conn = (URL(imageSource).openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 20000
                        if (authToken.isNotBlank()) {
                            setRequestProperty("Authorization", "Bearer $authToken")
                        }
                        if (groupId.isNotBlank()) {
                            setRequestProperty("X-Group-Id", groupId)
                        }
                    }
                    conn.inputStream.use { input ->
                        input.copyTo(out)
                    }
                } else {
                    java.io.FileInputStream(File(imageSource)).use { input ->
                        input.copyTo(out)
                    }
                }
            } ?: return@withContext Result.failure(Exception("تعذر فتح مجرى الكتابة للصورة"))

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Result.success(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving image to gallery", e)
            Result.failure(e)
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
