package com.magd.tanweer.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.magd.tanweer.data.local.TanweerDatabase
import com.magd.tanweer.util.AcademicReminderPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TanweerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> TanweerNotificationManager.scheduleDailyReminder(context)
            ACTION_HOMEWORK_REMINDER -> {
                val homeworkId = intent.getStringExtra(EXTRA_HOMEWORK_ID) ?: return
                dispatchHomeworkReminder(context.applicationContext, homeworkId)
            }
            ACTION_EXAM_REMINDER -> {
                val examId = intent.getStringExtra(EXTRA_EXAM_ID) ?: return
                dispatchExamReminder(context.applicationContext, examId)
            }
            else -> TanweerNotificationManager.checkAndSendReminders(context)
        }
    }

    private fun dispatchHomeworkReminder(context: Context, homeworkId: String) {
        val expectedGeneration = TanweerNotificationManager.homeworkNotificationGenerationSnapshot()
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = TanweerDatabase.getInstance(context)
                val user = db.userDao().getUserSync()
                if (user == null) {
                    TanweerNotificationManager.cancelReminder(context, homeworkId, ACTION_HOMEWORK_REMINDER)
                    return@launch
                }
                val homework = db.homeworkDao().getHomeworkById(homeworkId)
                val exists = homework != null && homework.syncStatus != "DELETE_PENDING"
                val isMember = homework != null &&
                    db.groupDao().getMyGroupsSync().any { it.id == homework.groupId }
                val completed = db.homeworkDao().getCompletionsForUser(user.id)
                    .first().any { it.homeworkId == homeworkId }
                val enabled = context.getSharedPreferences("tanweer_prefs", Context.MODE_PRIVATE)
                    .getBoolean("notify_homework", true)
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
                val shouldNotify = AcademicReminderPolicy.shouldShowHomework(
                    enabled = enabled,
                    exists = exists,
                    completed = completed,
                    memberOfGroup = isMember,
                    dueDate = homework?.dueDate.orEmpty(),
                    today = today
                )
                if (shouldNotify && homework != null) {
                    TanweerNotificationManager.showIndividualHomeworkNotification(
                        context,
                        homework.id,
                        homework.title,
                        homework.subjectName,
                        homework.taskType,
                        expectedGeneration
                    )
                } else {
                    TanweerNotificationManager.cancelReminder(context, homeworkId, ACTION_HOMEWORK_REMINDER)
                }
            } catch (error: Exception) {
                Log.w("TanweerAlarmReceiver", "Could not verify homework reminder " + homeworkId, error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun dispatchExamReminder(context: Context, examId: String) {
        val expectedGeneration = TanweerNotificationManager.examNotificationGenerationSnapshot()
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = TanweerDatabase.getInstance(context)
                val user = db.userDao().getUserSync()
                if (user == null) {
                    TanweerNotificationManager.cancelReminder(context, examId, ACTION_EXAM_REMINDER)
                    return@launch
                }
                val exam = db.examDao().getExamById(examId)
                val exists = exam != null && exam.syncStatus != "DELETE_PENDING"
                val isMember = exam != null &&
                    db.groupDao().getMyGroupsSync().any { it.id == exam.groupId }
                val enabled = context.getSharedPreferences("tanweer_prefs", Context.MODE_PRIVATE)
                    .getBoolean("notify_exams", true)
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
                val shouldNotify = AcademicReminderPolicy.shouldShowExam(
                    enabled = enabled,
                    exists = exists,
                    completed = exam?.isCompleted ?: false,
                    memberOfGroup = isMember,
                    examDate = exam?.examDate.orEmpty(),
                    today = today
                )
                if (shouldNotify && exam != null) {
                    TanweerNotificationManager.showIndividualExamNotification(
                        context, exam.id, exam.title, exam.subjectName, expectedGeneration
                    )
                } else {
                    TanweerNotificationManager.cancelReminder(context, examId, ACTION_EXAM_REMINDER)
                }
            } catch (error: Exception) {
                Log.w("TanweerAlarmReceiver", "Could not verify exam reminder " + examId, error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK_REMINDERS = "com.magd.tanweer.ACTION_CHECK_REMINDERS"
        const val ACTION_HOMEWORK_REMINDER = "com.magd.tanweer.ACTION_HOMEWORK_REMINDER"
        const val ACTION_EXAM_REMINDER = "com.magd.tanweer.ACTION_EXAM_REMINDER"
        const val EXTRA_HOMEWORK_ID = "extra_homework_id"
        const val EXTRA_HOMEWORK_TITLE = "extra_homework_title"
        const val EXTRA_EXAM_ID = "extra_exam_id"
        const val EXTRA_EXAM_TITLE = "extra_exam_title"
        const val EXTRA_SUBJECT_NAME = "extra_subject_name"
    }
}
