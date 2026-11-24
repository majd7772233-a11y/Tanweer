package com.magd.tanweer.ui.screens

/**
 * Pure list-delta and scroll policy for the group chat.
 * The previous last message is used because LazyListState may already report the
 * new item count by the time the Room Flow emission reaches the effect.
 */
internal data class GroupChatMessageDelta(
    val addedIds: Set<String>,
    val removedIds: Set<String>
)

internal object GroupChatAutoScrollPolicy {
    fun calculateDelta(
        previousIds: List<String>,
        currentIds: List<String>
    ): GroupChatMessageDelta {
        val previous = previousIds.toSet()
        val current = currentIds.toSet()
        return GroupChatMessageDelta(
            addedIds = currentIds.filterTo(linkedSetOf()) { it !in previous },
            removedIds = previous.filterTo(linkedSetOf()) { it !in current }
        )
    }

    fun shouldAutoFollow(
        previousLastMessageIndex: Int,
        lastVisibleItemIndex: Int?,
        lastVisibleItemEnd: Int?,
        viewportEndOffset: Int,
        thresholdPx: Int
    ): Boolean {
        if (previousLastMessageIndex < 0 || lastVisibleItemIndex == null) return true
        if (lastVisibleItemIndex < previousLastMessageIndex) return false
        val itemEnd = lastVisibleItemEnd ?: return false
        return viewportEndOffset - itemEnd <= thresholdPx.coerceAtLeast(0)
    }

    fun mergeUnreadMessageIds(
        previousUnreadIds: Set<String>,
        delta: GroupChatMessageDelta,
        currentMessageIds: Set<String>,
        shouldAutoFollow: Boolean
    ): Set<String> {
        if (shouldAutoFollow) return emptySet()
        val survivingUnread = previousUnreadIds.intersect(currentMessageIds)
        return survivingUnread + delta.addedIds
    }
}
