package com.magd.tanweer.util

/**
 * Exact canonical subject matching for the library.
 *
 * Parent/child membership is opt-in and explicit. A parent filter never
 * absorbs a sub-subject merely because its identifier contains the parent ID.
 */
object LibrarySubjectFilterPolicy {
    private val aliases = mapOf(
        "neighborhoods" to "biology",
        "neighborhoodsactivities" to "biology_act",
        "chemistryactivities" to "chemistry_act",
        "physicsactivities" to "physics_act",
        "englishwork" to "english_work",
        "englishpupils" to "english"
    )

    private val subSubjectsByParent = mapOf(
        "biology" to setOf("biology_act"),
        "chemistry" to setOf("chemistry_act"),
        "physics" to setOf("physics_act"),
        "english" to setOf("english_work")
    )

    /** Normalize complete, known aliases only; never infer a subject from a substring. */
    fun canonicalSubjectId(rawSubjectId: String): String {
        val key = rawSubjectId.trim().lowercase().replace("-", "_")
        return aliases[key] ?: key
    }

    fun subSubjectIds(parentSubjectId: String): Set<String> =
        subSubjectsByParent[canonicalSubjectId(parentSubjectId)].orEmpty()

    fun hasAvailableSubSubjects(
        parentSubjectId: String,
        availableSubjectIds: Iterable<String>
    ): Boolean {
        val available = availableSubjectIds.mapTo(mutableSetOf(), ::canonicalSubjectId)
        return subSubjectIds(parentSubjectId).any { it in available }
    }

    fun countAvailableSubSubjectBooks(
        parentSubjectId: String,
        bookSubjectIds: Iterable<String>
    ): Int {
        val childIds = subSubjectIds(parentSubjectId)
        if (childIds.isEmpty()) return 0
        return bookSubjectIds.count { canonicalSubjectId(it) in childIds }
    }

    fun matches(
        bookSubjectId: String,
        selectedSubjectId: String,
        includeSubSubjects: Boolean = false
    ): Boolean {
        val bookId = canonicalSubjectId(bookSubjectId)
        val selectedId = canonicalSubjectId(selectedSubjectId)
        return bookId == selectedId ||
            (includeSubSubjects && bookId in subSubjectIds(selectedId))
    }
}
