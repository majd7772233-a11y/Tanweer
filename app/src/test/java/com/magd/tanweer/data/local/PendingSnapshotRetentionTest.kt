package com.magd.tanweer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingSnapshotRetentionTest {
    private lateinit var database: TanweerDatabase

    private val groupId = "class_10_B"
    private val date = "2026-11-02"

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

    private fun content(id: String, status: String) = ContentEntity(
        id = id,
        groupId = groupId,
        studyDate = date,
        subjectId = "math",
        type = "LESSON",
        title = id,
        description = null,
        authorName = "Student",
        authorGradeSection = "10/B",
        viewsCount = 0,
        usefulCount = 0,
        createdAt = 1L,
        subjectName = "Math",
        subjectIcon = "📐",
        colorHex = "#123456",
        syncStatus = status
    )

    private fun homework(id: String, status: String) = HomeworkEntity(
        id = id,
        groupId = groupId,
        studyDate = date,
        dueDate = date,
        subjectId = "math",
        title = id,
        details = null,
        pageNumbers = null,
        questionNumbers = null,
        taskType = "HOMEWORK",
        subjectName = "Math",
        subjectIcon = "📐",
        colorHex = "#123456",
        createdAt = 1L,
        syncStatus = status
    )

    private fun exam(id: String, status: String) = ExamEntity(
        id = id,
        groupId = groupId,
        examDate = date,
        subjectId = "math",
        title = id,
        requiredChapters = null,
        notes = null,
        subjectName = "Math",
        subjectIcon = "📐",
        colorHex = "#123456",
        syncStatus = status
    )

    private fun event(id: String, status: String) = EventEntity(
        id = id,
        groupId = groupId,
        eventDate = date,
        timeStr = null,
        title = id,
        description = null,
        category = "GENERAL",
        location = null,
        createdAt = 1L,
        syncStatus = status
    )

    private fun issue(id: String, status: String) = IssueEntity(
        id = id,
        groupId = groupId,
        subjectId = "math",
        homeworkId = null,
        examId = null,
        title = id,
        description = null,
        status = "OPEN",
        bestCommentId = null,
        authorName = "Student",
        subjectName = "Math",
        subjectIcon = "📐",
        commentsCount = 0,
        createdAt = 1L,
        syncStatus = status
    )

    private val pendingStatuses = listOf("PENDING", "SYNCING", "FAILED", "DELETE_PENDING")

    @Test
    fun pendingQueriesReturnEveryUnsentStatusAndExcludeSyncedRows() = runBlocking {
        val statuses = pendingStatuses + "SYNCED"

        database.contentDao().insertContents(statuses.map { content("content_$it", it) })
        database.homeworkDao().insertHomeworks(statuses.map { homework("homework_$it", it) })
        database.examDao().insertExams(statuses.map { exam("exam_$it", it) })
        database.eventDao().insertEvents(statuses.map { event("event_$it", it) })
        database.issueDao().insertIssues(statuses.map { issue("issue_$it", it) })

        assertEquals(pendingStatuses.toSet(), database.contentDao().getPendingContents(groupId).map { it.syncStatus }.toSet())
        assertEquals(pendingStatuses.toSet(), database.contentDao().getAllPendingContents().map { it.syncStatus }.toSet())
        assertEquals(pendingStatuses.toSet(), database.homeworkDao().getPendingHomeworks(groupId).map { it.syncStatus }.toSet())
        assertEquals(pendingStatuses.toSet(), database.examDao().getPendingExams(groupId).map { it.syncStatus }.toSet())
        assertEquals(pendingStatuses.toSet(), database.eventDao().getPendingEvents(groupId).map { it.syncStatus }.toSet())
        assertEquals(pendingStatuses.toSet(), database.issueDao().getPendingIssues(groupId).map { it.syncStatus }.toSet())
    }

    @Test
    fun deletePendingRowsSurviveFullDateAndMonthSnapshotClears() = runBlocking {
        database.contentDao().insertContent(content("content_delete", "DELETE_PENDING"))
        database.homeworkDao().insertHomework(homework("homework_delete", "DELETE_PENDING"))
        database.examDao().insertExam(exam("exam_delete", "DELETE_PENDING"))
        database.eventDao().insertEvent(event("event_delete", "DELETE_PENDING"))
        database.issueDao().insertIssue(issue("issue_delete", "DELETE_PENDING"))

        database.contentDao().clearSyncedContents(groupId)
        database.contentDao().clearSyncedContentsForDate(groupId, date)
        database.homeworkDao().clearSyncedHomeworks(groupId)
        database.homeworkDao().clearSyncedHomeworksForDate(groupId, date)
        database.homeworkDao().clearSyncedHomeworksForMonth(groupId, "2026-11")
        database.examDao().clearSyncedExams(groupId)
        database.examDao().clearSyncedExamsForDate(groupId, date)
        database.examDao().clearSyncedExamsForMonth(groupId, "2026-11")
        database.eventDao().clearSyncedEvents(groupId)
        database.eventDao().clearSyncedEventsForDate(groupId, date)
        database.eventDao().clearSyncedEventsForMonth(groupId, "2026-11")
        database.issueDao().clearSyncedIssues(groupId)

        assertEquals("DELETE_PENDING", database.contentDao().getPendingContents(groupId).single().syncStatus)
        assertEquals("DELETE_PENDING", database.homeworkDao().getPendingHomeworks(groupId).single().syncStatus)
        assertEquals("DELETE_PENDING", database.examDao().getPendingExams(groupId).single().syncStatus)
        assertEquals("DELETE_PENDING", database.eventDao().getPendingEvents(groupId).single().syncStatus)
        assertEquals("DELETE_PENDING", database.issueDao().getPendingIssues(groupId).single().syncStatus)
    }
}
