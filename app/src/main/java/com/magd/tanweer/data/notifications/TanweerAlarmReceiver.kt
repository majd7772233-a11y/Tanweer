package com.magd.tanweer.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TanweerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            TanweerNotificationManager.scheduleDailyReminder(context)
        } else {
            TanweerNotificationManager.checkAndSendReminders(context)
        }
    }

    companion object {
        const val ACTION_CHECK_REMINDERS = "com.magd.tanweer.ACTION_CHECK_REMINDERS"
    }
}
