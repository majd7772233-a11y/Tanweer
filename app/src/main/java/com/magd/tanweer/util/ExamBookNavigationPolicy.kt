package com.magd.tanweer.util

import com.magd.tanweer.data.model.BookItem

sealed class ExamBookNavigationTarget {
    data class OpenBook(val book: BookItem) : ExamBookNavigationTarget()
    data class OpenSubjectLibrary(val gradeId: Int, val subjectId: String) : ExamBookNavigationTarget()
    data class OpenGeneralLibrary(val gradeId: Int) : ExamBookNavigationTarget()
}

/** Resolves an exam's book action only from an exact grade + canonical subject association. */
object ExamBookNavigationPolicy {
    fun resolve(
        examSubjectId: String,
        gradeId: Int,
        books: List<BookItem>,
        hasAuthoritativeCatalog: Boolean
    ): ExamBookNavigationTarget {
        val subjectId = LibrarySubjectFilterPolicy.canonicalSubjectId(examSubjectId)
        if (subjectId.isBlank() || gradeId <= 0) {
            return ExamBookNavigationTarget.OpenGeneralLibrary(gradeId.coerceAtLeast(1))
        }

        val matchingBooks = books.filter { book ->
            book.gradeId == gradeId &&
                LibrarySubjectFilterPolicy.canonicalSubjectId(book.subjectId) == subjectId
        }

        return when (matchingBooks.size) {
            1 -> ExamBookNavigationTarget.OpenBook(matchingBooks.single())
            in 2..Int.MAX_VALUE -> ExamBookNavigationTarget.OpenSubjectLibrary(gradeId, subjectId)
            else -> if (hasAuthoritativeCatalog) {
                ExamBookNavigationTarget.OpenGeneralLibrary(gradeId)
            } else {
                // An empty/partial Room emission is not proof that the subject has no book.
                ExamBookNavigationTarget.OpenSubjectLibrary(gradeId, subjectId)
            }
        }
    }
}
