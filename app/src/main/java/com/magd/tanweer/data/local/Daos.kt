package com.magd.tanweer.data.local

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

    @Query("SELECT * FROM cached_groups WHERE isDiscoverable = 0")
    fun getMyGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM cached_groups WHERE isDiscoverable = 1")
    fun getDiscoverGroups(): Flow<List<GroupEntity>>

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
    @Query("SELECT * FROM cached_contents WHERE groupId = :groupId ORDER BY studyDate DESC, createdAt DESC")
    fun getAllContents(groupId: String): Flow<List<ContentEntity>>

    @Query("SELECT * FROM cached_contents WHERE groupId = :groupId AND studyDate = :date ORDER BY createdAt ASC")
    fun getContentsByDate(groupId: String, date: String): Flow<List<ContentEntity>>

    @Query("SELECT * FROM cached_contents WHERE groupId = :groupId AND subjectId = :subjectId ORDER BY studyDate DESC, createdAt DESC")
    fun getContentsBySubject(groupId: String, subjectId: String): Flow<List<ContentEntity>>

    @Query("SELECT * FROM cached_contents WHERE groupId = :groupId AND (title LIKE '%' || :query || '%' OR subjectName LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY studyDate DESC")
    fun searchContents(groupId: String, query: String): Flow<List<ContentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContents(contents: List<ContentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(content: ContentEntity)

    @Query("DELETE FROM cached_contents WHERE id = :id")
    suspend fun deleteContentById(id: String)

    @Query("UPDATE cached_contents SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("UPDATE cached_contents SET mediaUrlsJson = :mediaJson, syncStatus = :status WHERE id = :id")
    suspend fun updateMediaAndSyncStatus(id: String, mediaJson: String, status: String)

    @Query("SELECT * FROM cached_contents WHERE id = :id LIMIT 1")
    suspend fun getContentById(id: String): ContentEntity?

    @Query("DELETE FROM cached_contents WHERE groupId = :groupId")
    suspend fun clearContents(groupId: String)

    @Query("DELETE FROM cached_contents WHERE groupId = :groupId AND studyDate = :date AND syncStatus NOT IN ('PENDING', 'SYNCING', 'FAILED')")
    suspend fun clearSyncedContentsForDate(groupId: String, date: String)

    @Query("DELETE FROM cached_contents WHERE groupId = :groupId AND syncStatus NOT IN ('PENDING', 'SYNCING', 'FAILED')")
    suspend fun clearSyncedContents(groupId: String)
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

    @Query("UPDATE cached_homeworks SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("SELECT * FROM cached_homeworks WHERE id = :id LIMIT 1")
    suspend fun getHomeworkById(id: String): HomeworkEntity?

    @Query("DELETE FROM cached_homeworks WHERE id = :id")
    suspend fun deleteHomeworkById(id: String)

    @Query("DELETE FROM cached_homeworks WHERE groupId = :groupId AND syncStatus NOT IN ('PENDING', 'SYNCING', 'FAILED')")
    suspend fun clearSyncedHomeworks(groupId: String)

    @Query("DELETE FROM cached_homeworks WHERE groupId = :groupId AND (studyDate = :date OR dueDate = :date) AND syncStatus NOT IN ('PENDING', 'SYNCING', 'FAILED')")
    suspend fun clearSyncedHomeworksForDate(groupId: String, date: String)

    @Query("SELECT * FROM cached_homework_completions WHERE userId = :userId")
    fun getCompletionsForUser(userId: String): Flow<List<HomeworkCompletionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: HomeworkCompletionEntity)

    @Query("DELETE FROM cached_homework_completions WHERE homeworkId = :homeworkId AND userId = :userId")
    suspend fun deleteCompletion(homeworkId: String, userId: String)

    @Query("DELETE FROM cached_homework_completions WHERE userId = :userId")
    suspend fun clearCompletionsForUser(userId: String)

    @Query("DELETE FROM cached_homeworks WHERE groupId = :groupId")
    suspend fun clearHomeworks(groupId: String)
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM cached_exams WHERE groupId = :groupId ORDER BY examDate ASC")
    fun getExams(groupId: String): Flow<List<ExamEntity>>

    @Query("SELECT * FROM cached_exams WHERE groupId = :groupId AND examDate = :date")
    fun getExamsByDate(groupId: String, date: String): Flow<List<ExamEntity>>

    @Query("SELECT * FROM cached_exams WHERE id = :id LIMIT 1")
    suspend fun getExamById(id: String): ExamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExams(exams: List<ExamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: ExamEntity)

    @Query("UPDATE cached_exams SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM cached_exams WHERE id = :id")
    suspend fun deleteExamById(id: String)

    @Query("DELETE FROM cached_exams WHERE groupId = :groupId AND syncStatus NOT IN ('PENDING', 'SYNCING', 'FAILED')")
    suspend fun clearSyncedExams(groupId: String)

    @Query("DELETE FROM cached_exams WHERE groupId = :groupId AND examDate = :date AND syncStatus NOT IN ('PENDING', 'SYNCING', 'FAILED')")
    suspend fun clearSyncedExamsForDate(groupId: String, date: String)

    @Query("DELETE FROM cached_exams WHERE groupId = :groupId")
    suspend fun clearExams(groupId: String)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM cached_events WHERE groupId = :groupId ORDER BY eventDate ASC")
    fun getEvents(groupId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM cached_events WHERE groupId = :groupId AND eventDate = :date")
    fun getEventsByDate(groupId: String, date: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM cached_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: String): EventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    @Query("UPDATE cached_events SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM cached_events WHERE groupId = :groupId AND syncStatus NOT IN ('PENDING', 'SYNCING', 'FAILED')")
    suspend fun clearSyncedEvents(groupId: String)

    @Query("DELETE FROM cached_events WHERE groupId = :groupId AND eventDate = :date AND syncStatus NOT IN ('PENDING', 'SYNCING', 'FAILED')")
    suspend fun clearSyncedEventsForDate(groupId: String, date: String)

    @Query("DELETE FROM cached_events WHERE groupId = :groupId")
    suspend fun clearEvents(groupId: String)
}

@Dao
interface IssueDao {
    @Query("SELECT * FROM cached_issues WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun getIssues(groupId: String): Flow<List<IssueEntity>>

    @Query("SELECT * FROM cached_issues WHERE id = :issueId LIMIT 1")
    fun getIssue(issueId: String): Flow<IssueEntity?>

    @Query("SELECT * FROM cached_issues WHERE id = :issueId LIMIT 1")
    suspend fun getIssueSync(issueId: String): IssueEntity?

    @Query("SELECT * FROM cached_issue_comments WHERE issueId = :issueId ORDER BY isBestAnswer DESC, createdAt ASC")
    fun getComments(issueId: String): Flow<List<IssueCommentEntity>>

    @Query("SELECT * FROM cached_issue_comments WHERE id = :id LIMIT 1")
    suspend fun getCommentById(id: String): IssueCommentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssues(issues: List<IssueEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssue(issue: IssueEntity)

    @Query("UPDATE cached_issues SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM cached_issues WHERE groupId = :groupId AND syncStatus NOT IN ('PENDING', 'SYNCING', 'FAILED')")
    suspend fun clearSyncedIssues(groupId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<IssueCommentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: IssueCommentEntity)

    @Query("UPDATE cached_issue_comments SET syncStatus = :status WHERE id = :id")
    suspend fun updateCommentSyncStatus(id: String, status: String)

    @Query("UPDATE cached_issue_comments SET isBestAnswer = (id = :commentId) WHERE issueId = :issueId")
    suspend fun setBestAnswer(issueId: String, commentId: String)

    @Query("UPDATE cached_issues SET bestCommentId = :commentId, status = 'SOLVED' WHERE id = :issueId")
    suspend fun markIssueSolved(issueId: String, commentId: String)

    @Query("UPDATE cached_issues SET bestCommentId = :bestCommentId, status = :status WHERE id = :issueId")
    suspend fun revertIssueStatus(issueId: String, bestCommentId: String?, status: String)

    @Query("UPDATE cached_issue_comments SET isBestAnswer = (id = :bestCommentId) WHERE issueId = :issueId")
    suspend fun revertCommentBestAnswer(issueId: String, bestCommentId: String?)

    @Query("DELETE FROM cached_issues WHERE groupId = :groupId")
    suspend fun clearIssues(groupId: String)
}

@Dao
interface BookDao {
    @Query("SELECT * FROM cached_books WHERE gradeId = :gradeId")
    fun getBooks(gradeId: Int): Flow<List<BookEntity>>

    @Query("SELECT * FROM cached_books")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Query("DELETE FROM cached_books")
    suspend fun clearBooks()
}

@Dao
interface BookReadingDao {
    @Query("SELECT * FROM book_reading_state WHERE bookId = :bookId LIMIT 1")
    fun getReadingState(bookId: String): Flow<BookReadingStateEntity?>

    @Query("SELECT * FROM book_reading_state WHERE bookId = :bookId LIMIT 1")
    suspend fun getReadingStateSync(bookId: String): BookReadingStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReadingState(state: BookReadingStateEntity)

    @Query("SELECT * FROM book_bookmarks WHERE bookId = :bookId ORDER BY pageIndex ASC")
    fun getBookmarks(bookId: String): Flow<List<BookBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookBookmarkEntity)

    @Query("DELETE FROM book_bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String)

    @Query("SELECT * FROM book_notes WHERE bookId = :bookId ORDER BY pageIndex ASC")
    fun getNotes(bookId: String): Flow<List<BookNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: BookNoteEntity)

    @Query("DELETE FROM book_notes WHERE id = :id")
    suspend fun deleteNote(id: String)

    @Query("SELECT * FROM book_vocabulary WHERE bookId = :bookId ORDER BY pageIndex ASC")
    fun getVocabulary(bookId: String): Flow<List<BookVocabularyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabulary(vocab: BookVocabularyEntity)

    @Query("DELETE FROM book_vocabulary WHERE id = :id")
    suspend fun deleteVocabulary(id: String)

    @Query("SELECT * FROM book_drawings WHERE bookId = :bookId")
    fun getDrawings(bookId: String): Flow<List<BookDrawingEntity>>

    @Query("SELECT * FROM book_drawings WHERE bookId = :bookId")
    suspend fun getDrawingsSync(bookId: String): List<BookDrawingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDrawing(drawing: BookDrawingEntity)

    @Query("DELETE FROM book_drawings WHERE bookId = :bookId AND pageIndex = :pageIndex")
    suspend fun clearDrawingForPage(bookId: String, pageIndex: Int)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM cached_chat_messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun getMessages(groupId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(msgs: List<ChatMessageEntity>)

    @Query("UPDATE cached_chat_messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: String)

    @Query("DELETE FROM cached_chat_messages WHERE groupId = :groupId")
    suspend fun clearMessages(groupId: String)
}

@Dao
interface SyncMetaDao {
    @Query("SELECT lastSyncedAt FROM cached_sync_meta WHERE `key` = :key LIMIT 1")
    suspend fun getLastSyncTime(key: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setLastSyncTime(meta: SyncMetaEntity)
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    fun getPendingItems(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    suspend fun getPendingItemsSync(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE id = :id LIMIT 1")
    suspend fun getOutboxItemById(id: String): OutboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutbox(item: OutboxEntity)

    @Query("UPDATE outbox SET status = :status, attempts = attempts + 1, lastAttemptAt = :now, error = :error WHERE id = :id")
    suspend fun updateOutboxStatus(id: String, status: String, now: Long = System.currentTimeMillis(), error: String? = null)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteOutbox(id: String)

    @Query("DELETE FROM outbox WHERE entityId = :entityId")
    suspend fun deleteByEntityId(entityId: String)

    @Query("DELETE FROM outbox WHERE status = 'SYNCED'")
    suspend fun clearSynced()
}

@Dao
interface CorrectionDao {
    @Query("SELECT * FROM correction_requests WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun getCorrections(groupId: String): Flow<List<CorrectionRequestEntity>>

    @Query("SELECT * FROM correction_requests WHERE contentId = :contentId ORDER BY createdAt DESC")
    fun getCorrectionsForContent(contentId: String): Flow<List<CorrectionRequestEntity>>

    @Query("SELECT * FROM correction_requests WHERE id = :id LIMIT 1")
    suspend fun getCorrectionById(id: String): CorrectionRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrection(correction: CorrectionRequestEntity)

    @Query("UPDATE correction_requests SET upvotes = upvotes + 1 WHERE id = :id")
    suspend fun upvoteCorrection(id: String)

    @Query("UPDATE correction_requests SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}

@Dao
interface DailyPlanDao {
    @Query("SELECT * FROM cached_daily_plan WHERE userId = :userId AND planDate = :date ORDER BY isCompleted ASC, createdAt DESC")
    fun getPlanForDate(userId: String, date: String): Flow<List<DailyPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanItem(item: DailyPlanEntity)

    @Query("UPDATE cached_daily_plan SET isCompleted = :completed WHERE id = :id")
    suspend fun setItemCompleted(id: String, completed: Boolean)

    @Query("DELETE FROM cached_daily_plan WHERE id = :id")
    suspend fun deletePlanItem(id: String)

    @Query("DELETE FROM cached_daily_plan WHERE homeworkId = :homeworkId AND userId = :userId")
    suspend fun deleteByHomeworkId(homeworkId: String, userId: String)

    @Query("SELECT homeworkId FROM cached_daily_plan WHERE userId = :userId AND planDate = :date AND homeworkId IS NOT NULL")
    fun getPlannedHomeworkIds(userId: String, date: String): Flow<List<String>>
}
