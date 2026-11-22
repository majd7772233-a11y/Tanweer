package com.magd.tanweer.data.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.magd.tanweer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotificationToggleBehaviorTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun disablingHomeworkCancelsOnlyHomeworkSummaryAndItemReminder() {
        val homeworkId = "toggle-homework-001"
        postNotification(TanweerNotificationManager.NOTIFICATION_HOMEWORK_ID)
        postNotification(TanweerNotificationManager.NOTIFICATION_EXAM_ID)
        postNotification(TanweerNotificationManager.NOTIFICATION_SCHEDULE_ID)
        postNotification(requestCode(homeworkId))
        createScheduledReminder(homeworkId, TanweerAlarmReceiver.ACTION_HOMEWORK_REMINDER)
        createScheduledReminder(homeworkId, TanweerAlarmReceiver.ACTION_EXAM_REMINDER)

        TanweerNotificationManager.cancelHomeworkNotifications(context, listOf(homeworkId))

        val activeIds = activeNotificationIds()
        assertFalse(activeIds.contains(TanweerNotificationManager.NOTIFICATION_HOMEWORK_ID))
        assertFalse(activeIds.contains(requestCode(homeworkId)))
        assertTrue(activeIds.contains(TanweerNotificationManager.NOTIFICATION_EXAM_ID))
        assertTrue(activeIds.contains(TanweerNotificationManager.NOTIFICATION_SCHEDULE_ID))
        assertNull(findPendingIntent(homeworkId, TanweerAlarmReceiver.ACTION_HOMEWORK_REMINDER))
        assertNotNull(findPendingIntent(homeworkId, TanweerAlarmReceiver.ACTION_EXAM_REMINDER))
    }

    @Test
    fun disablingExamsCancelsOnlyExamSummaryAndItemReminder() {
        val examId = "toggle-exam-001"
        postNotification(TanweerNotificationManager.NOTIFICATION_HOMEWORK_ID)
        postNotification(TanweerNotificationManager.NOTIFICATION_EXAM_ID)
        postNotification(TanweerNotificationManager.NOTIFICATION_SCHEDULE_ID)
        postNotification(requestCode(examId))
        createScheduledReminder(examId, TanweerAlarmReceiver.ACTION_HOMEWORK_REMINDER)
        createScheduledReminder(examId, TanweerAlarmReceiver.ACTION_EXAM_REMINDER)

        TanweerNotificationManager.cancelExamNotifications(context, listOf(examId))

        val activeIds = activeNotificationIds()
        assertTrue(activeIds.contains(TanweerNotificationManager.NOTIFICATION_HOMEWORK_ID))
        assertFalse(activeIds.contains(TanweerNotificationManager.NOTIFICATION_EXAM_ID))
        assertFalse(activeIds.contains(requestCode(examId)))
        assertTrue(activeIds.contains(TanweerNotificationManager.NOTIFICATION_SCHEDULE_ID))
        assertNotNull(findPendingIntent(examId, TanweerAlarmReceiver.ACTION_HOMEWORK_REMINDER))
        assertNull(findPendingIntent(examId, TanweerAlarmReceiver.ACTION_EXAM_REMINDER))
    }

    @Test
    fun homeworkAlarmCannotBeScheduledWhileItsGlobalPreferenceIsDisabled() {
        preferences().edit().putBoolean("notify_homework", false).commit()

        val result = TanweerNotificationManager.scheduleHomeworkReminder(
            context = context,
            homeworkId = "disabled-homework",
            title = "Test homework",
            subjectName = "Math",
            dueDate = "2099-12-31"
        )

        assertEquals(TanweerNotificationManager.ScheduleReminderResult.NOTIFICATIONS_DISABLED, result)
        assertNull(findPendingIntent("disabled-homework", TanweerAlarmReceiver.ACTION_HOMEWORK_REMINDER))
    }

    @Test
    fun examAlarmCannotBeScheduledWhileItsGlobalPreferenceIsDisabled() {
        preferences().edit().putBoolean("notify_exams", false).commit()

        val result = TanweerNotificationManager.scheduleExamReminder(
            context = context,
            examId = "disabled-exam",
            title = "Test exam",
            subjectName = "Science",
            examDate = "2099-12-31"
        )

        assertEquals(TanweerNotificationManager.ScheduleReminderResult.NOTIFICATIONS_DISABLED, result)
        assertNull(findPendingIntent("disabled-exam", TanweerAlarmReceiver.ACTION_EXAM_REMINDER))
    }

    @Test
    fun individualHomeworkAndExamNotificationsCannotPostWhenTheirTypesAreDisabled() {
        preferences().edit()
            .putBoolean("notify_homework", false)
            .putBoolean("notify_exams", false)
            .commit()

        val homeworkId = "blocked-homework-alert"
        val examId = "blocked-exam-alert"
        TanweerNotificationManager.showIndividualHomeworkNotification(
            context, homeworkId, "Homework", "Math", "HOMEWORK"
        )
        TanweerNotificationManager.showIndividualExamNotification(
            context, examId, "Exam", "Science"
        )

        val activeIds = activeNotificationIds()
        assertFalse(activeIds.contains(requestCode(homeworkId)))
        assertFalse(activeIds.contains(requestCode(examId)))
    }

    @Test
    fun turningOffScheduleCancelsItsSummaryWithoutCancelingOtherTypes() {
        postNotification(TanweerNotificationManager.NOTIFICATION_HOMEWORK_ID)
        postNotification(TanweerNotificationManager.NOTIFICATION_EXAM_ID)
        postNotification(TanweerNotificationManager.NOTIFICATION_SCHEDULE_ID)

        TanweerNotificationManager.setScheduleNotificationsEnabled(context, false)

        val activeIds = activeNotificationIds()
        assertTrue(activeIds.contains(TanweerNotificationManager.NOTIFICATION_HOMEWORK_ID))
        assertTrue(activeIds.contains(TanweerNotificationManager.NOTIFICATION_EXAM_ID))
        assertFalse(activeIds.contains(TanweerNotificationManager.NOTIFICATION_SCHEDULE_ID))
        assertFalse(preferences().getBoolean("notify_schedule", true))
    }


    @Test
    fun disablingHomeworkThroughSettingsCancelsPersistedAlarmsAndActiveAlerts() {
        val homeworkId = "persisted-homework-reminder"
        preferences().edit().putBoolean("notify_homework", true).commit()
        val result = TanweerNotificationManager.scheduleHomeworkReminder(
            context, homeworkId, "Homework", "Math", "2099-12-31"
        )
        assertEquals(TanweerNotificationManager.ScheduleReminderResult.SUCCESS, result)
        postNotification(TanweerNotificationManager.NOTIFICATION_HOMEWORK_ID)
        postNotification(requestCode(homeworkId))
        postNotification(TanweerNotificationManager.NOTIFICATION_EXAM_ID)
        postNotification(TanweerNotificationManager.NOTIFICATION_SCHEDULE_ID)

        TanweerNotificationManager.setHomeworkNotificationsEnabled(context, false)

        assertFalse(preferences().getBoolean("notify_homework", true))
        assertNull(findPendingIntent(homeworkId, TanweerAlarmReceiver.ACTION_HOMEWORK_REMINDER))
        assertFalse(activeNotificationIds().contains(TanweerNotificationManager.NOTIFICATION_HOMEWORK_ID))
        assertFalse(activeNotificationIds().contains(requestCode(homeworkId)))
        assertTrue(activeNotificationIds().contains(TanweerNotificationManager.NOTIFICATION_EXAM_ID))
        assertTrue(activeNotificationIds().contains(TanweerNotificationManager.NOTIFICATION_SCHEDULE_ID))
        assertTrue(preferences().getStringSet("scheduled_homework_reminder_ids", emptySet()).isNullOrEmpty())
    }

    @Test
    fun disablingExamsThroughSettingsCancelsPersistedAlarmsAndActiveAlerts() {
        val examId = "persisted-exam-reminder"
        preferences().edit().putBoolean("notify_exams", true).commit()
        val result = TanweerNotificationManager.scheduleExamReminder(
            context, examId, "Exam", "Science", "2099-12-31"
        )
        assertEquals(TanweerNotificationManager.ScheduleReminderResult.SUCCESS, result)
        postNotification(TanweerNotificationManager.NOTIFICATION_EXAM_ID)
        postNotification(requestCode(examId))
        postNotification(TanweerNotificationManager.NOTIFICATION_HOMEWORK_ID)
        postNotification(TanweerNotificationManager.NOTIFICATION_SCHEDULE_ID)

        TanweerNotificationManager.setExamNotificationsEnabled(context, false)

        assertFalse(preferences().getBoolean("notify_exams", true))
        assertNull(findPendingIntent(examId, TanweerAlarmReceiver.ACTION_EXAM_REMINDER))
        assertFalse(activeNotificationIds().contains(TanweerNotificationManager.NOTIFICATION_EXAM_ID))
        assertFalse(activeNotificationIds().contains(requestCode(examId)))
        assertTrue(activeNotificationIds().contains(TanweerNotificationManager.NOTIFICATION_HOMEWORK_ID))
        assertTrue(activeNotificationIds().contains(TanweerNotificationManager.NOTIFICATION_SCHEDULE_ID))
        assertTrue(preferences().getStringSet("scheduled_exam_reminder_ids", emptySet()).isNullOrEmpty())
    }


    @Test
    fun persistedOffPreferencesCancelRegisteredAlarmsDuringTheNextReminderCheck() {
        val homeworkId = "restart-homework-reminder"
        val examId = "restart-exam-reminder"
        preferences().edit()
            .putBoolean("notify_homework", true)
            .putBoolean("notify_exams", true)
            .commit()

        assertEquals(
            TanweerNotificationManager.ScheduleReminderResult.SUCCESS,
            TanweerNotificationManager.scheduleHomeworkReminder(
                context, homeworkId, "Homework", "Math", "2099-12-31"
            )
        )
        assertEquals(
            TanweerNotificationManager.ScheduleReminderResult.SUCCESS,
            TanweerNotificationManager.scheduleExamReminder(
                context, examId, "Exam", "Science", "2099-12-31"
            )
        )
        assertNotNull(findPendingIntent(homeworkId, TanweerAlarmReceiver.ACTION_HOMEWORK_REMINDER))
        assertNotNull(findPendingIntent(examId, TanweerAlarmReceiver.ACTION_EXAM_REMINDER))

        // Simulate values restored from SharedPreferences after process death:
        // no ViewModel setter/generation increment runs in the new process.
        preferences().edit()
            .putBoolean("notify_homework", false)
            .putBoolean("notify_exams", false)
            .commit()

        TanweerNotificationManager.checkAndSendReminders(context)

        assertNull(findPendingIntent(homeworkId, TanweerAlarmReceiver.ACTION_HOMEWORK_REMINDER))
        assertNull(findPendingIntent(examId, TanweerAlarmReceiver.ACTION_EXAM_REMINDER))
        assertTrue(preferences().getStringSet("scheduled_homework_reminder_ids", emptySet()).isNullOrEmpty())
        assertTrue(preferences().getStringSet("scheduled_exam_reminder_ids", emptySet()).isNullOrEmpty())
    }

    @Test
    fun anAlarmStartedBeforeOffCannotPostAfterThePreferenceIsTurnedBackOn() {
        val homeworkId = "stale-homework-broadcast"
        val examId = "stale-exam-broadcast"
        preferences().edit()
            .putBoolean("notify_homework", true)
            .putBoolean("notify_exams", true)
            .commit()

        val homeworkGeneration = TanweerNotificationManager.homeworkNotificationGenerationSnapshot()
        val examGeneration = TanweerNotificationManager.examNotificationGenerationSnapshot()

        TanweerNotificationManager.setHomeworkNotificationsEnabled(context, false)
        TanweerNotificationManager.setExamNotificationsEnabled(context, false)

        // Simulate a quick ON transition after OFF. The in-flight broadcasts
        // retain the older generations captured when Android delivered them.
        preferences().edit()
            .putBoolean("notify_homework", true)
            .putBoolean("notify_exams", true)
            .commit()

        TanweerNotificationManager.showIndividualHomeworkNotification(
            context, homeworkId, "Homework", "Math", "HOMEWORK", homeworkGeneration
        )
        TanweerNotificationManager.showIndividualExamNotification(
            context, examId, "Exam", "Science", examGeneration
        )

        assertFalse(activeNotificationIds().contains(requestCode(homeworkId)))
        assertFalse(activeNotificationIds().contains(requestCode(examId)))
    }

    private fun postNotification(id: Int) {
        TanweerNotificationManager.initChannel(context)
        val notification: Notification = NotificationCompat.Builder(
            context, TanweerNotificationManager.CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Test notification")
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun activeNotificationIds(): Set<Int> {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.map { it.id }.toSet()
    }

    private fun requestCode(id: String): Int = id.hashCode() and 0x7FFFFFFF

    private fun createScheduledReminder(id: String, action: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(id),
            reminderIntent(id, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000L, pendingIntent)
    }

    private fun findPendingIntent(id: String, action: String): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode(id),
            reminderIntent(id, action),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

    private fun reminderIntent(id: String, action: String): Intent =
        Intent(context, TanweerAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(TanweerAlarmReceiver.EXTRA_HOMEWORK_ID, id)
            putExtra(TanweerAlarmReceiver.EXTRA_EXAM_ID, id)
        }

    private fun preferences() =
        context.getSharedPreferences("tanweer_prefs", Context.MODE_PRIVATE)
}
