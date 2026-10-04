package com.magd.tanweer.data.repository

import android.util.Log
import com.magd.tanweer.data.local.*
import com.magd.tanweer.data.model.*
import com.magd.tanweer.data.remote.*
import com.magd.tanweer.util.ProcessedPageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

sealed class SyncResult {
    data class Success(val count: Int = 0) : SyncResult()
    data class Offline(val reason: String = "وضع عدم الاتصال") : SyncResult()
    data class Error(val message: String, val isNetworkError: Boolean = false) : SyncResult()
}

class TanweerRepository(
    private val db: TanweerDatabase,
    private val api: TanweerApiService = NetworkModule.apiService,
    private val gitHubApi: GitHubApiService = NetworkModule.gitHubApiService,
    val chatManager: RealtimeChatManager = RealtimeChatManager()
) {
    val currentUser: Flow<User?> = db.userDao().getUser().map { entity ->
        entity?.let {
            NetworkModule.setAuthToken(it.token)
            User(
                id = it.id,
                fullName = it.fullName,
                phoneNumber = it.phoneNumber,
                gradeId = it.gradeId,
                sectionId = it.sectionId,
                role = it.role,
                defaultGroupId = it.defaultGroupId,
                gradeName = SchoolHierarchy.getGradeName(it.gradeId),
                stats = UserStats(
                    lessonsCount = it.lessonsCount,
                    homeworksCount = it.homeworksCount,
                    issuesCount = it.issuesCount,
                    photosCount = it.photosCount
                )
            )
        }
    }

    // =========================================================================
    // AUTHENTICATION & USER MANAGEMENT (REAL SERVER FIRST, NO MOCK FALLBACKS)
    // =========================================================================

    suspend fun register(
        fullName: String,
        phone: String,
        pass: String,
        gradeId: Int,
        sectionId: String
    ): Result<Pair<User, String>> = withContext(Dispatchers.IO) {
        val classGroupId = "class_${gradeId}_${sectionId.uppercase()}"
        val gradeName = SchoolHierarchy.getGradeName(gradeId)
        val secAr = SchoolHierarchy.getSectionArabicName(sectionId)
        val groupName = "$gradeName — شعبة $secAr"

        try {
            val req = RegisterRequest(
                fullName = fullName,
                phoneNumber = phone,
                password = pass,
                gradeId = gradeId,
                sectionId = sectionId.uppercase()
            )
            val res = api.register(req)
            val body = res.body()
            if (res.isSuccessful && body?.success == true && body.user != null && body.token != null) {
                NetworkModule.setAuthToken(body.token)
                db.userDao().insertUser(
                    UserEntity(
                        id = body.user.id,
                        fullName = body.user.fullName,
                        phoneNumber = body.user.phoneNumber,
                        gradeId = body.user.gradeId,
                        sectionId = body.user.sectionId,
                        role = body.user.role,
                        defaultGroupId = body.user.defaultGroupId.ifEmpty { classGroupId },
                        token = body.token,
                        recoveryCode = body.recoveryCode
                    )
                )
                db.groupDao().insertGroups(
                    listOf(
                        GroupEntity(
                            id = body.user.defaultGroupId.ifEmpty { classGroupId },
                            name = groupName,
                            type = "CLASS",
                            description = "المجموعة الدراسية الرسمية للشعبة",
                            icon = "🏫",
                            role = "MEMBER",
                            memberCount = 1
                        )
                    )
                )
                Result.success(Pair(body.user, body.recoveryCode ?: ""))
            } else {
                val errorMsg = body?.error?.message ?: res.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: "تعذر إنشاء الحساب، يرجى المحاولة لاحقاً"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("TanweerRepository", "Registration network error", e)
            Result.failure(Exception("تعذر الاتصال بالسيرفر. يرجى التحقق من اتصال الإنترنت"))
        }
    }

    suspend fun login(phone: String, pass: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val res = api.login(LoginRequest(phoneNumber = phone, password = pass))
            val body = res.body()
            if (res.isSuccessful && body?.success == true && body.user != null && body.token != null) {
                NetworkModule.setAuthToken(body.token)
                db.userDao().insertUser(
                    UserEntity(
                        id = body.user.id,
                        fullName = body.user.fullName,
                        phoneNumber = body.user.phoneNumber,
                        gradeId = body.user.gradeId,
                        sectionId = body.user.sectionId,
                        role = body.user.role,
                        defaultGroupId = body.user.defaultGroupId,
                        token = body.token,
                        recoveryCode = null
                    )
                )
                Result.success(body.user)
            } else {
                val errorMsg = body?.error?.message ?: "بيانات الدخول غير صحيحة"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("TanweerRepository", "Login network error", e)
            Result.failure(Exception("تعذر الاتصال بالسيرفر للتحقق من كلمة المرور والحساب بأمان. يلزم الاتصال بالإنترنت لتسجيل الدخول."))
        }
    }

    suspend fun loginWithRecoveryCode(code: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val res = api.loginWithRecoveryCode(RecoveryLoginRequest(recoveryCode = code))
            val body = res.body()
            if (res.isSuccessful && body?.success == true && body.user != null && body.token != null) {
                NetworkModule.setAuthToken(body.token)
                db.userDao().insertUser(
                    UserEntity(
                        id = body.user.id,
                        fullName = body.user.fullName,
                        phoneNumber = body.user.phoneNumber,
                        gradeId = body.user.gradeId,
                        sectionId = body.user.sectionId,
                        role = body.user.role,
                        defaultGroupId = body.user.defaultGroupId,
                        token = body.token,
                        recoveryCode = code
                    )
                )
                Result.success(body.user)
            } else {
                val errorMsg = body?.error?.message ?: "رمز الاسترداد غير صحيح أو منتهي الصلاحية"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("TanweerRepository", "Recovery login error", e)
            Result.failure(Exception("تعذر الاتصال بالسيرفر للتحقق من رمز الاسترداد بأمان. يلزم الاتصال بالإنترنت."))
        }
    }

    suspend fun updateProfile(
        fullName: String?,
        gradeId: Int?,
        sectionId: String?
    ): Result<User> = withContext(Dispatchers.IO) {
        val currentUserEntity = db.userDao().getUserSync()
            ?: return@withContext Result.failure(Exception("لا يوجد مستخدم مسجل حالياً"))

        val newFullName = fullName ?: currentUserEntity.fullName
        val newGradeId = gradeId ?: currentUserEntity.gradeId
        val newSectionId = sectionId ?: currentUserEntity.sectionId
        val newGroupId = "class_${newGradeId}_${newSectionId.uppercase()}"

        try {
            val res = api.updateProfile(
                UpdateProfileRequest(
                    fullName = newFullName,
                    gradeId = newGradeId,
                    sectionId = newSectionId.uppercase()
                )
            )
            if (!res.isSuccessful || res.body()?.user == null) {
                val errorMsg = res.body()?.message ?: res.message().let { if (it.isNullOrBlank()) "فشل تحديث بيانات الحساب على السيرفر" else it }
                return@withContext Result.failure(Exception(errorMsg))
            }
            val updatedUser = res.body()!!.user!!

            db.userDao().insertUser(
                UserEntity(
                    id = updatedUser.id,
                    fullName = updatedUser.fullName,
                    phoneNumber = updatedUser.phoneNumber,
                    gradeId = updatedUser.gradeId,
                    sectionId = updatedUser.sectionId,
                    role = updatedUser.role,
                    defaultGroupId = updatedUser.defaultGroupId,
                    token = currentUserEntity.token,
                    recoveryCode = currentUserEntity.recoveryCode
                )
            )

            // If class changed, re-sync groups to reflect current membership from server
            syncGroups()

            Result.success(updatedUser)
        } catch (e: Exception) {
            Log.e("TanweerRepository", "Update profile error", e)
            Result.failure(Exception("تعذر الاتصال بالسيرفر لحفظ التعديلات. يرجى التأكد من اتصالك بالإنترنت."))
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        chatManager.disconnect()
        NetworkModule.setAuthToken(null)
        // Complete isolation: clear all local database tables so subsequent users never see prior user's cached data
        db.clearAllTables()
    }

    // =========================================================================
    // UNIFIED SYNC ENGINE: Server -> Sync Engine -> Room -> Compose UI
    // =========================================================================

    suspend fun syncInitial(groupId: String, todayDate: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            syncProfile()
            syncGroups()
            if (groupId.isNotBlank()) {
                syncSchedule(groupId)
                syncDay(groupId, todayDate)
                syncHomeworks(groupId)
                syncExams(groupId)
                syncIssues(groupId)
            }
            SyncResult.Success()
        } catch (e: Exception) {
            Log.e("TanweerRepository", "Initial sync error", e)
            SyncResult.Offline("وضع عدم الاتصال: تعذر إكمال المزامنة الكاملة")
        }
    }

    suspend fun syncProfile(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val res = api.getProfile()
            if (res.isSuccessful && res.body()?.user != null) {
                val user = res.body()!!.user!!
                val currentToken = db.userDao().getUserSync()?.token ?: ""
                val currentRecovery = db.userDao().getUserSync()?.recoveryCode
                db.userDao().insertUser(
                    UserEntity(
                        id = user.id,
                        fullName = user.fullName,
                        phoneNumber = user.phoneNumber,
                        gradeId = user.gradeId,
                        sectionId = user.sectionId,
                        role = user.role,
                        defaultGroupId = user.defaultGroupId,
                        token = currentToken,
                        recoveryCode = currentRecovery,
                        lessonsCount = user.stats?.lessonsCount ?: 0,
                        homeworksCount = user.stats?.homeworksCount ?: 0,
                        issuesCount = user.stats?.issuesCount ?: 0,
                        photosCount = user.stats?.photosCount ?: 0
                    )
                )
                SyncResult.Success(1)
            } else {
                SyncResult.Offline()
            }
        } catch (e: Exception) {
            SyncResult.Offline("أوفلاين: الملف الشخصي")
        }
    }

    suspend fun syncGroups(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val res = api.getGroups()
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                val myEntities = body.myGroups.map {
                    GroupEntity(
                        id = it.id,
                        name = it.name,
                        type = it.type,
                        description = it.description,
                        icon = it.icon,
                        role = it.role,
                        memberCount = it.memberCount,
                        isDiscoverable = false,
                        isOfficial = (it.type == "CLASS" || it.id.startsWith("class_") || it.role == "OFFICIAL")
                    )
                }
                val discoverEntities = body.discoverGroups.map {
                    GroupEntity(
                        id = it.id,
                        name = it.name,
                        type = it.type,
                        description = it.description,
                        icon = it.icon,
                        role = it.role,
                        memberCount = it.memberCount,
                        isDiscoverable = true,
                        isOfficial = (it.type == "CLASS" || it.id.startsWith("class_") || it.role == "OFFICIAL")
                    )
                }
                val allEntities = myEntities + discoverEntities
                db.groupDao().clearGroups()
                if (allEntities.isNotEmpty()) {
                    db.groupDao().insertGroups(allEntities)
                }
                SyncResult.Success(allEntities.size)
            } else {
                SyncResult.Offline()
            }
        } catch (e: Exception) {
            SyncResult.Offline("أوفلاين: المجموعات")
        }
    }

    suspend fun syncSchedule(groupId: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getSchedule(groupId)
            if (res.isSuccessful && res.body() != null) {
                val slots = res.body()!!.slots.map {
                    ScheduleSlotEntity(
                        id = it.id,
                        groupId = groupId,
                        dayOfWeek = it.dayOfWeek,
                        slotOrder = it.slotOrder,
                        subjectId = it.subjectId,
                        subjectName = it.subjectName,
                        subjectIcon = it.subjectIcon,
                        colorHex = it.colorHex,
                        startTime = it.startTime,
                        endTime = it.endTime
                    )
                }
                db.scheduleDao().clearSlots(groupId)
                if (slots.isNotEmpty()) {
                    db.scheduleDao().insertSlots(slots)
                }
                SyncResult.Success(slots.size)
            } else {
                SyncResult.Offline()
            }
        } catch (e: Exception) {
            SyncResult.Offline("أوفلاين: جدول الحصص")
        }
    }

    suspend fun syncDay(groupId: String, date: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank() || date.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getDayDetail(date, groupId)
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!

                // Reconcile Lessons for this date
                db.contentDao().clearSyncedContentsForDate(groupId, date)
                if (body.contents.isNotEmpty()) {
                    val contents = body.contents.map {
                        ContentEntity(
                            id = it.id,
                            groupId = it.groupId,
                            studyDate = it.studyDate,
                            subjectId = it.subjectId,
                            type = it.type,
                            title = it.title,
                            description = it.description,
                            authorName = it.authorName,
                            authorGradeSection = it.authorGradeSection,
                            viewsCount = it.viewsCount,
                            usefulCount = it.usefulCount,
                            createdAt = it.createdAt,
                            subjectName = it.subjectName,
                            subjectIcon = it.subjectIcon,
                            colorHex = it.colorHex
                        )
                    }
                    db.contentDao().insertContents(contents)
                }

                // Reconcile Homeworks for this date
                db.homeworkDao().clearSyncedHomeworksForDate(groupId, date)
                if (body.homeworks.isNotEmpty()) {
                    val homeworks = body.homeworks.map {
                        HomeworkEntity(
                            id = it.id,
                            groupId = it.groupId,
                            studyDate = it.studyDate,
                            dueDate = it.dueDate,
                            subjectId = it.subjectId,
                            title = it.title,
                            details = it.details,
                            pageNumbers = it.pageNumbers,
                            questionNumbers = it.questionNumbers,
                            taskType = it.taskType,
                            subjectName = it.subjectName,
                            subjectIcon = it.subjectIcon,
                            colorHex = it.colorHex,
                            mediaUrlsJson = serializeStringList(it.mediaUrls),
                            createdAt = it.createdAt
                        )
                    }
                    db.homeworkDao().insertHomeworks(homeworks)
                }

                // Reconcile Exams for this date
                db.examDao().clearSyncedExamsForDate(groupId, date)
                if (body.exams.isNotEmpty()) {
                    val exams = body.exams.map {
                        ExamEntity(
                            id = it.id,
                            groupId = it.groupId,
                            examDate = it.examDate,
                            subjectId = it.subjectId,
                            title = it.title,
                            requiredChapters = it.requiredChapters,
                            notes = it.notes,
                            subjectName = it.subjectName,
                            subjectIcon = it.subjectIcon,
                            colorHex = it.colorHex
                        )
                    }
                    db.examDao().insertExams(exams)
                }

                // Reconcile Events for this date
                db.eventDao().clearSyncedEventsForDate(groupId, date)
                if (body.events.isNotEmpty()) {
                    val events = body.events.map {
                        EventEntity(
                            id = it.id,
                            groupId = it.groupId,
                            eventDate = it.eventDate,
                            timeStr = it.timeStr,
                            title = it.title,
                            description = it.description,
                            category = it.category,
                            location = it.location,
                            createdAt = it.createdAt
                        )
                    }
                    db.eventDao().insertEvents(events)
                }

                SyncResult.Success(body.contents.size + body.homeworks.size + body.exams.size)
            } else {
                SyncResult.Offline()
            }
        } catch (e: Exception) {
            SyncResult.Offline("أوفلاين: أحداث اليوم")
        }
    }

    suspend fun syncHomeworks(groupId: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getHomeworks(groupId)
            if (res.isSuccessful && res.body()?.homeworks != null) {
                val hwList = res.body()!!.homeworks.map {
                    HomeworkEntity(
                        id = it.id,
                        groupId = it.groupId,
                        studyDate = it.studyDate,
                        dueDate = it.dueDate,
                        subjectId = it.subjectId,
                        title = it.title,
                        details = it.details,
                        pageNumbers = it.pageNumbers,
                        questionNumbers = it.questionNumbers,
                        taskType = it.taskType,
                        subjectName = it.subjectName,
                        subjectIcon = it.subjectIcon,
                        colorHex = it.colorHex,
                        mediaUrlsJson = serializeStringList(it.mediaUrls),
                        createdAt = it.createdAt
                    )
                }
                db.homeworkDao().clearSyncedHomeworks(groupId)
                if (hwList.isNotEmpty()) {
                    db.homeworkDao().insertHomeworks(hwList)
                }
                SyncResult.Success(hwList.size)
            } else {
                SyncResult.Offline()
            }
        } catch (e: Exception) {
            SyncResult.Offline("أوفلاين: الواجبات")
        }
    }

    suspend fun syncExams(groupId: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getExams(groupId)
            if (res.isSuccessful && res.body()?.exams != null) {
                val exams = res.body()!!.exams.map {
                    ExamEntity(
                        id = it.id,
                        groupId = it.groupId,
                        examDate = it.examDate,
                        subjectId = it.subjectId,
                        title = it.title,
                        requiredChapters = it.requiredChapters,
                        notes = it.notes,
                        subjectName = it.subjectName,
                        subjectIcon = it.subjectIcon,
                        colorHex = it.colorHex
                    )
                }
                db.examDao().clearSyncedExams(groupId)
                if (exams.isNotEmpty()) {
                    db.examDao().insertExams(exams)
                }
                SyncResult.Success(exams.size)
            } else {
                SyncResult.Offline()
            }
        } catch (e: Exception) {
            SyncResult.Offline("أوفلاين: الاختبارات")
        }
    }

    suspend fun syncEvents(groupId: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getEvents(groupId)
            if (res.isSuccessful && res.body()?.events != null) {
                val events = res.body()!!.events.map {
                    EventEntity(
                        id = it.id,
                        groupId = it.groupId,
                        eventDate = it.eventDate,
                        timeStr = it.timeStr,
                        title = it.title,
                        description = it.description,
                        category = it.category,
                        location = it.location,
                        createdAt = it.createdAt
                    )
                }
                db.eventDao().clearSyncedEvents(groupId)
                if (events.isNotEmpty()) {
                    db.eventDao().insertEvents(events)
                }
                SyncResult.Success(events.size)
            } else {
                SyncResult.Offline()
            }
        } catch (e: Exception) {
            SyncResult.Offline("أوفلاين: التقويم")
        }
    }

    suspend fun syncIssues(groupId: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getIssues(groupId)
            if (res.isSuccessful && res.body()?.issues != null) {
                val issues = res.body()!!.issues.map {
                    IssueEntity(
                        id = it.id,
                        groupId = it.groupId,
                        subjectId = it.subjectId,
                        homeworkId = it.homeworkId,
                        examId = it.examId,
                        title = it.title,
                        description = it.description,
                        status = it.status,
                        bestCommentId = it.bestCommentId,
                        authorName = it.authorName,
                        subjectName = it.subjectName,
                        subjectIcon = it.subjectIcon,
                        commentsCount = it.commentsCount,
                        createdAt = it.createdAt
                    )
                }
                db.issueDao().clearSyncedIssues(groupId)
                if (issues.isNotEmpty()) {
                    db.issueDao().insertIssues(issues)
                }
                SyncResult.Success(issues.size)
            } else {
                SyncResult.Offline()
            }
        } catch (e: Exception) {
            SyncResult.Offline("أوفلاين: الاستفسارات")
        }
    }

    private val BOOKS_SYNC_COOLDOWN_MS = 24 * 60 * 60 * 1000L // 24 hours

    suspend fun syncBooks(gradeId: Int, forceRefresh: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val syncKey = "books_sync_grade_$gradeId"
        val lastSync = db.syncMetaDao().getLastSyncTime(syncKey) ?: 0L

        // If not forced and synced within 24h, return Success immediately using cached Room database
        if (!forceRefresh && (now - lastSync < BOOKS_SYNC_COOLDOWN_MS) && lastSync > 0L) {
            return@withContext SyncResult.Success()
        }

        try {
            var count = 0
            var fetchAttempted = false
            var anySourceReached = false

            // 1. Primary: Try fetching official pre-compiled catalog.json directly from GitHub
            var catalogSuccess = false
            try {
                fetchAttempted = true
                val catalogRes = gitHubApi.getCatalog()
                if (catalogRes.isSuccessful && catalogRes.body() != null) {
                    anySourceReached = true
                    val catalog = catalogRes.body()!!
                    val catalogBooks = catalog.books
                        .filter { it.grade_id == gradeId || gradeId == 0 }
                        .map {
                            BookEntity(
                                id = it.id,
                                gradeId = it.grade_id,
                                subjectId = it.subject_id,
                                title = it.title,
                                edition = it.edition,
                                fileSizeMb = it.size_mb,
                                fileUrl = it.download_url,
                                thumbnailUrl = null,
                                subjectName = it.subject_name,
                                subjectIcon = it.subject_icon
                            )
                        }
                    if (catalogBooks.isNotEmpty()) {
                        db.bookDao().insertBooks(catalogBooks)
                        count += catalogBooks.size
                        catalogSuccess = true
                    }
                }
            } catch (e: Exception) {
                Log.d("TanweerRepository", "catalog.json not available or failed: ${e.message}")
            }

            // 2. Fallback: Parse directly from GitHub Releases if catalog.json was not loaded
            if (!catalogSuccess) {
                try {
                    val ghRes = gitHubApi.getReleases()
                    if (ghRes.isSuccessful && ghRes.body() != null) {
                        anySourceReached = true
                        val releases = ghRes.body()!!
                        val ghBooks = releases.flatMap { release ->
                            release.assets.mapNotNull { asset ->
                                BookParser.parseAsset(asset)
                            }
                        }.filter { it.gradeId == gradeId || gradeId == 0 }.map {
                            BookEntity(
                                id = it.id,
                                gradeId = it.gradeId,
                                subjectId = it.subjectId,
                                title = it.title,
                                edition = it.edition,
                                fileSizeMb = it.fileSizeMb,
                                fileUrl = it.fileUrl,
                                thumbnailUrl = it.thumbnailUrl,
                                subjectName = it.subjectName,
                                subjectIcon = it.subjectIcon
                            )
                        }
                        if (ghBooks.isNotEmpty()) {
                            db.bookDao().insertBooks(ghBooks)
                            count += ghBooks.size
                        }
                    }
                } catch (e: Exception) {
                    Log.d("TanweerRepository", "GitHub Releases fallback failed: ${e.message}")
                }
            }

            if (count > 0 || anySourceReached) {
                db.syncMetaDao().setLastSyncTime(SyncMetaEntity(key = syncKey, lastSyncedAt = now))
                SyncResult.Success(count)
            } else {
                SyncResult.Error("تعذر تحميل قائمة الكتب الدراسية من الخادم والمصدر الرسمي")
            }
        } catch (e: Exception) {
            SyncResult.Offline("أوفلاين: الكتب المنهجية")
        }
    }

    // =========================================================================
    // REACTIVE LOCAL DATABASE QUERIES (ROOM -> COMPOSE UI)
    // =========================================================================

    fun getGroups(): Flow<List<GroupItem>> {
        return db.groupDao().getGroups().map { list ->
            list.map {
                GroupItem(
                    id = it.id,
                    name = it.name,
                    type = it.type,
                    description = it.description,
                    icon = it.icon,
                    role = it.role,
                    memberCount = it.memberCount
                )
            }
        }
    }

    fun getMyGroups(): Flow<List<GroupItem>> {
        return db.groupDao().getMyGroups().map { list ->
            list.map {
                GroupItem(
                    id = it.id,
                    name = it.name,
                    type = it.type,
                    description = it.description,
                    icon = it.icon,
                    role = it.role,
                    memberCount = it.memberCount
                )
            }
        }
    }

    fun getDiscoverGroups(): Flow<List<GroupItem>> {
        return db.groupDao().getDiscoverGroups().map { list ->
            list.map {
                GroupItem(
                    id = it.id,
                    name = it.name,
                    type = it.type,
                    description = it.description,
                    icon = it.icon,
                    role = it.role,
                    memberCount = it.memberCount
                )
            }
        }
    }

    suspend fun joinGroup(groupId: String): Result<GenericResponse> = withContext(Dispatchers.IO) {
        try {
            val res = api.joinGroupRequest(groupId)
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                syncGroups()
                Result.success(body)
            } else {
                val errorMsg = res.body()?.error?.message ?: res.body()?.message ?: "تعذر إتمام طلب الانضمام"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // BOOK READING PERSISTENCE & ANNOTATIONS (ROOM LOCAL STORAGE)
    // =========================================================================

    fun getBookReadingState(bookId: String): Flow<BookReadingStateEntity?> {
        return db.bookReadingDao().getReadingState(bookId)
    }

    suspend fun getBookReadingStateSync(bookId: String): BookReadingStateEntity? = withContext(Dispatchers.IO) {
        db.bookReadingDao().getReadingStateSync(bookId)
    }

    suspend fun saveBookReadingState(
        bookId: String,
        lastPage: Int,
        zoom: Float = 1.0f,
        theme: String = "LIGHT",
        totalStudySeconds: Long = 0L,
        uniquePagesCount: Int = 1
    ) = withContext(Dispatchers.IO) {
        db.bookReadingDao().saveReadingState(
            BookReadingStateEntity(
                bookId = bookId,
                lastPage = lastPage,
                zoom = zoom,
                theme = theme,
                totalStudySeconds = totalStudySeconds,
                uniquePagesCount = uniquePagesCount,
                lastReadAt = System.currentTimeMillis()
            )
        )
    }

    fun getBookBookmarks(bookId: String): Flow<List<BookBookmarkEntity>> {
        return db.bookReadingDao().getBookmarks(bookId)
    }

    suspend fun addBookmark(bookId: String, pageIndex: Int, title: String) = withContext(Dispatchers.IO) {
        val entity = BookBookmarkEntity(
            id = "bm_${bookId}_${pageIndex}_${System.currentTimeMillis()}",
            bookId = bookId,
            pageIndex = pageIndex,
            title = title
        )
        db.bookReadingDao().insertBookmark(entity)
    }

    suspend fun deleteBookmark(id: String) = withContext(Dispatchers.IO) {
        db.bookReadingDao().deleteBookmark(id)
    }

    fun getBookNotes(bookId: String): Flow<List<BookNoteEntity>> {
        return db.bookReadingDao().getNotes(bookId)
    }

    suspend fun addNote(bookId: String, pageIndex: Int, noteText: String) = withContext(Dispatchers.IO) {
        val entity = BookNoteEntity(
            id = "note_${bookId}_${pageIndex}_${System.currentTimeMillis()}",
            bookId = bookId,
            pageIndex = pageIndex,
            noteText = noteText
        )
        db.bookReadingDao().insertNote(entity)
    }

    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        db.bookReadingDao().deleteNote(id)
    }

    fun getBookVocabulary(bookId: String): Flow<List<BookVocabularyEntity>> {
        return db.bookReadingDao().getVocabulary(bookId)
    }

    suspend fun addVocabulary(bookId: String, word: String, meaning: String, pageIndex: Int) = withContext(Dispatchers.IO) {
        val entity = BookVocabularyEntity(
            id = "vocab_${bookId}_${System.currentTimeMillis()}",
            bookId = bookId,
            word = word,
            meaning = meaning,
            pageIndex = pageIndex
        )
        db.bookReadingDao().insertVocabulary(entity)
    }

    suspend fun deleteVocabulary(id: String) = withContext(Dispatchers.IO) {
        db.bookReadingDao().deleteVocabulary(id)
    }

    fun getBookDrawings(bookId: String): Flow<List<BookDrawingEntity>> {
        return db.bookReadingDao().getDrawings(bookId)
    }

    suspend fun getBookDrawingsSync(bookId: String): List<BookDrawingEntity> = withContext(Dispatchers.IO) {
        db.bookReadingDao().getDrawingsSync(bookId)
    }

    suspend fun saveBookDrawing(bookId: String, pageIndex: Int, strokesJson: String) = withContext(Dispatchers.IO) {
        db.bookReadingDao().saveDrawing(
            BookDrawingEntity(
                id = "${bookId}_${pageIndex}",
                bookId = bookId,
                pageIndex = pageIndex,
                strokesJson = strokesJson
            )
        )
    }

    suspend fun clearBookDrawing(bookId: String, pageIndex: Int) = withContext(Dispatchers.IO) {
        db.bookReadingDao().clearDrawingForPage(bookId, pageIndex)
    }

    fun getScheduleSlots(groupId: String, dayOfWeek: Int): Flow<List<ScheduleSlot>> {
        return db.scheduleDao().getSlotsForDay(groupId, dayOfWeek).map { list ->
            list.map {
                ScheduleSlot(
                    id = it.id,
                    dayOfWeek = it.dayOfWeek,
                    slotOrder = it.slotOrder,
                    subjectId = it.subjectId,
                    subjectName = it.subjectName,
                    subjectIcon = it.subjectIcon,
                    colorHex = it.colorHex,
                    startTime = it.startTime,
                    endTime = it.endTime
                )
            }
        }
    }

    fun getAllScheduleSlots(groupId: String): Flow<List<ScheduleSlot>> {
        return db.scheduleDao().getSlots(groupId).map { list ->
            list.map {
                ScheduleSlot(
                    id = it.id,
                    dayOfWeek = it.dayOfWeek,
                    slotOrder = it.slotOrder,
                    subjectId = it.subjectId,
                    subjectName = it.subjectName,
                    subjectIcon = it.subjectIcon,
                    colorHex = it.colorHex,
                    startTime = it.startTime,
                    endTime = it.endTime
                )
            }
        }
    }

    suspend fun addScheduleSlot(
        groupId: String,
        dayOfWeek: Int,
        slotOrder: Int,
        subjectId: String,
        subjectName: String,
        subjectIcon: String,
        colorHex: String?,
        startTime: String?,
        endTime: String?
    ) = withContext(Dispatchers.IO) {
        val slotId = "slot_${groupId}_${dayOfWeek}_${slotOrder}"
        val entity = ScheduleSlotEntity(
            id = slotId,
            groupId = groupId,
            dayOfWeek = dayOfWeek,
            slotOrder = slotOrder,
            subjectId = subjectId,
            subjectName = subjectName,
            subjectIcon = subjectIcon,
            colorHex = colorHex,
            startTime = startTime,
            endTime = endTime
        )
        db.scheduleDao().insertSlots(listOf(entity))

        try {
            api.createScheduleSlot(
                groupId = groupId,
                req = CreateScheduleSlotRequest(
                    dayOfWeek = dayOfWeek,
                    slotOrder = slotOrder,
                    subjectId = subjectId,
                    subjectName = subjectName,
                    subjectIcon = subjectIcon,
                    colorHex = colorHex,
                    startTime = startTime,
                    endTime = endTime
                )
            )
        } catch (_: Exception) {
            // Also fall back to proposal API if server requires approval workflow
            try {
                api.proposeScheduleChange(
                    ScheduleProposalRequest(
                        groupId = groupId,
                        dayOfWeek = dayOfWeek,
                        slotOrder = slotOrder,
                        oldSubjectId = null,
                        newSubjectId = subjectId,
                        reason = "تحديث الجدول الدراسي"
                    )
                )
            } catch (_: Exception) {}
        }
    }

    suspend fun deleteScheduleSlot(groupId: String, dayOfWeek: Int, slotOrder: Int) = withContext(Dispatchers.IO) {
        db.scheduleDao().deleteSlot(groupId, dayOfWeek, slotOrder)
        try {
            api.deleteScheduleSlot(groupId, dayOfWeek, slotOrder)
        } catch (_: Exception) {}
    }

    suspend fun deleteScheduleSlotById(slotId: String) = withContext(Dispatchers.IO) {
        db.scheduleDao().deleteSlotById(slotId)
    }

    suspend fun proposeScheduleSlotChange(
        groupId: String,
        dayOfWeek: Int,
        slotOrder: Int,
        oldSubjectId: String?,
        newSubjectId: String,
        reason: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.proposeScheduleChange(
                ScheduleProposalRequest(
                    groupId = groupId,
                    dayOfWeek = dayOfWeek,
                    slotOrder = slotOrder,
                    oldSubjectId = oldSubjectId,
                    newSubjectId = newSubjectId,
                    reason = reason
                )
            )
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()?.message ?: "تم إرسال اقتراح التعديل بنجاح")
            } else {
                Result.failure(Exception(res.body()?.message ?: "تعذر إرسال الاقتراح"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitRoleUpgradeRequest(
        requestedRole: String,
        reason: String,
        groupId: String? = null
    ): Result<com.magd.tanweer.data.model.SubmitRoleUpgradeResponse> = withContext(Dispatchers.IO) {
        try {
            val res = api.submitRoleRequest(
                com.magd.tanweer.data.model.SubmitRoleUpgradeRequest(
                    requestedRole = requestedRole,
                    reason = reason,
                    groupId = groupId
                )
            )
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                Result.success(body)
            } else {
                Result.failure(Exception(body?.message ?: "تعذر إرسال طلب الترقية"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun redeemRoleCode(
        code: String,
        requestId: String? = null
    ): Result<com.magd.tanweer.data.model.RedeemRoleCodeResponse> = withContext(Dispatchers.IO) {
        try {
            val res = api.redeemRoleCode(
                com.magd.tanweer.data.model.RedeemRoleCodeRequest(
                    code = code,
                    requestId = requestId
                )
            )
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                val currentUser = db.userDao().getUserSync()
                if (currentUser != null && body.newRole != null) {
                    db.userDao().insertUser(currentUser.copy(role = body.newRole))
                }
                Result.success(body)
            } else {
                Result.failure(Exception(body?.message ?: "رمز التفعيل غير صالح"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyRoleRequests(): List<com.magd.tanweer.data.model.RoleRequestItem> = withContext(Dispatchers.IO) {
        try {
            val res = api.getMyRoleRequests()
            if (res.isSuccessful) {
                res.body()?.requests ?: emptyList()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getScheduleMetaAndSlots(groupId: String): Result<com.magd.tanweer.data.model.ScheduleResponse> = withContext(Dispatchers.IO) {
        try {
            val res = api.getSchedule(groupId)
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                val slots = body.slots.map {
                    ScheduleSlotEntity(
                        id = it.id,
                        groupId = groupId,
                        dayOfWeek = it.dayOfWeek,
                        slotOrder = it.slotOrder,
                        subjectId = it.subjectId,
                        subjectName = it.subjectName,
                        subjectIcon = it.subjectIcon,
                        colorHex = it.colorHex,
                        startTime = it.startTime,
                        endTime = it.endTime
                    )
                }
                db.scheduleDao().clearSlots(groupId)
                if (slots.isNotEmpty()) {
                    db.scheduleDao().insertSlots(slots)
                }
                Result.success(body)
            } else {
                Result.failure(Exception("تعذر جلب بيانات الجدول"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveScheduleBatch(groupId: String, slots: List<ScheduleSlot>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.saveScheduleBatch(groupId, com.magd.tanweer.data.model.BatchScheduleRequest(slots))
            if (res.isSuccessful && res.body()?.success == true) {
                syncSchedule(groupId)
                Result.success(res.body()?.message ?: "تم حفظ وتحديث الجدول بنجاح")
            } else {
                Result.failure(Exception(res.body()?.message ?: "تعذر حفظ الجدول"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getScheduleProposals(groupId: String): Result<List<com.magd.tanweer.data.model.ScheduleProposalItem>> = withContext(Dispatchers.IO) {
        try {
            val res = api.getScheduleProposals(groupId)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!.proposals)
            } else {
                Result.failure(Exception("تعذر جلب مقترحات الجدول"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun voteScheduleProposal(proposalId: String, voteType: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.voteScheduleProposal(proposalId, com.magd.tanweer.data.model.VoteProposalRequest(voteType))
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()?.message ?: "تم تسجيل التصويت بنجاح")
            } else {
                Result.failure(Exception(res.body()?.message ?: "تعذر التصويت على المقترح"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun approveScheduleProposal(proposalId: String, groupId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.approveScheduleProposal(proposalId)
            if (res.isSuccessful && res.body()?.success == true) {
                syncSchedule(groupId)
                Result.success(res.body()?.message ?: "تم اعتماد وتطبيق المقترح بنجاح")
            } else {
                Result.failure(Exception(res.body()?.message ?: "تعذر اعتماد المقترح"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectScheduleProposal(proposalId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.rejectScheduleProposal(proposalId)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()?.message ?: "تم رفض المقترح")
            } else {
                Result.failure(Exception(res.body()?.message ?: "تعذر رفض المقترح"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroupMembers(groupId: String): Result<com.magd.tanweer.data.model.GroupMembersResponse> = withContext(Dispatchers.IO) {
        try {
            val res = api.getGroupMembers(groupId)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("تعذر جلب أعضاء المجموعة"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGroupMemberRole(groupId: String, targetUserId: String, newRole: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.updateGroupMemberRole(groupId, targetUserId, com.magd.tanweer.data.model.UpdateMemberRoleRequest(newRole))
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()?.message ?: "تم تحديث رتبة العضو بنجاح")
            } else {
                Result.failure(Exception(res.body()?.message ?: "تعذر تحديث رتبة العضو"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeGroupMember(groupId: String, targetUserId: String, isBan: Boolean): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = if (isBan) api.banGroupMember(groupId, targetUserId) else api.removeGroupMember(groupId, targetUserId)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()?.message ?: if (isBan) "تم حظر العضو بنجاح" else "تمت إزالة العضو بنجاح")
            } else {
                Result.failure(Exception(res.body()?.message ?: "تعذر تنفيذ الإجراء"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun approveGroupJoinRequest(groupId: String, targetUserId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.approveGroupJoinRequest(groupId, targetUserId)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()?.message ?: "تم قبول العضو واعتماده بنجاح")
            } else {
                Result.failure(Exception(res.body()?.message ?: "تعذر اعتماد طلب الانضمام"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitContentCorrection(
        contentId: String,
        fieldName: String,
        originalValue: String?,
        proposedValue: String,
        reason: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.submitCorrection(
                contentId,
                SubmitCorrectionRequest(
                    fieldName = fieldName,
                    originalValue = originalValue ?: "",
                    proposedValue = proposedValue,
                    reason = reason
                )
            )
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()?.message ?: "تم إرسال طلب التصحيح بنجاح")
            } else {
                Result.failure(Exception(res.body()?.message ?: "تعذر إرسال التصحيح"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroupCorrections(groupId: String): Result<List<com.magd.tanweer.data.model.ContentCorrectionItem>> = withContext(Dispatchers.IO) {
        try {
            val res = api.getGroupCorrections(groupId)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!.corrections)
            } else {
                Result.failure(Exception("تعذر جلب طلبات التصحيح"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun approveCorrection(correctionId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.approveCorrection(correctionId)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()?.message ?: "تم اعتماد التصحيح بنجاح")
            } else {
                Result.failure(Exception(res.body()?.message ?: "تعذر اعتماد التصحيح"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectCorrection(correctionId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.rejectCorrection(correctionId)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()?.message ?: "تم رفض طلب التصحيح")
            } else {
                Result.failure(Exception(res.body()?.message ?: "تعذر رفض التصحيح"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCommunityDecisions(groupId: String): Result<List<com.magd.tanweer.data.model.CommunityDecisionItem>> = withContext(Dispatchers.IO) {
        try {
            val res = api.getCommunityDecisions(groupId)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!.decisions)
            } else {
                Result.failure(Exception("تعذر جلب القرارات والتصويتات الجماعية"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun voteCommunityDecision(decisionId: String, voteChoice: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.voteCommunityDecision(decisionId, com.magd.tanweer.data.model.VoteDecisionRequest(voteChoice))
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()?.message ?: "تم تسجيل التصويت بنجاح")
            } else {
                Result.failure(Exception(res.body()?.message ?: "تعذر تسجيل التصويت"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTeacherDashboard(): Result<com.magd.tanweer.data.model.TeacherDashboardResponse> = withContext(Dispatchers.IO) {
        try {
            val res = api.getTeacherDashboard()
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("تعذر جلب بيانات مركز المعلم"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getModeratorDashboard(): Result<com.magd.tanweer.data.model.ModeratorDashboardResponse> = withContext(Dispatchers.IO) {
        try {
            val res = api.getModeratorDashboard()
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("تعذر جلب بيانات مركز الإشراف"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAdminDashboard(): Result<com.magd.tanweer.data.model.AdminDashboardResponse> = withContext(Dispatchers.IO) {
        try {
            val res = api.getAdminDashboard()
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("تعذر جلب بيانات إدارة المدرسة"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val recentMessageIds = java.util.Collections.synchronizedSet(java.util.LinkedHashSet<String>())

    private fun parseStringListJson(jsonStr: String?): List<String> {
        if (jsonStr.isNullOrBlank() || jsonStr == "[]") return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val s = array.optString(i)
                if (!s.isNullOrBlank()) list.add(s)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeStringList(list: List<String>): String {
        if (list.isEmpty()) return "[]"
        val array = JSONArray()
        list.forEach { if (it.isNotBlank()) array.put(it) }
        return array.toString()
    }

    private fun parseMediaJson(jsonStr: String?): List<MediaItem> {
        if (jsonStr.isNullOrBlank() || jsonStr == "[]") return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<MediaItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                if (obj != null) {
                    list.add(
                        MediaItem(
                            id = obj.optString("id", "media_$i"),
                            pageOrder = obj.optInt("pageOrder", i + 1),
                            url = obj.optString("url", ""),
                            mimeType = obj.optString("mimeType", "image/jpeg"),
                            fileSize = obj.optInt("fileSize", 0)
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeMediaList(media: List<MediaItem>): String {
        if (media.isEmpty()) return "[]"
        val array = JSONArray()
        for (m in media) {
            val obj = JSONObject().apply {
                put("id", m.id)
                put("pageOrder", m.pageOrder)
                put("url", m.url)
                put("mimeType", m.mimeType)
                put("fileSize", m.fileSize)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun getDayContents(groupId: String, date: String): Flow<List<ContentItem>> {
        return db.contentDao().getContentsByDate(groupId, date).map { list ->
            list.map {
                ContentItem(
                    id = it.id,
                    groupId = it.groupId,
                    studyDate = it.studyDate,
                    subjectId = it.subjectId,
                    type = it.type,
                    title = it.title,
                    description = it.description,
                    authorName = it.authorName,
                    authorGradeSection = it.authorGradeSection,
                    viewsCount = it.viewsCount,
                    usefulCount = it.usefulCount,
                    createdAt = it.createdAt,
                    subjectName = it.subjectName,
                    subjectIcon = it.subjectIcon,
                    colorHex = it.colorHex,
                    media = parseMediaJson(it.mediaUrlsJson),
                    syncStatus = it.syncStatus
                )
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getHomeworks(groupId: String): Flow<List<HomeworkItem>> {
        return db.userDao().getUser().flatMapLatest { user ->
            val userId = user?.id ?: ""
            combine(
                db.homeworkDao().getHomeworks(groupId),
                db.homeworkDao().getCompletionsForUser(userId)
            ) { hwList: List<HomeworkEntity>, completions: List<HomeworkCompletionEntity> ->
                val completedIds = completions.map { it.homeworkId }.toSet()
                hwList.map {
                    HomeworkItem(
                        id = it.id,
                        groupId = it.groupId,
                        studyDate = it.studyDate,
                        dueDate = it.dueDate,
                        subjectId = it.subjectId,
                        title = it.title,
                        details = it.details,
                        pageNumbers = it.pageNumbers,
                        questionNumbers = it.questionNumbers,
                        taskType = it.taskType,
                        subjectName = it.subjectName,
                        subjectIcon = it.subjectIcon,
                        colorHex = it.colorHex,
                        mediaUrls = parseStringListJson(it.mediaUrlsJson),
                        isCompleted = completedIds.contains(it.id),
                        createdAt = it.createdAt,
                        syncStatus = it.syncStatus
                    )
                }
            }
        }
    }

    fun getExams(groupId: String): Flow<List<ExamItem>> {
        return db.examDao().getExams(groupId).map { list ->
            list.map {
                ExamItem(
                    id = it.id,
                    groupId = it.groupId,
                    examDate = it.examDate,
                    subjectId = it.subjectId,
                    title = it.title,
                    requiredChapters = it.requiredChapters,
                    notes = it.notes,
                    subjectName = it.subjectName,
                    subjectIcon = it.subjectIcon,
                    colorHex = it.colorHex,
                    syncStatus = it.syncStatus
                )
            }
        }
    }

    fun getEvents(groupId: String): Flow<List<SchoolEventItem>> {
        return db.eventDao().getEvents(groupId).map { list ->
            list.map {
                SchoolEventItem(
                    id = it.id,
                    groupId = it.groupId,
                    eventDate = it.eventDate,
                    timeStr = it.timeStr,
                    title = it.title,
                    description = it.description,
                    category = it.category,
                    location = it.location,
                    createdAt = it.createdAt,
                    syncStatus = it.syncStatus
                )
            }
        }
    }

    fun getIssues(groupId: String): Flow<List<IssueItem>> {
        return db.issueDao().getIssues(groupId).map { list ->
            list.map {
                IssueItem(
                    id = it.id,
                    groupId = it.groupId,
                    subjectId = it.subjectId,
                    homeworkId = it.homeworkId,
                    examId = it.examId,
                    title = it.title,
                    description = it.description,
                    status = it.status,
                    bestCommentId = it.bestCommentId,
                    authorName = it.authorName,
                    subjectName = it.subjectName,
                    subjectIcon = it.subjectIcon,
                    commentsCount = it.commentsCount,
                    createdAt = it.createdAt,
                    syncStatus = it.syncStatus
                )
            }
        }
    }

    fun getIssue(issueId: String): Flow<IssueItem?> {
        return db.issueDao().getIssue(issueId).map { entity ->
            entity?.let {
                IssueItem(
                    id = it.id,
                    groupId = it.groupId,
                    subjectId = it.subjectId,
                    homeworkId = it.homeworkId,
                    examId = it.examId,
                    title = it.title,
                    description = it.description,
                    status = it.status,
                    bestCommentId = it.bestCommentId,
                    authorName = it.authorName,
                    subjectName = it.subjectName,
                    subjectIcon = it.subjectIcon,
                    commentsCount = it.commentsCount,
                    createdAt = it.createdAt,
                    syncStatus = it.syncStatus
                )
            }
        }
    }

    fun getIssueComments(issueId: String): Flow<List<IssueCommentItem>> {
        return db.issueDao().getComments(issueId).map { list ->
            list.map {
                IssueCommentItem(
                    id = it.id,
                    issueId = it.issueId,
                    userId = it.userId,
                    authorName = it.authorName,
                    comment = it.comment,
                    isBestAnswer = it.isBestAnswer,
                    createdAt = it.createdAt,
                    syncStatus = it.syncStatus
                )
            }
        }
    }

    fun getBooks(gradeId: Int): Flow<List<BookItem>> {
        return db.bookDao().getBooks(gradeId).map { list ->
            list.map {
                BookItem(
                    id = it.id,
                    gradeId = it.gradeId,
                    subjectId = it.subjectId,
                    title = it.title,
                    edition = it.edition,
                    fileSizeMb = it.fileSizeMb,
                    fileUrl = it.fileUrl,
                    thumbnailUrl = it.thumbnailUrl,
                    subjectName = it.subjectName,
                    subjectIcon = it.subjectIcon
                )
            }
        }
    }

    fun getAllBooks(): Flow<List<BookItem>> {
        return db.bookDao().getAllBooks().map { list ->
            list.map {
                BookItem(
                    id = it.id,
                    gradeId = it.gradeId,
                    subjectId = it.subjectId,
                    title = it.title,
                    edition = it.edition,
                    fileSizeMb = it.fileSizeMb,
                    fileUrl = it.fileUrl,
                    thumbnailUrl = it.thumbnailUrl,
                    subjectName = it.subjectName,
                    subjectIcon = it.subjectIcon
                )
            }
        }
    }

    // Chat with real WebSocket + Server REST API
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getChatMessages(groupId: String): Flow<List<ChatMessageItem>> {
        return db.userDao().getUser().flatMapLatest { user ->
            val currentUserId = user?.id ?: ""
            db.chatDao().getMessages(groupId).map { list ->
                list.map {
                    ChatMessageItem(
                        id = it.id,
                        groupId = it.groupId,
                        senderId = it.senderId,
                        senderName = it.senderName,
                        senderGradeSection = it.senderGradeSection,
                        text = it.text,
                        timestamp = it.timestamp,
                        isMe = (it.senderId == currentUserId),
                        status = it.status
                    )
                }
            }
        }
    }

    suspend fun connectToGroupChat(groupId: String, myUserId: String) = withContext(Dispatchers.IO) {
        try {
            val res = api.getGroupMessages(groupId)
            if (res.isSuccessful && res.body()?.messages != null) {
                val msgs = res.body()!!.messages.map {
                    ChatMessageEntity(
                        id = it.id,
                        groupId = it.groupId,
                        senderId = it.senderId,
                        senderName = it.senderName,
                        senderGradeSection = it.senderGradeSection,
                        text = it.text,
                        timestamp = it.timestamp,
                        status = "SENT"
                    )
                }
                db.chatDao().insertMessages(msgs)
            }
        } catch (_: Exception) {}

        chatManager.connect(groupId) { json ->
            try {
                val type = json.optString("type", "")
                if (type == "ack") {
                    val ackId = json.optString("id", "")
                    if (ackId.isNotBlank()) {
                        recentMessageIds.add(ackId)
                        CoroutineScope(Dispatchers.IO).launch {
                            db.chatDao().updateMessageStatus(ackId, "SENT")
                        }
                    }
                    return@connect
                }

                if (type == "message_error") {
                    val errId = json.optString("id", "")
                    if (errId.isNotBlank()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            db.chatDao().updateMessageStatus(errId, "FAILED")
                        }
                    }
                    return@connect
                }

                val id = json.optString("id", "")
                val senderId = json.optString("senderId", "")
                val senderName = json.optString("senderName", "")
                val senderGradeSection = json.optString("senderGradeSection", "")
                val text = json.optString("text", "")
                val timestamp = json.optLong("timestamp", System.currentTimeMillis())

                if (text.isNotBlank() && id.isNotBlank()) {
                    if (senderId == myUserId) {
                        // My own message echo from server broadcast: confirmed saved and published
                        recentMessageIds.add(id)
                        CoroutineScope(Dispatchers.IO).launch {
                            db.chatDao().updateMessageStatus(id, "SENT")
                        }
                    } else {
                        // Message from another member: insert if not already received
                        if (recentMessageIds.add(id)) {
                            val entity = ChatMessageEntity(
                                id = id,
                                groupId = groupId,
                                senderId = senderId,
                                senderName = senderName,
                                senderGradeSection = senderGradeSection,
                                text = text,
                                timestamp = timestamp,
                                status = "SENT"
                            )
                            CoroutineScope(Dispatchers.IO).launch {
                                db.chatDao().insertMessage(entity)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TanweerRepository", "Error handling incoming chat WS frame", e)
            }
        }
    }

    private var lastSentMessageSignature: String? = null
    private var lastSentMessageTime: Long = 0L

    suspend fun sendChatMessage(
        groupId: String,
        senderId: String,
        senderName: String,
        senderGradeSection: String,
        text: String
    ) = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@withContext

        // Debounce exact duplicate sends within 1.5s
        val now = System.currentTimeMillis()
        val sig = "$groupId|$senderId|$cleanText"
        if (sig == lastSentMessageSignature && (now - lastSentMessageTime < 1500L)) {
            return@withContext
        }
        lastSentMessageSignature = sig
        lastSentMessageTime = now

        val msgId = "msg_${now}_${(1000..9999).random()}"
        recentMessageIds.add(msgId)

        val localMsg = ChatMessageEntity(
            id = msgId,
            groupId = groupId,
            senderId = senderId,
            senderName = senderName,
            senderGradeSection = senderGradeSection,
            text = cleanText,
            timestamp = now,
            status = "SENDING"
        )
        db.chatDao().insertMessage(localMsg)

        val json = JSONObject().apply {
            put("id", msgId)
            put("groupId", groupId)
            put("senderId", senderId)
            put("senderName", senderName)
            put("senderGradeSection", senderGradeSection)
            put("text", cleanText)
            put("timestamp", now)
        }
        val wsSent = chatManager.sendMessage(json)

        if (!wsSent) {
            // Fallback to HTTP REST endpoint only if WebSocket is disconnected
            try {
                val res = api.postGroupMessage(groupId, PostMessageRequest(id = msgId, text = cleanText))
                if (res.isSuccessful) {
                    db.chatDao().updateMessageStatus(msgId, "SENT")
                } else {
                    db.chatDao().updateMessageStatus(msgId, "FAILED")
                }
            } catch (_: Exception) {
                db.chatDao().updateMessageStatus(msgId, "FAILED")
            }
        }
        // If wsSent is true, message status remains SENDING until confirmed by server ACK/echo
    }

    suspend fun retryChatMessage(msg: ChatMessageItem) = withContext(Dispatchers.IO) {
        db.chatDao().updateMessageStatus(msg.id, "SENDING")
        val json = JSONObject().apply {
            put("id", msg.id)
            put("groupId", msg.groupId)
            put("senderId", msg.senderId)
            put("senderName", msg.senderName)
            put("senderGradeSection", msg.senderGradeSection)
            put("text", msg.text)
            put("timestamp", msg.timestamp)
        }
        val wsSent = chatManager.sendMessage(json)
        if (!wsSent) {
            try {
                val res = api.postGroupMessage(msg.groupId, PostMessageRequest(id = msg.id, text = msg.text))
                if (res.isSuccessful) {
                    db.chatDao().updateMessageStatus(msg.id, "SENT")
                } else {
                    db.chatDao().updateMessageStatus(msg.id, "FAILED")
                }
            } catch (_: Exception) {
                db.chatDao().updateMessageStatus(msg.id, "FAILED")
            }
        }
        // If wsSent is true, message status remains SENDING until server ACK/echo confirms persistence
    }

    suspend fun addLesson(
        groupId: String,
        date: String,
        subjectId: String,
        title: String,
        description: String?,
        authorName: String,
        authorGradeSection: String
    ) = addLessonWithPages(
        groupId = groupId,
        date = date,
        subjectId = subjectId,
        title = title,
        description = description,
        authorName = authorName,
        authorGradeSection = authorGradeSection,
        pages = emptyList()
    )

    suspend fun addLessonWithPages(
        groupId: String,
        date: String,
        subjectId: String,
        title: String,
        description: String?,
        authorName: String,
        authorGradeSection: String,
        pages: List<ProcessedPageResult>
    ): Result<String> = withContext(Dispatchers.IO) {
        val user = db.userDao().getUserSync()
        val gradeSubjects = SchoolHierarchy.getSubjectsForGrade(user?.gradeId ?: 10)
        val subject = gradeSubjects.find { it.id == subjectId }
            ?: DefaultSubjects.find { it.id == subjectId }

        val id = "cnt_${System.currentTimeMillis()}"

        // 1. Initial optimistic media list with local paths
        val initialMediaItems = pages.mapIndexed { index, page ->
            MediaItem(
                id = "media_${System.currentTimeMillis()}_$index",
                pageOrder = index + 1,
                url = page.file.absolutePath,
                mimeType = "image/jpeg",
                fileSize = page.sizeBytes.toInt()
            )
        }

        val entity = ContentEntity(
            id = id,
            groupId = groupId,
            studyDate = date,
            subjectId = subjectId,
            type = "LESSON",
            title = title,
            description = description,
            authorName = authorName,
            authorGradeSection = authorGradeSection,
            viewsCount = 1,
            usefulCount = 0,
            createdAt = System.currentTimeMillis(),
            subjectName = subject?.name ?: subjectId,
            subjectIcon = subject?.icon ?: "📚",
            colorHex = subject?.colorHex ?: "#00E5FF",
            mediaUrlsJson = serializeMediaList(initialMediaItems),
            syncStatus = "SYNCING"
        )
        db.contentDao().insertContent(entity)

        // 2. Upload actual images to server via multipart
        val uploadedMediaItems = mutableListOf<MediaItem>()
        var anyUploadFailed = false

        for ((index, page) in pages.withIndex()) {
            var remoteUrl: String? = null
            try {
                if (page.file.exists() && page.file.length() > 0) {
                    val reqFile = page.file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("file", "page_${index + 1}_${page.file.name}", reqFile)
                    val uploadRes = api.uploadMedia(part)
                    if (uploadRes.isSuccessful && uploadRes.body() != null) {
                        val body = uploadRes.body()!!
                        remoteUrl = body.url ?: body.relativeUrl?.let { rel ->
                            if (rel.startsWith("http")) rel else "https://tanweer.magd.workers.dev/${rel.removePrefix("/")}"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TanweerRepository", "Upload failed for page $index: ${e.message}")
            }

            if (remoteUrl != null) {
                uploadedMediaItems.add(
                    MediaItem(
                        id = "media_${System.currentTimeMillis()}_$index",
                        pageOrder = index + 1,
                        url = remoteUrl,
                        mimeType = "image/jpeg",
                        fileSize = page.sizeBytes.toInt()
                    )
                )
            } else {
                anyUploadFailed = true
                uploadedMediaItems.add(
                    MediaItem(
                        id = "media_${System.currentTimeMillis()}_$index",
                        pageOrder = index + 1,
                        url = page.file.absolutePath,
                        mimeType = "image/jpeg",
                        fileSize = page.sizeBytes.toInt()
                    )
                )
            }
        }

        // If any image upload failed, NEVER send phone local paths to the server!
        if (anyUploadFailed) {
            db.contentDao().updateMediaAndSyncStatus(id, serializeMediaList(uploadedMediaItems), "FAILED")
            return@withContext Result.failure(Exception("تعذر رفع بعض صفحات الدرس إلى الخادم. تم حفظ الدرس محلياً ويمكنك إعادة المحاولة لاحقاً."))
        }

        // 3. Post lesson content with remote URLs
        try {
            val res = api.createContent(
                CreateContentRequest(
                    groupId = groupId,
                    studyDate = date,
                    subjectId = subjectId,
                    title = title,
                    description = description,
                    media = uploadedMediaItems
                )
            )
            if (res.isSuccessful) {
                val body = res.body()
                if (body?.isDuplicate == true && body.contentId != null && body.contentId != id) {
                    // Server detected a duplicate and linked it to an existing lesson
                    // Remove local ghost duplicate draft and refresh real server content
                    db.contentDao().deleteContentById(id)
                    syncDay(groupId, date)
                } else {
                    db.contentDao().updateMediaAndSyncStatus(id, serializeMediaList(uploadedMediaItems), "SYNCED")
                }
                // Cleanup temporary pending files on success
                pages.forEach { try { it.file.delete() } catch (_: Exception) {} }
                Result.success(id)
            } else {
                db.contentDao().updateMediaAndSyncStatus(id, serializeMediaList(uploadedMediaItems), "FAILED")
                Result.failure(Exception(res.message() ?: "فشل تسجيل الدرس على السيرفر"))
            }
        } catch (e: Exception) {
            db.contentDao().updateMediaAndSyncStatus(id, serializeMediaList(uploadedMediaItems), "FAILED")
            Result.failure(e)
        }
    }

    suspend fun retrySyncContent(contentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val content = db.contentDao().getContentById(contentId)
            ?: return@withContext Result.failure(Exception("الدرس غير موجود"))

        db.contentDao().updateSyncStatus(contentId, "SYNCING")
        val mediaItems = parseMediaJson(content.mediaUrlsJson)
        val updatedMediaItems = mutableListOf<MediaItem>()
        var uploadFailed = false

        for (item in mediaItems) {
            if (item.url.startsWith("http://") || item.url.startsWith("https://")) {
                updatedMediaItems.add(item)
            } else {
                val file = java.io.File(item.url)
                if (file.exists() && file.length() > 0) {
                    try {
                        val reqFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        val part = MultipartBody.Part.createFormData("file", "retry_${file.name}", reqFile)
                        val uploadRes = api.uploadMedia(part)
                        if (uploadRes.isSuccessful && uploadRes.body() != null) {
                            val body = uploadRes.body()!!
                            val remoteUrl = body.url ?: body.relativeUrl?.let { rel ->
                                if (rel.startsWith("http")) rel else "https://tanweer.magd.workers.dev/${rel.removePrefix("/")}"
                            }
                            if (remoteUrl != null) {
                                updatedMediaItems.add(item.copy(url = remoteUrl))
                            } else {
                                uploadFailed = true
                                updatedMediaItems.add(item)
                            }
                        } else {
                            uploadFailed = true
                            updatedMediaItems.add(item)
                        }
                    } catch (e: Exception) {
                        Log.e("TanweerRepository", "Retry upload failed for ${file.name}", e)
                        uploadFailed = true
                        updatedMediaItems.add(item)
                    }
                } else {
                    uploadFailed = true
                    updatedMediaItems.add(item)
                }
            }
        }

        db.contentDao().updateMediaAndSyncStatus(contentId, serializeMediaList(updatedMediaItems), if (uploadFailed) "FAILED" else "SYNCING")

        if (uploadFailed) {
            return@withContext Result.failure(Exception("تعذر رفع بعض صفحات الدرس. تأكد من اتصال الإنترنت وحاول مجدداً."))
        }

        try {
            val res = api.createContent(
                CreateContentRequest(
                    groupId = content.groupId,
                    studyDate = content.studyDate,
                    subjectId = content.subjectId,
                    title = content.title,
                    description = content.description,
                    media = updatedMediaItems
                )
            )
            if (res.isSuccessful) {
                val body = res.body()
                if (body?.isDuplicate == true && body.contentId != null && body.contentId != contentId) {
                    db.contentDao().deleteContentById(contentId)
                    syncDay(content.groupId, content.studyDate)
                } else {
                    db.contentDao().updateMediaAndSyncStatus(contentId, serializeMediaList(updatedMediaItems), "SYNCED")
                }
                for (item in mediaItems) {
                    if (!item.url.startsWith("http")) {
                        try { java.io.File(item.url).delete() } catch (_: Exception) {}
                    }
                }
                Result.success(Unit)
            } else {
                db.contentDao().updateSyncStatus(contentId, "FAILED")
                Result.failure(Exception(res.message() ?: "فشل مزامنة الدرس مع السيرفر"))
            }
        } catch (e: Exception) {
            db.contentDao().updateSyncStatus(contentId, "FAILED")
            Result.failure(e)
        }
    }

    suspend fun addHomework(
        groupId: String,
        studyDate: String,
        dueDate: String,
        subjectId: String,
        title: String,
        details: String?,
        pageNumbers: String?,
        questionNumbers: String?,
        taskType: String = "HOMEWORK",
        mediaUrls: List<String> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val user = db.userDao().getUserSync()
        val gradeSubjects = SchoolHierarchy.getSubjectsForGrade(user?.gradeId ?: 10)
        val subject = gradeSubjects.find { it.id == subjectId }
            ?: DefaultSubjects.find { it.id == subjectId }
        val id = "hw_${System.currentTimeMillis()}"
        val entity = HomeworkEntity(
            id = id,
            groupId = groupId,
            studyDate = studyDate,
            dueDate = dueDate,
            subjectId = subjectId,
            title = title,
            details = details,
            pageNumbers = pageNumbers,
            questionNumbers = questionNumbers,
            taskType = taskType,
            subjectName = subject?.name ?: subjectId,
            subjectIcon = subject?.icon ?: "📝",
            colorHex = subject?.colorHex ?: "#00E5FF",
            mediaUrlsJson = serializeStringList(mediaUrls),
            createdAt = System.currentTimeMillis(),
            syncStatus = "SYNCING"
        )
        db.homeworkDao().insertHomework(entity)

        try {
            val res = api.createHomework(
                CreateHomeworkRequest(
                    groupId = groupId,
                    studyDate = studyDate,
                    dueDate = dueDate,
                    subjectId = subjectId,
                    title = title,
                    details = details,
                    pageNumbers = pageNumbers,
                    questionNumbers = questionNumbers,
                    taskType = taskType,
                    mediaUrls = mediaUrls
                )
            )
            if (res.isSuccessful) {
                db.homeworkDao().updateSyncStatus(id, "SYNCED")
                Result.success(id)
            } else {
                db.homeworkDao().updateSyncStatus(id, "FAILED")
                Result.failure(Exception(res.message() ?: "فشل حفظ الواجب في السيرفر"))
            }
        } catch (e: Exception) {
            db.homeworkDao().updateSyncStatus(id, "FAILED")
            Result.failure(e)
        }
    }

    suspend fun addHomeworkWithImages(
        groupId: String,
        studyDate: String,
        dueDate: String,
        subjectId: String,
        title: String,
        details: String?,
        pageNumbers: String?,
        questionNumbers: String?,
        taskType: String = "HOMEWORK",
        pages: List<ProcessedPageResult> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val user = db.userDao().getUserSync()
        val gradeSubjects = SchoolHierarchy.getSubjectsForGrade(user?.gradeId ?: 10)
        val subject = gradeSubjects.find { it.id == subjectId }
            ?: DefaultSubjects.find { it.id == subjectId }
        val id = "hw_${System.currentTimeMillis()}"

        val localUrls = pages.map { it.file.absolutePath }
        val entity = HomeworkEntity(
            id = id,
            groupId = groupId,
            studyDate = studyDate,
            dueDate = dueDate,
            subjectId = subjectId,
            title = title,
            details = details,
            pageNumbers = pageNumbers,
            questionNumbers = questionNumbers,
            taskType = taskType,
            subjectName = subject?.name ?: subjectId,
            subjectIcon = subject?.icon ?: "📝",
            colorHex = subject?.colorHex ?: "#00E5FF",
            mediaUrlsJson = serializeStringList(localUrls),
            createdAt = System.currentTimeMillis(),
            syncStatus = "SYNCING"
        )
        db.homeworkDao().insertHomework(entity)

        val uploadedUrls = mutableListOf<String>()
        var anyUploadFailed = false

        for ((index, page) in pages.withIndex()) {
            var remoteUrl: String? = null
            try {
                if (page.file.exists() && page.file.length() > 0) {
                    val reqFile = page.file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("file", "hw_${index + 1}_${page.file.name}", reqFile)
                    val uploadRes = api.uploadMedia(part)
                    if (uploadRes.isSuccessful && uploadRes.body() != null) {
                        val body = uploadRes.body()!!
                        remoteUrl = body.url ?: body.relativeUrl?.let { rel ->
                            if (rel.startsWith("http")) rel else "https://tanweer.magd.workers.dev/${rel.removePrefix("/")}"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TanweerRepository", "Upload failed for homework page $index: ${e.message}")
            }

            if (remoteUrl != null) {
                uploadedUrls.add(remoteUrl)
            } else {
                uploadedUrls.add(page.file.absolutePath)
                anyUploadFailed = true
            }
        }

        // If any image upload failed, NEVER send phone local paths to the server!
        if (anyUploadFailed) {
            val updatedEntity = entity.copy(
                mediaUrlsJson = serializeStringList(uploadedUrls),
                syncStatus = "FAILED"
            )
            db.homeworkDao().insertHomework(updatedEntity)
            return@withContext Result.failure(Exception("تعذر رفع بعض صور الواجب إلى الخادم. تم حفظ الواجب محلياً ويمكنك إعادة المحاولة لاحقاً."))
        }

        try {
            val res = api.createHomework(
                CreateHomeworkRequest(
                    groupId = groupId,
                    studyDate = studyDate,
                    dueDate = dueDate,
                    subjectId = subjectId,
                    title = title,
                    details = details,
                    pageNumbers = pageNumbers,
                    questionNumbers = questionNumbers,
                    taskType = taskType,
                    mediaUrls = uploadedUrls
                )
            )
            if (res.isSuccessful) {
                val updatedEntity = entity.copy(
                    mediaUrlsJson = serializeStringList(uploadedUrls),
                    syncStatus = "SYNCED"
                )
                db.homeworkDao().insertHomework(updatedEntity)
                pages.forEach { try { it.file.delete() } catch (_: Exception) {} }
                Result.success(id)
            } else {
                val updatedEntity = entity.copy(
                    mediaUrlsJson = serializeStringList(uploadedUrls),
                    syncStatus = "FAILED"
                )
                db.homeworkDao().insertHomework(updatedEntity)
                Result.failure(Exception("فشل إرسال الواجب للسيرفر"))
            }
        } catch (e: Exception) {
            val updatedEntity = entity.copy(
                mediaUrlsJson = serializeStringList(uploadedUrls),
                syncStatus = "FAILED"
            )
            db.homeworkDao().insertHomework(updatedEntity)
            Result.failure(e)
        }
    }

    suspend fun retrySyncHomework(homeworkId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val hw = db.homeworkDao().getHomeworkById(homeworkId)
            ?: return@withContext Result.failure(Exception("الواجب غير موجود"))
        db.homeworkDao().updateSyncStatus(homeworkId, "SYNCING")

        val currentUrls = parseStringListJson(hw.mediaUrlsJson)
        val finalRemoteUrls = mutableListOf<String>()
        var uploadFailed = false

        for (url in currentUrls) {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                finalRemoteUrls.add(url)
            } else {
                val file = java.io.File(url)
                if (file.exists() && file.length() > 0) {
                    try {
                        val reqFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        val part = MultipartBody.Part.createFormData("file", "retry_hw_${file.name}", reqFile)
                        val uploadRes = api.uploadMedia(part)
                        if (uploadRes.isSuccessful && uploadRes.body() != null) {
                            val body = uploadRes.body()!!
                            val remoteUrl = body.url ?: body.relativeUrl?.let { rel ->
                                if (rel.startsWith("http")) rel else "https://tanweer.magd.workers.dev/${rel.removePrefix("/")}"
                            }
                            if (remoteUrl != null) {
                                finalRemoteUrls.add(remoteUrl)
                            } else {
                                uploadFailed = true
                                finalRemoteUrls.add(url)
                            }
                        } else {
                            uploadFailed = true
                            finalRemoteUrls.add(url)
                        }
                    } catch (e: Exception) {
                        Log.e("TanweerRepository", "Retry hw upload failed for ${file.name}", e)
                        uploadFailed = true
                        finalRemoteUrls.add(url)
                    }
                } else {
                    uploadFailed = true
                    finalRemoteUrls.add(url)
                }
            }
        }

        db.homeworkDao().insertHomework(
            hw.copy(
                mediaUrlsJson = serializeStringList(finalRemoteUrls),
                syncStatus = if (uploadFailed) "FAILED" else "SYNCING"
            )
        )

        if (uploadFailed) {
            return@withContext Result.failure(Exception("تعذر رفع بعض صور الواجب. تأكد من اتصال الإنترنت وحاول مجدداً."))
        }

        try {
            val res = api.createHomework(
                CreateHomeworkRequest(
                    groupId = hw.groupId,
                    studyDate = hw.studyDate,
                    dueDate = hw.dueDate,
                    subjectId = hw.subjectId,
                    title = hw.title,
                    details = hw.details,
                    pageNumbers = hw.pageNumbers,
                    questionNumbers = hw.questionNumbers,
                    taskType = hw.taskType,
                    mediaUrls = finalRemoteUrls
                )
            )
            if (res.isSuccessful) {
                db.homeworkDao().insertHomework(
                    hw.copy(
                        mediaUrlsJson = serializeStringList(finalRemoteUrls),
                        syncStatus = "SYNCED"
                    )
                )
                for (url in currentUrls) {
                    if (!url.startsWith("http")) {
                        try { java.io.File(url).delete() } catch (_: Exception) {}
                    }
                }
                Result.success(Unit)
            } else {
                db.homeworkDao().updateSyncStatus(homeworkId, "FAILED")
                Result.failure(Exception(res.message() ?: "فشل المزامنة"))
            }
        } catch (e: Exception) {
            db.homeworkDao().updateSyncStatus(homeworkId, "FAILED")
            Result.failure(e)
        }
    }

    suspend fun toggleHomeworkCompletion(homeworkId: String, currentStatus: Boolean) = withContext(Dispatchers.IO) {
        val currentUserId = db.userDao().getUserSync()?.id ?: "local_user"
        if (currentStatus) {
            db.homeworkDao().deleteCompletion(homeworkId, currentUserId)
        } else {
            db.homeworkDao().insertCompletion(HomeworkCompletionEntity(homeworkId = homeworkId, userId = currentUserId))
        }
        try {
            api.toggleHomework(homeworkId)
        } catch (_: Exception) {}
    }

    suspend fun addExam(
        groupId: String,
        examDate: String,
        subjectId: String,
        title: String,
        requiredChapters: String?,
        notes: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val user = db.userDao().getUserSync()
        val gradeSubjects = SchoolHierarchy.getSubjectsForGrade(user?.gradeId ?: 10)
        val subject = gradeSubjects.find { it.id == subjectId }
            ?: DefaultSubjects.find { it.id == subjectId }
        val id = "exm_${System.currentTimeMillis()}"
        val entity = ExamEntity(
            id = id,
            groupId = groupId,
            examDate = examDate,
            subjectId = subjectId,
            title = title,
            requiredChapters = requiredChapters,
            notes = notes,
            subjectName = subject?.name ?: subjectId,
            subjectIcon = subject?.icon ?: "🔴",
            colorHex = subject?.colorHex ?: "#FF3366",
            syncStatus = "SYNCING"
        )
        db.examDao().insertExam(entity)

        try {
            val res = api.createExam(
                CreateExamRequest(
                    groupId = groupId,
                    examDate = examDate,
                    subjectId = subjectId,
                    title = title,
                    requiredChapters = requiredChapters,
                    notes = notes
                )
            )
            if (res.isSuccessful) {
                db.examDao().updateSyncStatus(id, "SYNCED")
                Result.success(id)
            } else {
                db.examDao().updateSyncStatus(id, "FAILED")
                Result.failure(Exception(res.message() ?: "فشل حفظ الاختبار في السيرفر"))
            }
        } catch (e: Exception) {
            db.examDao().updateSyncStatus(id, "FAILED")
            Result.failure(e)
        }
    }

    suspend fun addEvent(
        groupId: String,
        eventDate: String,
        title: String,
        timeStr: String?,
        category: String,
        description: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val id = "evt_${System.currentTimeMillis()}"
        val entity = EventEntity(
            id = id,
            groupId = groupId,
            eventDate = eventDate,
            timeStr = timeStr,
            title = title,
            description = description,
            category = category,
            location = null,
            createdAt = System.currentTimeMillis(),
            syncStatus = "SYNCING"
        )
        db.eventDao().insertEvent(entity)

        try {
            val res = api.createEvent(
                CreateEventRequest(
                    groupId = groupId,
                    eventDate = eventDate,
                    timeStr = timeStr,
                    title = title,
                    description = description,
                    category = category
                )
            )
            if (res.isSuccessful) {
                db.eventDao().updateSyncStatus(id, "SYNCED")
                Result.success(id)
            } else {
                db.eventDao().updateSyncStatus(id, "FAILED")
                Result.failure(Exception(res.message() ?: "فشل حفظ الفعالية في السيرفر"))
            }
        } catch (e: Exception) {
            db.eventDao().updateSyncStatus(id, "FAILED")
            Result.failure(e)
        }
    }

    suspend fun syncIssueDetails(issueId: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            val res = api.getIssueDetails(issueId)
            if (res.isSuccessful && res.body() != null) {
                val detail = res.body()!!
                val comments = detail.comments.map {
                    IssueCommentEntity(
                        id = it.id,
                        issueId = it.issueId,
                        userId = it.userId,
                        authorName = it.authorName,
                        comment = it.comment,
                        isBestAnswer = it.isBestAnswer || (detail.issue.bestCommentId == it.id),
                        createdAt = it.createdAt
                    )
                }
                if (comments.isNotEmpty()) {
                    db.issueDao().insertComments(comments)
                }
                val issue = detail.issue
                db.issueDao().insertIssue(
                    IssueEntity(
                        id = issue.id,
                        groupId = issue.groupId,
                        subjectId = issue.subjectId,
                        homeworkId = issue.homeworkId,
                        examId = issue.examId,
                        title = issue.title,
                        description = issue.description,
                        status = issue.status,
                        bestCommentId = issue.bestCommentId,
                        authorName = issue.authorName,
                        subjectName = issue.subjectName,
                        subjectIcon = issue.subjectIcon,
                        commentsCount = comments.size,
                        createdAt = issue.createdAt
                    )
                )
                SyncResult.Success(comments.size)
            } else {
                SyncResult.Offline()
            }
        } catch (e: Exception) {
            SyncResult.Offline("أوفلاين: تفاصيل الاستفسار")
        }
    }

    suspend fun addIssueComment(issueId: String, comment: String): Result<Unit> = withContext(Dispatchers.IO) {
        val user = db.userDao().getUserSync() ?: return@withContext Result.failure(Exception("غير مسجل"))
        val commentId = "cmt_${System.currentTimeMillis()}"
        val entity = IssueCommentEntity(
            id = commentId,
            issueId = issueId,
            userId = user.id,
            authorName = user.fullName,
            comment = comment,
            isBestAnswer = false,
            createdAt = System.currentTimeMillis(),
            syncStatus = "SYNCING"
        )
        db.issueDao().insertComment(entity)

        try {
            val res = api.addIssueComment(issueId, AddCommentRequest(comment))
            if (res.isSuccessful) {
                db.issueDao().updateCommentSyncStatus(commentId, "SYNCED")
                Result.success(Unit)
            } else {
                db.issueDao().updateCommentSyncStatus(commentId, "FAILED")
                Result.failure(Exception(res.message() ?: "فشل إرسال التعليق إلى السيرفر"))
            }
        } catch (e: Exception) {
            db.issueDao().updateCommentSyncStatus(commentId, "FAILED")
            Result.failure(e)
        }
    }

    suspend fun retrySyncIssueComment(issueId: String, commentId: String, commentText: String): Result<Unit> = withContext(Dispatchers.IO) {
        db.issueDao().updateCommentSyncStatus(commentId, "SYNCING")
        try {
            val res = api.addIssueComment(issueId, AddCommentRequest(commentText))
            if (res.isSuccessful) {
                db.issueDao().updateCommentSyncStatus(commentId, "SYNCED")
                Result.success(Unit)
            } else {
                db.issueDao().updateCommentSyncStatus(commentId, "FAILED")
                Result.failure(Exception("فشل إرسال التعليق"))
            }
        } catch (e: Exception) {
            db.issueDao().updateCommentSyncStatus(commentId, "FAILED")
            Result.failure(e)
        }
    }

    suspend fun markBestAnswer(issueId: String, commentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        db.issueDao().setBestAnswer(issueId, commentId)
        db.issueDao().markIssueSolved(issueId, commentId)
        try {
            val res = api.markBestAnswer(issueId, commentId)
            if (res.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("فشل اعتماد أفضل إجابة في السيرفر"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createIssue(
        groupId: String,
        title: String,
        description: String?,
        subjectId: String?,
        authorName: String,
        homeworkId: String? = null,
        examId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val subject = DefaultSubjects.find { it.id == subjectId }
        val id = "iss_${System.currentTimeMillis()}"
        val entity = IssueEntity(
            id = id,
            groupId = groupId,
            subjectId = subjectId,
            homeworkId = homeworkId,
            examId = examId,
            title = title,
            description = description,
            status = "OPEN",
            bestCommentId = null,
            authorName = authorName,
            subjectName = subject?.name,
            subjectIcon = subject?.icon,
            commentsCount = 0,
            createdAt = System.currentTimeMillis(),
            syncStatus = "SYNCING"
        )
        db.issueDao().insertIssue(entity)

        try {
            val res = api.createIssue(
                CreateIssueRequest(
                    groupId = groupId,
                    subjectId = subjectId,
                    homeworkId = homeworkId,
                    examId = examId,
                    title = title,
                    description = description
                )
            )
            if (res.isSuccessful) {
                db.issueDao().updateSyncStatus(id, "SYNCED")
                Result.success(id)
            } else {
                db.issueDao().updateSyncStatus(id, "FAILED")
                Result.failure(Exception(res.message() ?: "فشل إنشاء الاستفسار في السيرفر"))
            }
        } catch (e: Exception) {
            db.issueDao().updateSyncStatus(id, "FAILED")
            Result.failure(e)
        }
    }

    suspend fun voteUsefulContent(contentId: String) = withContext(Dispatchers.IO) {
        try {
            api.voteUseful(contentId)
        } catch (_: Exception) {}
    }

    suspend fun deleteExamById(examId: String) = withContext(Dispatchers.IO) {
        db.examDao().deleteExamById(examId)
    }

    suspend fun deleteContentById(contentId: String) = withContext(Dispatchers.IO) {
        db.contentDao().deleteContentById(contentId)
    }

    fun getPendingOutboxCount(): Flow<Int> {
        return db.outboxDao().getPendingItems().map { it.size }
    }

    suspend fun processOutboxQueue(): Result<Int> = withContext(Dispatchers.IO) {
        val pending = db.outboxDao().getPendingItemsSync()
        if (pending.isEmpty()) return@withContext Result.success(0)
        var successCount = 0

        for (item in pending) {
            db.outboxDao().updateOutboxStatus(item.id, "SYNCING")
            try {
                if (item.entityType == "CORRECTION") {
                    val correction = db.correctionDao().getCorrectionById(item.entityId)
                    if (correction != null) {
                        val res = api.submitCorrection(
                            correction.contentId,
                            SubmitCorrectionRequest(
                                fieldName = correction.fieldName,
                                originalValue = correction.originalValue,
                                proposedValue = correction.proposedValue,
                                reason = correction.reason
                            )
                        )
                        if (res.isSuccessful) {
                            db.correctionDao().updateStatus(correction.id, "SUBMITTED")
                            db.outboxDao().updateOutboxStatus(item.id, "SYNCED")
                            successCount++
                        } else {
                            db.outboxDao().updateOutboxStatus(item.id, "FAILED", error = res.message())
                        }
                    }
                } else {
                    db.outboxDao().updateOutboxStatus(item.id, "SYNCED")
                    successCount++
                }
            } catch (e: Exception) {
                db.outboxDao().updateOutboxStatus(item.id, "FAILED", error = e.localizedMessage)
            }
        }
        db.outboxDao().clearSynced()
        Result.success(successCount)
    }

    suspend fun submitCorrection(
        contentId: String,
        groupId: String,
        fieldName: String,
        originalValue: String,
        proposedValue: String,
        reason: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val user = db.userDao().getUserSync()
        val id = "corr_${System.currentTimeMillis()}"
        val entity = CorrectionRequestEntity(
            id = id,
            contentId = contentId,
            groupId = groupId,
            fieldName = fieldName,
            originalValue = originalValue,
            proposedValue = proposedValue,
            reason = reason,
            authorName = user?.fullName ?: "طالب"
        )
        db.correctionDao().insertCorrection(entity)

        val req = SubmitCorrectionRequest(fieldName, originalValue, proposedValue, reason)
        try {
            val res = api.submitCorrection(contentId, req)
            if (res.isSuccessful) {
                db.correctionDao().updateStatus(id, "SUBMITTED")
            }
        } catch (_: Exception) {}

        Result.success(id)
    }
}
