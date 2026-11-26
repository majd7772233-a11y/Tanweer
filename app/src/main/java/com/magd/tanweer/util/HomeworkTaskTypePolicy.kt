package com.magd.tanweer.util

import java.util.Locale

/**
 * A single classification boundary for records stored in the homework table.
 * Null/blank types keep the legacy HOMEWORK meaning. Unknown future values
 * remain visible but must not silently inflate homework or project totals.
 */
enum class HomeworkTaskKind { HOMEWORK, TASK, UNKNOWN }

data class HomeworkTaskTypeSummary(
    val homeworkCount: Int,
    val projectCount: Int,
    val otherAssignmentCount: Int
)

object HomeworkTaskTypePolicy {
    fun classify(taskType: String?): HomeworkTaskKind {
        return when (taskType?.trim()?.uppercase(Locale.ROOT)) {
            null, "", "HOMEWORK" -> HomeworkTaskKind.HOMEWORK
            "TASK" -> HomeworkTaskKind.TASK
            else -> HomeworkTaskKind.UNKNOWN
        }
    }

    fun isHomework(taskType: String?): Boolean =
        classify(taskType) == HomeworkTaskKind.HOMEWORK

    fun isTask(taskType: String?): Boolean =
        classify(taskType) == HomeworkTaskKind.TASK

    fun summarize(taskTypes: Iterable<String?>): HomeworkTaskTypeSummary {
        var homeworkCount = 0
        var projectCount = 0
        var otherAssignmentCount = 0
        taskTypes.forEach { taskType ->
            when (classify(taskType)) {
                HomeworkTaskKind.HOMEWORK -> homeworkCount++
                HomeworkTaskKind.TASK -> projectCount++
                HomeworkTaskKind.UNKNOWN -> otherAssignmentCount++
            }
        }
        return HomeworkTaskTypeSummary(homeworkCount, projectCount, otherAssignmentCount)
    }
}
