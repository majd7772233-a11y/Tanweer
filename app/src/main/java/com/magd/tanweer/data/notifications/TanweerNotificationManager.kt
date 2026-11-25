package com.magd.tanweer.data.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.magd.tanweer.MainActivity
import com.magd.tanweer.R
import com.magd.tanweer.data.local.TanweerDatabase
import com.magd.tanweer.data.notifications.NotificationNavigationPolicy
import com.magd.tanweer.util.HomeworkTaskKind
import com.magd.tanweer.util.HomeworkTaskTypePolicy
import com.magd.tanweer.util.NotificationDeliveryPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong

object TanweerNotificationManager {

    const val CHANNEL_ID = "tanweer_academic_channel"
    internal const val NOTIFICATION_HOMEWORK_ID = 2001
    internal const val NOTIFICATION_EXAM_ID = 2002
    internal const val NOTIFICATION_SCHEDULE_ID = 2003

    private const val PREFERENCES_NAME = "tanweer_prefs"
    private const val PREF_NOTIFY_HOMEWORK = "notify_homework"
    private const val PREF_NOTIFY_EXAMS = "notify_exams"
    private const val PREF_NOTIFY_SCHEDULE = "notify_schedule"
    private const val PREF_HOMEWORK_REMINDER_IDS = "scheduled_homework_reminder_ids"
    private const val PREF_EXAM_REMINDER_IDS = "scheduled_exam_reminder_ids"

    private val notificationStateLock = Any()
    private val homeworkNotificationGeneration = AtomicLong(0L)
    private val examNotificationGeneration = AtomicLong(0L)
    private val scheduleNotificationGeneration = AtomicLong(0L)

    private data class NotificationSettingsSnapshot(
        val homeworkEnabled: Boolean,
        val scheduleEnabled: Boolean,
        val examsEnabled: Boolean,
        val autoSyncEnabled: Boolean,
        val homeworkGeneration: Long,
        val scheduleGeneration: Long,
        val examGeneration: Long
    )

    internal fun homeworkNotificationGenerationSnapshot(): Long =
        synchronized(notificationStateLock) { homeworkNotificationGeneration.get() }

    internal fun examNotificationGenerationSnapshot(): Long =
        synchronized(notificationStateLock) { examNotificationGeneration.get() }

