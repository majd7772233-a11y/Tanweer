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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object TanweerNotificationManager {

    const val CHANNEL_ID = "tanweer_academic_channel"
    private const val NOTIFICATION_HOMEWORK_ID = 2001
    private const val NOTIFICATION_EXAM_ID = 2002
    private const val NOTIFICATION_SCHEDULE_ID = 2003

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

    fun checkAndSendReminders(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences("tanweer_prefs", Context.MODE_PRIVATE)
        val notifyHw = prefs.getBoolean("notify_homework", true)
        val notifySch = prefs.getBoolean("notify_schedule", true)
        val notifyExm = prefs.getBoolean("notify_exams", true)
        val autoSyncEnabled = prefs.getBoolean("auto_sync_enabled", true)

        initChannel(context)

        // Run independent background maintenance (books + updates) regardless of notification settings
        performBackgroundMaintenance(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = TanweerDatabase.getInstance(context)
                val user = db.userDao().getUserSync() ?: return@launch
                val groupId = user.defaultGroupId
                if (groupId.isBlank()) return@launch

                // Pre-sync with server before sending notifications so data is fresh (only if auto-sync is enabled)
                if (autoSyncEnabled) {
                    try {
                        val repo = com.magd.tanweer.data.repository.TanweerRepository(db)
                        repo.syncHomeworks(groupId)
                        repo.syncExams(groupId)
                        repo.syncSchedule(groupId)
                        repo.syncEvents(groupId)
                    } catch (_: Exception) {}
                }

                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                val todayStr = sdf.format(Date())

                val openHomeworkIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(EXTRA_NAVIGATE_TO, "HOMEWORK")
                }
                val pendingHwIntent = PendingIntent.getActivity(
                    context,
                    101,
                    openHomeworkIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val openExamIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(EXTRA_NAVIGATE_TO, "EXAMS")
                }
                val pendingExamIntent = PendingIntent.getActivity(
                    context,
                    102,
                    openExamIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val openScheduleIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(EXTRA_NAVIGATE_TO, "SCHEDULE")
                }
                val pendingScheduleIntent = PendingIntent.getActivity(
                    context,
                    103,
                    openScheduleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // 1. Check Homeworks if user enabled it
                if (notifyHw) {
                    val homeworks = db.homeworkDao().getHomeworks(groupId).first()
                    val completions = db.homeworkDao().getCompletionsForUser(user.id).first()
                    val completedIds = completions.map { it.homeworkId }.toSet()
                    val pendingHw = homeworks.filter { !completedIds.contains(it.id) }

                    if (pendingHw.isNotEmpty()) {
                        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("📝 تذكير الواجبات المدرسية")
                            .setContentText("لديك ${pendingHw.size} واجبات مدرسية قيد الإنجاز للشعبة.")
                            .setStyle(NotificationCompat.BigTextStyle().bigText("لديك ${pendingHw.size} واجبات غير مكتملة، منها: ${pendingHw.first().title}."))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentIntent(pendingHwIntent)
                            .setAutoCancel(true)
                            .build()

                        try {
                            NotificationManagerCompat.from(context).notify(NOTIFICATION_HOMEWORK_ID, notification)
                        } catch (_: SecurityException) {}
                    }
                }

                // 2. Check Upcoming Exams if user enabled it
                if (notifyExm) {
                    val exams = db.examDao().getExams(groupId).first()
                    val upcoming = exams.filter { it.examDate >= todayStr }
                    if (upcoming.isNotEmpty()) {
                        val nextExam = upcoming.first()
                        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("🔴 تذكير بالاختبارات القادمة")
                            .setContentText("اختبار ${nextExam.subjectName} بتاريخ ${nextExam.examDate}")
                            .setStyle(NotificationCompat.BigTextStyle().bigText("اختبار ${nextExam.subjectName}: ${nextExam.title}، المقرر: ${nextExam.requiredChapters ?: "كامل المنهج"}."))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentIntent(pendingExamIntent)
                            .setAutoCancel(true)
                            .build()

                        try {
                            NotificationManagerCompat.from(context).notify(NOTIFICATION_EXAM_ID, notification)
                        } catch (_: SecurityException) {}
                    }
                }

                // 3. Check Schedule if user enabled it (Point 20: Fix DAY_OF_WEEK conversion from Calendar to App indices)
                if (notifySch) {
                    val calendar = Calendar.getInstance()
                    val rawDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon, 3=Tue, 4=Wed, 5=Thu, 6=Fri, 7=Sat
                    val appDayOfWeek = calendarDayToAppDayIndex(rawDayOfWeek)

                    if (appDayOfWeek >= 0) {
                        val slots = db.scheduleDao().getSlotsForDay(groupId, appDayOfWeek).first()
                        if (slots.isNotEmpty()) {
                            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                                .setSmallIcon(R.mipmap.ic_launcher)
                                .setContentTitle("🏫 جدول حصص اليوم")
                                .setContentText("لديك ${slots.size} حصص دراسية مجدولة لليوم.")
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .setContentIntent(pendingScheduleIntent)
                                .setAutoCancel(true)
                                .build()

                            try {
                                NotificationManagerCompat.from(context).notify(NOTIFICATION_SCHEDULE_ID, notification)
                            } catch (_: SecurityException) {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TanweerNotifications", "Error checking academic reminders", e)
            }
        }
    }

    const val EXTRA_NAVIGATE_TO = "extra_navigate_to"

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
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
        subjectName: String
    ) {
        initChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_NAVIGATE_TO, "HOMEWORK")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (homeworkId.hashCode() and 0x7FFFFFFF),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⏰ تذكير بموعد تسليم واجب: $subjectName")
            .setContentText("تذكير: $title")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "تذكير الواجب المدرسي لمادة $subjectName:\n$title\nيرجى إنجاز الواجب ورفعه للشعبة قبل انتهاء المهلة."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 350, 250, 350))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                (homeworkId.hashCode() and 0x7FFFFFFF),
                notification
            )
        } catch (_: SecurityException) {}
    }

    fun scheduleExamReminder(
        context: Context,
        examId: String,
        title: String,
        subjectName: String,
        examDate: String
    ): ScheduleReminderResult {
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
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

    fun cancelReminder(context: Context, itemId: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, TanweerAlarmReceiver::class.java)
            val requestCode = (itemId.hashCode() and 0x7FFFFFFF)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null && alarmManager != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
            NotificationManagerCompat.from(context).cancel(requestCode)
        } catch (_: Exception) {}
    }

    fun showIndividualExamNotification(
        context: Context,
        examId: String,
        title: String,
        subjectName: String
    ) {
        initChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_NAVIGATE_TO, "EXAMS")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (examId.hashCode() and 0x7FFFFFFF),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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

        try {
            NotificationManagerCompat.from(context).notify(
                (examId.hashCode() and 0x7FFFFFFF),
                notification
            )
        } catch (_: SecurityException) {}
    }
}
