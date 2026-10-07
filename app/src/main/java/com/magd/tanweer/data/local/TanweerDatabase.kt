package com.magd.tanweer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        BookVocabularyEntity::class,
        BookDrawingEntity::class,
        SyncMetaEntity::class,
        OutboxEntity::class,
        CorrectionRequestEntity::class,
        DailyPlanEntity::class
    ],
    version = 11,
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
    abstract fun syncMetaDao(): SyncMetaDao
    abstract fun outboxDao(): OutboxDao
    abstract fun correctionDao(): CorrectionDao
    abstract fun dailyPlanDao(): DailyPlanDao

    companion object {
        @Volatile
        private var INSTANCE: TanweerDatabase? = null

        val MIGRATION_PRESERVE_ALL = object : Migration(1, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Non-destructive schema migration preserving all local user data
                database.execSQL("CREATE TABLE IF NOT EXISTS `daily_plans` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `relatedHomeworkId` TEXT, `date` TEXT NOT NULL, `time` TEXT, `isDone` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `correction_requests` (`id` TEXT NOT NULL, `contentId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `fieldName` TEXT NOT NULL, `originalValue` TEXT NOT NULL, `proposedValue` TEXT NOT NULL, `reason` TEXT, `authorName` TEXT NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `outbox` (`id` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `payload` TEXT NOT NULL, `status` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `lastError` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                try {
                    database.execSQL("ALTER TABLE `cached_exams` ADD COLUMN `isCompleted` INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Column might already exist
                }
            }
        }

        fun getInstance(context: Context): TanweerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TanweerDatabase::class.java,
                    "tanweer_local.db"
                )
                    .addMigrations(MIGRATION_PRESERVE_ALL)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
