package com.magd.tanweer.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatAutoScrollPolicyTest {
    @Test
    fun deltaCountsOnlyMessagesNewToTheCurrentSnapshot() {
        val delta = GroupChatAutoScrollPolicy.calculateDelta(
            previousIds = listOf("m1", "m2"),
            currentIds = listOf("m1", "m2", "m3", "m4")
        )

        assertEquals(setOf("m3", "m4"), delta.addedIds)
        assertTrue(delta.removedIds.isEmpty())
    }

    @Test
    fun deltaSeparatesDeletedMessagesFromNewMessages() {
        val delta = GroupChatAutoScrollPolicy.calculateDelta(
            previousIds = listOf("m1", "m2", "m3"),
            currentIds = listOf("m1", "m3", "m4")
        )

        assertEquals(setOf("m4"), delta.addedIds)
        assertEquals(setOf("m2"), delta.removedIds)
    }

    @Test
    fun userAtPreviousLatestItemWithinThresholdAutoFollows() {
        assertTrue(
            GroupChatAutoScrollPolicy.shouldAutoFollow(
                previousLastMessageIndex = 9,
                lastVisibleItemIndex = 9,
                lastVisibleItemEnd = 920,
                viewportEndOffset = 980,
                thresholdPx = 96
            )
        )
    }

    @Test
    fun missingVisibleAnchorDoesNotAssumeAnExistingConversationIsAtBottom() {
        assertFalse(
            GroupChatAutoScrollPolicy.shouldAutoFollow(
                previousLastMessageIndex = 12,
                lastVisibleItemIndex = null,
                lastVisibleItemEnd = null,
                viewportEndOffset = 0,
                thresholdPx = 96
            )
        )
    }

    @Test
    fun userReadingOlderMessagesIsNotPulledToLatest() {
        assertFalse(
            GroupChatAutoScrollPolicy.shouldAutoFollow(
                previousLastMessageIndex = 30,
                lastVisibleItemIndex = 18,
                lastVisibleItemEnd = 950,
                viewportEndOffset = 980,
                thresholdPx = 96
            )
        )
    }

    @Test
    fun visiblePreviousLatestItemFarAboveViewportIsNotNearBottom() {
        assertFalse(
            GroupChatAutoScrollPolicy.shouldAutoFollow(
                previousLastMessageIndex = 30,
                lastVisibleItemIndex = 30,
                lastVisibleItemEnd = 600,
                viewportEndOffset = 980,
                thresholdPx = 96
            )
        )
    }

    @Test
    fun viewportResizeForImeStillTreatsPreviousLatestItemAsBottomWhenItExtendsPastEdge() {
        assertTrue(
            GroupChatAutoScrollPolicy.shouldAutoFollow(
                previousLastMessageIndex = 30,
                lastVisibleItemIndex = 30,
                lastVisibleItemEnd = 720,
                viewportEndOffset = 650,
                thresholdPx = 96
            )
        )
    }

    @Test
    fun unreadIdsAccumulateWhenUserIsReadingOlderMessages() {
        val delta = GroupChatAutoScrollPolicy.calculateDelta(
            previousIds = listOf("m1"),
            currentIds = listOf("m1", "m2", "m3")
        )

        assertEquals(
            setOf("m2", "m3"),
            GroupChatAutoScrollPolicy.mergeUnreadMessageIds(
                previousUnreadIds = setOf("m1"),
                delta = delta,
                currentMessageIds = setOf("m1", "m2", "m3"),
                shouldAutoFollow = false
            )
        )
    }

    @Test
    fun deletingUnreadMessagesRemovesThemFromUnreadCount() {
        val delta = GroupChatAutoScrollPolicy.calculateDelta(
            previousIds = listOf("m1", "m2", "m3"),
            currentIds = listOf("m1", "m3")
        )

        assertEquals(
            emptySet<String>(),
            GroupChatAutoScrollPolicy.mergeUnreadMessageIds(
                previousUnreadIds = setOf("m2"),
                delta = delta,
                currentMessageIds = setOf("m1", "m3"),
                shouldAutoFollow = false
            )
        )
    }

    @Test
    fun followingLatestClearsExistingUnreadIds() {
        val delta = GroupChatAutoScrollPolicy.calculateDelta(
            previousIds = listOf("m1", "m2"),
            currentIds = listOf("m1", "m2", "m3")
        )

        assertEquals(
            emptySet<String>(),
            GroupChatAutoScrollPolicy.mergeUnreadMessageIds(
                previousUnreadIds = setOf("m0", "m2"),
                delta = delta,
                currentMessageIds = setOf("m0", "m1", "m2", "m3"),
                shouldAutoFollow = true
            )
        )
    }
}
