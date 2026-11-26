package com.magd.tanweer.ui

import com.magd.tanweer.data.model.Role
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleCenterAccessPolicyTest {
    @Test
    fun studentSeesAnExplicitLockedStateOnlyWhenOpeningTheRolesCategory() {
        assertEquals(RoleCenterAccess.LOCKED, roleCenterAccess(Role.STUDENT, "ROLES"))
        assertEquals(RoleCenterAccess.HIDDEN, roleCenterAccess(Role.STUDENT, "ALL"))
    }

    @Test
    fun nonRoleCategoriesNeverExposeRoleCentersOrTheLockedCard() {
        listOf(Role.STUDENT, Role.MODERATOR, Role.TEACHER, Role.ADMIN).forEach { role ->
            assertEquals(RoleCenterAccess.HIDDEN, roleCenterAccess(role, "ACADEMIC"))
            assertEquals(RoleCenterAccess.HIDDEN, roleCenterAccess(role, "COMMUNITY"))
            assertEquals(RoleCenterAccess.HIDDEN, roleCenterAccess(role, "SETTINGS"))
        }
    }

    @Test
    fun approvedStaffRolesKeepTheirExistingCenterInAllAndRolesFilters() {
        listOf(Role.MODERATOR, Role.TEACHER, Role.ADMIN).forEach { role ->
            assertEquals(RoleCenterAccess.AVAILABLE, roleCenterAccess(role, "ALL"))
            assertEquals(RoleCenterAccess.AVAILABLE, roleCenterAccess(role, "ROLES"))
        }
    }

    @Test
    fun unknownCategoryFailsClosed() {
        assertEquals(RoleCenterAccess.HIDDEN, roleCenterAccess(Role.ADMIN, "UNKNOWN"))
    }
}
