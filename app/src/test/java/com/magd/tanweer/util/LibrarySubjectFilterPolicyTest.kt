package com.magd.tanweer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySubjectFilterPolicyTest {
    @Test
    fun parentFilterMatchesOnlyTheExactCanonicalSubjectByDefault() {
        assertTrue(LibrarySubjectFilterPolicy.matches("biology", "biology"))
        assertFalse(LibrarySubjectFilterPolicy.matches("biology_act", "biology"))
        assertFalse(LibrarySubjectFilterPolicy.matches("chemistry", "biology"))
    }

    @Test
    fun parentCanIncludeOnlyExplicitlyMappedSubSubjectsWhenRequested() {
        assertTrue(
            LibrarySubjectFilterPolicy.matches(
                bookSubjectId = "biology_act",
                selectedSubjectId = "biology",
                includeSubSubjects = true
            )
        )
        assertTrue(
            LibrarySubjectFilterPolicy.matches(
                bookSubjectId = "chemistry_act",
                selectedSubjectId = "chemistry",
                includeSubSubjects = true
            )
        )
        assertFalse(
            LibrarySubjectFilterPolicy.matches(
                bookSubjectId = "physics_act",
                selectedSubjectId = "biology",
                includeSubSubjects = true
            )
        )
    }

    @Test
    fun selectingAChildDoesNotIncludeItsParent() {
        assertFalse(
            LibrarySubjectFilterPolicy.matches(
                bookSubjectId = "biology",
                selectedSubjectId = "biology_act",
                includeSubSubjects = true
            )
        )
    }

    @Test
    fun aliasesAreComparedAfterCanonicalNormalization() {
        assertTrue(LibrarySubjectFilterPolicy.matches("neighborhoods", "biology"))
        assertFalse(LibrarySubjectFilterPolicy.matches("neighborhoodsactivities", "biology"))
        assertFalse(LibrarySubjectFilterPolicy.matches("biology_chapter", "biology"))
        assertTrue(LibrarySubjectFilterPolicy.matches("biology-act", "biology_act"))
        assertTrue(
            LibrarySubjectFilterPolicy.matches(
                "neighborhoodsactivities",
                "biology",
                includeSubSubjects = true
            )
        )
    }

    @Test
    fun availableChildCountIsDerivedOnlyFromExplicitChildren() {
        assertTrue(
            LibrarySubjectFilterPolicy.hasAvailableSubSubjects(
                "biology",
                listOf("biology", "biology_act")
            )
        )
        assertEquals(
            2,
            LibrarySubjectFilterPolicy.countAvailableSubSubjectBooks(
                "biology",
                listOf("biology", "biology_act", "biology_act", "chemistry_act")
            )
        )
        assertFalse(
            LibrarySubjectFilterPolicy.hasAvailableSubSubjects(
                "history",
                listOf("history", "biology_act")
            )
        )
    }
}
