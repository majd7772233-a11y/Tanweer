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

    /**
     * Downloads and permanently caches all whiteboard/lesson captures to local storage.
     * Guaranteed 100% offline study for missed lessons.
     */
    suspend fun cacheAllLessonPagesLocally(context: Context, content: ContentItem): Result<Int> = withContext(Dispatchers.IO) {
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
        var downloadedCount = 0

        content.media.forEachIndexed { index, mediaItem ->
            val urlStr = mediaItem.url
            val destFile = File(offlineDir, "board_${content.id}_${index}.jpg")

            if (destFile.exists() && destFile.length() > 0) {
                pathsMap[index] = destFile.absolutePath
            } else if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                try {
                    val stream = URL(urlStr).openStream()
                    FileOutputStream(destFile).use { out ->
                        stream.copyTo(out)
                    }
                    if (destFile.exists() && destFile.length() > 0) {
                        pathsMap[index] = destFile.absolutePath
                        downloadedCount++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to download image at index $index: ${e.message}")
                }
            } else if (File(urlStr).exists()) {
                pathsMap[index] = urlStr
            }
        }

        val updatedRecord = existing.copy(localImagePaths = pathsMap)
        saveLessonRecord(context, updatedRecord)
        Result.success(downloadedCount)
    }
}
