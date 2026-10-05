package com.magd.tanweer.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TanweerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                TanweerNotificationManager.scheduleDailyReminder(context)
            }
            ACTION_HOMEWORK_REMINDER -> {
                val homeworkId = intent.getStringExtra(EXTRA_HOMEWORK_ID) ?: "hw"
                val title = intent.getStringExtra(EXTRA_HOMEWORK_TITLE) ?: "واجب مدرسي"
                val subject = intent.getStringExtra(EXTRA_SUBJECT_NAME) ?: "المادة"
                TanweerNotificationManager.showIndividualHomeworkNotification(context, homeworkId, title, subject)
            }
            else -> {
                TanweerNotificationManager.checkAndSendReminders(context)
            }
        }
    }

    companion object {
        const val ACTION_CHECK_REMINDERS = "com.magd.tanweer.ACTION_CHECK_REMINDERS"
        const val ACTION_HOMEWORK_REMINDER = "com.magd.tanweer.ACTION_HOMEWORK_REMINDER"
        const val EXTRA_HOMEWORK_ID = "extra_homework_id"
        const val EXTRA_HOMEWORK_TITLE = "extra_homework_title"
        const val EXTRA_SUBJECT_NAME = "extra_subject_name"
    }
}
