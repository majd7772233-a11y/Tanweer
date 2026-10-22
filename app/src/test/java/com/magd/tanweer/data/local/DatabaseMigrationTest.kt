package com.magd.tanweer.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {

    @Test
    fun `universal schema upgrade migrates legacy outbox and daily_plans non-destructively`() {
        kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_legacy.db"
        context.deleteDatabase(dbName)

        // 1. Create a legacy SQLite database simulating version 1 with older schemas
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create base tables from v1
                    db.execSQL("CREATE TABLE `cached_user` (`id` TEXT NOT NULL, `fullName` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `gradeId` INTEGER NOT NULL, `sectionId` TEXT NOT NULL, `role` TEXT NOT NULL, `defaultGroupId` TEXT NOT NULL, `token` TEXT NOT NULL, `recoveryCode` TEXT, `lessonsCount` INTEGER NOT NULL, `homeworksCount` INTEGER NOT NULL, `issuesCount` INTEGER NOT NULL, `photosCount` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `cached_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `description` TEXT, `icon` TEXT, `role` TEXT NOT NULL, `memberCount` INTEGER NOT NULL, `isDiscoverable` INTEGER NOT NULL, `isOfficial` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `cached_schedule_slots` (`id` TEXT NOT NULL, `groupId` TEXT NOT NULL, `dayOfWeek` INTEGER NOT NULL, `slotOrder` INTEGER NOT NULL, `subjectId` TEXT NOT NULL, `subjectName` TEXT NOT NULL, `subjectIcon` TEXT NOT NULL, `colorHex` TEXT, `startTime` TEXT, `endTime` TEXT, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `cached_contents` (`id` TEXT NOT NULL, `groupId` TEXT NOT NULL, `studyDate` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `authorName` TEXT NOT NULL, `authorGradeSection` TEXT NOT NULL, `viewsCount` INTEGER NOT NULL, `usefulCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `subjectName` TEXT NOT NULL, `subjectIcon` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `mediaUrlsJson` TEXT NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `cached_homeworks` (`id` TEXT NOT NULL, `groupId` TEXT NOT NULL, `studyDate` TEXT NOT NULL, `dueDate` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `title` TEXT NOT NULL, `details` TEXT, `pageNumbers` TEXT, `questionNumbers` TEXT, `taskType` TEXT NOT NULL, `subjectName` TEXT NOT NULL, `subjectIcon` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `mediaUrlsJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `cached_homework_completions` (`homeworkId` TEXT NOT NULL, `userId` TEXT NOT NULL, `completedAt` INTEGER NOT NULL, PRIMARY KEY(`homeworkId`, `userId`))")
                    db.execSQL("CREATE TABLE `cached_exams` (`id` TEXT NOT NULL, `groupId` TEXT NOT NULL, `examDate` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `title` TEXT NOT NULL, `requiredChapters` TEXT, `notes` TEXT, `subjectName` TEXT NOT NULL, `subjectIcon` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `cached_events` (`id` TEXT NOT NULL, `groupId` TEXT NOT NULL, `eventDate` TEXT NOT NULL, `timeStr` TEXT, `title` TEXT NOT NULL, `description` TEXT, `category` TEXT NOT NULL, `location` TEXT, `createdAt` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `cached_issues` (`id` TEXT NOT NULL, `groupId` TEXT NOT NULL, `subjectId` TEXT, `homeworkId` TEXT, `examId` TEXT, `title` TEXT NOT NULL, `description` TEXT, `status` TEXT NOT NULL, `bestCommentId` TEXT, `authorName` TEXT NOT NULL, `subjectName` TEXT, `subjectIcon` TEXT, `commentsCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `cached_issue_comments` (`id` TEXT NOT NULL, `issueId` TEXT NOT NULL, `userId` TEXT NOT NULL, `authorName` TEXT NOT NULL, `comment` TEXT NOT NULL, `isBestAnswer` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `cached_books` (`id` TEXT NOT NULL, `gradeId` INTEGER NOT NULL, `subjectId` TEXT NOT NULL, `title` TEXT NOT NULL, `edition` TEXT, `fileSizeMb` REAL NOT NULL, `fileUrl` TEXT NOT NULL, `thumbnailUrl` TEXT, `subjectName` TEXT NOT NULL, `subjectIcon` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `cached_chat_messages` (`id` TEXT NOT NULL, `groupId` TEXT NOT NULL, `senderId` TEXT NOT NULL, `senderName` TEXT NOT NULL, `senderGradeSection` TEXT NOT NULL, `text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `book_reading_state` (`bookId` TEXT NOT NULL, `lastPage` INTEGER NOT NULL, `zoom` REAL NOT NULL, `theme` TEXT NOT NULL, `totalStudySeconds` INTEGER NOT NULL, `uniquePagesCount` INTEGER NOT NULL, `lastReadAt` INTEGER NOT NULL, PRIMARY KEY(`bookId`))")
                    db.execSQL("CREATE TABLE `book_bookmarks` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `book_notes` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL, `noteText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `book_vocabulary` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `word` TEXT NOT NULL, `meaning` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `book_drawings` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL, `strokesJson` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `cached_sync_meta` (`key` TEXT NOT NULL, `lastSyncedAt` INTEGER NOT NULL, `etag` TEXT, PRIMARY KEY(`key`))")

                    // Legacy outbox with older columns (payload, retryCount, lastError)
                    db.execSQL("CREATE TABLE `outbox` (`id` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `payload` TEXT NOT NULL, `status` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `lastError` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("INSERT INTO `outbox` VALUES ('legacy_item_1', 'HOMEWORK', 'hw_123', '{\"isCompleted\":true}', 'PENDING', 2, 'Network timeout', 1600000000)")

                    // Legacy daily_plans
                    db.execSQL("CREATE TABLE `daily_plans` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `relatedHomeworkId` TEXT, `date` TEXT NOT NULL, `time` TEXT, `isDone` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("INSERT INTO `daily_plans` VALUES ('plan_1', 'user_1', 'حل واجب الرياضيات', 'HOMEWORK', 'hw_123', '2026-10-19', '18:00', 1, 1600000000)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val rawDb = helper.writableDatabase

        // Verify legacy tables before migration
        val cursorOld = rawDb.query("SELECT * FROM outbox WHERE id = 'legacy_item_1'")
        assertTrue(cursorOld.moveToFirst())
        assertEquals("{\"isCompleted\":true}", cursorOld.getString(cursorOld.getColumnIndexOrThrow("payload")))
        assertEquals(2, cursorOld.getInt(cursorOld.getColumnIndexOrThrow("retryCount")))
        cursorOld.close()
        rawDb.close()

        // 2. Open via Room at version 12 with universal migrations
        val roomDb = Room.databaseBuilder(context, TanweerDatabase::class.java, dbName)
            .addMigrations(
                TanweerDatabase.MIGRATION_PRESERVE_ALL,
                *TanweerDatabase.MIGRATIONS_FROM_LEGACY_VERSIONS,
                TanweerDatabase.MIGRATION_10_11,
                TanweerDatabase.MIGRATION_10_12,
                TanweerDatabase.MIGRATION_11_12
            )
            .build()

        // Access DAOs to trigger Room schema validation and ensure no IllegalStateException is thrown
        val outboxDao = roomDb.outboxDao()
        val dailyPlanDao = roomDb.dailyPlanDao()
        val examDao = roomDb.examDao()

        // 3. Verify that legacy outbox data was non-destructively migrated to OutboxEntity schema
        val pendingItems = outboxDao.getPendingItemsSync()
        assertEquals(1, pendingItems.size)
        val migratedItem = pendingItems[0]
        assertEquals("legacy_item_1", migratedItem.id)
        assertEquals("HOMEWORK", migratedItem.entityType)
        assertEquals("hw_123", migratedItem.entityId)
        assertEquals("{\"isCompleted\":true}", migratedItem.payloadJson)
        assertEquals(2, migratedItem.attempts)
        assertEquals("Network timeout", migratedItem.error)
        assertEquals("CREATE", migratedItem.operation)

        // 4. Verify that legacy daily_plans data was migrated to cached_daily_plan
        val planItems = dailyPlanDao.getPlanForDateSync("user_1", "2026-10-19")
        assertEquals(1, planItems.size)
        assertEquals("plan_1", planItems[0].id)
        assertEquals("user_1", planItems[0].userId)
        assertEquals("hw_123", planItems[0].homeworkId)
        assertEquals("حل واجب الرياضيات", planItems[0].title)
        assertTrue(planItems[0].isCompleted)

        roomDb.close()
        context.deleteDatabase(dbName)
        }
    }
}
