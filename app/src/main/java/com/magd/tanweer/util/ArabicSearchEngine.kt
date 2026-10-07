package com.magd.tanweer.util

import android.content.Context
import com.magd.tanweer.data.local.ChatMessageEntity
import com.magd.tanweer.data.model.*

object ArabicSearchEngine {

    // Removes Arabic tashkeel (diacritics), tatweel, and normalizes letters
    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var s = input.trim()
        // Remove Tashkeel / Harakat
        s = s.replace(Regex("[\u064B-\u065F\u0670]"), "")
        // Remove Tatweel (Kashida)
        s = s.replace("\u0640", "")
        // Normalize Alef variants: أ, إ, آ, ٱ -> ا
        s = s.replace(Regex("[أإآٱ]"), "ا")
        // Normalize Yaa variants: ى -> ي
        s = s.replace("ى", "ي")
        // Normalize Taa Marbuta: ة -> ه
        s = s.replace("ة", "ه")
        // Normalize Hamza variants: ؤ -> و, ئ -> ي
        s = s.replace("ؤ", "و").replace("ئ", "ي")
        // Lowercase and strip special punctuations
        s = s.lowercase().replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    fun tokenize(normalizedText: String): List<String> {
        return normalizedText.split(" ").filter { it.isNotBlank() && it.length > 1 }
    }

    fun calculateRelevance(
        query: String,
        title: String,
        subject: String? = null,
        body: String? = null,
        extra: String? = null
    ): Int {
        val qNorm = normalize(query)
        if (qNorm.isBlank()) return 1

        val qTokens = tokenize(qNorm)
        if (qTokens.isEmpty()) return 0

        val tNorm = normalize(title)
        val sNorm = normalize(subject)
        val bNorm = normalize(body)
        val eNorm = normalize(extra)

        var score = 0

        // Exact phrase match in title (Highest relevance)
        if (tNorm.contains(qNorm)) {
            score += 120
            if (tNorm == qNorm) score += 50
        }

        // Exact phrase match in subject
        if (sNorm.contains(qNorm)) {
            score += 60
        }

        // Exact phrase match in body
        if (bNorm.contains(qNorm)) {
            score += 40
        }

        // Multi-token matches
        for (tok in qTokens) {
            if (tNorm.contains(tok)) {
                score += if (tNorm.split(" ").contains(tok)) 35 else 20
            }
            if (sNorm.contains(tok)) {
                score += if (sNorm.split(" ").contains(tok)) 25 else 15
            }
            if (bNorm.contains(tok)) {
                score += 15
            }
            if (eNorm.contains(tok)) {
                score += 10
            }
        }

        return score
    }

    // Search History persistence helpers
    private const val PREF_KEY_SEARCH_HISTORY = "tanweer_search_history"
    private const val MAX_HISTORY_ITEMS = 8

    fun getRecentSearches(context: Context): List<String> {
        val prefs = context.getSharedPreferences("tanweer_search_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString(PREF_KEY_SEARCH_HISTORY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|||").filter { it.isNotBlank() }
    }

    fun saveRecentSearch(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        val current = getRecentSearches(context).toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val limited = current.take(MAX_HISTORY_ITEMS)
        val joined = limited.joinToString("|||")
        context.getSharedPreferences("tanweer_search_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY_SEARCH_HISTORY, joined)
            .apply()
    }

    fun clearRecentSearches(context: Context) {
        context.getSharedPreferences("tanweer_search_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_KEY_SEARCH_HISTORY)
            .apply()
    }

    val suggestedKeywords = listOf(
        "رياضيات",
        "فيزياء",
        "كيمياء",
        "أحياء",
        "لغة عربية",
        "اختبار",
        "واجب",
        "سبورة",
        "فعالية",
        "استفسار"
    )
}
