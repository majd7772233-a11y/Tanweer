package com.magd.tanweer.data.repository

import com.magd.tanweer.data.local.BookNoteEntity
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BookNoteFlowConfirmationTest {
    @Test
    fun waitsUntilTheExpectedInsertedIdAppearsInRoomFlow() {
        runBlocking {
            val existing = BookNoteEntity("old", "book", 1, "same text")
            val inserted = BookNoteEntity("new", "book", 1, "same text")
            val result = BookNoteFlowConfirmation.awaitPersistedNote(
                notes = flowOf(emptyList(), listOf(existing), listOf(existing, inserted)),
                expectedNoteId = inserted.id
            )
            assertEquals(inserted, result)
        }
    }

    @Test
    fun matchesByIdRatherThanTextOrPage() {
        runBlocking {
            val first = BookNoteEntity("first", "book", 2, "QA_NOTE_PAGE_2")
            val second = BookNoteEntity("second", "book", 2, "QA_NOTE_PAGE_2")
            val result = BookNoteFlowConfirmation.awaitPersistedNote(
                notes = flowOf(listOf(first, second)),
                expectedNoteId = second.id
            )
            assertEquals(second.id, result.id)
            assertEquals(second.noteText, result.noteText)
        }
    }

    @Test
    fun acceptsRetryOnlyWhenTheStableIdBelongsToTheSameNoteRequest() {
        val saved = BookNoteEntity("stable-id", "book", 2, "QA_NOTE_PAGE_2")
        assertEquals(
            saved,
            BookNoteFlowConfirmation.requireMatchingRequest(
                existing = saved,
                bookId = "book",
                pageIndex = 2,
                noteText = "QA_NOTE_PAGE_2"
            )
        )
    }

    @Test
    fun rejectsReusingStableIdForDifferentContentOrPage() {
        val saved = BookNoteEntity("stable-id", "book", 2, "original")
        val mismatches = listOf(
            Triple("book", 2, "changed"),
            Triple("book", 3, "original"),
            Triple("another-book", 2, "original")
        )

        mismatches.forEach { (bookId, pageIndex, noteText) ->
            try {
                BookNoteFlowConfirmation.requireMatchingRequest(
                    existing = saved,
                    bookId = bookId,
                    pageIndex = pageIndex,
                    noteText = noteText
                )
                fail("A stable note ID must not be reused for a different request payload")
            } catch (_: IllegalArgumentException) {
                // Expected: the ID is already owned by another payload.
            }
        }
    }
    @Test
    fun failsFastWhenTheObservationFlowCompletesWithoutTheInsertedId() {
        runBlocking {
            try {
                BookNoteFlowConfirmation.awaitPersistedNote(
                    notes = emptyFlow(),
                    expectedNoteId = "missing"
                )
                fail("Expected a completed Flow without the note to fail")
            } catch (_: NoSuchElementException) {
                // Expected: the observation source completed without emitting the note.
            }
        }
    }

    @Test
    fun timesOutWhenTheRoomFlowNeverEmitsTheInsertedId() {
        runBlocking {
            try {
                BookNoteFlowConfirmation.awaitPersistedNote(
                    notes = flow { awaitCancellation() },
                    expectedNoteId = "missing",
                    timeoutMillis = 20L
                )
                fail("Expected Room Flow confirmation to time out")
            } catch (_: TimeoutCancellationException) {
                // Expected: the saved row was never observed before the timeout.
            }
        }
    }
}
