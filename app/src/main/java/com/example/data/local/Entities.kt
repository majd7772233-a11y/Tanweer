package com.example.data.local

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
    val recoveryCode: String?
)

@Entity(tableName = "cached_groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val description: String?,
    val icon: String?,
    val role: String,
    val memberCount: Int
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
    val mediaUrlsJson: String = "[]"
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
    val isCompleted: Boolean,
    val createdAt: Long
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
    val colorHex: String
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
    val createdAt: Long
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
    val createdAt: Long
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
    val isMe: Boolean
)
