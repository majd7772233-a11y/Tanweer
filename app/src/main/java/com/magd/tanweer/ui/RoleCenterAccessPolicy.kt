package com.magd.tanweer.ui

import com.magd.tanweer.data.model.Role

internal enum class RoleCenterAccess {
    HIDDEN,
    AVAILABLE,
    LOCKED
}

/**
 * Role-center visibility is only a presentation decision. Dashboard screens keep
 * their own permission checks; a locked student state must never route to a dashboard.
 */
internal fun roleCenterAccess(role: Role, selectedCategoryFilter: String): RoleCenterAccess {
    if (selectedCategoryFilter != "ALL" && selectedCategoryFilter != "ROLES") {
        return RoleCenterAccess.HIDDEN
    }
    if (selectedCategoryFilter == "ROLES" && role == Role.STUDENT) {
        return RoleCenterAccess.LOCKED
    }
    return when (role) {
        Role.STUDENT -> RoleCenterAccess.HIDDEN
        Role.MODERATOR, Role.TEACHER, Role.ADMIN -> RoleCenterAccess.AVAILABLE
    }
}
