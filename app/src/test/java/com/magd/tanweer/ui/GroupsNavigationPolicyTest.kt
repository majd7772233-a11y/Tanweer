package com.magd.tanweer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupsNavigationPolicyTest {
    @Test
    fun returnsToModeratorDashboardAndPreservesTheRootTab() {
        val origin = GroupsReturnDestination(
            subScreen = SubScreen.MODERATOR_DASHBOARD,
            currentTab = NavigationTab.MORE
        )

        assertEquals(origin, GroupsNavigationPolicy.resolve(origin, NavigationTab.TODAY))
    }

    @Test
    fun directGroupsEntryKeepsTheExistingRootTabBackDestination() {
        val fallback = GroupsNavigationPolicy.resolve(
            origin = null,
            fallbackTab = NavigationTab.CALENDAR
        )

        assertEquals(SubScreen.NONE, fallback.subScreen)
        assertEquals(NavigationTab.CALENDAR, fallback.currentTab)
    }

    @Test
    fun invalidGroupsOriginCannotCreateANavigationLoop() {
        val invalidOrigins = listOf(SubScreen.GROUPS, SubScreen.PDF_VIEWER, SubScreen.LIBRARY)

        invalidOrigins.forEach { screen ->
            val resolved = GroupsNavigationPolicy.resolve(
                origin = GroupsReturnDestination(screen, NavigationTab.EXAMS),
                fallbackTab = NavigationTab.HOMEWORK
            )

            assertEquals(SubScreen.NONE, resolved.subScreen)
            assertEquals(NavigationTab.HOMEWORK, resolved.currentTab)
        }
    }
}
