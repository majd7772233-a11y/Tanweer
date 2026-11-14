package com.magd.tanweer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
class ExamDeleteOutboxTest {
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

    @Test
    fun `delete pending status survives full exam reconciliation clears`() = runBlocking {
        val examId = "exam_delete_pending"
        database.examDao().insertExam(
            ExamEntity(
                id = examId,
                groupId = "class_10_A",
                examDate = "2026-11-02",
                subjectId = "math",
                title = "اختبار الرياضيات",
                requiredChapters = null,
                notes = null,
                subjectName = "الرياضيات",
                subjectIcon = "📐",
                colorHex = "#FF3366",
                syncStatus = "DELETE_PENDING"
            )
        )

        database.examDao().clearSyncedExams("class_10_A")
        assertNotNull(database.examDao().getExamById(examId))

        database.examDao().clearSyncedExamsForDate("class_10_A", "2026-11-02")
        assertNotNull(database.examDao().getExamById(examId))

        database.examDao().clearSyncedExamsForMonth("class_10_A", "2026-11")
        assertNotNull(database.examDao().getExamById(examId))
    }

    @Test
    fun `typed pending mutation lookup only returns requested operation`() = runBlocking {
        val examId = "exam_delete_typed"
        val toggle = OutboxEntity(
            id = "ob_toggle",
            entityType = "EXAM_TOGGLE",
            entityId = examId,
            operation = "UPDATE",
            payloadJson = "{}"
        )
        val delete = OutboxEntity(
            id = "ob_delete",
            entityType = "EXAM_DELETE",
            entityId = examId,
            operation = "DELETE",
            payloadJson = "{}",
            status = "SYNCING"
        )
        database.outboxDao().insertOutbox(toggle)
        database.outboxDao().insertOutbox(delete)

        assertEquals("EXAM_DELETE", database.outboxDao().getPendingMutation(examId, "EXAM_DELETE")?.entityType)
        assertNull(database.outboxDao().getPendingMutation(examId, "EXAM_CREATE"))
        assertNotNull(database.outboxDao().getPendingMutation(examId, "EXAM_TOGGLE"))
    }
    @Test
    fun `legacy exam delete and create entries remain discoverable by typed lookup`() = runBlocking {
        val examId = "legacy_exam"
        database.outboxDao().insertOutbox(
            OutboxEntity(
                id = "legacy_delete",
                entityType = "EXAM",
                entityId = examId,
                operation = "DELETE",
                payloadJson = "{}"
            )
        )
        database.outboxDao().insertOutbox(
            OutboxEntity(
                id = "legacy_create",
                entityType = "EXAM",
                entityId = examId,
                operation = "CREATE",
                payloadJson = "{}"
            )
        )

        assertEquals("legacy_delete", database.outboxDao().getPendingMutation(examId, "EXAM_DELETE")?.id)
        assertEquals("legacy_create", database.outboxDao().getPendingMutation(examId, "EXAM_CREATE")?.id)
    }

    @Test
    fun `deleting one mutation type preserves the other operation for same exam`() = runBlocking {
        val examId = "typed_delete_exam"
        database.outboxDao().insertOutbox(
            OutboxEntity("toggle_op", "EXAM_TOGGLE", examId, "UPDATE", "{}")
        )
        database.outboxDao().insertOutbox(
            OutboxEntity("delete_op", "EXAM_DELETE", examId, "DELETE", "{}")
        )

        database.outboxDao().deleteByEntityIdAndType(examId, "EXAM_TOGGLE")

        assertNull(database.outboxDao().getPendingMutation(examId, "EXAM_TOGGLE"))
        assertEquals("delete_op", database.outboxDao().getPendingMutation(examId, "EXAM_DELETE")?.id)
    }

    @Test
    fun `unclaimed create cancellation is atomic and does not touch syncing create`() = runBlocking {
        val pendingId = "pending_exam"
        val syncingId = "syncing_exam"
        database.outboxDao().insertOutbox(
            OutboxEntity("pending_create", "EXAM_CREATE", pendingId, "CREATE", "{}")
        )
        database.outboxDao().insertOutbox(
            OutboxEntity("syncing_create", "EXAM_CREATE", syncingId, "CREATE", "{}", status = "SYNCING")
        )

        assertEquals(1, database.outboxDao().deleteUnclaimedMutation(pendingId, "EXAM_CREATE"))
        assertNull(database.outboxDao().getPendingMutation(pendingId, "EXAM_CREATE"))
        assertEquals(0, database.outboxDao().deleteUnclaimedMutation(syncingId, "EXAM_CREATE"))
        assertNotNull(database.outboxDao().getPendingMutation(syncingId, "EXAM_CREATE"))
    }

}