    private fun readNotificationSettings(context: Context): NotificationSettingsSnapshot =
        synchronized(notificationStateLock) {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            NotificationSettingsSnapshot(
                homeworkEnabled = preferences.getBoolean(PREF_NOTIFY_HOMEWORK, true),
                scheduleEnabled = preferences.getBoolean(PREF_NOTIFY_SCHEDULE, true),
                examsEnabled = preferences.getBoolean(PREF_NOTIFY_EXAMS, true),
                autoSyncEnabled = preferences.getBoolean("auto_sync_enabled", true),
                homeworkGeneration = homeworkNotificationGeneration.get(),
                scheduleGeneration = scheduleNotificationGeneration.get(),
                examGeneration = examNotificationGeneration.get()
            )
        }

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "التنبيهات الأكاديمية والمدرسية"
            val descriptionText = "إشعارات تذكيرية بالواجبات المدرسية، مواعيد الاختبارات، والجدول الدراسي"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleDailyReminder(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, TanweerAlarmReceiver::class.java).apply {
                action = TanweerAlarmReceiver.ACTION_CHECK_REMINDERS
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Set daily morning check at 07:00 AM
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 7)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
            Log.d("TanweerNotifications", "Daily academic reminders scheduled successfully")
        } catch (e: Exception) {
            Log.e("TanweerNotifications", "Failed to schedule daily reminders", e)
        }
    }

    /**
     * Independent periodic background maintenance for books catalog and app update checks.
     * Operates completely decoupled from notification permissions or user reminder settings.
     * Enforces strict cooldowns (24h for app updates, 12h for books) to ensure minimal data usage.
     */
    fun performBackgroundMaintenance(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences("tanweer_prefs", Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()

                // 1. Periodic App Update Check (24h cooldown)
                val lastUpdateCheck = prefs.getLong("last_background_update_check_ts", 0L)
                if (now - lastUpdateCheck >= 24 * 60 * 60 * 1000L) {
                    try {
                        com.magd.tanweer.util.AppVersionManager.checkPeriodicUpdate(context)
                        prefs.edit().putLong("last_background_update_check_ts", now).apply()
                    } catch (e: Exception) {
                        Log.w("TanweerMaintenance", "Background update check note: ${e.message}")
                    }
                }

                // 2. Periodic Books Catalog Sync (12h cooldown, only if auto-sync is enabled)
                val autoSyncEnabled = prefs.getBoolean("auto_sync_enabled", true)
                val lastBooksSync = prefs.getLong("last_background_books_sync_ts", 0L)
                if (autoSyncEnabled && now - lastBooksSync >= 12 * 60 * 60 * 1000L) {
                    try {
                        val db = TanweerDatabase.getInstance(context)
                        val user = db.userDao().getUserSync()
                        val gradeId = user?.gradeId ?: 10
                        val repo = com.magd.tanweer.data.repository.TanweerRepository(db)
                        repo.syncBooks(gradeId)
                        prefs.edit().putLong("last_background_books_sync_ts", now).apply()
                    } catch (e: Exception) {
                        Log.w("TanweerMaintenance", "Background books sync note: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w("TanweerMaintenance", "Background maintenance error: ${e.message}")
            }
        }
    }

    fun setHomeworkNotificationsEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        synchronized(notificationStateLock) {
            appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_NOTIFY_HOMEWORK, enabled)
                .apply()
            homeworkNotificationGeneration.incrementAndGet()
            if (!enabled) {
                NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_HOMEWORK_ID)
            }
        }
        scheduleDailyReminder(appContext)
        if (enabled) {
            checkAndSendReminders(appContext)
        } else {
            cancelStoredHomeworkRemindersIfDisabled(appContext)
            cancelCachedHomeworkRemindersIfDisabled(appContext)
        }
    }

    fun setExamNotificationsEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        synchronized(notificationStateLock) {
            appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_NOTIFY_EXAMS, enabled)
                .apply()
            examNotificationGeneration.incrementAndGet()
            if (!enabled) {
                NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_EXAM_ID)
            }
        }
        scheduleDailyReminder(appContext)
        if (enabled) {
            checkAndSendReminders(appContext)
        } else {
            cancelStoredExamRemindersIfDisabled(appContext)
            cancelCachedExamRemindersIfDisabled(appContext)
        }
    }

    fun setScheduleNotificationsEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        synchronized(notificationStateLock) {
            appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_NOTIFY_SCHEDULE, enabled)
                .apply()
            scheduleNotificationGeneration.incrementAndGet()
            if (!enabled) {
                NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_SCHEDULE_ID)
            }
        }
        scheduleDailyReminder(appContext)
        if (enabled) {
            checkAndSendReminders(appContext)
        }
    }

    private fun reminderRegistryKey(action: String): String? = when (action) {
        TanweerAlarmReceiver.ACTION_HOMEWORK_REMINDER -> PREF_HOMEWORK_REMINDER_IDS
        TanweerAlarmReceiver.ACTION_EXAM_REMINDER -> PREF_EXAM_REMINDER_IDS
        else -> null
    }

    private fun rememberScheduledReminderLocked(context: Context, itemId: String, action: String) {
        val key = reminderRegistryKey(action) ?: return
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val ids = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        ids.add(itemId)
        preferences.edit().putStringSet(key, ids).apply()
    }

    private fun forgetReminderRegistryEntry(context: Context, itemId: String, action: String?) {
        val keys = action?.let { listOfNotNull(reminderRegistryKey(it)) } ?: listOf(
            PREF_HOMEWORK_REMINDER_IDS,
            PREF_EXAM_REMINDER_IDS
        )
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        keys.forEach { key ->
            val ids = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
            if (ids.remove(itemId)) editor.putStringSet(key, ids)
        }
        editor.apply()
    }

    private fun cancelStoredHomeworkRemindersIfDisabled(context: Context) {
        synchronized(notificationStateLock) {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            if (preferences.getBoolean(PREF_NOTIFY_HOMEWORK, true)) return
            val ids = preferences.getStringSet(PREF_HOMEWORK_REMINDER_IDS, emptySet()).orEmpty().toList()
            cancelHomeworkNotifications(context, ids)
            preferences.edit().remove(PREF_HOMEWORK_REMINDER_IDS).apply()
        }
    }

    private fun cancelStoredExamRemindersIfDisabled(context: Context) {
        synchronized(notificationStateLock) {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            if (preferences.getBoolean(PREF_NOTIFY_EXAMS, true)) return
            val ids = preferences.getStringSet(PREF_EXAM_REMINDER_IDS, emptySet()).orEmpty().toList()
            cancelExamNotifications(context, ids)
            preferences.edit().remove(PREF_EXAM_REMINDER_IDS).apply()
        }
    }

    private fun cancelCachedHomeworkRemindersIfDisabled(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ids = TanweerDatabase.getInstance(context).homeworkDao().getAllHomeworkIds()
                synchronized(notificationStateLock) {
                    val enabled = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_NOTIFY_HOMEWORK, true)
                    if (!enabled) {
                        val registeredIds = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                            .getStringSet(PREF_HOMEWORK_REMINDER_IDS, emptySet()).orEmpty()
                        cancelHomeworkNotifications(context, ids + registeredIds)
                        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                            .edit().remove(PREF_HOMEWORK_REMINDER_IDS).apply()
                    }
                }
            } catch (error: Exception) {
                Log.w("TanweerNotifications", "Could not cancel every cached homework reminder", error)
            }
        }
    }

    private fun cancelCachedExamRemindersIfDisabled(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ids = TanweerDatabase.getInstance(context).examDao().getAllExamIds()
                synchronized(notificationStateLock) {
                    val enabled = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_NOTIFY_EXAMS, true)
                    if (!enabled) {
                        val registeredIds = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                            .getStringSet(PREF_EXAM_REMINDER_IDS, emptySet()).orEmpty()
                        cancelExamNotifications(context, ids + registeredIds)
                        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                            .edit().remove(PREF_EXAM_REMINDER_IDS).apply()
                    }
                }
            } catch (error: Exception) {
                Log.w("TanweerNotifications", "Could not cancel every cached exam reminder", error)
            }
        }
    }

    /**
     * Cancels only homework/project summary and per-item alerts. Exam and
     * schedule notifications remain untouched.
     */
    fun cancelHomeworkNotifications(context: Context, homeworkIds: Collection<String>) {
        synchronized(notificationStateLock) {
            val notifications = NotificationManagerCompat.from(context)
            notifications.cancel(NOTIFICATION_HOMEWORK_ID)
            homeworkIds.distinct().forEach { id ->
                cancelReminder(context, id, TanweerAlarmReceiver.ACTION_HOMEWORK_REMINDER)
            }
        }
    }

    /**
     * Cancels only exam summary and per-item alerts. Homework and schedule
     * notifications remain untouched.
     */
    fun cancelExamNotifications(context: Context, examIds: Collection<String>) {
        synchronized(notificationStateLock) {
            val notifications = NotificationManagerCompat.from(context)
            notifications.cancel(NOTIFICATION_EXAM_ID)
            examIds.distinct().forEach { id ->
                cancelReminder(context, id, TanweerAlarmReceiver.ACTION_EXAM_REMINDER)
            }
        }
    }

    private fun isPreferenceCurrentAndEnabled(
        context: Context,
        preferenceKey: String,
        generation: AtomicLong,
        expectedGeneration: Long
    ): Boolean = synchronized(notificationStateLock) {
        NotificationDeliveryPolicy.mayPost(
            enabled = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean(preferenceKey, true),
            expectedGeneration = expectedGeneration,
            currentGeneration = generation.get()
        )
    }

    private fun postNotificationIfCurrent(
        context: Context,
        preferenceKey: String,
        generation: AtomicLong,
        expectedGeneration: Long,
        notificationId: Int,
        notification: android.app.Notification
    ) {
        synchronized(notificationStateLock) {
            val notifications = NotificationManagerCompat.from(context)
            val enabled = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean(preferenceKey, true)
            if (!enabled) {
                notifications.cancel(notificationId)
                return
            }
            if (!NotificationDeliveryPolicy.mayPost(
                    enabled = enabled,
                    expectedGeneration = expectedGeneration,
                    currentGeneration = generation.get()
                )
            ) return
            try {
                notifications.notify(notificationId, notification)
            } catch (_: SecurityException) {
                // Notification permission can be revoked while this background
                // operation is running.
            }
        }
    }

    private fun cancelNotificationIfCurrent(
        context: Context,
        generation: AtomicLong,
        expectedGeneration: Long,
        notificationId: Int
    ) {
        synchronized(notificationStateLock) {
            if (generation.get() == expectedGeneration) {
                NotificationManagerCompat.from(context).cancel(notificationId)
            }
        }
    }

    fun checkAndSendReminders(context: Context) {
        // Capture each preference with its generation atomically so a toggle
        // between separate reads cannot make a stale OFF/ON decision current.
        val settings = readNotificationSettings(context)
        val notifyHw = settings.homeworkEnabled
        val notifySch = settings.scheduleEnabled
        val notifyExm = settings.examsEnabled
        val homeworkGeneration = settings.homeworkGeneration
        val examGeneration = settings.examGeneration
        val scheduleGeneration = settings.scheduleGeneration
        val autoSyncEnabled = settings.autoSyncEnabled

        // Reconcile persisted OFF preferences after process recreation as well.
        // This also removes any individual notification that survived a restart.
        if (!notifyHw) {
            cancelStoredHomeworkRemindersIfDisabled(context)
            cancelCachedHomeworkRemindersIfDisabled(context)
        }
        if (!notifyExm) {
            cancelStoredExamRemindersIfDisabled(context)
            cancelCachedExamRemindersIfDisabled(context)
        }

        initChannel(context)
        performBackgroundMaintenance(context)

        CoroutineScope(Dispatchers.IO).launch {
            val notifications = NotificationManagerCompat.from(context)
            try {
                val db = TanweerDatabase.getInstance(context)
                val user = db.userDao().getUserSync()
                if (user == null || user.defaultGroupId.isBlank()) {
                    notifications.cancel(NOTIFICATION_HOMEWORK_ID)
                    notifications.cancel(NOTIFICATION_EXAM_ID)
                    notifications.cancel(NOTIFICATION_SCHEDULE_ID)
                    return@launch
                }

                val groupId = user.defaultGroupId
                var syncSucceeded = !autoSyncEnabled
                if (autoSyncEnabled) {
                    val syncResults = try {
                        val repo = com.magd.tanweer.data.repository.TanweerRepository(db)
                        listOf(
                            repo.syncHomeworks(groupId),
                            repo.syncExams(groupId),
                            repo.syncSchedule(groupId),
                            repo.syncEvents(groupId)
                        )
                    } catch (error: Exception) {
                        Log.w("TanweerNotifications", "Reminder refresh failed; retaining existing alerts", error)
                        emptyList()
                    }
                    syncSucceeded = syncResults.size == 4 &&
                        syncResults.all { it is com.magd.tanweer.data.repository.SyncResult.Success }
                    if (!syncSucceeded) {
                        Log.w("TanweerNotifications", "At least one reminder source did not return a complete server snapshot")
                    }
                }
                val mayDismissEmptySnapshot =
                    com.magd.tanweer.util.AcademicReminderPolicy
                        .mayDismissEmptySnapshot(autoSyncEnabled, syncSucceeded)

                val today = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
                val pendingHwIntent = createNavigationPendingIntent(context, 101, "HOMEWORK")
                val pendingExamIntent = createNavigationPendingIntent(context, 102, "EXAMS")
                val pendingScheduleIntent = createNavigationPendingIntent(context, 103, "SCHEDULE")

                if (!notifyHw) {
                    cancelNotificationIfCurrent(
                        context, homeworkNotificationGeneration, homeworkGeneration, NOTIFICATION_HOMEWORK_ID
                    )
                } else {
                    try {
                        val homeworks = db.homeworkDao().getHomeworks(groupId).first()
                        val completions = db.homeworkDao().getCompletionsForUser(user.id).first()
                        val completedIds = completions.map { it.homeworkId }.toSet()
                        val pending = homeworks.filter {
                            it.id !in completedIds &&
                                it.syncStatus != "DELETE_PENDING" &&
                                HomeworkTaskTypePolicy.isHomework(it.taskType)
                        }
                        if (pending.isNotEmpty()) {
                            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                                .setSmallIcon(R.mipmap.ic_launcher)
                                .setContentTitle(context.getString(R.string.homework_notification_title))
                                .setContentText(context.getString(R.string.homework_notification_content, pending.size))
                                .setStyle(
                                    NotificationCompat.BigTextStyle().bigText(
                                        context.getString(
                                            R.string.homework_notification_big_text,
                                            pending.size,
                                            pending.first().title
                                        )
                                    )
                                )
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setContentIntent(pendingHwIntent)
                                .setAutoCancel(true)
                                .build()
                            postNotificationIfCurrent(
                                context, PREF_NOTIFY_HOMEWORK, homeworkNotificationGeneration,
                                homeworkGeneration, NOTIFICATION_HOMEWORK_ID, notification
                            )
                        } else if (mayDismissEmptySnapshot) {
                            cancelNotificationIfCurrent(
                                context, homeworkNotificationGeneration, homeworkGeneration, NOTIFICATION_HOMEWORK_ID
                            )
                        }
                    } catch (error: Exception) {
                        Log.w("TanweerNotifications", "Could not verify homework state; keeping the existing alert", error)
                    }
                }

                if (!notifyExm) {
                    cancelNotificationIfCurrent(
                        context, examNotificationGeneration, examGeneration, NOTIFICATION_EXAM_ID
                    )
                } else {
                    try {
                        val exams = db.examDao().getExams(groupId).first()
                        val upcoming = exams.filter {
                            it.examDate >= today && !it.isCompleted && it.syncStatus != "DELETE_PENDING"
                        }
                        if (upcoming.isNotEmpty()) {
                            val nextExam = upcoming.minByOrNull { it.examDate }!!
                            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                                .setSmallIcon(R.mipmap.ic_launcher)
                                .setContentTitle("🔴 تذكير بالاختبارات القادمة")
                                .setContentText("اختبار ${nextExam.subjectName} بتاريخ ${nextExam.examDate}")
                                .setStyle(NotificationCompat.BigTextStyle().bigText("اختبار ${nextExam.subjectName}: ${nextExam.title}، المقرر: ${nextExam.requiredChapters ?: "كامل المنهج"}."))
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setContentIntent(pendingExamIntent)
                                .setAutoCancel(true)
                                .build()
                            postNotificationIfCurrent(
                                context, PREF_NOTIFY_EXAMS, examNotificationGeneration,
                                examGeneration, NOTIFICATION_EXAM_ID, notification
                            )
                        } else if (mayDismissEmptySnapshot) {
                            cancelNotificationIfCurrent(
                                context, examNotificationGeneration, examGeneration, NOTIFICATION_EXAM_ID
                            )
                        }
                    } catch (error: Exception) {
                        Log.w("TanweerNotifications", "Could not verify exam state; keeping the existing alert", error)
                    }
                }

                if (!notifySch) {
                    cancelNotificationIfCurrent(
                        context, scheduleNotificationGeneration, scheduleGeneration, NOTIFICATION_SCHEDULE_ID
                    )
                } else {
                    try {
                        val appDay = calendarDayToAppDayIndex(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
                        val slots = if (appDay >= 0) {
                            db.scheduleDao().getSlotsForDay(groupId, appDay).first()
                        } else emptyList()
                        if (slots.isNotEmpty()) {
                            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                                .setSmallIcon(R.mipmap.ic_launcher)
                                .setContentTitle("🏫 جدول حصص اليوم")
                                .setContentText("لديك ${slots.size} حصص دراسية مجدولة لليوم.")
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .setContentIntent(pendingScheduleIntent)
                                .setAutoCancel(true)
                                .build()
                            postNotificationIfCurrent(
                                context, PREF_NOTIFY_SCHEDULE, scheduleNotificationGeneration,
                                scheduleGeneration, NOTIFICATION_SCHEDULE_ID, notification
                            )
                        } else if (mayDismissEmptySnapshot) {
                            cancelNotificationIfCurrent(
                                context, scheduleNotificationGeneration, scheduleGeneration, NOTIFICATION_SCHEDULE_ID
                            )
                        }
                    } catch (error: Exception) {
                        Log.w("TanweerNotifications", "Could not verify schedule state; keeping the existing alert", error)
                    }
                }
            } catch (error: Exception) {
                Log.e("TanweerNotifications", "Error checking academic reminders; existing alerts retained", error)
            }
        }
    }

    const val EXTRA_NAVIGATE_TO = "extra_navigate_to"

    private fun createNavigationPendingIntent(
        context: Context,
        requestCode: Int,
        destination: String
    ): PendingIntent {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = NotificationNavigationPolicy.activityLaunchFlags()
            putExtra(EXTRA_NAVIGATE_TO, destination)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Converts standard Java Calendar.DAY_OF_WEEK (Sunday = 1, Saturday = 7)
     * to Tanweer application schedule day index (Sunday = 0 .. Thursday = 4, Weekend = -1).
     * Point 20: Explicit unified conversion between systems.
     */
    fun calendarDayToAppDayIndex(calendarDayOfWeek: Int): Int {
        return when (calendarDayOfWeek) {
            Calendar.SUNDAY -> 0
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            else -> -1 // Weekend (Friday / Saturday)
        }
    }

    enum class ScheduleReminderResult {
        SUCCESS,
        NOTIFICATIONS_DISABLED,
        PERMISSION_NOTIFICATIONS_DENIED,
        PERMISSION_EXACT_ALARM_DENIED,
        ALARM_SERVICE_UNAVAILABLE,
        GENERAL_ERROR
    }

    fun scheduleHomeworkReminder(
        context: Context,
        homeworkId: String,
        title: String,
        subjectName: String,
        dueDate: String
    ): ScheduleReminderResult {
        val expectedGeneration = homeworkNotificationGeneration.get()
        if (!isPreferenceCurrentAndEnabled(
                context, PREF_NOTIFY_HOMEWORK, homeworkNotificationGeneration, expectedGeneration
            )
        ) return ScheduleReminderResult.NOTIFICATIONS_DISABLED
        initChannel(context)
        return try {
            // Check Notification permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasNotif = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasNotif) {
                    return ScheduleReminderResult.PERMISSION_NOTIFICATIONS_DENIED
                }
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return ScheduleReminderResult.ALARM_SERVICE_UNAVAILABLE

            // Check Exact Alarm permission on Android 12+ (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    return ScheduleReminderResult.PERMISSION_EXACT_ALARM_DENIED
                }
            }

            val intent = Intent(context, TanweerAlarmReceiver::class.java).apply {
                action = TanweerAlarmReceiver.ACTION_HOMEWORK_REMINDER
                putExtra(TanweerAlarmReceiver.EXTRA_HOMEWORK_ID, homeworkId)
                putExtra(TanweerAlarmReceiver.EXTRA_HOMEWORK_TITLE, title)
                putExtra(TanweerAlarmReceiver.EXTRA_SUBJECT_NAME, subjectName)
            }
            val requestCode = (homeworkId.hashCode() and 0x7FFFFFFF)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            var triggerTime = System.currentTimeMillis()

            try {
                val due = sdf.parse(dueDate)
                if (due != null) {
                    val dueCal = Calendar.getInstance().apply {
                        time = due
                        set(Calendar.HOUR_OF_DAY, 16) // 4:00 PM evening study reminder
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }

                    if (dueCal.timeInMillis > System.currentTimeMillis()) {
                        triggerTime = dueCal.timeInMillis
                    } else {
                        // If due date is today and 4:00 PM has already passed, schedule for tonight 8:00 PM
                        val tonightCal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 20)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }
                        if (tonightCal.timeInMillis > System.currentTimeMillis()) {
                            triggerTime = tonightCal.timeInMillis
                        } else {
                            // If it's already late night, schedule in 2 hours
                            triggerTime = System.currentTimeMillis() + (2 * 3600 * 1000L)
                        }
                    }
                } else {
                    triggerTime = System.currentTimeMillis() + (3600 * 1000L)
                }
            } catch (_: Exception) {
                triggerTime = System.currentTimeMillis() + (3600 * 1000L)
            }

            val scheduled = synchronized(notificationStateLock) {
                val stillEnabled = NotificationDeliveryPolicy.mayPost(
                    enabled = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_NOTIFY_HOMEWORK, true),
                    expectedGeneration = expectedGeneration,
                    currentGeneration = homeworkNotificationGeneration.get()
                )
                if (stillEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                    rememberScheduledReminderLocked(
                        context, homeworkId, TanweerAlarmReceiver.ACTION_HOMEWORK_REMINDER
                    )
                }
                stillEnabled
            }
            if (!scheduled) {
                pendingIntent.cancel()
                return ScheduleReminderResult.NOTIFICATIONS_DISABLED
            }
            Log.d("TanweerNotifications", "Exact homework reminder scheduled at $triggerTime for $homeworkId")
            ScheduleReminderResult.SUCCESS
        } catch (e: SecurityException) {
            Log.e("TanweerNotifications", "SecurityException scheduling homework reminder", e)
            ScheduleReminderResult.PERMISSION_EXACT_ALARM_DENIED
        } catch (e: Exception) {
            Log.e("TanweerNotifications", "Failed to schedule homework reminder", e)
            ScheduleReminderResult.GENERAL_ERROR
        }
    }

    fun showIndividualHomeworkNotification(
        context: Context,
        homeworkId: String,
        title: String,
        subjectName: String,
        taskType: String = "HOMEWORK",
        expectedGeneration: Long = homeworkNotificationGenerationSnapshot()
    ) {
        initChannel(context)
        val kind = HomeworkTaskTypePolicy.classify(taskType)
        val titleResource = when (kind) {
            HomeworkTaskKind.HOMEWORK -> R.string.homework_individual_notification_title
            HomeworkTaskKind.TASK -> R.string.project_individual_notification_title
            HomeworkTaskKind.UNKNOWN -> R.string.other_individual_notification_title
        }
        val contentResource = when (kind) {
            HomeworkTaskKind.HOMEWORK -> R.string.homework_individual_notification_content
            HomeworkTaskKind.TASK -> R.string.project_individual_notification_content
            HomeworkTaskKind.UNKNOWN -> R.string.other_individual_notification_content
        }
        val bigTextResource = when (kind) {
            HomeworkTaskKind.HOMEWORK -> R.string.homework_individual_notification_big_text
            HomeworkTaskKind.TASK -> R.string.project_individual_notification_big_text
            HomeworkTaskKind.UNKNOWN -> R.string.other_individual_notification_big_text
        }
        val pendingIntent = createNavigationPendingIntent(
            context,
            (homeworkId.hashCode() and 0x7FFFFFFF),
            "HOMEWORK"
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(titleResource, subjectName))
            .setContentText(context.getString(contentResource, title))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(bigTextResource, subjectName, title)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 350, 250, 350))
            .build()

        postNotificationIfCurrent(
            context, PREF_NOTIFY_HOMEWORK, homeworkNotificationGeneration,
            expectedGeneration, (homeworkId.hashCode() and 0x7FFFFFFF), notification
        )
    }

    fun scheduleExamReminder(
        context: Context,
        examId: String,
        title: String,
        subjectName: String,
        examDate: String
    ): ScheduleReminderResult {
        val expectedGeneration = examNotificationGeneration.get()
        if (!isPreferenceCurrentAndEnabled(
                context, PREF_NOTIFY_EXAMS, examNotificationGeneration, expectedGeneration
            )
        ) return ScheduleReminderResult.NOTIFICATIONS_DISABLED
        initChannel(context)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasNotif = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasNotif) {
                    return ScheduleReminderResult.PERMISSION_NOTIFICATIONS_DENIED
                }
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return ScheduleReminderResult.ALARM_SERVICE_UNAVAILABLE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    return ScheduleReminderResult.PERMISSION_EXACT_ALARM_DENIED
                }
            }

            val intent = Intent(context, TanweerAlarmReceiver::class.java).apply {
                action = TanweerAlarmReceiver.ACTION_EXAM_REMINDER
                putExtra(TanweerAlarmReceiver.EXTRA_EXAM_ID, examId)
                putExtra(TanweerAlarmReceiver.EXTRA_EXAM_TITLE, title)
                putExtra(TanweerAlarmReceiver.EXTRA_SUBJECT_NAME, subjectName)
            }
            val requestCode = (examId.hashCode() and 0x7FFFFFFF)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            var triggerTime = System.currentTimeMillis()

            try {
                val date = sdf.parse(examDate)
                if (date != null) {
                    val reminderCal = Calendar.getInstance().apply {
                        time = date
                        add(Calendar.DAY_OF_YEAR, -1)
                        set(Calendar.HOUR_OF_DAY, 18) // 6:00 PM previous evening
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }

                    if (reminderCal.timeInMillis > System.currentTimeMillis()) {
                        triggerTime = reminderCal.timeInMillis
                    } else {
                        val morningCal = Calendar.getInstance().apply {
                            time = date
                            set(Calendar.HOUR_OF_DAY, 6)
                            set(Calendar.MINUTE, 30)
                            set(Calendar.SECOND, 0)
                        }
                        if (morningCal.timeInMillis > System.currentTimeMillis()) {
                            triggerTime = morningCal.timeInMillis
                        } else {
                            triggerTime = System.currentTimeMillis() + (3600 * 1000L)
                        }
                    }
                } else {
                    triggerTime = System.currentTimeMillis() + (3600 * 1000L)
                }
            } catch (_: Exception) {
                triggerTime = System.currentTimeMillis() + (3600 * 1000L)
            }

            val scheduled = synchronized(notificationStateLock) {
                val stillEnabled = NotificationDeliveryPolicy.mayPost(
                    enabled = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_NOTIFY_EXAMS, true),
                    expectedGeneration = expectedGeneration,
                    currentGeneration = examNotificationGeneration.get()
                )
                if (stillEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                    rememberScheduledReminderLocked(
                        context, examId, TanweerAlarmReceiver.ACTION_EXAM_REMINDER
                    )
                }
                stillEnabled
            }
            if (!scheduled) {
                pendingIntent.cancel()
                return ScheduleReminderResult.NOTIFICATIONS_DISABLED
            }
            Log.d("TanweerNotifications", "Exact exam reminder scheduled at $triggerTime for $examId")
            ScheduleReminderResult.SUCCESS
        } catch (e: SecurityException) {
            Log.e("TanweerNotifications", "SecurityException scheduling exam reminder", e)
            ScheduleReminderResult.PERMISSION_EXACT_ALARM_DENIED
        } catch (e: Exception) {
            Log.e("TanweerNotifications", "Failed to schedule exam reminder", e)
            ScheduleReminderResult.GENERAL_ERROR
        }
    }

    /**
     * Cancels alarms created from the signed-in account's cached homework and exams.
     * IDs are read before Room is cleared so stale alerts cannot fire after a purge.
     */
    fun cancelAccountReminders(context: Context, homeworkIds: Collection<String>, examIds: Collection<String>) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            fun cancelAlarm(requestCode: Int, action: String) {
                val intent = Intent(context, TanweerAlarmReceiver::class.java).apply { this.action = action }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager?.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
            cancelAlarm(1001, TanweerAlarmReceiver.ACTION_CHECK_REMINDERS)
            homeworkIds.distinct().forEach {
                cancelAlarm(it.hashCode() and 0x7FFFFFFF, TanweerAlarmReceiver.ACTION_HOMEWORK_REMINDER)
            }
            examIds.distinct().forEach {
                cancelAlarm(it.hashCode() and 0x7FFFFFFF, TanweerAlarmReceiver.ACTION_EXAM_REMINDER)
            }
            NotificationManagerCompat.from(context).cancelAll()
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_HOMEWORK_REMINDER_IDS)
                .remove(PREF_EXAM_REMINDER_IDS)
                .apply()
        } catch (e: Exception) {
            Log.w("TanweerNotifications", "Could not cancel every account reminder during local reset", e)
        }
    }

    fun cancelReminder(context: Context, itemId: String, action: String? = null) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val requestCode = itemId.hashCode() and 0x7FFFFFFF
            val actions = action?.let { listOf(it) } ?: listOf(
                TanweerAlarmReceiver.ACTION_HOMEWORK_REMINDER,
                TanweerAlarmReceiver.ACTION_EXAM_REMINDER
            )
            actions.forEach { reminderAction ->
                val intent = Intent(context, TanweerAlarmReceiver::class.java).apply {
                    this.action = reminderAction
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager?.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
            NotificationManagerCompat.from(context).cancel(requestCode)
            forgetReminderRegistryEntry(context, itemId, action)
        } catch (error: Exception) {
            Log.w("TanweerNotifications", "Could not cancel reminder for " + itemId, error)
        }
    }

    fun showIndividualExamNotification(
        context: Context,
        examId: String,
        title: String,
        subjectName: String,
        expectedGeneration: Long = examNotificationGenerationSnapshot()
    ) {
        initChannel(context)
        val pendingIntent = createNavigationPendingIntent(
            context,
            (examId.hashCode() and 0x7FFFFFFF),
            "EXAMS"
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🔴 اقترب موعد اختبار: $subjectName")
            .setContentText("تذكير: $title")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "تذكير بموعد اختبار مادة $subjectName:\n$title\nراجع المقررات والفصول المطلوبة من جدول الاختبارات في تطبيق تنوير."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .build()

        postNotificationIfCurrent(
            context, PREF_NOTIFY_EXAMS, examNotificationGeneration,
            expectedGeneration, (examId.hashCode() and 0x7FFFFFFF), notification
        )
    }
}
