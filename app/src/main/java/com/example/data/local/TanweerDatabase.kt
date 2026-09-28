package com.example.data.local

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
        ExamEntity::class,
        EventEntity::class,
        IssueEntity::class,
        BookEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
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
