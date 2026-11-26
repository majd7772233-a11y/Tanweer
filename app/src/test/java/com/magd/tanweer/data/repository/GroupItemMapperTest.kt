package com.magd.tanweer.data.repository

import com.magd.tanweer.data.local.GroupEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupItemMapperTest {

    @Test
    fun preservesPrivateVisibilityAndChatPolicyFromRoomForDiscoverCards() {
        val group = GroupEntity(
            id = "private-club",
            name = "نادي البرمجة",
            type = "OPTIONAL",
            description = null,
            icon = "💻",
            role = "MEMBER",
            memberCount = 4,
            isDiscoverable = true,
            isOfficial = false,
            visibility = "PRIVATE",
            chatPolicy = "STAFF_ONLY"
        )

        val item = group.toGroupItem()

        assertEquals("PRIVATE", item.visibility)
        assertEquals("STAFF_ONLY", item.chatPolicy)
    }

    @Test
    fun keepsPublicMetadataOnPublicGroups() {
        val group = GroupEntity(
            id = "public-club",
            name = "نادي الرياضيات",
            type = "OPTIONAL",
            description = null,
            icon = "📐",
            role = "MEMBER",
            memberCount = 8,
            isDiscoverable = true,
            isOfficial = false,
            visibility = "PUBLIC",
            chatPolicy = "MEMBERS"
        )

        val item = group.toGroupItem()

        assertEquals("PUBLIC", item.visibility)
        assertEquals("MEMBERS", item.chatPolicy)
    }
}
