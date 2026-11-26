package com.magd.tanweer.ui.screens

import java.util.Locale

/** Case-insensitive matcher for the static sections-hub keyword catalogue. */
internal object ExtraSectionsHubSearchPolicy {
    fun matches(query: String, searchableText: String): Boolean {
        val normalizedQuery = normalize(query)
        return normalizedQuery.isEmpty() || normalize(searchableText).contains(normalizedQuery)
    }

    private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)
}
