package com.magd.tanweer.data.repository

import com.magd.tanweer.data.local.BookNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** Waits for Room's book-note Flow to emit the exact row that was just inserted. */
internal object BookNoteFlowConfirmation {
    fun requireMatchingRequest(
        existing: BookNoteEntity,
        bookId: String,
        pageIndex: Int,
        noteText: String
    ): BookNoteEntity {
        require(
            existing.bookId == bookId &&
                existing.pageIndex == pageIndex &&
                existing.noteText == noteText
        ) { "Note ID already belongs to a different saved note" }
        return existing
    }

    suspend fun awaitPersistedNote(
        notes: Flow<List<BookNoteEntity>>,
        expectedNoteId: String,
        timeoutMillis: Long = 5_000L
    ): BookNoteEntity {
        require(expectedNoteId.isNotBlank()) { "Expected note ID must not be blank" }
        require(timeoutMillis > 0L) { "Timeout must be positive" }

        val emittedNotes = withTimeout(timeoutMillis) {
            notes.first { rows -> rows.any { it.id == expectedNoteId } }
        }
        return emittedNotes.first { it.id == expectedNoteId }
    }
}
