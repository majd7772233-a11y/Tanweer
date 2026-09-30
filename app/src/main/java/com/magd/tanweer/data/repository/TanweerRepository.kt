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
                gradeName = SchoolHierarchy.getGradeName(it.gradeId)
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
            // Allow offline cached session ONLY if the exact registered user exists in local Room DB
            val cached = db.userDao().getUserSync()
            if (cached != null && cached.phoneNumber == phone) {
                Result.success(
                    User(
                        id = cached.id,
                        fullName = cached.fullName,
                        phoneNumber = cached.phoneNumber,
                        gradeId = cached.gradeId,
                        sectionId = cached.sectionId,
                        role = cached.role,
                        defaultGroupId = cached.defaultGroupId,
                        gradeName = SchoolHierarchy.getGradeName(cached.gradeId)
                    )
                )
            } else {
                Result.failure(Exception("تعذر الاتصال بالسيرفر للتأكد من بيانات الحساب"))
            }
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
            val cached = db.userDao().getUserSync()
            if (cached != null && cached.recoveryCode == code) {
                Result.success(
                    User(
                        id = cached.id,
                        fullName = cached.fullName,
                        phoneNumber = cached.phoneNumber,
                        gradeId = cached.gradeId,
                        sectionId = cached.sectionId,
                        role = cached.role,
                        defaultGroupId = cached.defaultGroupId,
                        gradeName = SchoolHierarchy.getGradeName(cached.gradeId)
                    )
                )
            } else {
                Result.failure(Exception("تعذر الاتصال بالسيرفر للتحقق من رمز الاسترداد"))
            }
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
            val updatedUser = if (res.isSuccessful && res.body()?.user != null) {
                res.body()!!.user!!
            } else {
                User(
                    id = currentUserEntity.id,
                    fullName = newFullName,
                    phoneNumber = currentUserEntity.phoneNumber,
                    gradeId = newGradeId,
                    sectionId = newSectionId,
                    role = currentUserEntity.role,
                    defaultGroupId = newGroupId,
                    gradeName = SchoolHierarchy.getGradeName(newGradeId)
                )
            }

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

            val gradeName = SchoolHierarchy.getGradeName(newGradeId)
            val secAr = SchoolHierarchy.getSectionArabicName(newSectionId)
            db.groupDao().insertGroups(
                listOf(
                    GroupEntity(
                        id = newGroupId,
                        name = "$gradeName — شعبة $secAr",
                        type = "CLASS",
                        description = "المجموعة الدراسية الرسمية للشعبة",
                        icon = "🏫",
                        role = "MEMBER",
                        memberCount = 1
                    )
                )
            )

            Result.success(updatedUser)
        } catch (e: Exception) {
            // Local fallback if offline
            val localUpdated = User(
                id = currentUserEntity.id,
                fullName = newFullName,
                phoneNumber = currentUserEntity.phoneNumber,
                gradeId = newGradeId,
                sectionId = newSectionId,
                role = currentUserEntity.role,
                defaultGroupId = newGroupId,
                gradeName = SchoolHierarchy.getGradeName(newGradeId)
            )
            db.userDao().insertUser(
                UserEntity(
                    id = localUpdated.id,
                    fullName = localUpdated.fullName,
                    phoneNumber = localUpdated.phoneNumber,
                    gradeId = localUpdated.gradeId,
                    sectionId = localUpdated.sectionId,
                    role = localUpdated.role,
                    defaultGroupId = localUpdated.defaultGroupId,
                    token = currentUserEntity.token,
                    recoveryCode = currentUserEntity.recoveryCode
                )
            )
            Result.success(localUpdated)
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
                        recoveryCode = currentRecovery
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

                // Save Lessons
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

                // Save Homeworks
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
                            createdAt = it.createdAt
                        )
                    }
                    db.homeworkDao().insertHomeworks(homeworks)
                }

                // Save Exams
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

                // Save Events
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
                        createdAt = it.createdAt
                    )
                }
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

    private var lastBooksSyncTime: Long = 0L
    private val BOOKS_SYNC_COOLDOWN_MS = 24 * 60 * 60 * 1000L // 24 hours

    suspend fun syncBooks(gradeId: Int, forceRefresh: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // If not forced and synced within 24h, return Success immediately using cached Room database
        if (!forceRefresh && (now - lastBooksSyncTime < BOOKS_SYNC_COOLDOWN_MS) && lastBooksSyncTime > 0L) {
            return@withContext SyncResult.Success()
        }

        try {
            var count = 0

            // 1. Primary: Try fetching official pre-compiled catalog.json directly from GitHub
            var catalogSuccess = false
            try {
                val catalogRes = gitHubApi.getCatalog()
                if (catalogRes.isSuccessful && catalogRes.body() != null) {
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
                Log.d("TanweerRepository", "catalog.json not available or failed, falling back to releases: ${e.message}")
            }

            // 2. Fallback: Parse directly from GitHub Releases if catalog.json was not loaded
            if (!catalogSuccess) {
                try {
                    val ghRes = gitHubApi.getReleases()
                    if (ghRes.isSuccessful && ghRes.body() != null) {
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
                } catch (_: Exception) {}
            }

            lastBooksSyncTime = now
            SyncResult.Success(count)
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

    suspend fun joinGroup(groupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val res = api.joinGroupRequest(groupId)
            if (res.isSuccessful) {
                syncGroups()
                Result.success(Unit)
            } else {
                Result.failure(Exception(res.body()?.message ?: "تعذر إتمام طلب الانضمام"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
                val id = json.optString("id", "msg_${System.currentTimeMillis()}")
                val senderId = json.optString("senderId", "")
                val senderName = json.optString("senderName", "")
                val senderGradeSection = json.optString("senderGradeSection", "")
                val text = json.optString("text", "")
                val timestamp = json.optLong("timestamp", System.currentTimeMillis())

                if (text.isNotBlank()) {
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
            } catch (e: Exception) {
                Log.e("TanweerRepository", "Error handling incoming chat WS frame", e)
            }
        }
    }

    suspend fun sendChatMessage(
        groupId: String,
        senderId: String,
        senderName: String,
        senderGradeSection: String,
        text: String
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val msgId = "msg_$now"
        val localMsg = ChatMessageEntity(
            id = msgId,
            groupId = groupId,
            senderId = senderId,
            senderName = senderName,
            senderGradeSection = senderGradeSection,
            text = text,
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
            put("text", text)
            put("timestamp", now)
        }
        val wsSent = chatManager.sendMessage(json)

        try {
            val res = api.postGroupMessage(groupId, PostMessageRequest(text = text))
            if (res.isSuccessful || wsSent) {
                db.chatDao().updateMessageStatus(msgId, "SENT")
            } else {
                db.chatDao().updateMessageStatus(msgId, "FAILED")
            }
        } catch (_: Exception) {
            if (wsSent) {
                db.chatDao().updateMessageStatus(msgId, "SENT")
            } else {
                db.chatDao().updateMessageStatus(msgId, "FAILED")
            }
        }
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
        try {
            val res = api.postGroupMessage(msg.groupId, PostMessageRequest(text = msg.text))
            if (res.isSuccessful || wsSent) {
                db.chatDao().updateMessageStatus(msg.id, "SENT")
            } else {
                db.chatDao().updateMessageStatus(msg.id, "FAILED")
            }
        } catch (_: Exception) {
            if (wsSent) {
                db.chatDao().updateMessageStatus(msg.id, "SENT")
            } else {
                db.chatDao().updateMessageStatus(msg.id, "FAILED")
            }
        }
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
            if (res.isSuccessful && !anyUploadFailed) {
                db.contentDao().updateMediaAndSyncStatus(id, serializeMediaList(uploadedMediaItems), "SYNCED")
                Result.success(id)
            } else {
                val status = if (anyUploadFailed) "FAILED" else "LOCAL"
                db.contentDao().updateMediaAndSyncStatus(id, serializeMediaList(uploadedMediaItems), status)
                Result.failure(Exception("فشل إرسال الدرس أو رفع بعض الصفحات للسيرفر"))
            }
        } catch (e: Exception) {
            db.contentDao().updateMediaAndSyncStatus(id, serializeMediaList(uploadedMediaItems), "FAILED")
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
        taskType: String = "HOMEWORK"
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
                    taskType = taskType
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

    suspend fun retrySyncHomework(homeworkId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val hw = db.homeworkDao().getHomeworkById(homeworkId) ?: return@withContext Result.failure(Exception("الواجب غير موجود"))
        db.homeworkDao().updateSyncStatus(homeworkId, "SYNCING")
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
                    taskType = hw.taskType
                )
            )
            if (res.isSuccessful) {
                db.homeworkDao().updateSyncStatus(homeworkId, "SYNCED")
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
}
