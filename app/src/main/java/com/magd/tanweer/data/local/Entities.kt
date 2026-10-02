package com.magd.tanweer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_user")
data class UserEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val phoneNumber: String,
    val gradeId: Int,
    val sectionId: String,
    val role: String,
    val defaultGroupId: String,
    val token: String,
    val recoveryCode: String?,
    val lessonsCount: Int = 0,
    val homeworksCount: Int = 0,
    val issuesCount: Int = 0,
    val photosCount: Int = 0
)

@Entity(tableName = "cached_groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val description: String?,
    val icon: String?,
    val role: String,
    val memberCount: Int,
    val isDiscoverable: Boolean = false,
    val isOfficial: Boolean = false
)

@Entity(tableName = "cached_schedule_slots")
data class ScheduleSlotEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val dayOfWeek: Int,
    val slotOrder: Int,
    val subjectId: String,
    val subjectName: String,
    val subjectIcon: String,
    val colorHex: String?,
    val startTime: String?,
    val endTime: String?
)

@Entity(tableName = "cached_contents")
data class ContentEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val studyDate: String,
    val subjectId: String,
    val type: String,
    val title: String,
    val description: String?,
    val authorName: String,
    val authorGradeSection: String,
    val viewsCount: Int,
    val usefulCount: Int,
    val createdAt: Long,
    val subjectName: String,
    val subjectIcon: String,
    val colorHex: String,
    val mediaUrlsJson: String = "[]",
    val syncStatus: String = "SYNCED"
)

@Entity(tableName = "cached_homeworks")
data class HomeworkEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val studyDate: String,
    val dueDate: String,
    val subjectId: String,
    val title: String,
    val details: String?,
    val pageNumbers: String?,
    val questionNumbers: String?,
    val taskType: String,
    val subjectName: String,
    val subjectIcon: String,
    val colorHex: String,
    val mediaUrlsJson: String = "[]",
    val createdAt: Long,
    val syncStatus: String = "SYNCED"
)

@Entity(tableName = "cached_homework_completions", primaryKeys = ["homeworkId", "userId"])
data class HomeworkCompletionEntity(
    val homeworkId: String,
    val userId: String,
    val completedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_exams")
data class ExamEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val examDate: String,
    val subjectId: String,
    val title: String,
    val requiredChapters: String?,
    val notes: String?,
    val subjectName: String,
    val subjectIcon: String,
    val colorHex: String,
    val syncStatus: String = "SYNCED"
)

@Entity(tableName = "cached_events")
data class EventEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val eventDate: String,
    val timeStr: String?,
    val title: String,
    val description: String?,
    val category: String,
    val location: String?,
    val createdAt: Long,
    val syncStatus: String = "SYNCED"
)

@Entity(tableName = "cached_issues")
data class IssueEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val subjectId: String?,
    val homeworkId: String?,
    val examId: String?,
    val title: String,
    val description: String?,
    val status: String,
    val bestCommentId: String?,
    val authorName: String,
    val subjectName: String?,
    val subjectIcon: String?,
    val commentsCount: Int,
    val createdAt: Long,
    val syncStatus: String = "SYNCED"
)

@Entity(tableName = "cached_issue_comments")
data class IssueCommentEntity(
    @PrimaryKey val id: String,
    val issueId: String,
    val userId: String,
    val authorName: String,
    val comment: String,
    val isBestAnswer: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "SYNCED"
)

@Entity(tableName = "cached_books")
data class BookEntity(
    @PrimaryKey val id: String,
    val gradeId: Int,
    val subjectId: String,
    val title: String,
    val edition: String?,
    val fileSizeMb: Double,
    val fileUrl: String,
    val thumbnailUrl: String?,
    val subjectName: String,
    val subjectIcon: String
)

@Entity(tableName = "cached_chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val senderId: String,
    val senderName: String,
    val senderGradeSection: String,
    val text: String,
    val timestamp: Long,
    val status: String = "SENT" // "SENDING", "SENT", "FAILED"
)

@Entity(tableName = "book_reading_state")
data class BookReadingStateEntity(
    @PrimaryKey val bookId: String,
    val lastPage: Int = 0,
    val zoom: Float = 1.0f,
    val theme: String = "LIGHT",
    val totalStudySeconds: Long = 0L,
    val uniquePagesCount: Int = 1,
    val lastReadAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "book_drawings")
data class BookDrawingEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val pageIndex: Int,
    val strokesJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "book_bookmarks")
data class BookBookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val pageIndex: Int,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "book_notes")
data class BookNoteEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val pageIndex: Int,
    val noteText: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "book_vocabulary")
data class BookVocabularyEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val word: String,
    val meaning: String,
    val pageIndex: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val lastSyncedAt: Long,
    val etag: String? = null
)

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val id: String,
    val entityType: String, // "CONTENT", "HOMEWORK", "EXAM", "ISSUE", "COMMENT", "CHAT_MESSAGE", "CORRECTION"
    val entityId: String,
    val operation: String, // "CREATE", "UPDATE", "DELETE"
    val payloadJson: String,
    val attempts: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long = 0L,
    val error: String? = null,
    val status: String = "PENDING" // "PENDING", "SYNCING", "FAILED", "SYNCED"
)

@Entity(tableName = "correction_requests")
data class CorrectionRequestEntity(
    @PrimaryKey val id: String,
    val contentId: String,
    val groupId: String,
    val fieldName: String,
    val originalValue: String,
    val proposedValue: String,
    val reason: String?,
    val authorName: String,
    val status: String = "PENDING_REVIEW",
    val upvotes: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)
