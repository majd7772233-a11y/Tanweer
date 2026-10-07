package com.magd.tanweer.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class User(
    val id: String,
    val fullName: String,
    val phoneNumber: String,
    val gradeId: Int,
    val sectionId: String,
    val role: String = "MEMBER",
    val defaultGroupId: String = "",
    val gradeName: String = "",
    val stats: UserStats? = null
)

@JsonClass(generateAdapter = true)
data class UserStats(
    val lessonsCount: Int = 0,
    val homeworksCount: Int = 0,
    val issuesCount: Int = 0,
    val photosCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val success: Boolean,
    val token: String? = null,
    val recoveryCode: String? = null,
    val user: User? = null,
    val error: ApiError? = null
)

@JsonClass(generateAdapter = true)
data class ApiError(
    val code: String,
    val message: String
)

data class GradeInfo(
    val id: Int,
    val name: String,
    val allowedSections: List<String>
)

@JsonClass(generateAdapter = true)
data class SubjectItem(
    val id: String,
    val name: String,
    val icon: String,
    val colorHex: String
)

object SchoolHierarchy {
    val grades = listOf(
        GradeInfo(7, "الصف السابع", listOf("A")),
        GradeInfo(8, "الصف الثامن", listOf("A", "B")),
        GradeInfo(9, "الصف التاسع", listOf("A", "B")),
        GradeInfo(10, "الأول الثانوي", listOf("A", "B", "C", "D")),
        GradeInfo(11, "الثاني الثانوي", listOf("A", "B", "C", "D")),
        GradeInfo(12, "الثالث الثانوي", listOf("A", "B", "C"))
    )

    fun getSectionArabicName(sectionCode: String): String {
        return when (sectionCode.uppercase()) {
            "A" -> "أ"
            "B" -> "ب"
            "C" -> "ج"
            "D" -> "د"
            else -> sectionCode
        }
    }

    fun getGradeName(gradeId: Int): String {
        return when (gradeId) {
            5 -> "الصف الخامس"
            6 -> "الصف السادس"
            7 -> "الصف السابع"
            8 -> "الصف الثامن"
            9 -> "الصف التاسع"
            10 -> "الأول الثانوي"
            11 -> "الثاني الثانوي"
            12 -> "الثالث الثانوي"
            else -> "الصف $gradeId"
        }
    }

    // Official subject mappings per grade requirement
    // Grades 7, 8, 9: قرآن، إسلامية، لغة عربية، لغة إنجليزية، رياضيات، علوم، اجتماعيات
    private val subjectsGrades7to9 = listOf(
        SubjectItem("quran", "القرآن الكريم", "✨", "#1DE9B6"),
        SubjectItem("islamic", "التربية الإسلامية", "🕌", "#00B0FF"),
        SubjectItem("arabic", "اللغة العربية", "📖", "#FFB300"),
        SubjectItem("english", "اللغة الإنجليزية", "🇬🇧", "#2979FF"),
        SubjectItem("math", "الرياضيات", "📐", "#00E5FF"),
        SubjectItem("science", "العلوم", "🔬", "#69F0AE"),
        SubjectItem("social", "الاجتماعيات", "🌍", "#FF9100")
    )

    // Grade 10 (أول ثانوي): قرآن، إسلامية، لغة عربية، لغة إنجليزية، رياضيات، كيمياء، فيزياء، أحياء، جغرافيا، تاريخ، وطنية
    private val subjectsGrade10 = listOf(
        SubjectItem("quran", "القرآن الكريم", "✨", "#1DE9B6"),
        SubjectItem("islamic", "التربية الإسلامية", "🕌", "#00B0FF"),
        SubjectItem("arabic", "اللغة العربية", "📖", "#FFB300"),
        SubjectItem("english", "اللغة الإنجليزية", "🇬🇧", "#2979FF"),
        SubjectItem("math", "الرياضيات", "📐", "#00E5FF"),
        SubjectItem("chemistry", "الكيمياء", "🧪", "#00E676"),
        SubjectItem("physics", "الفيزياء", "⚡", "#7C4DFF"),
        SubjectItem("biology", "الأحياء", "🧬", "#69F0AE"),
        SubjectItem("geography", "الجغرافيا", "🧭", "#FFAB00"),
        SubjectItem("history", "التاريخ", "🏛️", "#FF6D00"),
        SubjectItem("civics", "التربية الوطنية", "🇾🇪", "#E040FB")
    )

