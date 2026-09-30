package com.magd.tanweer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        GroupEntity::class,
        ScheduleSlotEntity::class,
        ContentEntity::class,
        HomeworkEntity::class,
        HomeworkCompletionEntity::class,
        ExamEntity::class,
        EventEntity::class,
        IssueEntity::class,
        IssueCommentEntity::class,
        BookEntity::class,
        ChatMessageEntity::class,
        BookReadingStateEntity::class,
        BookBookmarkEntity::class,
        BookNoteEntity::class,
        BookVocabularyEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class TanweerDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun groupDao(): GroupDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun contentDao(): ContentDao
    abstract fun homeworkDao(): HomeworkDao
    abstract fun examDao(): ExamDao
    abstract fun eventDao(): EventDao
    abstract fun issueDao(): IssueDao
    abstract fun bookDao(): BookDao
    abstract fun chatDao(): ChatDao
    abstract fun bookReadingDao(): BookReadingDao

    companion object {
        @Volatile
        private var INSTANCE: TanweerDatabase? = null

        fun getInstance(context: Context): TanweerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TanweerDatabase::class.java,
                    "tanweer_local.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
