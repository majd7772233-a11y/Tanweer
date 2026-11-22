package com.magd.tanweer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IssueCommentReconciliationTest {
    private lateinit var database: TanweerDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TanweerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun comment(
        id: String,
        status: String = "SYNCED",
        isTeacherAnswer: Boolean = false
    ) = IssueCommentEntity(
        id = id,
        issueId = "issue_1",
        userId = "user_$id",
        authorName = "author_$id",
        comment = "comment_$id",
        isBestAnswer = false,
        isTeacherAnswer = isTeacherAnswer,
        createdAt = 1_700_000_000L,
        syncStatus = status
    )

    @Test
    fun serverSnapshotReplacesStaleRowsAndPreservesUnacknowledgedLocalComments() = runBlocking {
        database.issueDao().insertComments(
            listOf(
                comment("stale_server"),
                comment("local_pending", status = "PENDING"),
                comment("local_failed", status = "FAILED"),
                comment("same_id_pending", status = "PENDING")
            )
        )

        database.issueDao().replaceSyncedComments(
            issueId = "issue_1",
            serverComments = listOf(
                comment("teacher_answer", isTeacherAnswer = true),
                comment("same_id_pending", isTeacherAnswer = false)
            )
        )

        val actual = database.issueDao().getComments("issue_1").first().associateBy { it.id }
        assertNull(actual["stale_server"])
        assertNotNull(actual["teacher_answer"])
        assertTrue(actual["teacher_answer"]!!.isTeacherAnswer)
        assertEquals("SYNCED", actual["teacher_answer"]!!.syncStatus)
        assertEquals("SYNCED", actual["same_id_pending"]!!.syncStatus)
        assertFalse(actual["same_id_pending"]!!.isTeacherAnswer)
        assertEquals("PENDING", actual["local_pending"]!!.syncStatus)
        assertEquals("FAILED", actual["local_failed"]!!.syncStatus)
    }

    @Test
    fun emptyAuthoritativeSnapshotClearsOnlyServerConfirmedComments() = runBlocking {
        database.issueDao().insertComments(
            listOf(
                comment("server_old"),
                comment("local_pending", status = "PENDING"),
                comment("local_syncing", status = "SYNCING")
            )
        )

        database.issueDao().replaceSyncedComments("issue_1", emptyList())

        val actual = database.issueDao().getComments("issue_1").first()
        assertEquals(setOf("local_pending", "local_syncing"), actual.map { it.id }.toSet())
        assertTrue(actual.all { it.syncStatus != "SYNCED" })
    }

    @Test
    fun teacherAnswerMarkerIsStoredInTheObservableRoomFlow() = runBlocking {
        database.issueDao().replaceSyncedComments(
            "issue_1",
            listOf(comment("approved_teacher_answer", isTeacherAnswer = true))
        )

        val visible = database.issueDao().getComments("issue_1").first().single()
        assertTrue(visible.isTeacherAnswer)
    }
}
