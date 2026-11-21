package com.magd.tanweer.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeChatManagerPolicyTest {

    @Test
    fun initialConnectionDoesNotRequireAnExtraHistoryReconciliation() {
        assertFalse(
            shouldReconcileChatHistoryAfterOpen(
                hasConnectedInCurrentSession = false,
                hasScheduledRetry = false
            )
        )
    }

    @Test
    fun reconnectAfterAnEstablishedSocketReconcilesHistory() {
        assertTrue(
            shouldReconcileChatHistoryAfterOpen(
                hasConnectedInCurrentSession = true,
                hasScheduledRetry = false
            )
        )
    }

    @Test
    fun firstSuccessfulSocketAfterAnInitialFailureReconcilesHistory() {
        assertTrue(
            shouldReconcileChatHistoryAfterOpen(
                hasConnectedInCurrentSession = false,
                hasScheduledRetry = true
            )
        )
    }
}
