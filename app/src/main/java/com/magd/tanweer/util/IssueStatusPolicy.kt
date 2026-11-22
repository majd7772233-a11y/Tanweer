package com.magd.tanweer.util

import androidx.annotation.StringRes
import com.magd.tanweer.R

enum class IssueFilter(val labelRes: Int) {
    ALL(R.string.issues_filter_all),
    DISCUSSING(R.string.issues_filter_discussing),
    SOLVED(R.string.issues_filter_solved),
    CLOSED(R.string.issues_filter_closed)
}

enum class IssueStatus(
    val labelRes: Int,
    val isResolved: Boolean = false
) {
    OPEN(R.string.issue_status_open),
    IN_DISCUSSION(R.string.issue_status_in_discussion),
    SOLVED(R.string.issue_status_solved, isResolved = true),
    VERIFIED(R.string.issue_status_verified, isResolved = true),
    CLOSED(R.string.issue_status_closed),
    UNKNOWN(R.string.issue_status_unknown)
}

object IssueStatusPolicy {
    fun fromApiValue(value: String?): IssueStatus =
        when (value?.trim()?.uppercase()) {
            "OPEN" -> IssueStatus.OPEN
            "IN_DISCUSSION" -> IssueStatus.IN_DISCUSSION
            "SOLVED" -> IssueStatus.SOLVED
            "VERIFIED" -> IssueStatus.VERIFIED
            "CLOSED" -> IssueStatus.CLOSED
            else -> IssueStatus.UNKNOWN
        }

    fun matches(filter: IssueFilter, rawStatus: String?): Boolean {
        val status = fromApiValue(rawStatus)
        return when (filter) {
            IssueFilter.ALL -> true
            // OPEN remains in this list until the server receives its first comment.
            IssueFilter.DISCUSSING -> status == IssueStatus.OPEN || status == IssueStatus.IN_DISCUSSION
            IssueFilter.SOLVED -> status == IssueStatus.SOLVED || status == IssueStatus.VERIFIED
            IssueFilter.CLOSED -> status == IssueStatus.CLOSED
        }
    }
}
