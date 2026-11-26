package com.magd.tanweer.data.repository

import com.magd.tanweer.data.model.MediaItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stable Room representation for lesson media.
 * Reads legacy arrays of URL strings and current arrays of full media objects.
 */
internal object ContentMediaJson {
    fun parse(json: String?): List<MediaItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    when (val value = array.opt(index)) {
                        is JSONObject -> {
                            val url = value.optString("url", "").trim()
                            if (url.isNotEmpty()) {
                                add(MediaItem(
                                    id = value.optString("id", "media_$index")
                                        .takeIf { it.isNotBlank() && it != "null" } ?: "media_$index",
                                    pageOrder = value.optInt("pageOrder", index + 1).coerceAtLeast(1),
                                    url = url,
                                    mimeType = value.optString("mimeType", "image/jpeg")
                                        .takeIf { it.isNotBlank() && it != "null" } ?: "image/jpeg",
                                    fileSize = value.optInt("fileSize", 0).coerceAtLeast(0),
                                    checksum = value.optString("checksum", "")
                                        .takeIf { it.isNotBlank() && it != "null" }
                                ))
                            }
                        }
                        is String -> {
                            val url = value.trim()
                            if (url.isNotEmpty()) {
                                add(MediaItem(id = "media_$index", pageOrder = index + 1, url = url))
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun serialize(media: List<MediaItem>): String {
        if (media.isEmpty()) return "[]"
        val array = JSONArray()
        media.sortedBy { it.pageOrder }.forEach { item ->
            if (item.url.isNotBlank()) {
                array.put(JSONObject().apply {
                    put("id", item.id)
                    put("pageOrder", item.pageOrder)
                    put("url", item.url)
                    put("mimeType", item.mimeType)
                    put("fileSize", item.fileSize.coerceAtLeast(0))
                    item.checksum?.takeIf { it.isNotBlank() }?.let { put("checksum", it) }
                })
            }
        }
        return array.toString()
    }
}
