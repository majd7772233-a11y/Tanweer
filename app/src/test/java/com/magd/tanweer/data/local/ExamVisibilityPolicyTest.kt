package com.magd.tanweer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.magd.tanweer.util.ServerSnapshotReconciliation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExamVisibilityPolicyTest {
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

    private fun exam(
        id: String,
        status: String,
        groupId: String = "class_10_A",
        date: String = "2026-11-21"
    ) = ExamEntity(
        id = id,
        groupId = groupId,
        examDate = date,
        subjectId = "math",
        title = id,
        requiredChapters = null,
        notes = null,
        subjectName = "الرياضيات",
        subjectIcon = "📐",
        colorHex = "#FF3366",
        syncStatus = status
    )

    @Test
    fun publishedStreamsExcludeEveryUnconfirmedLocalState() = runBlocking {
        database.examDao().insertExams(
            listOf(
                exam("published", "SYNCED"),
                exam("pending", "PENDING"),
                exam("syncing", "SYNCING"),
                exam("legacy_local", "LOCAL"),
                exam("failed", "FAILED"),
                exam("delete_pending", "DELETE_PENDING"),
                exam("other_group_published", "SYNCED", groupId = "class_10_B")
            )
        )

        assertEquals(
            listOf("published"),
            database.examDao().getExams("class_10_A").first().map { it.id }
        )
        assertEquals(
            listOf("published", "other_group_published"),
            database.examDao().getAllHistoricalExams().first().map { it.id }
        )
        assertEquals(
            listOf("published"),
            database.examDao().getExamsByDate("class_10_A", "2026-11-21").first().map { it.id }
        )
        assertEquals(
            setOf("pending", "syncing", "legacy_local"),
            database.examDao().getLocalPendingExams("class_10_A").first().map { it.id }.toSet()
        )
        assertEquals(
            listOf("failed"),
            database.examDao().getFailedExams("class_10_A").first().map { it.id }
        )
    }

    @Test
    fun snapshotCleanupKeepsAllLocalUnconfirmedRowsUntilExplicitResolution() = runBlocking {
        database.examDao().insertExams(
            listOf(
                exam("published", "SYNCED"),
                exam("pending", "PENDING"),
                exam("syncing", "SYNCING"),
                exam("legacy_local", "LOCAL"),
                exam("failed", "FAILED"),
                exam("delete_pending", "DELETE_PENDING")
            )
        )

        database.examDao().clearSyncedExams("class_10_A")
        listOf("pending", "syncing", "legacy_local", "failed", "delete_pending").forEach { id ->
            assertNotNull("Local row $id must survive full reconciliation", database.examDao().getExamById(id))
        }
        assertNull(database.examDao().getExamById("published"))

        database.examDao().clearSyncedExamsForDate("class_10_A", "2026-11-21")
        listOf("pending", "syncing", "legacy_local", "failed", "delete_pending").forEach { id ->
            assertNotNull("Local row $id must survive date reconciliation", database.examDao().getExamById(id))
        }

        database.examDao().clearSyncedExamsForMonth("class_10_A", "2026-11")
        listOf("pending", "syncing", "legacy_local", "failed", "delete_pending").forEach { id ->
            assertNotNull("Local row $id must survive month reconciliation", database.examDao().getExamById(id))
        }
    }

    @Test
    fun failedRowYieldsToAnAuthoritativeServerRecordButStaysLocalWhenAbsent() = runBlocking {
        database.examDao().insertExams(
            listOf(
                exam("server_confirms_failed", "FAILED"),
                exam("still_rejected", "FAILED"),
                exam("other_pending", "PENDING")
            )
        )

        val localPending = database.examDao().getExamSnapshotPreservationRows("class_10_A")
        assertEquals(setOf("other_pending"), localPending.map { it.id }.toSet())
        assertEquals("FAILED", database.examDao().getExamById("still_rejected")?.syncStatus)

        val reconciled = ServerSnapshotReconciliation.preservePending(
            serverRows = listOf(exam("server_confirms_failed", "SYNCED")),
            localPendingRows = localPending
        ) { it.id }

        database.examDao().clearSyncedExams("class_10_A")
        database.examDao().insertExams(reconciled)

        assertEquals("SYNCED", database.examDao().getExamById("server_confirms_failed")?.syncStatus)
        assertEquals("FAILED", database.examDao().getExamById("still_rejected")?.syncStatus)
        assertEquals("PENDING", database.examDao().getExamById("other_pending")?.syncStatus)
        assertEquals(
            listOf("still_rejected"),
            database.examDao().getFailedExams("class_10_A").first().map { it.id }
        )
    }
    @Test
    fun examCreateRetryOnlyRequeuesTheFailedCreateOperation() = runBlocking {
        database.outboxDao().insertOutbox(
            OutboxEntity(
                id = "retry_failed_exam_create",
                entityType = "EXAM_CREATE",
                entityId = "exam_retry",
                operation = "CREATE",
                payloadJson = """{"id":"exam_retry","groupId":"class_10_A","title":"Retry"}""",
                attempts = 4,
                status = "FAILED",
                error = "HTTP 409"
            )
        )
        database.outboxDao().insertOutbox(
            OutboxEntity(
                id = "retry_failed_exam_toggle",
                entityType = "EXAM_TOGGLE",
                entityId = "exam_retry",
                operation = "UPDATE",
                payloadJson = """{"isCompleted":true}""",
                attempts = 2,
                status = "FAILED",
                error = "toggle rejected"
            )
        )
        database.outboxDao().insertOutbox(
            OutboxEntity(
                id = "retry_failed_exam_delete",
                entityType = "EXAM",
                entityId = "exam_retry",
                operation = "DELETE",
                payloadJson = """{"id":"exam_retry"}""",
                attempts = 3,
                status = "FAILED",
                error = "delete rejected"
            )
        )
        database.outboxDao().insertOutbox(
            OutboxEntity(
                id = "retry_failed_homework",
                entityType = "HOMEWORK_CREATE",
                entityId = "exam_retry",
                operation = "CREATE",
                payloadJson = """{"title":"unrelated"}""",
                attempts = 3,
                status = "FAILED",
                error = "unrelated failure"
            )
        )
        database.outboxDao().insertOutbox(
            OutboxEntity(
                id = "retry_pending_exam_create",
                entityType = "EXAM_CREATE",
                entityId = "exam_retry",
                operation = "CREATE",
                payloadJson = """{"id":"exam_retry"}""",
                status = "PENDING"
            )
        )
        database.outboxDao().insertOutbox(
            OutboxEntity(
                id = "active_exam_create",
                entityType = "EXAM_CREATE",
                entityId = "exam_active",
                operation = "CREATE",
                payloadJson = """{"id":"exam_active"}""",
                status = "SYNCING"
            )
        )

        assertEquals(1, database.outboxDao().requeueFailedExamCreate("exam_retry"))
        assertEquals(0, database.outboxDao().requeueFailedExamCreate("exam_retry"))
        val retried = database.outboxDao().getOutboxItemById("retry_failed_exam_create")
        assertEquals("PENDING", retried?.status)
        assertEquals(0, retried?.attempts)
        assertEquals(0L, retried?.lastAttemptAt)
        assertNull(retried?.error)

        val untouched = listOf(
            "retry_failed_exam_toggle" to "FAILED",
            "retry_failed_exam_delete" to "FAILED",
            "retry_failed_homework" to "FAILED",
            "retry_pending_exam_create" to "PENDING"
        )
        untouched.forEach { (id, expectedStatus) ->
            assertEquals(expectedStatus, database.outboxDao().getOutboxItemById(id)?.status)
        }
        assertEquals(2, database.outboxDao().getOutboxItemById("retry_failed_exam_toggle")?.attempts)
        assertEquals(3, database.outboxDao().getOutboxItemById("retry_failed_exam_delete")?.attempts)
        assertEquals(3, database.outboxDao().getOutboxItemById("retry_failed_homework")?.attempts)
        assertEquals("SYNCING", database.outboxDao().getActiveExamCreateMutation("exam_active")?.status)
        assertEquals("PENDING", database.outboxDao().getActiveExamCreateMutation("exam_retry")?.status)
    }
    @Test
    fun failedCreateCleanupOnlyDeletesTheFailedCreateOperation() = runBlocking {
        database.outboxDao().insertOutbox(
            OutboxEntity(
                id = "failed_exam_create",
                entityType = "EXAM_CREATE",
                entityId = "exam_failed",
                operation = "CREATE",
                payloadJson = """{"id":"exam_failed","groupId":"class_10_A","title":"Rejected"}""",
                status = "FAILED",
                attempts = 1,
                error = "HTTP 409"
            )
        )
        database.outboxDao().insertOutbox(
            OutboxEntity(
                id = "failed_exam_toggle",
                entityType = "EXAM_TOGGLE",
                entityId = "exam_failed",
                operation = "UPDATE",
                payloadJson = """{"isCompleted":true}""",
                status = "FAILED",
                attempts = 2
            )
        )
        database.outboxDao().insertOutbox(
            OutboxEntity(
                id = "pending_exam_create",
                entityType = "EXAM_CREATE",
                entityId = "exam_pending",
                operation = "CREATE",
                payloadJson = """{"id":"exam_pending"}""",
                status = "PENDING"
            )
        )

        assertEquals("failed_exam_create", database.outboxDao().getExamCreateMutation("exam_failed")?.id)
        assertEquals(1, database.outboxDao().getFailedExamCreateItems().size)
        assertEquals(1, database.outboxDao().deleteFailedExamCreate("exam_failed"))
        assertNull(database.outboxDao().getExamCreateMutation("exam_failed"))
        assertNotNull(database.outboxDao().getPendingMutation("exam_failed", "EXAM_TOGGLE"))
        assertEquals(0, database.outboxDao().deleteFailedExamCreate("exam_pending"))
        assertNotNull(database.outboxDao().getExamCreateMutation("exam_pending"))
    }
}