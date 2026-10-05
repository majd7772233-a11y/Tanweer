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

    fun checkAndSendReminders(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences("tanweer_prefs", Context.MODE_PRIVATE)
        val notifyHw = prefs.getBoolean("notify_homework", true)
        val notifySch = prefs.getBoolean("notify_schedule", true)
        val notifyExm = prefs.getBoolean("notify_exams", true)

        initChannel(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = TanweerDatabase.getInstance(context)
                val user = db.userDao().getUserSync() ?: return@launch
                val groupId = user.defaultGroupId
                if (groupId.isBlank()) return@launch

                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                val todayStr = sdf.format(Date())

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openIntent,
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
                            .setContentIntent(pendingIntent)
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
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .build()

                        try {
                            NotificationManagerCompat.from(context).notify(NOTIFICATION_EXAM_ID, notification)
                        } catch (_: SecurityException) {}
                    }
                }

                // 3. Check Schedule if user enabled it
                if (notifySch) {
                    val calendar = Calendar.getInstance()
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1=Sun, 7=Sat
                    val slots = db.scheduleDao().getSlotsForDay(groupId, dayOfWeek).first()
                    if (slots.isNotEmpty()) {
                        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("🏫 جدول حصص اليوم")
                            .setContentText("لديك ${slots.size} حصص دراسية مجدولة لليوم.")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .build()

                        try {
                            NotificationManagerCompat.from(context).notify(NOTIFICATION_SCHEDULE_ID, notification)
                        } catch (_: SecurityException) {}
                    }
                }
            } catch (e: Exception) {
                Log.e("TanweerNotifications", "Error checking academic reminders", e)
            }
        }
    }

    fun scheduleHomeworkReminder(
        context: Context,
        homeworkId: String,
        title: String,
        subjectName: String,
        dueDate: String
    ): Boolean {
        initChannel(context)
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
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
                        set(Calendar.HOUR_OF_DAY, 8)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }

                    if (dueCal.timeInMillis > System.currentTimeMillis()) {
                        triggerTime = dueCal.timeInMillis
                    } else {
                        // If date is today or earlier, schedule a test reminder in 15 seconds
                        triggerTime = System.currentTimeMillis() + 15_000L
                    }
                } else {
                    triggerTime = System.currentTimeMillis() + 15_000L
                }
            } catch (_: Exception) {
                triggerTime = System.currentTimeMillis() + 15_000L
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d("TanweerNotifications", "Exact homework reminder scheduled at $triggerTime for $homeworkId")
            true
        } catch (e: Exception) {
            Log.e("TanweerNotifications", "Failed to schedule homework reminder", e)
            false
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
}
