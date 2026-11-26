package com.magd.tanweer.ui.screens

import com.magd.tanweer.data.model.SchoolEventItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventOwnershipPolicyTest {
    private fun event(
        groupId: String = "class_10_A",
        category: String = "ACTIVITY",
        createdBy: String = "student_1",
        syncStatus: String = "SYNCED"
    ) = SchoolEventItem(
        id = "event_1",
        groupId = groupId,
        eventDate = "2026-11-23",
        title = "فعالية تجريبية",
        category = category,
        createdBy = createdBy,
        syncStatus = syncStatus
    )

    @Test
    fun syncedGroupEventActionsAreVisibleOnlyToItsCreator() {
        val owned = event()
        assertTrue(EventOwnershipPolicy.canEdit(owned, "student_1", "STUDENT"))
        assertTrue(EventOwnershipPolicy.canDelete(owned, "student_1", "STUDENT"))
        assertFalse(EventOwnershipPolicy.canEdit(owned, "student_2", "STUDENT"))
        assertFalse(EventOwnershipPolicy.canDelete(owned, "student_2", "STUDENT"))
    }

    @Test
    fun legacyOrUnconfirmedEventsNeverExposeManagementActions() {
        val legacy = event(createdBy = "")
        val pending = event(syncStatus = "PENDING")
        val failed = event(syncStatus = "FAILED")

        listOf(legacy, pending, failed).forEach { candidate ->
            assertFalse(EventOwnershipPolicy.canEdit(candidate, "student_1", "STUDENT"))
            assertFalse(EventOwnershipPolicy.canDelete(candidate, "student_1", "STUDENT"))
        }
    }

    @Test
    fun schoolWideAnnouncementsRequireSchoolAdminEvenForTheirCreator() {
        val announcement = event(groupId = "all", category = "ANNOUNCEMENT")
        assertFalse(EventOwnershipPolicy.canEdit(announcement, "student_1", "TEACHER"))
        assertFalse(EventOwnershipPolicy.canDelete(announcement, "student_1", "TEACHER"))
        assertTrue(EventOwnershipPolicy.canEdit(announcement, "student_1", "ADMIN"))
        assertTrue(EventOwnershipPolicy.canDelete(announcement, "student_1", "SYSTEM_OWNER"))
    }

    @Test
    fun holidayEditsRequireAnOfficialPublicationRole() {
        val holiday = event(category = "HOLIDAY")
        assertFalse(EventOwnershipPolicy.canEdit(holiday, "student_1", "STUDENT"))
        assertTrue(EventOwnershipPolicy.canEdit(holiday, "student_1", "VERIFIED_TEACHER"))
        assertTrue(EventOwnershipPolicy.canDelete(holiday, "student_1", "STUDENT"))
    }

    @Test
    fun blankCurrentUserNeverMatchesAnEventOwner() {
        val owned = event()
        assertFalse(EventOwnershipPolicy.canEdit(owned, "", "ADMIN"))
        assertFalse(EventOwnershipPolicy.canDelete(owned, "", "ADMIN"))
    }
}