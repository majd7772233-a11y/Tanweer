package com.magd.tanweer.data.model

/**
 * Tanweer Platform Permissions Core
 * Centralized authorization engine evaluating permissions based on philosophy & roles.
 */
enum class Permission {
    // Universal Student & Member Contributions (Everyone Can Contribute)
    CONTRIBUTE_CONTENT,    // Create lessons, summaries, board notes
    PROPOSE_SCHEDULE,      // Propose schedule changes
    CREATE_HOMEWORK,        // Add homework or task
    CREATE_EXAM,            // Add exam dates & curriculum
    CREATE_EVENT,           // Add school activity or event
    ASK_QUESTION,           // Ask a study question
    ANSWER_QUESTION,        // Add answer or discussion comment
    SEND_CHAT_MESSAGE,      // Participate in class chat
    UPLOAD_MEDIA,           // Upload board pictures or documents
    REQUEST_ROLE_UPGRADE,   // Request teacher/moderator status

    // Official Governance & Moderation (Moderator, Teacher, Admin, Owner)
    MANAGE_SCHEDULE,        // Directly create, modify, or delete official schedule slots
    MODERATE_CONTENT,       // Edit, verify, or review content created by others
    DELETE_ANY_CONTENT,     // Force delete content across group/platform
    PIN_CONTENT,            // Pin key lessons or announcements
    MODERATE_HOMEWORK,      // Edit or remove homework of others
    MODERATE_EXAM,          // Edit or remove exams of others
    MODERATE_EVENT,         // Edit or remove events of others
    MODERATE_ISSUES,        // Close, reopen, or moderate questions
    VERIFY_BEST_ANSWER,     // Mark an official certified answer
    MODERATE_CHAT,          // Delete chat messages or silence members

    // Group Membership Administration
    MANAGE_GROUP_MEMBERS,   // Change roles in class group, moderate members
    MANAGE_GROUP_SETTINGS,  // Change group description, title

    // Administrative & System Governance
    REVIEW_ROLE_REQUESTS,   // Approve or reject teacher/moderator requests
    MANAGE_USERS,           // Change global user roles, suspend/enable
    REVOKE_SESSIONS,        // Force terminate sessions
    VIEW_AUDIT_LOG,         // Inspect administrative action logs
    ACCESS_OWNER_DASHBOARD  // Access Server Control Center
}

/**
 * Client-Side Permission Evaluator on Android.
 * IMPORTANT ARCHITECTURE RULE:
 * This evaluator is used EXCLUSIVELY for UI rendering hints (e.g. enabling buttons,
 * optimistic screen states, hiding edit icons).
 * The Tanweer Cloudflare Server is the SOLE FINAL AUTHORITY for all authorization
 * and strictly verifies every API endpoint independently.
 */
object PermissionEvaluator {
    fun hasPermission(
        user: User?,
        permission: Permission,
        isResourceOwner: Boolean = false,
        groupRole: String? = null
    ): Boolean {
        if (user == null) return false
        val globalRole = Role.fromString(user.role)

        // 1. SYSTEM_OWNER has master bypass
        if (globalRole == Role.SYSTEM_OWNER) return true

        // 2. Owner-only dashboard restriction
        if (permission == Permission.ACCESS_OWNER_DASHBOARD) return false

        // 3. ADMIN has global administrative capabilities
        if (globalRole == Role.ADMIN) return true

        // 4. Universal Student & Member Contributions:
        // Everyone (including STUDENT) can contribute to lessons, news, homework, exams, questions, and chat!
        val universal = setOf(
            Permission.CONTRIBUTE_CONTENT,
            Permission.PROPOSE_SCHEDULE,
            Permission.CREATE_HOMEWORK,
            Permission.CREATE_EXAM,
            Permission.CREATE_EVENT,
            Permission.ASK_QUESTION,
            Permission.ANSWER_QUESTION,
            Permission.SEND_CHAT_MESSAGE,
            Permission.UPLOAD_MEDIA,
            Permission.REQUEST_ROLE_UPGRADE
        )
        if (permission in universal) return true

        // 5. Self-authorship check
        if (isResourceOwner) {
            if (permission == Permission.VERIFY_BEST_ANSWER || permission == Permission.MODERATE_CONTENT) {
                return true
            }
        }

        // 6. Effective group-level role
        val effectiveRole = if (!groupRole.isNullOrBlank()) {
            val parsedGroupRole = Role.fromString(groupRole)
            if (parsedGroupRole.level > globalRole.level) parsedGroupRole else globalRole
        } else {
            globalRole
        }

        // 7. Role-specific privilege evaluation (Teacher Level 3 vs Moderator Level 2)
        return when (effectiveRole) {
            Role.TEACHER -> permission in setOf(
                Permission.MANAGE_SCHEDULE,
                Permission.MODERATE_CONTENT,
                Permission.PIN_CONTENT,
                Permission.MODERATE_HOMEWORK,
                Permission.MODERATE_EXAM,
                Permission.MODERATE_EVENT,
                Permission.MODERATE_ISSUES,
                Permission.VERIFY_BEST_ANSWER,
                Permission.MODERATE_CHAT,
                Permission.MANAGE_GROUP_MEMBERS
            )
            Role.MODERATOR -> permission in setOf(
                Permission.MODERATE_CHAT,
                Permission.MANAGE_GROUP_MEMBERS,
                Permission.MODERATE_CONTENT,
                Permission.MODERATE_ISSUES,
                Permission.MANAGE_SCHEDULE
            )
            Role.STUDENT -> false
            Role.ADMIN, Role.SYSTEM_OWNER -> true
        }
    }
}

fun User.hasPermission(
    permission: Permission,
    isResourceOwner: Boolean = false,
    groupRole: String? = null
): Boolean = PermissionEvaluator.hasPermission(this, permission, isResourceOwner, groupRole)

fun User.getRoleEnum(): Role = Role.fromString(role)
