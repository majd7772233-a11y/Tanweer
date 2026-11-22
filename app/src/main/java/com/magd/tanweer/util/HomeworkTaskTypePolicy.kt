package com.magd.tanweer.util

import java.util.Locale

/**
 * A single classification boundary for records stored in the homework table.
 * Null/blank types keep the legacy HOMEWORK meaning. Unknown future values
 * remain visible but must not silently inflate homework or project totals.
 */
enum class HomeworkTaskKind { HOMEWORK, TASK, UNKNOWN }

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
}
