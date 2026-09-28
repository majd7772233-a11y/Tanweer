package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM cached_user LIMIT 1")
    fun getUser(): Flow<UserEntity?>

    @Query("SELECT * FROM cached_user LIMIT 1")
    suspend fun getUserSync(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM cached_user")
    suspend fun clearUser()
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM cached_groups")
    fun getGroups(): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    @Query("DELETE FROM cached_groups")
    suspend fun clearGroups()
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM cached_schedule_slots WHERE groupId = :groupId ORDER BY dayOfWeek ASC, slotOrder ASC")
    fun getSlots(groupId: String): Flow<List<ScheduleSlotEntity>>

    @Query("SELECT * FROM cached_schedule_slots WHERE groupId = :groupId AND dayOfWeek = :dayOfWeek ORDER BY slotOrder ASC")
    fun getSlotsForDay(groupId: String, dayOfWeek: Int): Flow<List<ScheduleSlotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<ScheduleSlotEntity>)

    @Query("DELETE FROM cached_schedule_slots WHERE groupId = :groupId AND dayOfWeek = :dayOfWeek AND slotOrder = :slotOrder")
    suspend fun deleteSlot(groupId: String, dayOfWeek: Int, slotOrder: Int)

    @Query("DELETE FROM cached_schedule_slots WHERE id = :id")
    suspend fun deleteSlotById(id: String)

    @Query("DELETE FROM cached_schedule_slots WHERE groupId = :groupId")
    suspend fun clearSlots(groupId: String)
}

@Dao
interface ContentDao {
    @Query("SELECT * FROM cached_contents WHERE groupId = :groupId AND studyDate = :date ORDER BY createdAt ASC")
    fun getContentsByDate(groupId: String, date: String): Flow<List<ContentEntity>>

    @Query("SELECT * FROM cached_contents WHERE groupId = :groupId AND subjectId = :subjectId ORDER BY studyDate DESC, createdAt DESC")
    fun getContentsBySubject(groupId: String, subjectId: String): Flow<List<ContentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContents(contents: List<ContentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(content: ContentEntity)

    @Query("DELETE FROM cached_contents WHERE id = :id")
    suspend fun deleteContentById(id: String)
}

@Dao
interface HomeworkDao {
    @Query("SELECT * FROM cached_homeworks WHERE groupId = :groupId ORDER BY dueDate ASC")
    fun getHomeworks(groupId: String): Flow<List<HomeworkEntity>>

    @Query("SELECT * FROM cached_homeworks WHERE groupId = :groupId AND (studyDate = :date OR dueDate = :date)")
    fun getHomeworksByDate(groupId: String, date: String): Flow<List<HomeworkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeworks(homeworks: List<HomeworkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomework(homework: HomeworkEntity)

    @Query("UPDATE cached_homeworks SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateCompletion(id: String, isCompleted: Boolean)
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM cached_exams WHERE groupId = :groupId ORDER BY examDate ASC")
    fun getExams(groupId: String): Flow<List<ExamEntity>>

    @Query("SELECT * FROM cached_exams WHERE groupId = :groupId AND examDate = :date")
    fun getExamsByDate(groupId: String, date: String): Flow<List<ExamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExams(exams: List<ExamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: ExamEntity)

    @Query("DELETE FROM cached_exams WHERE id = :id")
    suspend fun deleteExamById(id: String)

    @Query("DELETE FROM cached_exams WHERE groupId = :groupId")
    suspend fun clearExams(groupId: String)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM cached_events WHERE groupId = :groupId ORDER BY eventDate ASC")
    fun getEvents(groupId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM cached_events WHERE groupId = :groupId AND eventDate = :date")
    fun getEventsByDate(groupId: String, date: String): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)
}

@Dao
interface IssueDao {
    @Query("SELECT * FROM cached_issues WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun getIssues(groupId: String): Flow<List<IssueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssues(issues: List<IssueEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssue(issue: IssueEntity)
}

@Dao
interface BookDao {
    @Query("SELECT * FROM cached_books WHERE gradeId = :gradeId")
    fun getBooks(gradeId: Int): Flow<List<BookEntity>>

    @Query("SELECT * FROM cached_books")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM cached_chat_messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun getMessages(groupId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(msgs: List<ChatMessageEntity>)
}
