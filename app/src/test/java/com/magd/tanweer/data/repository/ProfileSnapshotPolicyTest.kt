package com.magd.tanweer.data.repository

import com.magd.tanweer.data.model.UserStats
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSnapshotPolicyTest {
    @Test
    fun profile_snapshot_must_match_the_request_and_current_account_session() {
        assertTrue(
            ProfileSnapshotPolicy.belongsToCurrentSession(
                requestedUserId = "user-a",
                requestedToken = "token-a",
                responseUserId = "user-a",
                currentUserId = "user-a",
                currentToken = "token-a",
            )
        )
        assertFalse(
            ProfileSnapshotPolicy.belongsToCurrentSession(
                requestedUserId = "user-a",
                requestedToken = "token-a",
                responseUserId = "user-b",
                currentUserId = "user-b",
                currentToken = "token-b",
            )
        )
        assertFalse(
            ProfileSnapshotPolicy.belongsToCurrentSession(
                requestedUserId = "user-a",
                requestedToken = "token-a",
                responseUserId = "user-a",
                currentUserId = "user-a",
                currentToken = "token-b",
            )
        )
        assertFalse(
            ProfileSnapshotPolicy.belongsToCurrentSession(
                requestedUserId = "",
                requestedToken = "token-a",
                responseUserId = "",
                currentUserId = "",
                currentToken = "token-a",
            )
        )
    }

    @Test
    fun profile_stats_must_exist_and_contain_only_non_negative_counts() {
        assertTrue(ProfileSnapshotPolicy.hasValidStats(UserStats(0, 0, 0, 0)))
        assertTrue(ProfileSnapshotPolicy.hasValidStats(UserStats(1, 2, 3, 4)))
        assertFalse(ProfileSnapshotPolicy.hasValidStats(null))
        assertFalse(ProfileSnapshotPolicy.hasValidStats(UserStats(-1, 0, 0, 0)))
        assertFalse(ProfileSnapshotPolicy.hasValidStats(UserStats(0, -1, 0, 0)))
        assertFalse(ProfileSnapshotPolicy.hasValidStats(UserStats(0, 0, -1, 0)))
        assertFalse(ProfileSnapshotPolicy.hasValidStats(UserStats(0, 0, 0, -1)))
    }
}
