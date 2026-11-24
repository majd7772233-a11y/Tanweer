package com.magd.tanweer.util

import com.magd.tanweer.data.model.BookItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamBookNavigationPolicyTest {
    private fun book(
        id: String,
        gradeId: Int,
        subjectId: String
    ) = BookItem(
        id = id,
        gradeId = gradeId,
        subjectId = subjectId,
        title = id
    )

    @Test
    fun opensTheOnlyBookForTheExactGradeAndSubject() {
        val matching = book("quran_grade_10", 10, "quran")
        val target = ExamBookNavigationPolicy.resolve(
            examSubjectId = "quran",
            gradeId = 10,
            books = listOf(matching, book("quran_grade_11", 11, "quran")),
            hasAuthoritativeCatalog = true
        )

        assertTrue(target is ExamBookNavigationTarget.OpenBook)
        assertEquals(matching, (target as ExamBookNavigationTarget.OpenBook).book)
    }

    @Test
    fun resolvesKnownSubjectAliasesWithoutSubstringMatching() {
        val matching = book("english", 10, "englishpupils")
        val target = ExamBookNavigationPolicy.resolve(
            examSubjectId = "english",
            gradeId = 10,
            books = listOf(matching),
            hasAuthoritativeCatalog = true
        )

        assertTrue(target is ExamBookNavigationTarget.OpenBook)
        assertEquals(matching, (target as ExamBookNavigationTarget.OpenBook).book)
    }

    @Test
    fun multipleVolumesOpenTheLibraryFilteredToTheExamSubject() {
        val target = ExamBookNavigationPolicy.resolve(
            examSubjectId = "biology",
            gradeId = 11,
            books = listOf(
                book("biology_part_1", 11, "biology"),
                book("biology_part_2", 11, "biology"),
                book("biology_other_grade", 10, "biology")
            ),
            hasAuthoritativeCatalog = true
        )

        assertEquals(
            ExamBookNavigationTarget.OpenSubjectLibrary(11, "biology"),
            target
        )
    }

    @Test
    fun confirmedMissingBookOpensTheGeneralLibrary() {
        val target = ExamBookNavigationPolicy.resolve(
            examSubjectId = "quran",
            gradeId = 10,
            books = listOf(book("arabic", 10, "arabic")),
            hasAuthoritativeCatalog = true
        )

        assertEquals(ExamBookNavigationTarget.OpenGeneralLibrary(10), target)
    }

    @Test
    fun unconfirmedEmptyCatalogKeepsTheRequestedSubjectFilter() {
        val target = ExamBookNavigationPolicy.resolve(
            examSubjectId = "quran",
            gradeId = 10,
            books = emptyList(),
            hasAuthoritativeCatalog = false
        )

        assertEquals(
            ExamBookNavigationTarget.OpenSubjectLibrary(10, "quran"),
            target
        )
    }
}
