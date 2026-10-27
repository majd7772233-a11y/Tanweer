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
    version = 13,
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

        private fun applyUniversalSchemaUpgrades(database: SupportSQLiteDatabase) {
            // 1. Migrate / Create cached_daily_plan (preserving any rows from legacy daily_plans)
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `cached_daily_plan` (" +
                "`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `homeworkId` TEXT, " +
                "`title` TEXT NOT NULL, `subjectName` TEXT, `planDate` TEXT NOT NULL, " +
                "`isCompleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            try {
                // If legacy table daily_plans exists, migrate rows to cached_daily_plan
                val cursor = database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='daily_plans'")
                if (cursor.count > 0) {
                    database.execSQL(
                        "INSERT OR IGNORE INTO `cached_daily_plan` (`id`, `userId`, `homeworkId`, `title`, `subjectName`, `planDate`, `isCompleted`, `createdAt`) " +
                        "SELECT `id`, `userId`, `relatedHomeworkId`, `title`, NULL, `date`, `isDone`, `createdAt` FROM `daily_plans`"
                    )
                    database.execSQL("DROP TABLE IF EXISTS `daily_plans`")
                }
                cursor.close()
            } catch (_: Exception) {}

            // 2. Migrate / Create correction_requests (with upvotes and createdAt)
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `correction_requests` (" +
                "`id` TEXT NOT NULL, `contentId` TEXT NOT NULL, `groupId` TEXT NOT NULL, " +
                "`fieldName` TEXT NOT NULL, `originalValue` TEXT NOT NULL, `proposedValue` TEXT NOT NULL, " +
                "`reason` TEXT, `authorName` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                "`upvotes` INTEGER NOT NULL DEFAULT 1, `createdAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))"
            )
            try {
                database.execSQL("ALTER TABLE `correction_requests` ADD COLUMN `upvotes` INTEGER NOT NULL DEFAULT 1")
            } catch (_: Exception) {}
            try {
                database.execSQL("ALTER TABLE `correction_requests` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}

            // 3. Non-destructive migration for outbox matching OutboxEntity exactly
            val outboxCursor = database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='outbox'")
            val outboxExists = outboxCursor.count > 0
            outboxCursor.close()

            if (!outboxExists) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outbox` (" +
                    "`id` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, " +
                    "`operation` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `attempts` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `lastAttemptAt` INTEGER NOT NULL, " +
                    "`error` TEXT, `status` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
            } else {
                // Check if current outbox table has the modern schema with payloadJson and attempts
                val pragmaCursor = database.query("PRAGMA table_info(`outbox`)")
                val existingCols = mutableSetOf<String>()
                while (pragmaCursor.moveToNext()) {
                    val colName = pragmaCursor.getString(1)
                    existingCols.add(colName)
                }
                pragmaCursor.close()

                val needsRebuild = !existingCols.contains("payloadJson") || !existingCols.contains("operation") || !existingCols.contains("attempts")

                if (needsRebuild) {
                    database.execSQL(
                        "CREATE TABLE `outbox_v12_temp` (" +
                        "`id` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, " +
                        "`operation` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `attempts` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `lastAttemptAt` INTEGER NOT NULL, " +
                        "`error` TEXT, `status` TEXT NOT NULL, PRIMARY KEY(`id`))"
                    )

                    val payloadCol = if (existingCols.contains("payloadJson")) "`payloadJson`" else if (existingCols.contains("payload")) "`payload`" else "''"
                    val attemptsCol = if (existingCols.contains("attempts")) "`attempts`" else if (existingCols.contains("retryCount")) "`retryCount`" else "0"
                    val errorCol = if (existingCols.contains("error")) "`error`" else if (existingCols.contains("lastError")) "`lastError`" else "NULL"
                    val opCol = if (existingCols.contains("operation")) "COALESCE(NULLIF(`operation`, ''), 'CREATE')" else "'CREATE'"
                    val lastAttemptCol = if (existingCols.contains("lastAttemptAt")) "`lastAttemptAt`" else "0"

                    database.execSQL(
                        "INSERT INTO `outbox_v12_temp` (`id`, `entityType`, `entityId`, `operation`, `payloadJson`, `attempts`, `createdAt`, `lastAttemptAt`, `error`, `status`) " +
                        "SELECT `id`, `entityType`, `entityId`, $opCol, $payloadCol, $attemptsCol, `createdAt`, $lastAttemptCol, $errorCol, `status` FROM `outbox`"
                    )
                    database.execSQL("DROP TABLE `outbox`")
                    database.execSQL("ALTER TABLE `outbox_v12_temp` RENAME TO `outbox`")
                }
            }

            // 4. Ensure all newly added columns in legacy tables
            try {
                database.execSQL("ALTER TABLE `cached_exams` ADD COLUMN `isCompleted` INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
            try {
                database.execSQL("ALTER TABLE `cached_exams` ADD COLUMN `examType` TEXT NOT NULL DEFAULT 'MONTHLY'")
            } catch (_: Exception) {}
            try {
                database.execSQL("ALTER TABLE `cached_schedule_slots` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'SYNCED'")
            } catch (_: Exception) {}
            try {
                database.execSQL("ALTER TABLE `cached_contents` ADD COLUMN `createdBy` TEXT NOT NULL DEFAULT ''")
            } catch (_: Exception) {}

            // 5. Ensure cached_user matches UserEntity (remove recoveryCode for security)
            try {
                val userPragma = database.query("PRAGMA table_info(`cached_user`)")
                val userCols = mutableSetOf<String>()
                while (userPragma.moveToNext()) {
                    userCols.add(userPragma.getString(1))
                }
                userPragma.close()

                if (userCols.contains("recoveryCode")) {
                    database.execSQL(
                        "CREATE TABLE `cached_user_v13_temp` (" +
                        "`id` TEXT NOT NULL, `fullName` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, " +
                        "`gradeId` INTEGER NOT NULL, `sectionId` TEXT NOT NULL, `role` TEXT NOT NULL, " +
                        "`defaultGroupId` TEXT NOT NULL, `token` TEXT NOT NULL, `lessonsCount` INTEGER NOT NULL, " +
                        "`homeworksCount` INTEGER NOT NULL, `issuesCount` INTEGER NOT NULL, `photosCount` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                    )
                    database.execSQL(
                        "INSERT INTO `cached_user_v13_temp` (`id`, `fullName`, `phoneNumber`, `gradeId`, `sectionId`, `role`, `defaultGroupId`, `token`, `lessonsCount`, `homeworksCount`, `issuesCount`, `photosCount`) " +
                        "SELECT `id`, `fullName`, `phoneNumber`, `gradeId`, `sectionId`, `role`, `defaultGroupId`, `token`, `lessonsCount`, `homeworksCount`, `issuesCount`, `photosCount` FROM `cached_user`"
                    )
                    database.execSQL("DROP TABLE `cached_user`")
                    database.execSQL("ALTER TABLE `cached_user_v13_temp` RENAME TO `cached_user`")
                }
            } catch (_: Exception) {}
        }

        val MIGRATION_PRESERVE_ALL = object : Migration(1, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                applyUniversalSchemaUpgrades(database)
            }
        }

        val MIGRATIONS_FROM_LEGACY_VERSIONS: Array<Migration> = (2..11).map { fromVer ->
            object : Migration(fromVer, 13) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    applyUniversalSchemaUpgrades(database)
                }
            }
        }.toTypedArray()

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL(
                        "ALTER TABLE `cached_exams` " +
                        "ADD COLUMN `isCompleted` INTEGER NOT NULL DEFAULT 0"
                    )
                } catch (e: Exception) {
                    // Column might already exist
                }
            }
        }

        val MIGRATION_10_12 = object : Migration(10, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                applyUniversalSchemaUpgrades(database)
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                applyUniversalSchemaUpgrades(database)
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                applyUniversalSchemaUpgrades(database)
            }
        }

        fun getInstance(context: Context): TanweerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TanweerDatabase::class.java,
                    "tanweer_local.db"
                )
                    .addMigrations(
                        MIGRATION_PRESERVE_ALL,
                        *MIGRATIONS_FROM_LEGACY_VERSIONS,
                        MIGRATION_10_11,
                        MIGRATION_10_12,
                        MIGRATION_11_12,
                        MIGRATION_12_13
                    )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
