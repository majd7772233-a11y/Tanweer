package com.magd.tanweer.data.repository

import com.magd.tanweer.data.model.UserStats

/**
 * A profile response may replace the local account snapshot only when it still belongs
 * to the exact account/session that initiated the request.
 */
internal object ProfileSnapshotPolicy {
    fun belongsToCurrentSession(
        requestedUserId: String,
        requestedToken: String,
        responseUserId: String,
        currentUserId: String,
        currentToken: String,
    ): Boolean = requestedUserId.isNotBlank() &&
        requestedToken.isNotBlank() &&
        responseUserId == requestedUserId &&
        currentUserId == requestedUserId &&
        currentToken == requestedToken

    fun hasValidStats(stats: UserStats?): Boolean = stats != null &&
        stats.lessonsCount >= 0 &&
        stats.homeworksCount >= 0 &&
        stats.issuesCount >= 0 &&
        stats.photosCount >= 0
}
