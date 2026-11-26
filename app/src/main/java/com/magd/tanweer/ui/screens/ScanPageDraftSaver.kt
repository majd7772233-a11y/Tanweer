package com.magd.tanweer.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.saveable.Saver
import com.magd.tanweer.util.ImageProcessingUtils
import com.magd.tanweer.util.ProcessedPageResult
import org.json.JSONArray
import org.json.JSONObject

internal fun shouldResumeScanPageProcessing(
    processedResultAvailable: Boolean,
    sourceMediaAvailable: Boolean
): Boolean = !processedResultAvailable && sourceMediaAvailable
internal data class ScanPageDraftRecord(
    val id: String,
    val sourceUri: String?,
    val processedFilePath: String?,
    val rotation: Float,
    val autoEnhance: Boolean,
    val blackAndWhite: Boolean,
    val isProcessing: Boolean
)

/**
 * Saves only small media metadata and private file paths in Compose SavedState.
 * Full image bytes and Bitmap objects stay in app-private files / bounded previews.
 */
internal fun <T> scanPageListSaver(
    toRecord: (T) -> ScanPageDraftRecord,
    fromRecord: (
        record: ScanPageDraftRecord,
        processedResult: ProcessedPageResult?,
        previewBitmap: Bitmap?,
        shouldResumeProcessing: Boolean
    ) -> T
): Saver<List<T>, String> = Saver(
    save = { pages ->
        JSONArray().apply {
            pages.forEach { page ->
                val record = toRecord(page)
                put(
                    JSONObject()
                        .put("id", record.id)
                        .put("sourceUri", record.sourceUri)
                        .put("processedFilePath", record.processedFilePath)
                        .put("rotation", record.rotation.toDouble())
                        .put("autoEnhance", record.autoEnhance)
                        .put("blackAndWhite", record.blackAndWhite)
                        .put("isProcessing", record.isProcessing)
                )
            }
        }.toString()
    },
    restore = { encoded ->
        try {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val sourceUri = item.optString("sourceUri").takeUnless {
                        it.isBlank() || it == "null"
                    }
                    val processedPath = item.optString("processedFilePath").takeUnless {
                        it.isBlank() || it == "null"
                    }
                    val record = ScanPageDraftRecord(
                        id = item.optString("id").takeIf { it.isNotBlank() }
                            ?: "restored-$index",
                        sourceUri = sourceUri,
                        processedFilePath = processedPath,
                        rotation = item.optDouble("rotation", 0.0).toFloat(),
                        autoEnhance = item.optBoolean("autoEnhance", true),
                        blackAndWhite = item.optBoolean("blackAndWhite", false),
                        isProcessing = item.optBoolean("isProcessing", false)
                    )
                    val processed = ImageProcessingUtils.restoreProcessedPageResult(processedPath)
                    val preview = ImageProcessingUtils.decodeProcessedPreview(processedPath)
                    val sourceExists = sourceUri?.let { value ->
                        runCatching {
                            val uri = Uri.parse(value)
                            uri.scheme != "file" || uri.path?.let { java.io.File(it).isFile } == true
                        }.getOrDefault(false)
                    } == true
                    val shouldResume = shouldResumeScanPageProcessing(processed != null, sourceExists)
                    add(fromRecord(record, processed, preview, shouldResume))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
)