    // Grades 11 and 12 (ثاني وثالث ثانوي - على حسب):
    private val subjectsGrades11and12 = listOf(
        SubjectItem("quran", "القرآن الكريم", "✨", "#1DE9B6"),
        SubjectItem("islamic", "التربية الإسلامية", "🕌", "#00B0FF"),
        SubjectItem("arabic", "اللغة العربية", "📖", "#FFB300"),
        SubjectItem("english", "اللغة الإنجليزية", "🇬🇧", "#2979FF"),
        SubjectItem("math", "الرياضيات", "📐", "#00E5FF"),
        SubjectItem("chemistry", "الكيمياء", "🧪", "#00E676"),
        SubjectItem("physics", "الفيزياء", "⚡", "#7C4DFF"),
        SubjectItem("biology", "الأحياء", "🧬", "#69F0AE"),
        SubjectItem("geography", "الجغرافيا", "🧭", "#FFAB00"),
        SubjectItem("history", "التاريخ", "🏛️", "#FF6D00"),
        SubjectItem("civics", "التربية الوطنية", "🇾🇪", "#E040FB"),
        SubjectItem("computer", "الحاسوب وتكنولوجيا المعلومات", "💻", "#00B0FF")
    )

    fun getSubjectsForGrade(gradeId: Int): List<SubjectItem> {
        return when (gradeId) {
            7, 8, 9 -> subjectsGrades7to9
            10 -> subjectsGrade10
            11, 12 -> subjectsGrades11and12
            else -> subjectsGrades11and12
        }
    }
}

// Global default list for search/filter fallbacks
val DefaultSubjects = SchoolHierarchy.getSubjectsForGrade(11)

@JsonClass(generateAdapter = true)
data class GroupItem(
    val id: String,
    val name: String,
    val type: String, // 'CLASS', 'SHARED', 'OPTIONAL'
    val description: String? = null,
    val icon: String? = null,
    val role: String = "MEMBER",
    val memberCount: Int = 1
)

@JsonClass(generateAdapter = true)
data class ScheduleSlot(
    val id: String,
    val dayOfWeek: Int, // 0 = Sunday, 1 = Monday, 2 = Tuesday, 3 = Wednesday, 4 = Thursday
    val slotOrder: Int, // 1 to 6
    val subjectId: String,
    val subjectName: String,
    val subjectIcon: String,
    val colorHex: String? = null,
    val startTime: String? = null,
    val endTime: String? = null
)

@JsonClass(generateAdapter = true)
data class ScheduleVersionItem(
    val id: String,
    val versionNumber: Int = 1,
    val validFrom: String = "",
    val validUntil: String? = null,
    val createdBy: String? = null,
    val createdAt: Long = 0L,
    val creatorName: String? = null,
    val creatorRole: String? = null
)

