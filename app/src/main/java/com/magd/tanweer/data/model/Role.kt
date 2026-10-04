package com.magd.tanweer.data.model

import com.squareup.moshi.JsonClass

/**
 * Tanweer Platform Unified Role Management Core
 * Source of truth for all user ranks and roles across the platform.
 */
enum class Role(
    val level: Int,
    val displayNameAr: String,
    val badgeIcon: String,
    val descriptionAr: String
) {
    STUDENT(
        level = 1,
        displayNameAr = "طالب",
        badgeIcon = "👤",
        descriptionAr = "عضو مساهم في الشعبة، يستطيع رفع الدروس وطرح الأسئلة وإضافة الواجبات والاختبارات والمشاركة في بناء الجدول."
    ),
    MODERATOR(
        level = 2,
        displayNameAr = "مشرف / مسؤول",
        badgeIcon = "🛡️",
        descriptionAr = "مسؤول الشعبة، يملك صلاحيات مراجعة المحتوى والجدول والواجبات وإدارة الأعضاء."
    ),
    TEACHER(
        level = 3,
        displayNameAr = "أستاذ",
        badgeIcon = "🎓",
        descriptionAr = "معلم معتمد، يملك صلاحية اعتماد وتثبيت المحتوى والجدول وتوجيه الشعبة."
    ),
    ADMIN(
        level = 4,
        displayNameAr = "مدير",
        badgeIcon = "👑",
        descriptionAr = "مدير المنظومة المدرسية، يملك كامل الصلاحيات الإدارية وتعيين المشرفين."
    ),
    SYSTEM_OWNER(
        level = 5,
        displayNameAr = "مالك المنظومة",
        badgeIcon = "🔐",
        descriptionAr = "حساب مالك الخادم وغرفة التحكم المركزية."
    );

    companion object {
        fun fromString(roleStr: String?): Role {
            if (roleStr.isNullOrBlank()) return STUDENT
            return when (roleStr.trim().uppercase()) {
                "SYSTEM_OWNER", "OWNER" -> SYSTEM_OWNER
                "ADMIN", "ADMINISTRATOR" -> ADMIN
                "TEACHER", "VERIFIED_TEACHER" -> TEACHER
                "MODERATOR" -> MODERATOR
                "STUDENT", "MEMBER" -> STUDENT
                else -> STUDENT
            }
        }
    }
}

enum class RoleScope {
    GLOBAL,
    GROUP,
    SUBJECT
}

@JsonClass(generateAdapter = true)
data class RoleRequestItem(
    val id: String,
    val requestedRole: String,
    val groupId: String? = null,
    val reason: String? = null,
    val status: String = "PENDING",
    val reviewNotes: String? = null,
    val reviewedAt: Long? = null,
    val createdAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class SubmitRoleUpgradeRequest(
    val requestedRole: String,
    val reason: String,
    val groupId: String? = null
)

@JsonClass(generateAdapter = true)
data class SubmitRoleUpgradeResponse(
    val success: Boolean,
    val requestId: String? = null,
    val status: String? = null,
    val targetRole: String? = null,
    val targetRoleNameAr: String? = null,
    val whatsappNumber: String? = null,
    val whatsappUrl: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class RedeemRoleCodeRequest(
    val code: String,
    val requestId: String? = null
)

@JsonClass(generateAdapter = true)
data class RedeemRoleCodeResponse(
    val success: Boolean,
    val message: String? = null,
    val newRole: String? = null,
    val newRoleNameAr: String? = null
)

@JsonClass(generateAdapter = true)
data class MyRoleRequestsResponse(
    val success: Boolean,
    val whatsappNumber: String? = null,
    val requests: List<RoleRequestItem> = emptyList(),
    val message: String? = null
)