@JsonClass(generateAdapter = true)
data class ScheduleMeta(
    val isInitialSetup: Boolean = false,
    val canEditDirectly: Boolean = false,
    val canPropose: Boolean = true,
    val pendingProposalsCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class ScheduleResponse(
    val success: Boolean = true,
    val version: ScheduleVersionItem? = null,
    val slots: List<ScheduleSlot> = emptyList(),
    val meta: ScheduleMeta? = null
)

@JsonClass(generateAdapter = true)
data class ScheduleProposalItem(
    val id: String,
    val groupId: String,
    val proposedBy: String,
    val proposerName: String = "طالب في الشعبة",
    val proposerRole: String = "STUDENT",
    val dayOfWeek: Int,
    val slotOrder: Int,
    val oldSubjectId: String? = null,
    val oldSubjectName: String? = null,
    val newSubjectId: String,
    val newSubjectName: String? = null,
    val newSubjectIcon: String = "📚",
    val reason: String,
    val status: String = "PENDING", // PENDING, ACCEPTED, REJECTED
    val votesFor: Int = 0,
    val votesAgainst: Int = 0,
    val myVote: String? = null, // "FOR", "AGAINST", null
    val createdAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class ScheduleProposalsResponse(
    val success: Boolean = true,
    val canReviewProposals: Boolean = false,
    val proposals: List<ScheduleProposalItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ScheduleVersionsResponse(
    val success: Boolean = true,
    val versions: List<ScheduleVersionItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GroupMemberItem(
    val userId: String,
    val fullName: String,
    val phoneNumber: String? = null,
    val memberRole: String = "STUDENT",
    val globalRole: String = "STUDENT",
    val status: String = "ACTIVE", // ACTIVE, PENDING, BANNED
    val joinedAt: Long = 0L,
    val gradeId: Int = 0,
    val sectionId: String = ""
)

@JsonClass(generateAdapter = true)
data class GroupMembersResponse(
    val success: Boolean = true,
    val canManageMembers: Boolean = false,
    val members: List<GroupMemberItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BatchScheduleRequest(
    val slots: List<ScheduleSlot>,
    val validFrom: String? = null
)

@JsonClass(generateAdapter = true)
data class VoteProposalRequest(
    val voteType: String = "FOR" // "FOR" or "AGAINST"
)

@JsonClass(generateAdapter = true)
data class UpdateMemberRoleRequest(
    val newRole: String
)

@JsonClass(generateAdapter = true)
data class ContentCorrectionItem(
    val id: String,
    val contentId: String,
    val groupId: String,
    val userId: String,
    val authorName: String? = null,
    val fieldName: String,
    val originalValue: String? = null,
    val proposedValue: String,
    val reason: String,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED, APPLIED
    val contentTitle: String? = null,
    val createdAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class GroupCorrectionsResponse(
    val success: Boolean = true,
    val corrections: List<ContentCorrectionItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CommunityDecisionItem(
    val id: String,
    val groupId: String,
    val requestType: String, // DELETION, CORRECTION, SCHEDULE, VERIFICATION
    val targetId: String,
    val title: String,
    val description: String? = null,
    val requestedBy: String,
    val requesterName: String = "عضو في الشعبة",
    val totalEligibleVoters: Int = 1,
    val thresholdPercent: Int = 50,
    val votesFor: Int = 0,
    val votesAgainst: Int = 0,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED, APPLIED, EXPIRED
    val myVote: Int? = null, // 1 = For, 0 = Against
    val expiresAt: Long? = null,
    val createdAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class CommunityDecisionsResponse(
    val success: Boolean = true,
    val decisions: List<CommunityDecisionItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class VoteDecisionRequest(
    val voteChoice: Int // 1 = For, 0 = Against
)

@JsonClass(generateAdapter = true)
data class TeacherDashboardResponse(
    val success: Boolean = true,
    val teacher: TeacherInfo? = null,
    val stats: TeacherStats? = null,
    val recentHomeworks: List<HomeworkItem> = emptyList(),
    val recentExams: List<ExamItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TeacherInfo(
    val fullName: String,
    val phoneNumber: String,
    val role: String,
    val verified: Boolean = true
)

@JsonClass(generateAdapter = true)
data class TeacherStats(
    val officialHomeworksCount: Int = 0,
    val officialExamsCount: Int = 0,
    val teacherAnswersCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class ModeratorDashboardResponse(
    val success: Boolean = true,
    val groupId: String = "",
    val stats: ModeratorStats? = null,
    val pendingCorrections: List<ContentCorrectionItem> = emptyList(),
    val pendingProposals: List<ScheduleProposalItem> = emptyList(),
    val pendingDecisions: List<CommunityDecisionItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ModeratorStats(
    val pendingCorrectionsCount: Int = 0,
    val pendingProposalsCount: Int = 0,
    val pendingDecisionsCount: Int = 0,
    val totalMembers: Int = 0
)

@JsonClass(generateAdapter = true)
data class AdminDashboardResponse(
    val success: Boolean = true,
    val school: SchoolAdminInfo? = null,
    val stats: SchoolStats? = null,
    val teachers: List<UserBrief> = emptyList(),
    val moderators: List<UserBrief> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SchoolAdminInfo(
    val name: String,
    val adminName: String,
    val adminPhone: String
)

@JsonClass(generateAdapter = true)
data class SchoolStats(
    val totalUsers: Int = 0,
    val totalTeachers: Int = 0,
    val totalModerators: Int = 0,
    val totalGroups: Int = 0,
    val totalLessons: Int = 0,
    val totalHomeworks: Int = 0,
    val totalExams: Int = 0
)

@JsonClass(generateAdapter = true)
data class UserBrief(
    val id: String,
    val full_name: String,
    val phone_number: String,
    val role: String,
    val grade_id: Int = 0,
    val section_id: String = ""
)

@JsonClass(generateAdapter = true)
data class ContentItem(
    val id: String,
    val groupId: String,
    val studyDate: String,
    val subjectId: String,
    val type: String = "LESSON", // 'LESSON', 'SUMMARY', 'NOTE', 'FILE'
    val title: String,
    val description: String? = null,
    val authorName: String,
    val authorGradeSection: String,
    val viewsCount: Int = 0,
    val usefulCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val subjectName: String = "",
    val subjectIcon: String = "📚",
    val colorHex: String = "#00E5FF",
    val media: List<MediaItem> = emptyList(),
    val syncStatus: String = "SYNCED" // 'SYNCED', 'SYNCING', 'LOCAL', 'FAILED'
)

@JsonClass(generateAdapter = true)
data class MediaItem(
    val id: String,
    val pageOrder: Int = 1,
    val url: String,
    val mimeType: String = "image/jpeg",
    val fileSize: Int = 0
)

@JsonClass(generateAdapter = true)
data class HomeworkItem(
    val id: String,
    val groupId: String,
    val studyDate: String,
    val dueDate: String,
    val subjectId: String,
    val title: String,
    val details: String? = null,
    val pageNumbers: String? = null,
    val questionNumbers: String? = null,
    val taskType: String = "HOMEWORK", // 'HOMEWORK', 'TASK'
    val subjectName: String = "",
    val subjectIcon: String = "📝",
    val colorHex: String = "#00E5FF",
    val mediaUrls: List<String> = emptyList(),
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "SYNCED" // 'SYNCED', 'SYNCING', 'LOCAL', 'FAILED'
)

@JsonClass(generateAdapter = true)
data class ExamItem(
    val id: String,
    val groupId: String,
    val examDate: String,
    val subjectId: String,
    val title: String,
    val requiredChapters: String? = null,
    val notes: String? = null,
    val subjectName: String = "",
    val subjectIcon: String = "🔴",
    val colorHex: String = "#FF3366",
    val isCompleted: Boolean = false,
    val syncStatus: String = "SYNCED" // 'SYNCED', 'SYNCING', 'LOCAL', 'FAILED'
)

@JsonClass(generateAdapter = true)
data class SchoolEventItem(
    val id: String,
    val groupId: String,
    val eventDate: String,
    val timeStr: String? = null,
    val title: String,
    val description: String? = null,
    val category: String = "ACTIVITY", // 'ACTIVITY', 'COMPETITION', 'TRIP', 'ANNOUNCEMENT', 'HOLIDAY'
    val location: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "SYNCED" // 'SYNCED', 'SYNCING', 'LOCAL', 'FAILED'
)

@JsonClass(generateAdapter = true)
data class IssueItem(
    val id: String,
    val groupId: String,
    val subjectId: String? = null,
    val homeworkId: String? = null,
    val examId: String? = null,
    val title: String,
    val description: String? = null,
    val status: String = "OPEN", // 'OPEN', 'IN_DISCUSSION', 'SOLVED', 'CLOSED'
    val bestCommentId: String? = null,
    val authorName: String,
    val subjectName: String? = null,
    val subjectIcon: String? = null,
    val commentsCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "SYNCED" // 'SYNCED', 'SYNCING', 'LOCAL', 'FAILED'
)

@JsonClass(generateAdapter = true)
data class IssueCommentItem(
    val id: String,
    val issueId: String,
    val userId: String,
    val authorName: String,
    val comment: String,
    val isBestAnswer: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "SYNCED" // 'SYNCED', 'SYNCING', 'LOCAL', 'FAILED'
)

@JsonClass(generateAdapter = true)
data class BookItem(
    val id: String,
    val gradeId: Int,
    val subjectId: String,
    val title: String,
    val edition: String? = null,
    val fileSizeMb: Double = 0.0,
    val fileUrl: String = "",
    val thumbnailUrl: String? = null,
    val subjectName: String = "",
    val subjectIcon: String = "📕"
)

// GitHub auto-generated catalog.json models for tanweer-books
@JsonClass(generateAdapter = true)
data class BooksCatalogResponse(
    val version: Int = 1,
    val generated_at: String? = null,
    val total_books: Int = 0,
    val books: List<CatalogBookItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CatalogBookItem(
    val id: String,
    val grade_id: Int = 0,
    val subject_id: String = "",
    val subject_name: String = "",
    val subject_icon: String = "📕",
    val title: String = "",
    val edition: String? = null,
    val part_number: Int? = null,
    val asset_name: String? = null,
    val size_bytes: Long = 0L,
    val size_mb: Double = 0.0,
    val download_url: String = "",
    val updated_at: String? = null
)

// GitHub Releases models for tanweer-books
@JsonClass(generateAdapter = true)
data class GitHubRelease(
    val id: Long = 0,
    val tag_name: String? = null,
    val name: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseAsset(
    val id: Long = 0,
    val name: String = "",
    val size: Long = 0,
    val browser_download_url: String = "",
    val updated_at: String? = null
)

// Parser for filename pattern: المادة-الصف-الجزء.pdf (e.g. Arabic-9-1.pdf, Geography-10.pdf, Chemistryactivities-10.pdf)
object BookParser {
    fun parseAsset(asset: GitHubReleaseAsset): BookItem? {
        val rawName = asset.name.trim()
        if (!rawName.endsWith(".pdf", ignoreCase = true)) return null

        val baseName = rawName.substringBeforeLast(".pdf")
        val parts = baseName.split("-", "_")
        if (parts.isEmpty()) return null

        val subjectPart = parts[0].trim().lowercase()
        val gradePart = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
        val partNumber = parts.getOrNull(2)?.trim()?.toIntOrNull()

        val (subjectId, subjectName, subjectIcon) = mapSubject(subjectPart)
        val partAr = when {
            subjectPart.contains("activities") -> "كتاب الأنشطة والتجارب العملية"
            subjectPart.contains("pupil") || subjectPart.contains("pubil") -> "كتاب الطالب (Pupils Book)"
            subjectPart.contains("work") -> "كتاب التدريبات (Workbook)"
            partNumber == null -> "كتاب المنهج الكامل"
            partNumber == 1 -> "الجزء الأول"
            partNumber == 2 -> "الجزء الثاني"
            partNumber == 3 -> "الجزء الثالث"
            else -> "الجزء $partNumber"
        }

        val gradeName = SchoolHierarchy.getGradeName(gradePart)
        val title = "كتاب $subjectName ($partAr)"
        val sizeMb = Math.round((asset.size.toDouble() / (1024.0 * 1024.0)) * 10.0) / 10.0
        val canonicalId = com.magd.tanweer.util.BookCatalogResolver.getCanonicalBookId(gradePart, subjectId, partNumber)

        return BookItem(
            id = canonicalId,
            gradeId = gradePart,
            subjectId = subjectId,
            title = title,
            edition = "إصدار رسمي - تنوير",
            fileSizeMb = sizeMb,
            fileUrl = asset.browser_download_url,
            thumbnailUrl = null,
            subjectName = subjectName,
            subjectIcon = subjectIcon
        )
    }

    private fun mapSubject(sub: String): Triple<String, String, String> {
        val s = sub.lowercase()
        return when {
            s.contains("neighborhoodsactivities") -> Triple("biology_act", "الأنشطة والتجارب - الأحياء", "🧬")
            s.contains("neighborhoods") -> Triple("biology", "الأحياء (علم الأحياء)", "🧬")
            s.contains("chemistryactivities") -> Triple("chemistry_act", "الأنشطة والتجارب - الكيمياء", "🧪")
            s.contains("chemistry") -> Triple("chemistry", "الكيمياء", "🧪")
            s.contains("physicsactivities") -> Triple("physics_act", "الأنشطة والتجارب - الفيزياء", "⚡")
            s.contains("physics") || s.contains("فيز") -> Triple("physics", "الفيزياء", "⚡")
            s.contains("englishpubils") || s.contains("englishpupils") -> Triple("english", "اللغة الإنجليزية (كتاب الطالب)", "🇬🇧")
            s.contains("englishwork") -> Triple("english_work", "اللغة الإنجليزية (كتاب الأنشطة)", "🇬🇧")
            s.contains("english") || s.contains("إنجليز") || s.contains("انجليز") -> Triple("english", "اللغة الإنجليزية", "🇬🇧")
            s.contains("syntax") -> Triple("syntax", "النحو والصرف والقواعد", "📖")
            s.contains("literature") -> Triple("literature", "الأدب والنصوص والبلاغة", "📖")
            s.contains("reading") -> Triple("reading", "القراءة والمطالعة", "📖")
            s.contains("arabic") || s.contains("عرب") -> Triple("arabic", "اللغة العربية", "📖")
            s.contains("sirprophet") -> Triple("sirah", "السيرة النبوية الشريفة", "🕌")
            s.contains("hadith") -> Triple("hadith", "الحديث الشريف وعلومه", "🕌")
            s.contains("eman") -> Triple("eman", "الإيمان والتربية الإيمانية", "✨")
            s.contains("quran") || s.contains("قرآن") || s.contains("قران") -> Triple("quran", "القرآن الكريم وتلاوته", "✨")
            s.contains("islam") || s.contains("إسلام") || s.contains("اسلام") -> Triple("islamic", "التربية الإسلامية", "🕌")
            s.contains("nationaleducation") -> Triple("civics", "التربية الوطنية", "🇾🇪")
            s.contains("ymenisociety") -> Triple("yemen_society", "المجتمع اليمني والقضايا المعاصرة", "🇾🇪")
            s.contains("civic") || s.contains("وطن") -> Triple("civics", "التربية الوطنية", "🇾🇪")
            s.contains("geographic") || s.contains("geography") || s.contains("جغراف") -> Triple("geography", "الجغرافيا", "🧭")
            s.contains("history") || s.contains("تاريخ") -> Triple("history", "التاريخ", "🏛️")
            s.contains("math") || s.contains("رياض") -> Triple("math", "الرياضيات", "📐")
            s.contains("science") || s.contains("علوم") -> Triple("science", "العلوم", "🔬")
            s.contains("computer") || s.contains("حاس") -> Triple("computer", "الحاسوب وتكنولوجيا المعلومات", "💻")
            s.contains("bio") || s.contains("أحياء") || s.contains("احياء") -> Triple("biology", "الأحياء", "🧬")
            else -> Triple(sub, sub.replaceFirstChar { it.uppercase() }, "📕")
        }
    }
}

@JsonClass(generateAdapter = true)
data class DeletionRequestItem(
    val id: String,
    val contentId: String,
    val requestedBy: String,
    val reason: String,
    val totalEligibleVoters: Int,
    val votesInFavor: Int,
    val votesAgainst: Int,
    val status: String,
    val createdAt: Long
)

@JsonClass(generateAdapter = true)
data class ChatMessageItem(
    val id: String,
    val groupId: String,
    val senderId: String,
    val senderName: String,
    val senderGradeSection: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMe: Boolean = false,
    val status: String = "SENT" // "SENDING", "SENT", "FAILED"
)

@JsonClass(generateAdapter = true)
data class DailyPlanItem(
    val id: String,
    val userId: String,
    val homeworkId: String?,
    val title: String,
    val subjectName: String?,
    val planDate: String,
    val isCompleted: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)
