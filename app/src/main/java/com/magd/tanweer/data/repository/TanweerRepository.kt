package com.magd.tanweer.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.magd.tanweer.data.local.*
import com.magd.tanweer.data.model.*
import com.magd.tanweer.data.remote.*
import com.magd.tanweer.data.sync.UnifiedMutationQueue
import com.magd.tanweer.util.ImageProcessingUtils
import com.magd.tanweer.util.ProcessedPageResult
import com.magd.tanweer.util.DataGenerationPolicy
import com.magd.tanweer.util.isConfirmedServerWrite
import com.magd.tanweer.util.ServerSnapshotReconciliation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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
    val mutationQueue = UnifiedMutationQueue.getInstance(db, api)
    private val examMutationMutex = Mutex()

    init {
        mutationQueue.start()
        mutationQueue.onRetryHomework = { id -> retrySyncHomework(id) }
        mutationQueue.onRetryContent = { id -> retrySyncContent(id) }
    }

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
                gradeName = it.gradeId?.let { gid -> SchoolHierarchy.getGradeName(gid) } ?: "الإدارة المدرسية",
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
                sectionId = sectionId.uppercase(),
                deviceId = NetworkModule.deviceId,
                platform = "Android",
                appVersion = com.magd.tanweer.BuildConfig.VERSION_NAME
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
                        token = body.token
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
            val res = api.login(
                LoginRequest(
                    phoneNumber = phone,
                    password = pass,
                    deviceId = NetworkModule.deviceId,
                    platform = "Android",
                    appVersion = com.magd.tanweer.BuildConfig.VERSION_NAME
                )
            )
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
                        token = body.token
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
            val res = api.loginWithRecoveryCode(
                RecoveryLoginRequest(
                    recoveryCode = code,
                    deviceId = NetworkModule.deviceId,
                    platform = "Android",
                    appVersion = com.magd.tanweer.BuildConfig.VERSION_NAME
                )
            )
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
                        token = body.token
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
        val newGroupId = if (newGradeId != null && newSectionId != null) "class_${newGradeId}_${newSectionId.uppercase()}" else currentUserEntity.defaultGroupId

        val gradeToUpdate = if (gradeId != null && gradeId != currentUserEntity.gradeId) gradeId else null
        val sectionToUpdate = if (sectionId != null && sectionId.uppercase() != currentUserEntity.sectionId?.uppercase()) sectionId.uppercase() else null

        try {
            val res = api.updateProfile(
                UpdateProfileRequest(
                    fullName = fullName?.trim()?.takeIf { it.isNotBlank() },
                    gradeId = gradeToUpdate,
                    sectionId = sectionToUpdate
                )
            )
            if (!res.isSuccessful || res.body()?.user == null) {
                val errorMsg = res.body()?.message ?: res.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: res.message().let { if (it.isNullOrBlank()) "فشل تحديث بيانات الحساب على السيرفر" else it }
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
                    token = currentUserEntity.token
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

    suspend fun getRegisteredDevices(): Result<com.magd.tanweer.data.remote.RegisteredDevicesResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getRegisteredDevices()
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                Result.success(body)
            } else {
                val message = body?.error?.message ?: response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    ?: body?.message ?: "تعذر تحميل قائمة الأجهزة"
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e("TanweerRepository", "Load registered devices failed", e)
            Result.failure(Exception("تعذر الاتصال بالخادم لتحميل الأجهزة المسجلة"))
        }
    }

    suspend fun revokeRegisteredDevice(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.revokeRegisteredDevice(deviceId)
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                Result.success(Unit)
            } else {
                val message = body?.message ?: body?.error?.message
                    ?: response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    ?: "تعذر إزالة الجهاز"
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e("TanweerRepository", "Revoke registered device failed", e)
            Result.failure(Exception("تعذر الاتصال بالخادم لإزالة الجهاز"))
        }
    }

    suspend fun logout(context: android.content.Context? = null) = withContext(Dispatchers.IO) {
        // Revoke the server-side session before removing the local token.
        // A failed network request must not prevent local logout.
        try {
            api.logoutCurrentDevice()
        } catch (e: Exception) {
            Log.w("TanweerRepository", "Could not revoke server session during offline logout: ${e.message}")
        }
        mutationQueue.stop()
        chatManager.disconnect()
        NetworkModule.setAuthToken(null)
        // Complete isolation: clear all local database tables so subsequent users never see prior user's cached data
        db.clearAllTables()
        if (context != null) {
            com.magd.tanweer.util.StudentNotebookManager.clearAllUserData(context)
            try {
                val prefs = context.getSharedPreferences("tanweer_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().remove("auth_token").apply()
            } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // UNIFIED SYNC ENGINE: Server -> Sync Engine -> Room -> Compose UI
    // =========================================================================

    /**
     * Startup reconciliation. The server is authoritative: refresh the signed-in
     * user's profile and active memberships first, then fetch the current academic
     * view and every server-backed section for every group the user actively belongs to.
     */
    suspend fun syncInitial(groupId: String, todayDate: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<SyncResult>()

            // These must run first because they determine the current server identity
            // and the set of groups the authenticated account is allowed to access.
            results.add(syncProfile())
            results.add(syncGroups())

            val refreshedUser = db.userDao().getUserSync()
            val activeGroups = db.groupDao().getMyGroupsSync()
            val effectiveGroupId = activeGroups.firstOrNull { it.id == groupId }?.id
                ?: activeGroups.firstOrNull { it.id == refreshedUser?.defaultGroupId }?.id
                ?: activeGroups.firstOrNull()?.id
                ?: ""

            // Daily view is date-scoped; the complete group archives are synchronized below.
            if (effectiveGroupId.isNotBlank() && todayDate.isNotBlank()) {
                results.add(syncDay(effectiveGroupId, todayDate))
            }

            // This fetches the cross-group archive and reconciles lessons, homework,
            // exams, events, schedules, issues, and the selected calendar month.
            results.add(syncStudentAcademicArchive(todayDate.takeIf { it.length >= 7 }?.take(7)))

            // Books belong to the student's grade, not to a particular joined group.
            // New accounts with no group membership must still receive their catalog.
            refreshedUser?.gradeId?.let { gradeId ->
                results.add(syncBooks(gradeId))
            }

            val errors = results.filterIsInstance<SyncResult.Error>()
            val offlines = results.filterIsInstance<SyncResult.Offline>()
            when {
                errors.isNotEmpty() && errors.size == results.size ->
                    SyncResult.Error(errors.first().message, errors.first().isNetworkError)
                offlines.isNotEmpty() && offlines.size == results.size ->
                    SyncResult.Offline("وضع عدم الاتصال: تعذر إكمال المزامنة الكاملة")
                errors.isNotEmpty() || offlines.isNotEmpty() ->
                    SyncResult.Offline("اكتملت مزامنة بعض الأقسام، وبقيت أقسام أخرى في وضع عدم الاتصال أو معلقة")
                else -> SyncResult.Success()
            }
        } catch (e: Exception) {
            Log.e("TanweerRepository", "Initial server reconciliation error", e)
            SyncResult.Offline("وضع عدم الاتصال: تعذر إكمال المزامنة الكاملة")
        }
    }

    suspend fun syncContents(groupId: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getContents(groupId)
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                val list = body.contents
                if (!ServerSnapshotValidation.hasMatchingCount(body.totalCount, list.size) ||
                    !ServerSnapshotValidation.hasUniqueNonBlankIds(list.map { it.id }) ||
                    list.any { it.groupId != groupId }
                ) {
                    return@withContext SyncResult.Error(
                        "لقطة الدروس غير مكتملة أو مكررة أو لا تطابق المجموعة المطلوبة؛ احتُفظت البيانات المحلية دون مسحها"
                    )
                }

                val entities = list.map { item ->
                    val mediaJson = if (item.media.isNotEmpty()) {
                        try {
                            ContentMediaJson.serialize(item.media)
                        } catch (_: Exception) { "[]" }
                    } else "[]"
                    ContentEntity(
                        id = item.id,
                        groupId = item.groupId,
                        studyDate = item.studyDate,
                        subjectId = item.subjectId,
                        type = item.type,
                        title = item.title,
                        description = item.description,
                        createdBy = item.createdBy,
                        authorName = item.authorName,
                        authorGradeSection = item.authorGradeSection,
                        viewsCount = item.viewsCount,
                        usefulCount = item.usefulCount,
                        createdAt = item.createdAt,
                        subjectName = item.subjectName,
                        subjectIcon = item.subjectIcon,
                        colorHex = item.colorHex,
                        mediaUrlsJson = mediaJson,
                        syncStatus = "SYNCED"
                    )
                }

                db.withTransaction {
                    val reconciledContents = ServerSnapshotReconciliation.preservePending(
                        serverRows = entities,
                        localPendingRows = db.contentDao().getPendingContents(groupId)
                    ) { it.id }
                    db.contentDao().clearSyncedContents(groupId)
                    if (reconciledContents.isNotEmpty()) db.contentDao().insertContents(reconciledContents)
                }
                SyncResult.Success(entities.size)
            } else {
                resolveHttpError(res, "فشل مزامنة الدروس للشعبة")
            }
        } catch (e: Exception) {
            resolveException(e, "أوفلاين: تعذر جلب الدروس")
        }
    }

    suspend fun syncStudentAcademicArchive(calendarMonth: String? = null): SyncResult = withContext(Dispatchers.IO) {
        try {
            val subResults = mutableListOf<SyncResult>()

            // Pull the account-scoped cross-grade archive from the authenticated server.
            try {
                val archiveRes = api.getAcademicArchive()
                val archiveBody = archiveRes.body()
                if (archiveRes.isSuccessful && archiveBody?.success == true) {
                    if (!ServerSnapshotValidation.hasMatchingCount(archiveBody.totalCount, archiveBody.contents.size) ||
                        !ServerSnapshotValidation.hasUniqueNonBlankIds(archiveBody.contents.map { it.id })
                    ) {
                        subResults.add(
                            SyncResult.Error("لقطة الأرشيف الأكاديمي غير مكتملة أو تحتوي معرّفات مكررة؛ احتُفظت البيانات المحلية")
                        )
                    } else {
                        val entities = archiveBody.contents.map {
                            val mediaJson = if (it.media.isNotEmpty()) {
                                try {
                                    ContentMediaJson.serialize(it.media)
                                } catch (_: Exception) { "[]" }
                            } else "[]"
                            ContentEntity(
                                id = it.id,
                                groupId = it.groupId,
                                studyDate = it.studyDate,
                                subjectId = it.subjectId,
                                type = it.type,
                                title = it.title,
                                description = it.description,
                                createdBy = it.createdBy,
                                authorName = it.authorName,
                                authorGradeSection = it.authorGradeSection,
                                viewsCount = it.viewsCount,
                                usefulCount = it.usefulCount,
                                createdAt = it.createdAt,
                                subjectName = it.subjectName,
                                subjectIcon = it.subjectIcon,
                                colorHex = it.colorHex,
                                mediaUrlsJson = mediaJson,
                                syncStatus = "SYNCED"
                            )
                        }
                        db.withTransaction {
                            val safeArchiveContents = ServerSnapshotReconciliation.preservePending(
                                serverRows = entities,
                                localPendingRows = db.contentDao().getAllPendingContents()
                            ) { it.id }
                            if (safeArchiveContents.isNotEmpty()) db.contentDao().insertContents(safeArchiveContents)
                        }

                    subResults.add(SyncResult.Success(entities.size))
                    }
                } else {
                    subResults.add(resolveHttpError(archiveRes, "تعذر جلب الأرشيف الأكاديمي"))
                }
            } catch (e: Exception) {
                subResults.add(resolveException(e, "تعذر جلب الأرشيف الأكاديمي"))
            }

            // getMyGroupsSync() contains only active memberships returned by /groups.
            // Never request another account's private group data or treat discoverable
            // groups as joined groups.
            val activeGroups = db.groupDao().getMyGroupsSync()
            for (group in activeGroups) {
                try {
                    subResults.add(syncContents(group.id))
                    subResults.add(syncHomeworks(group.id))
                    subResults.add(syncExams(group.id))
                    subResults.add(syncEvents(group.id))
                    subResults.add(syncSchedule(group.id))
                    subResults.add(syncIssues(group.id))
                    if (!calendarMonth.isNullOrBlank()) {
                        subResults.add(syncCalendarMonth(group.id, calendarMonth))
                    }
                } catch (e: Exception) {
                    Log.w("TanweerRepository", "Failed syncing server data for group ${group.id}: ${e.message}")
                    subResults.add(resolveException(e, "تعذر مزامنة بيانات المجموعة ${group.name}"))
                }
            }

            val errors = subResults.filterIsInstance<SyncResult.Error>()
            val offlines = subResults.filterIsInstance<SyncResult.Offline>()
            val successes = subResults.filterIsInstance<SyncResult.Success>()

            when {
                errors.isNotEmpty() && errors.size == subResults.size ->
                    SyncResult.Error(errors.first().message, errors.first().isNetworkError)
                offlines.isNotEmpty() && offlines.size == subResults.size ->
                    SyncResult.Offline("وضع عدم الاتصال: تعذر إكمال مزامنة الأرشيف الأكاديمي")
                errors.isNotEmpty() || offlines.isNotEmpty() ->
                    SyncResult.Offline("اكتملت مزامنة بعض أقسام الأرشيف (${successes.size}/${subResults.size})، وبقيت أقسام أخرى غير مكتملة أو دون اتصال")
                else -> SyncResult.Success(successes.size)
            }
        } catch (e: Exception) {
            Log.e("TanweerRepository", "Academic archive reconciliation failed", e)
            SyncResult.Offline("أوفلاين: الأرشيف الأكاديمي")
        }
    }

    private fun <T> resolveHttpError(res: retrofit2.Response<T>, defaultOfflineMsg: String): SyncResult {
        val code = res.code()
        return when (code) {
            401 -> SyncResult.Error("انتهت الجلسة، يرجى تسجيل الدخول مجددًا (401)")
            403 -> SyncResult.Error("ليس لديك الصلاحية للوصول إلى هذه البيانات (403)")
            404 -> SyncResult.Error("المورد المطلوب غير موجود (404)")
            in 500..599 -> SyncResult.Error("خطأ في خادم تنوير (${code})، يجري المتابعة")
            else -> SyncResult.Error(res.message()?.ifBlank { null } ?: "خطأ في الاتصال ($code)")
        }
    }

    private fun resolveException(e: Exception, offlineReason: String): SyncResult {
        return if (e is java.io.IOException || e is java.net.SocketTimeoutException || e is java.net.UnknownHostException) {
            SyncResult.Offline(offlineReason)
        } else {
            SyncResult.Error(e.localizedMessage ?: "حدث خطأ غير متوقع", isNetworkError = false)
        }
    }

    suspend fun syncProfile(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val res = api.getProfile()
            if (res.isSuccessful && res.body()?.user != null) {
                val user = res.body()!!.user!!
                val currentToken = db.userDao().getUserSync()?.token ?: ""
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
                        lessonsCount = user.stats?.lessonsCount ?: 0,
                        homeworksCount = user.stats?.homeworksCount ?: 0,
                        issuesCount = user.stats?.issuesCount ?: 0,
                        photosCount = user.stats?.photosCount ?: 0
                    )
                )
                SyncResult.Success(1)
            } else {
                resolveHttpError(res, "أوفلاين: الملف الشخصي")
            }
        } catch (e: Exception) {
            resolveException(e, "أوفلاين: الملف الشخصي")
        }
    }

    suspend fun syncGroups(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val res = api.getGroups()
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
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
                        isOfficial = (it.type == "CLASS" || it.id.startsWith("class_") || it.role == "OFFICIAL"),
                        visibility = it.visibility,
                        chatPolicy = it.chatPolicy
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
                        isOfficial = (it.type == "CLASS" || it.id.startsWith("class_") || it.role == "OFFICIAL"),
                        visibility = it.visibility,
                        chatPolicy = it.chatPolicy
                    )
                }
                val allEntities = myEntities + discoverEntities
                val serverMyGroupIds = myEntities.map { it.id }
                if (serverMyGroupIds.any { it.isBlank() } || serverMyGroupIds.toSet().size != serverMyGroupIds.size) {
                    return@withContext SyncResult.Error("أعاد الخادم قائمة مجموعات غير مكتملة أو مكررة؛ احتُفظ بالمجموعات المحلية")
                }
                // The authenticated server snapshot is authoritative for active memberships.
                // Replacing group metadata does not delete academic caches: lesson, homework,
                // exam, and archive data live in independent Room tables.
                db.withTransaction {
                    db.groupDao().clearGroups()
                    if (allEntities.isNotEmpty()) db.groupDao().insertGroups(allEntities)
                }
                SyncResult.Success(allEntities.size)
            } else {
                resolveHttpError(res, "أوفلاين: المجموعات")
            }
        } catch (e: Exception) {
            resolveException(e, "أوفلاين: المجموعات")
        }
    }

    suspend fun syncSchedule(groupId: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getSchedule(groupId)
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
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
                        endTime = it.endTime,
                        syncStatus = "SYNCED"
                    )
                }

                // Room applies delete + insert + local mutation preservation in one transaction.
                db.scheduleDao().reconcileGroupSchedule(groupId, slots)
                mutationQueue.triggerDrain()
                SyncResult.Success(slots.size)
            } else {
                resolveHttpError(res, "أوفلاين: جدول الحصص")
            }
        } catch (e: Exception) {
            resolveException(e, "أوفلاين: جدول الحصص")
        }
    }

    suspend fun syncDay(groupId: String, date: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank() || date.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getDayDetail(date, groupId)
            if (res.isSuccessful && res.body()?.success == true) {
                val body = res.body()!!
                if (body.date != date ||
                    !ServerSnapshotValidation.hasUniqueNonBlankIds(body.contents.map { it.id }) ||
                    !ServerSnapshotValidation.hasUniqueNonBlankIds(body.homeworks.map { it.id }) ||
                    !ServerSnapshotValidation.hasUniqueNonBlankIds(body.exams.map { it.id }) ||
                    !ServerSnapshotValidation.hasUniqueNonBlankIds(body.events.map { it.id }) ||
                    body.contents.any { it.groupId != groupId || it.studyDate != date } ||
                    body.homeworks.any {
                        it.groupId != groupId || (it.studyDate != date && it.dueDate != date)
                    } ||
                    body.exams.any { it.groupId != groupId || it.examDate != date } ||
                    body.events.any {
                        (it.groupId != groupId && it.groupId != "all") || it.eventDate != date
                    }
                ) {
                    return@withContext SyncResult.Error(
                        "لقطة اليوم لا تطابق التاريخ أو المجموعة، أو تحتوي سجلات مكررة؛ احتُفظت البيانات المحلية دون مسحها"
                    )
                }

                db.withTransaction {
                    // Reconcile Lessons for this date
                    val contents = body.contents.map {
                        val existing = db.contentDao().getContentById(it.id)
                        val mediaJson = if (it.media.isNotEmpty()) {
                            try {
                                ContentMediaJson.serialize(it.media)
                            } catch (_: Exception) { existing?.mediaUrlsJson ?: "[]" }
                        } else {
                            existing?.mediaUrlsJson ?: "[]"
                        }
                        ContentEntity(
                            id = it.id,
                            groupId = it.groupId,
                            studyDate = it.studyDate,
                            subjectId = it.subjectId,
                            type = it.type,
                            title = it.title,
                            description = it.description,
                            createdBy = it.createdBy,
                            authorName = it.authorName,
                            authorGradeSection = it.authorGradeSection,
                            viewsCount = it.viewsCount,
                            usefulCount = it.usefulCount,
                            createdAt = it.createdAt,
                            subjectName = it.subjectName,
                            subjectIcon = it.subjectIcon,
                            colorHex = it.colorHex,
                            mediaUrlsJson = mediaJson,
                            syncStatus = "SYNCED"
                        )
                    }
                    val reconciledDayContents = ServerSnapshotReconciliation.preservePending(
                        serverRows = contents,
                        localPendingRows = db.contentDao().getPendingContents(groupId)
                    ) { it.id }
                    db.contentDao().clearSyncedContentsForDate(groupId, date)
                    if (reconciledDayContents.isNotEmpty()) {
                        db.contentDao().insertContents(reconciledDayContents)
                    }

                    // Reconcile Homeworks for this date
                    db.homeworkDao().clearSyncedHomeworksForDate(groupId, date)
                    if (body.homeworks.isNotEmpty()) {
                        val currentUserId = db.userDao().getUserSync()?.id ?: "local_user"
                        val homeworks = body.homeworks.map {
                            val pendingToggle = db.outboxDao().getPendingByEntityId(it.id)
                            if (pendingToggle == null || pendingToggle.entityType != "HOMEWORK_TOGGLE") {
                                if (it.isCompleted) {
                                    db.homeworkDao().insertCompletion(HomeworkCompletionEntity(homeworkId = it.id, userId = currentUserId))
                                } else {
                                    db.homeworkDao().deleteCompletion(it.id, currentUserId)
                                }
                            }
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
                                createdAt = it.createdAt,
                                isOfficial = it.isOfficial
                            )
                        }
                        val reconciledDayHomeworks = ServerSnapshotReconciliation.preservePending(
                            serverRows = homeworks,
                            localPendingRows = db.homeworkDao().getPendingHomeworks(groupId)
                        ) { it.id }
                        db.homeworkDao().insertHomeworks(reconciledDayHomeworks)
                    }

                    // Reconcile Exams for this date
                    db.examDao().clearSyncedExamsForDate(groupId, date)
                    if (body.exams.isNotEmpty()) {
                        val exams = body.exams.mapNotNull {
                            if (db.outboxDao().getPendingMutation(it.id, "EXAM_DELETE") != null) {
                                return@mapNotNull null
                            }
                            val local = db.examDao().getExamById(it.id)
                            val pendingToggle = db.outboxDao().getPendingByEntityId(it.id)
                            val isDone = if (pendingToggle != null && pendingToggle.entityType == "EXAM_TOGGLE") {
                                local?.isCompleted ?: it.isCompleted
                            } else if (local != null && local.syncStatus == "PENDING") {
                                local.isCompleted
                            } else {
                                it.isCompleted
                            }
                            val finalExamType = if (it.examType.isNotBlank()) it.examType else (local?.examType ?: "MONTHLY")
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
                                colorHex = it.colorHex,
                                isCompleted = isDone,
                                examType = finalExamType,
                                isOfficial = it.isOfficial
                            )
                        }
                        val reconciledDayExams = ServerSnapshotReconciliation.preservePending(
                            serverRows = exams,
                            localPendingRows = db.examDao().getPendingExams(groupId)
                        ) { it.id }
                        db.examDao().insertExams(reconciledDayExams)
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
                                createdAt = it.createdAt,
                                sourceName = it.sourceName,
                                isOfficial = it.isOfficial
                            )
                        }
                        val reconciledDayEvents = ServerSnapshotReconciliation.preservePending(
                            serverRows = events,
                            localPendingRows = db.eventDao().getPendingEvents(groupId)
                        ) { it.id }
                        db.eventDao().insertEvents(reconciledDayEvents)
                    }

                }

                mutationQueue.triggerDrain()
                SyncResult.Success(body.contents.size + body.homeworks.size + body.exams.size)
            } else {
                resolveHttpError(res, "أوفلاين: أحداث اليوم")
            }
        } catch (e: Exception) {
            resolveException(e, "أوفلاين: أحداث اليوم")
        }
    }

    suspend fun syncHomeworks(groupId: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getHomeworks(groupId)
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                if (body.homeworks.any { it.groupId != groupId }) {
                    return@withContext SyncResult.Error("رد الخادم للواجبات لا يطابق المجموعة المطلوبة؛ احتُفظ بالبيانات المحلية")
                }
                val currentUserId = db.userDao().getUserSync()?.id ?: "local_user"
                val completedIds = mutableListOf<String>()
                val incompleteIds = mutableListOf<String>()
                val hwList = body.homeworks.map { item ->
                    val pendingToggle = db.outboxDao().getPendingByEntityId(item.id)
                    if (pendingToggle == null || pendingToggle.entityType != "HOMEWORK_TOGGLE") {
                        if (item.isCompleted) completedIds.add(item.id) else incompleteIds.add(item.id)
                    }
                    HomeworkEntity(
                        id = item.id,
                        groupId = item.groupId,
                        studyDate = item.studyDate,
                        dueDate = item.dueDate,
                        subjectId = item.subjectId,
                        title = item.title,
                        details = item.details,
                        pageNumbers = item.pageNumbers,
                        questionNumbers = item.questionNumbers,
                        taskType = item.taskType,
                        subjectName = item.subjectName,
                        subjectIcon = item.subjectIcon,
                        colorHex = item.colorHex,
                        mediaUrlsJson = serializeStringList(item.mediaUrls),
                        createdAt = item.createdAt,
                        isOfficial = item.isOfficial
                    )
                }
                db.withTransaction {
                    val reconciledHomeworks = ServerSnapshotReconciliation.preservePending(
                        serverRows = hwList,
                        localPendingRows = db.homeworkDao().getPendingHomeworks(groupId)
                    ) { it.id }
                    db.homeworkDao().clearSyncedHomeworks(groupId)
                    if (reconciledHomeworks.isNotEmpty()) db.homeworkDao().insertHomeworks(reconciledHomeworks)
                    completedIds.forEach { db.homeworkDao().insertCompletion(HomeworkCompletionEntity(homeworkId = it, userId = currentUserId)) }
                    incompleteIds.forEach { db.homeworkDao().deleteCompletion(it, currentUserId) }
                }
                SyncResult.Success(hwList.size)
            } else {
                resolveHttpError(res, "أوفلاين: الواجبات")
            }
        } catch (e: Exception) {
            resolveException(e, "أوفلاين: الواجبات")
        }
    }

    suspend fun syncExams(groupId: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getExams(groupId)
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                if (body.exams.any { it.groupId != groupId }) {
                    return@withContext SyncResult.Error("رد الخادم للاختبارات لا يطابق المجموعة المطلوبة؛ احتُفظ بالبيانات المحلية")
                }
                val exams = body.exams.mapNotNull {
                    if (db.outboxDao().getPendingMutation(it.id, "EXAM_DELETE") != null) {
                        return@mapNotNull null
                    }
                    val local = db.examDao().getExamById(it.id)
                    val pendingToggle = db.outboxDao().getPendingByEntityId(it.id)
                    val isDone = if (pendingToggle != null && pendingToggle.entityType == "EXAM_TOGGLE") {
                        local?.isCompleted ?: it.isCompleted
                    } else if (local != null && local.syncStatus == "PENDING") {
                        local.isCompleted
                    } else {
                        it.isCompleted
                    }
                    val finalExamType = if (it.examType.isNotBlank()) it.examType else (local?.examType ?: "MONTHLY")
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
                        colorHex = it.colorHex,
                        isCompleted = isDone,
                        examType = finalExamType,
                        syncStatus = "SYNCED",
                        isOfficial = it.isOfficial
                    )
                }
                db.withTransaction {
                    val reconciledExams = ServerSnapshotReconciliation.preservePending(
                        serverRows = exams,
                        localPendingRows = db.examDao().getPendingExams(groupId)
                    ) { it.id }
                    db.examDao().clearSyncedExams(groupId)
                    if (reconciledExams.isNotEmpty()) db.examDao().insertExams(reconciledExams)
                }
                SyncResult.Success(exams.size)
            } else {
                resolveHttpError(res, "أوفلاين: الاختبارات")
            }
        } catch (e: Exception) {
            resolveException(e, "أوفلاين: الاختبارات")
        }
    }

    suspend fun syncEvents(groupId: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getEvents(groupId)
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                if (body.events.any { it.groupId != groupId && it.groupId != "all" }) {
                    return@withContext SyncResult.Error("رد الخادم للفعاليات لا يطابق المجموعة المطلوبة؛ احتُفظ بالبيانات المحلية")
                }
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
                        createdAt = it.createdAt,
                        sourceName = it.sourceName,
                        isOfficial = it.isOfficial
                    )
                }
                db.withTransaction {
                    val reconciledEvents = ServerSnapshotReconciliation.preservePending(
                        serverRows = events,
                        localPendingRows = db.eventDao().getPendingEvents(groupId)
                    ) { it.id }
                    db.eventDao().clearSyncedEvents(groupId)
                    if (reconciledEvents.isNotEmpty()) db.eventDao().insertEvents(reconciledEvents)
                }
                SyncResult.Success(events.size)
            } else {
                resolveHttpError(res, "أوفلاين: التقويم")
            }
        } catch (e: Exception) {
            resolveException(e, "أوفلاين: التقويم")
        }
    }

    suspend fun syncCalendarMonth(groupId: String, month: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getCalendar(groupId, month)
            if (res.isSuccessful && res.body()?.success == true) {
                val body = res.body()!!
                if (body.exams.any { it.groupId != groupId } ||
                    body.homeworks.any { it.groupId != groupId } ||
                    body.events.any { it.groupId != groupId && it.groupId != "all" }
                ) {
                    return@withContext SyncResult.Error("رد الخادم للشهر لا يطابق المجموعة المطلوبة؛ احتُفظ بالبيانات المحلية")
                }
                var count = 0

                db.withTransaction {
                    // Point 16: Full reconciliation of old month data on server deletion
                    db.examDao().clearSyncedExamsForMonth(groupId, month)
                    db.eventDao().clearSyncedEventsForMonth(groupId, month)
                    db.homeworkDao().clearSyncedHomeworksForMonth(groupId, month)

                    // 1. Process and cache exams
                    if (body.exams.isNotEmpty()) {
                        val exams = body.exams.mapNotNull {
                            if (db.outboxDao().getPendingMutation(it.id, "EXAM_DELETE") != null) {
                                return@mapNotNull null
                            }
                            val local = db.examDao().getExamById(it.id)
                            val pendingToggle = db.outboxDao().getPendingByEntityId(it.id)
                            val isDone = if (pendingToggle != null && pendingToggle.entityType == "EXAM_TOGGLE") {
                                local?.isCompleted ?: it.isCompleted
                            } else if (local != null && local.syncStatus == "PENDING") {
                                local.isCompleted
                            } else {
                                it.isCompleted
                            }
                            val finalExamType = if (it.examType.isNotBlank()) it.examType else (local?.examType ?: "MONTHLY")
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
                                colorHex = it.colorHex,
                                isCompleted = isDone,
                                examType = finalExamType,
                                isOfficial = it.isOfficial
                            )
                        }
                        val reconciledMonthExams = ServerSnapshotReconciliation.preservePending(
                            serverRows = exams,
                            localPendingRows = db.examDao().getPendingExams(groupId)
                        ) { it.id }
                        db.examDao().insertExams(reconciledMonthExams)
                        count += exams.size
                    }

                    // 2. Process and cache events
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
                        val reconciledMonthEvents = ServerSnapshotReconciliation.preservePending(
                            serverRows = events,
                            localPendingRows = db.eventDao().getPendingEvents(groupId)
                        ) { it.id }
                        db.eventDao().insertEvents(reconciledMonthEvents)
                        count += events.size
                    }

                    // 3. Process and cache homeworks
                    if (body.homeworks.isNotEmpty()) {
                        val currentUserId = db.userDao().getUserSync()?.id ?: "local_user"
                        val hws = body.homeworks.map {
                            val pendingToggle = db.outboxDao().getPendingByEntityId(it.id)
                            if (pendingToggle == null || pendingToggle.entityType != "HOMEWORK_TOGGLE") {
                                if (it.isCompleted) {
                                    db.homeworkDao().insertCompletion(HomeworkCompletionEntity(homeworkId = it.id, userId = currentUserId))
                                } else {
                                    db.homeworkDao().deleteCompletion(it.id, currentUserId)
                                }
                            }
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
                                mediaUrlsJson = org.json.JSONArray(it.mediaUrls).toString(),
                                createdAt = it.createdAt,
                                isOfficial = it.isOfficial
                            )
                        }
                        val reconciledMonthHomeworks = ServerSnapshotReconciliation.preservePending(
                            serverRows = hws,
                            localPendingRows = db.homeworkDao().getPendingHomeworks(groupId)
                        ) { it.id }
                        db.homeworkDao().insertHomeworks(reconciledMonthHomeworks)
                        count += hws.size
                    }

                }

                SyncResult.Success(count)
            } else {
                resolveHttpError(res, "أوفلاين: التقويم الشامل")
            }
        } catch (e: Exception) {
            resolveException(e, "أوفلاين: التقويم الشامل")
        }
    }

    suspend fun syncIssues(groupId: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getIssues(groupId)
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                if (body.issues.any { it.groupId != groupId }) {
                    return@withContext SyncResult.Error("رد الخادم للاستفسارات لا يطابق المجموعة المطلوبة؛ احتُفظ بالبيانات المحلية")
                }
                val issues = body.issues.map {
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
                db.withTransaction {
                    val reconciledIssues = ServerSnapshotReconciliation.preservePending(
                        serverRows = issues,
                        localPendingRows = db.issueDao().getPendingIssues(groupId)
                    ) { it.id }
                    db.issueDao().clearSyncedIssues(groupId)
                    if (reconciledIssues.isNotEmpty()) db.issueDao().insertIssues(reconciledIssues)
                }
                SyncResult.Success(issues.size)
            } else {
                resolveHttpError(res, "أوفلاين: الاستفسارات")
            }
        } catch (e: Exception) {
            resolveException(e, "أوفلاين: الاستفسارات")
        }
    }

    suspend fun syncBooks(gradeId: Int, forceRefresh: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val syncKey = "books_sync_grade_$gradeId"
        val lastSync = db.syncMetaDao().getLastSyncTime(syncKey) ?: 0L
        val storedReleaseTag = db.syncMetaDao().getSyncEtag("books_release_tag")

        // Proactively check if a new GitHub release has been published for curriculum books.
        // If a new release tag is detected, immediately bypass cache/cooldown so the user instantly sees the new release!
        var isNewReleaseDetected = false
        if (!forceRefresh) {
            try {
                val latestRelRes = gitHubApi.getBooksLatestRelease()
                if (latestRelRes.isSuccessful) {
                    val latestTag = latestRelRes.body()?.tag_name
                    if (!latestTag.isNullOrBlank() && latestTag != storedReleaseTag) {
                        isNewReleaseDetected = true
                        Log.i("TanweerRepository", "New curriculum release detected on GitHub: $latestTag (current: $storedReleaseTag)")
                    }
                }
            } catch (e: Exception) {
                // Ignore pre-check network glitches
            }
        }

        // When forceRefresh is true or a new release is detected, bypass cache.
        // Otherwise, apply a 2-minute debounce to prevent redundant requests on rapid tab switching.
        val debounceMs = 2 * 60 * 1000L
        if (!forceRefresh && !isNewReleaseDetected && (now - lastSync < debounceMs) && lastSync > 0L && !storedReleaseTag.isNullOrBlank()) {
            return@withContext SyncResult.Success()
        }

        try {
            var anySourceReached = false

            // Fetch both catalog.json and GitHub Releases simultaneously for fusion
            var catalogResponse: com.magd.tanweer.data.model.BooksCatalogResponse? = null
            var releasesList: List<com.magd.tanweer.data.model.GitHubRelease>? = null

            try {
                val catalogRes = gitHubApi.getCatalog()
                if (catalogRes.isSuccessful && catalogRes.body() != null) {
                    catalogResponse = catalogRes.body()
                    anySourceReached = true
                }
            } catch (e: Exception) {
                Log.d("TanweerRepository", "catalog.json fetch note: ${e.message}")
            }

            try {
                val ghRes = gitHubApi.getReleases()
                if (ghRes.isSuccessful && ghRes.body() != null) {
                    releasesList = ghRes.body()
                    anySourceReached = true
                }
            } catch (e: Exception) {
                Log.d("TanweerRepository", "GitHub Releases fetch note: ${e.message}")
            }

            // Resolve and merge both sources into a unified, duplicate-free catalog
            val resolvedCatalog = com.magd.tanweer.util.BookCatalogResolver.resolveAndMerge(
                catalogResponse = catalogResponse,
                releases = releasesList
            )

            if (resolvedCatalog.books.isNotEmpty()) {
                val filteredBooks = if (gradeId == 0) {
                    resolvedCatalog.books
                } else {
                    resolvedCatalog.books.filter { it.gradeId == gradeId }
                }

                // Reconcile and prune old/duplicate IDs from Room
                db.bookDao().reconcileBooksSnapshot(gradeId, filteredBooks)

                // Store active library release versioning metadata
                db.syncMetaDao().setLastSyncTime(
                    SyncMetaEntity(
                        key = syncKey,
                        lastSyncedAt = now,
                        etag = resolvedCatalog.releaseTag ?: resolvedCatalog.catalogVersion ?: "v1.0.0"
                    )
                )

                if (!resolvedCatalog.releaseTag.isNullOrBlank()) {
                    db.syncMetaDao().setLastSyncTime(
                        SyncMetaEntity(
                            key = "books_release_tag",
                            lastSyncedAt = now,
                            etag = resolvedCatalog.releaseTag
                        )
                    )
                }

                SyncResult.Success(filteredBooks.size)
            } else if (anySourceReached) {
                db.syncMetaDao().setLastSyncTime(SyncMetaEntity(key = syncKey, lastSyncedAt = now))
                SyncResult.Success(0)
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
        return db.groupDao().getGroups().map { list -> list.map { it.toGroupItem() } }
    }

    fun getMyGroups(): Flow<List<GroupItem>> {
        return db.groupDao().getMyGroups().map { list -> list.map { it.toGroupItem() } }
    }

    fun getDiscoverGroups(): Flow<List<GroupItem>> {
        return db.groupDao().getDiscoverGroups().map { list -> list.map { it.toGroupItem() } }
    }

    suspend fun joinGroup(groupId: String, reason: String? = null): Result<GenericResponse> = withContext(Dispatchers.IO) {
        try {
            val res = api.joinGroupRequest(groupId, com.magd.tanweer.data.model.JoinGroupBody(reason = reason))
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                Result.success(body)
            } else {
                val errorMsg = body?.error?.message ?: body?.message
                    ?: if (res.isSuccessful) "الخادم لم يؤكد طلب الانضمام (success=false)" else "تعذر إتمام طلب الانضمام (HTTP ${res.code()})"
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

    suspend fun getOrExtractPageText(
        context: android.content.Context,
        bookId: String,
        pageIndex: Int,
        pdfFile: java.io.File
    ): String = withContext(Dispatchers.IO) {
        val cached = db.bookReadingDao().getExtractedPage(bookId, pageIndex)
        if (cached != null && cached.extractedText.isNotBlank()) {
            return@withContext cached.extractedText
        }
        val extracted = com.magd.tanweer.util.PdfTextExtractor.extractPageText(context, pdfFile, pageIndex)
        if (extracted.isNotBlank()) {
            db.bookReadingDao().insertExtractedPage(
                BookExtractedPageEntity(
                    id = "${bookId}_${pageIndex}",
                    bookId = bookId,
                    pageIndex = pageIndex,
                    extractedText = extracted,
                    extractionType = "PDFBOX"
                )
            )
        }
        extracted
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
                    endTime = it.endTime,
                    syncStatus = it.syncStatus
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
    ): Result<ScheduleMutationResult> = withContext(Dispatchers.IO) {
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
            endTime = endTime,
            syncStatus = "PENDING"
        )

        try {
            val res = api.createScheduleSlot(
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
            if (res.isSuccessful && res.body()?.success == true) {
                db.scheduleDao().insertSlots(listOf(entity.copy(syncStatus = "SYNCED")))
                Result.success(ScheduleMutationResult.SavedDirectly())
            } else if (res.code() == 403) {
                val propRes = api.proposeScheduleChange(
                    ScheduleProposalRequest(
                        groupId = groupId,
                        dayOfWeek = dayOfWeek,
                        slotOrder = slotOrder,
                        oldSubjectId = null,
                        newSubjectId = subjectId,
                        reason = "اقتراح تعديل الجدول الدراسي"
                    )
                )
                if (propRes.isSuccessful && propRes.body()?.success == true) {
                    Result.success(ScheduleMutationResult.SubmittedAsProposal())
                } else {
                    Result.failure(Exception("لا تملك صلاحية تعديل الجدول مباشرة، وتعذر إرسال الاقتراح: ${propRes.message()}"))
                }
            } else {
                db.scheduleDao().insertSlots(listOf(entity.copy(syncStatus = "FAILED")))
                Result.failure(Exception(res.message().ifBlank { "تعذر حفظ الحصة في السيرفر (كود ${res.code()})" }))
            }
        } catch (e: Exception) {
            // Offline fallback: save locally as PENDING and enqueue in Outbox with full metadata
            db.scheduleDao().insertSlots(listOf(entity.copy(syncStatus = "PENDING")))
            val slotJson = try {
                org.json.JSONObject().apply {
                    put("groupId", groupId)
                    put("dayOfWeek", dayOfWeek)
                    put("slotOrder", slotOrder)
                    put("subjectId", subjectId)
                    put("subjectName", entity.subjectName)
                    put("subjectIcon", entity.subjectIcon)
                    put("colorHex", entity.colorHex)
                    put("startTime", startTime)
                    put("endTime", endTime)
                }.toString()
            } catch (_: Exception) {
                "{\"groupId\":\"$groupId\",\"dayOfWeek\":$dayOfWeek,\"slotOrder\":$slotOrder}"
            }
            val outboxItem = OutboxEntity(
                id = "ob_sch_${slotId}_${System.currentTimeMillis()}",
                entityType = "SCHEDULE_SLOT_UPDATE",
                entityId = slotId,
                operation = "CREATE",
                payloadJson = slotJson
            )
            db.outboxDao().insertOutbox(outboxItem)
            Result.success(ScheduleMutationResult.QueuedOffline())
        }
    }

    suspend fun deleteScheduleSlot(groupId: String, dayOfWeek: Int, slotOrder: Int): Result<ScheduleSlotDeleteResult> = withContext(Dispatchers.IO) {
        try {
            val res = api.deleteScheduleSlot(groupId, dayOfWeek, slotOrder)
            if (res.isSuccessful && res.body()?.success == true) {
                db.scheduleDao().deleteSlot(groupId, dayOfWeek, slotOrder)
                Result.success(ScheduleSlotDeleteResult.Synced())
            } else {
                Result.failure(Exception(res.message().ifBlank { "تعذر تفريغ الخانة في السيرفر (كود ${res.code()})" }))
            }
        } catch (e: Exception) {
            // Offline fallback: delete locally and queue Outbox
            db.scheduleDao().deleteSlot(groupId, dayOfWeek, slotOrder)
            val delJson = try {
                org.json.JSONObject().apply {
                    put("groupId", groupId)
                    put("dayOfWeek", dayOfWeek)
                    put("slotOrder", slotOrder)
                }.toString()
            } catch (_: Exception) { "{}" }
            val outboxItem = OutboxEntity(
                id = "ob_schdel_${groupId}_${dayOfWeek}_${slotOrder}_${System.currentTimeMillis()}",
                entityType = "SCHEDULE_SLOT_DELETE",
                entityId = "${groupId}:${dayOfWeek}:${slotOrder}",
                operation = "DELETE",
                payloadJson = delJson
            )
            db.outboxDao().insertOutbox(outboxItem)
            Result.success(ScheduleSlotDeleteResult.QueuedOffline())
        }
    }

    suspend fun deleteScheduleSlotById(slotId: String): Result<ScheduleSlotDeleteResult> = withContext(Dispatchers.IO) {
        val slot = db.scheduleDao().getSlotById(slotId)
        if (slot != null) {
            deleteScheduleSlot(slot.groupId, slot.dayOfWeek, slot.slotOrder)
        } else {
            db.scheduleDao().deleteSlotById(slotId)
            Result.success(ScheduleSlotDeleteResult.QueuedOffline("تم تفريغ الحصة من الجدول محلياً ✓"))
        }
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
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
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
                        endTime = it.endTime,
                        syncStatus = "SYNCED"
                    )
                }
                db.scheduleDao().reconcileGroupSchedule(groupId, slots)
                Result.success(body)
            } else {
                Result.failure(Exception(body?.let { "تعذر جلب بيانات الجدول (HTTP ${res.code()})" } ?: "تعذر جلب بيانات الجدول"))
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
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                Result.success(body)
            } else {
                val message = res.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    ?: "تعذر جلب أعضاء المجموعة (HTTP ${res.code()})"
                Result.failure(Exception(message))
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

    suspend fun rejectGroupJoinRequest(groupId: String, targetUserId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.rejectGroupJoinRequest(groupId, targetUserId)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()?.message ?: "تم رفض طلب الانضمام بنجاح")
            } else {
                Result.failure(Exception(res.body()?.message ?: "تعذر رفض طلب الانضمام"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(groupId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.leaveGroup(groupId)
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                Result.success(body.message ?: "تمت مغادرة المجموعة بنجاح")
            } else {
                Result.failure(Exception(body?.error?.message ?: body?.message ?: "تعذر مغادرة المجموعة"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPublicGroup(
        name: String,
        description: String? = null,
        icon: String? = "👥",
        visibility: String = "PUBLIC",
        chatPolicy: String = "MEMBERS"
    ): Result<GenericResponse> = withContext(Dispatchers.IO) {
        try {
            val res = api.createPublicGroup(
                com.magd.tanweer.data.model.CreateGroupRequest(name, description, icon, visibility, chatPolicy)
            )
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!)
            } else {
                val errorMsg = res.body()?.error?.message ?: res.body()?.message ?: "تعذر إنشاء المجموعة"
                Result.failure(Exception(errorMsg))
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
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                Result.success(body)
            } else {
                Result.failure(Exception(body?.let { "الخادم لم يؤكد بيانات إدارة المدرسة" } ?: "تعذر جلب بيانات إدارة المدرسة (HTTP ${res.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
 
    suspend fun updateSchoolStudentStatus(userId: String, status: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.updateSchoolStudentStatus(
                userId,
                com.magd.tanweer.data.model.UpdateStudentStatusRequest(status = status)
            )
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                Result.success(body.message ?: "تم تحديث حالة حساب الطالب")
            } else {
                Result.failure(Exception(body?.error?.message ?: body?.message ?: "تعذر تحديث حالة حساب الطالب (HTTP ${response.code()})"))
            }
        } catch (error: Exception) {
            Result.failure(Exception(error.localizedMessage ?: "تعذر الاتصال بالخادم لتحديث الحساب"))
        }
    }

    suspend fun removeSchoolStudentFromClass(userId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.removeSchoolStudentFromClass(userId)
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                Result.success(body.message ?: "تم فصل الطالب من شعبته")
            } else {
                Result.failure(Exception(body?.error?.message ?: body?.message ?: "تعذر فصل الطالب من شعبته (HTTP ${response.code()})"))
            }
        } catch (error: Exception) {
            Result.failure(Exception(error.localizedMessage ?: "تعذر الاتصال بالخادم لفصل الطالب"))
        }
    }
    suspend fun getMyNotifications(): Result<List<com.magd.tanweer.data.model.AppNotification>> = withContext(Dispatchers.IO) {
        try {
            val res = api.getMyNotifications()
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                Result.success(body.notifications)
            } else {
                Result.failure(Exception("تعذر جلب إشعارات الحساب"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateMyRecoveryCode(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.generateMyRecoveryCode()
            val body = res.body()
            if (res.isSuccessful && body?.success == true && !body.recoveryCode.isNullOrBlank()) {
                Result.success(body.recoveryCode)
            } else {
                Result.failure(Exception(body?.error?.message ?: body?.message ?: "تعذر تغيير رمز الاسترداد"))
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

    private fun parseMediaJson(jsonStr: String?): List<MediaItem> =
        ContentMediaJson.parse(jsonStr)

    private fun serializeMediaList(media: List<MediaItem>): String =
        ContentMediaJson.serialize(media)

    private fun mapContentEntityToItem(it: ContentEntity): ContentItem {
        return ContentItem(
            id = it.id,
            groupId = it.groupId,
            studyDate = it.studyDate,
            subjectId = it.subjectId,
            type = it.type,
            title = it.title,
            description = it.description,
            createdBy = it.createdBy,
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

    fun getAllHistoricalContents(): Flow<List<ContentItem>> {
        return db.contentDao().getAllHistoricalContents().map { list ->
            list.map { mapContentEntityToItem(it) }
        }
    }

    fun getAllContents(groupId: String): Flow<List<ContentItem>> {
        return db.contentDao().getAllContents(groupId).map { list ->
            list.map { mapContentEntityToItem(it) }
        }
    }

    fun getContentsBySubject(groupId: String, subjectId: String): Flow<List<ContentItem>> {
        return db.contentDao().getContentsBySubject(groupId, subjectId).map { list ->
            list.map { mapContentEntityToItem(it) }
        }
    }

    fun searchContents(groupId: String, query: String): Flow<List<ContentItem>> {
        return db.contentDao().searchContents(groupId, query).map { list ->
            list.map { mapContentEntityToItem(it) }
        }
    }

    fun getDayContents(groupId: String, date: String): Flow<List<ContentItem>> {
        return db.contentDao().getContentsByDate(groupId, date).map { list ->
            list.map { mapContentEntityToItem(it) }
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
                        syncStatus = it.syncStatus,
                        isOfficial = it.isOfficial
                    )
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getAllHistoricalHomeworks(): Flow<List<HomeworkItem>> {
        return db.userDao().getUser().flatMapLatest { user ->
            val userId = user?.id ?: ""
            combine(
                db.homeworkDao().getAllHistoricalHomeworks(),
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
                        syncStatus = it.syncStatus,
                        isOfficial = it.isOfficial
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
                    isCompleted = it.isCompleted,
                    examType = if (it.examType == "QUIZ") "MONTHLY" else it.examType,
                    syncStatus = it.syncStatus,
                    isOfficial = it.isOfficial
                )
            }
        }
    }

    fun getAllHistoricalExams(): Flow<List<ExamItem>> {
        return db.examDao().getAllHistoricalExams().map { list ->
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
                    isCompleted = it.isCompleted,
                    examType = if (it.examType == "QUIZ") "MONTHLY" else it.examType,
                    syncStatus = it.syncStatus,
                    isOfficial = it.isOfficial
                )
            }
        }
    }

    suspend fun getStoredBooksReleaseTag(): String? = withContext(Dispatchers.IO) {
        db.syncMetaDao().getSyncEtag("books_release_tag")
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
                    syncStatus = it.syncStatus,
                    sourceName = it.sourceName,
                    isOfficial = it.isOfficial
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

    /**
     * Fetches the most recent server-persisted chat messages without opening a WebSocket.
     * The server enforces active group membership and currently returns the latest 100 rows.
     * Existing local messages are not cleared because older cached history may predate that window.
     */
    suspend fun syncGroupChatHistory(groupId: String): SyncResult = withContext(Dispatchers.IO) {
        if (groupId.isBlank()) return@withContext SyncResult.Offline()
        try {
            val res = api.getGroupMessages(groupId)
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                if (body.messages.any { it.groupId != groupId }) {
                    return@withContext SyncResult.Error(
                        "رد الخادم للمحادثة لا يطابق المجموعة المطلوبة؛ احتُفظ بالرسائل المحلية"
                    )
                }
                val messages = body.messages.map {
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
                if (messages.isNotEmpty()) db.chatDao().insertMessages(messages)
                SyncResult.Success(messages.size)
            } else {
                resolveHttpError(res, "تعذر جلب سجل المحادثة")
            }
        } catch (e: Exception) {
            resolveException(e, "تعذر جلب سجل المحادثة")
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

    suspend fun disconnectFromGroupChat() = withContext(Dispatchers.IO) {
        chatManager.disconnect()
    }

    private var lastSentMessageSignature: String? = null
    private var lastSentMessageTime: Long = 0L

    suspend fun sendChatMessage(
        groupId: String,
        senderId: String,
        senderName: String,
        senderGradeSection: String,
        text: String
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@withContext false

        // Debounce exact duplicate sends within 1.5s
        val now = System.currentTimeMillis()
        val sig = "$groupId|$senderId|$cleanText"
        if (sig == lastSentMessageSignature && (now - lastSentMessageTime < 1500L)) {
            return@withContext true
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
            // Fallback to HTTP REST endpoint if WebSocket is not connected
            try {
                val res = api.postGroupMessage(groupId, PostMessageRequest(id = msgId, text = cleanText))
                if (res.isSuccessful && res.body()?.success == true) {
                    db.chatDao().updateMessageStatus(msgId, "SENT")
                    true
                } else {
                    db.chatDao().updateMessageStatus(msgId, "FAILED")
                    false
                }
            } catch (e: Exception) {
                Log.w("TanweerRepository", "HTTP fallback send failed, marking as FAILED", e)
                db.chatDao().updateMessageStatus(msgId, "FAILED")
                false
            }
        } else {
            // Timeout reconciliation after 12s so message never stays SENDING forever
            CoroutineScope(Dispatchers.IO).launch {
                delay(12_000L)
                val current = db.chatDao().getSendingMessagesSync(System.currentTimeMillis() - 10_000L)
                if (current.any { it.id == msgId }) {
                    try {
                        val res = api.postGroupMessage(groupId, PostMessageRequest(id = msgId, text = cleanText))
                        if (res.isSuccessful && res.body()?.success == true) {
                            db.chatDao().updateMessageStatus(msgId, "SENT")
                        } else {
                            db.chatDao().updateMessageStatus(msgId, "FAILED")
                        }
                    } catch (e: Exception) {
                        db.chatDao().updateMessageStatus(msgId, "FAILED")
                    }
                }
            }
            true
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
        if (!wsSent) {
            try {
                val res = api.postGroupMessage(msg.groupId, PostMessageRequest(id = msg.id, text = msg.text))
                if (res.isSuccessful && res.body()?.success == true) {
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
    ): Result<PublicationResult> = withContext(Dispatchers.IO) {
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
            createdBy = user?.id ?: "",
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
            var serverChecksum: String? = null
            try {
                if (page.file.exists() && page.file.length() > 0) {
                    val reqFile = page.file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("file", "page_${index + 1}_${page.file.name}", reqFile)
                    val uploadRes = api.uploadMedia(part)
                    if (isConfirmedServerWrite(uploadRes.isSuccessful, uploadRes.body()?.success)) {
                        val body = uploadRes.body()!!
                        remoteUrl = body.url ?: body.relativeUrl?.let { rel ->
                            if (rel.startsWith("http")) rel else NetworkModule.resolveApiUrl(rel)
                        }
                        serverChecksum = body.checksum ?: page.sha256
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
                        fileSize = page.sizeBytes.toInt(),
                        checksum = serverChecksum ?: page.sha256
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
                        fileSize = page.sizeBytes.toInt(),
                        checksum = page.sha256
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
                    id = id,
                    groupId = groupId,
                    studyDate = date,
                    subjectId = subjectId,
                    title = title,
                    description = description,
                    media = uploadedMediaItems
                )
            )
            if (isConfirmedServerWrite(res.isSuccessful, res.body()?.success)) {
                val body = res.body()
                val serverId = body?.contentId ?: body?.id
                if (body?.isDuplicate == true && serverId != null && serverId != id) {
                    // Server detected a duplicate and linked it to an existing lesson
                    // Remove local ghost duplicate draft and refresh real server content
                    db.contentDao().deleteContentById(id)
                    syncDay(groupId, date)
                } else if (serverId != null && serverId != id) {
                    val updated = entity.copy(id = serverId, mediaUrlsJson = serializeMediaList(uploadedMediaItems), syncStatus = "SYNCED")
                    db.contentDao().replaceContentId(id, updated)
                } else {
                    db.contentDao().updateMediaAndSyncStatus(id, serializeMediaList(uploadedMediaItems), "SYNCED")
                }
                // Cleanup temporary pending files on success
                pages.forEach { try { it.file.delete() } catch (_: Exception) {} }
                val finalId = serverId ?: id
                Result.success(PublicationResult(finalId, PublicationState.SYNCED, "تم توثيق ونشر الدرس على الخادم بنجاح 🚀"))
            } else {
                db.contentDao().updateMediaAndSyncStatus(id, serializeMediaList(uploadedMediaItems), "FAILED")
                val body = res.body()
                val message = body?.error?.message ?: body?.message
                    ?: if (res.isSuccessful) "الخادم لم يؤكد حفظ الدرس (success=false)" else "فشل تسجيل الدرس على السيرفر (HTTP ${res.code()})"
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            val payload = JSONObject().apply {
                put("groupId", groupId)
                put("studyDate", date)
                put("subjectId", subjectId)
                put("title", title)
                put("description", description ?: "")
                put("authorName", authorName)
                put("authorGradeSection", authorGradeSection)
            }.toString()
            val outboxItem = OutboxEntity(
                id = "ob_lsc_${id}_${System.currentTimeMillis()}",
                entityType = "LESSON_CREATE",
                entityId = id,
                operation = "CREATE",
                payloadJson = payload
            )
            db.outboxDao().insertOutbox(outboxItem)
            db.contentDao().updateMediaAndSyncStatus(id, serializeMediaList(uploadedMediaItems), "PENDING")
            Result.success(PublicationResult(id, PublicationState.PENDING, "تم حفظ الدرس محلياً، وجارٍ جدولة الرفع عند توفر الاتصال ⏳"))
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
                    if (isConfirmedServerWrite(uploadRes.isSuccessful, uploadRes.body()?.success)) {
                            val body = uploadRes.body()!!
                            val remoteUrl = body.url ?: body.relativeUrl?.let { rel ->
                                if (rel.startsWith("http")) rel else NetworkModule.resolveApiUrl(rel)
                            }
                            if (remoteUrl != null) {
                                val csum = body.checksum ?: item.checksum ?: try { ImageProcessingUtils.calculateSha256(file.readBytes()) } catch (_: Exception) { null }
                                updatedMediaItems.add(item.copy(url = remoteUrl, checksum = csum))
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
                    id = content.id,
                    groupId = content.groupId,
                    studyDate = content.studyDate,
                    subjectId = content.subjectId,
                    title = content.title,
                    description = content.description,
                    media = updatedMediaItems
                )
            )
            if (isConfirmedServerWrite(res.isSuccessful, res.body()?.success)) {
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
                val statusCode = res.code()
                val responseBody = res.body()
                val message = responseBody?.error?.message ?: responseBody?.message ?: when {
                    res.isSuccessful -> "الخادم لم يؤكد الحفظ (success=false)"
                    statusCode in listOf(401, 403) -> "لا تملك صلاحية النشر (HTTP $statusCode)"
                    else -> "رفض الخادم النشر (HTTP $statusCode)"
                }
                db.contentDao().updateSyncStatus(
                    contentId,
                    if (isRetryableHttpStatus(statusCode)) "PENDING" else "FAILED"
                )
                if (isRetryableHttpStatus(statusCode)) {
                    Result.failure(Exception(message))
                } else {
                    Result.failure(PermanentHttpFailureException(statusCode, message))
                }
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
        mediaUrls: List<String> = emptyList(),
        isOfficial: Boolean = false
    ): Result<PublicationResult> = withContext(Dispatchers.IO) {
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
            syncStatus = "SYNCING",
            isOfficial = isOfficial
        )
        db.homeworkDao().insertHomework(entity)

        try {
            val res = api.createHomework(
                CreateHomeworkRequest(
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
                    mediaUrls = mediaUrls,
                    isOfficial = isOfficial
                )
            )
            if (isConfirmedServerWrite(res.isSuccessful, res.body()?.success)) {
                val serverId = res.body()?.homeworkId ?: res.body()?.id
                if (serverId != null && serverId != id) {
                    val updatedEntity = entity.copy(id = serverId, syncStatus = "SYNCED")
                    db.homeworkDao().replaceHomeworkId(id, updatedEntity)
                    Result.success(PublicationResult(serverId, PublicationState.SYNCED, "تم حفظ ونشر الواجب على الخادم بنجاح ✨"))
                } else {
                    db.homeworkDao().updateSyncStatus(id, "SYNCED")
                    Result.success(PublicationResult(id, PublicationState.SYNCED, "تم حفظ ونشر الواجب على الخادم بنجاح ✨"))
                }
            } else {
                db.homeworkDao().updateSyncStatus(id, "FAILED")
                val body = res.body()
                val message = body?.error?.message ?: body?.message
                    ?: if (res.isSuccessful) "الخادم لم يؤكد حفظ الواجب (success=false)" else "فشل حفظ الواجب في السيرفر (HTTP ${res.code()})"
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            // Queue in Outbox for offline synchronization
            val payload = JSONObject().apply {
                put("groupId", groupId)
                put("studyDate", studyDate)
                put("dueDate", dueDate)
                put("subjectId", subjectId)
                put("title", title)
                put("details", details ?: "")
                put("pageNumbers", pageNumbers ?: "")
                put("questionNumbers", questionNumbers ?: "")
                put("taskType", taskType)
                put("mediaUrls", serializeStringList(mediaUrls))
                put("isOfficial", isOfficial)
            }.toString()
            val outboxItem = OutboxEntity(
                id = "ob_hwc_${id}_${System.currentTimeMillis()}",
                entityType = "HOMEWORK_CREATE",
                entityId = id,
                operation = "CREATE",
                payloadJson = payload
            )
            db.outboxDao().insertOutbox(outboxItem)
            db.homeworkDao().updateSyncStatus(id, "PENDING")
            Result.success(PublicationResult(id, PublicationState.PENDING, "تم حفظ الواجب محلياً بنجاح، وسيتم رفعه للخادم فور توفر الاتصال ⏳"))
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
        pages: List<ProcessedPageResult> = emptyList(),
        isOfficial: Boolean = false
    ): Result<PublicationResult> = withContext(Dispatchers.IO) {
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
            syncStatus = "SYNCING",
            isOfficial = isOfficial
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
                    if (isConfirmedServerWrite(uploadRes.isSuccessful, uploadRes.body()?.success)) {
                        val body = uploadRes.body()!!
                        remoteUrl = body.url ?: body.relativeUrl?.let { rel ->
                            if (rel.startsWith("http")) rel else NetworkModule.resolveApiUrl(rel)
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
                    mediaUrls = uploadedUrls,
                    isOfficial = isOfficial
                )
            )
            if (isConfirmedServerWrite(res.isSuccessful, res.body()?.success)) {
                val serverId = res.body()?.homeworkId ?: res.body()?.id
                val finalId = if (serverId != null && serverId != id) {
                    val updatedEntity = entity.copy(
                        id = serverId,
                        mediaUrlsJson = serializeStringList(uploadedUrls),
                        syncStatus = "SYNCED"
                    )
                    db.homeworkDao().replaceHomeworkId(id, updatedEntity)
                    serverId
                } else {
                    val updatedEntity = entity.copy(
                        mediaUrlsJson = serializeStringList(uploadedUrls),
                        syncStatus = "SYNCED"
                    )
                    db.homeworkDao().insertHomework(updatedEntity)
                    id
                }
                pages.forEach { try { it.file.delete() } catch (_: Exception) {} }
                Result.success(PublicationResult(finalId, PublicationState.SYNCED, "تم حفظ ونشر الواجب على الخادم بنجاح ✨"))
            } else {
                val updatedEntity = entity.copy(
                    mediaUrlsJson = serializeStringList(uploadedUrls),
                    syncStatus = "FAILED"
                )
                db.homeworkDao().insertHomework(updatedEntity)
                val body = res.body()
                val message = body?.error?.message ?: body?.message
                    ?: if (res.isSuccessful) "الخادم لم يؤكد حفظ الواجب (success=false)" else "فشل إرسال الواجب للسيرفر (HTTP ${res.code()})"
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            val updatedEntity = entity.copy(
                mediaUrlsJson = serializeStringList(uploadedUrls),
                syncStatus = "PENDING"
            )
            db.homeworkDao().insertHomework(updatedEntity)
            val payload = JSONObject().apply {
                put("groupId", groupId)
                put("studyDate", studyDate)
                put("dueDate", dueDate)
                put("subjectId", subjectId)
                put("title", title)
                put("details", details ?: "")
                put("pageNumbers", pageNumbers ?: "")
                put("questionNumbers", questionNumbers ?: "")
                put("taskType", taskType)
                put("mediaUrls", serializeStringList(uploadedUrls))
                put("isOfficial", isOfficial)
            }.toString()
            val outboxItem = OutboxEntity(
                id = "ob_hwc_${id}_${System.currentTimeMillis()}",
                entityType = "HOMEWORK_CREATE",
                entityId = id,
                operation = "CREATE",
                payloadJson = payload
            )
            db.outboxDao().insertOutbox(outboxItem)
            Result.success(PublicationResult(id, PublicationState.PENDING, "تم حفظ الواجب محلياً بنجاح، وسيتم رفعه للخادم فور توفر الاتصال ⏳"))
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
                    if (isConfirmedServerWrite(uploadRes.isSuccessful, uploadRes.body()?.success)) {
                            val body = uploadRes.body()!!
                            val remoteUrl = body.url ?: body.relativeUrl?.let { rel ->
                                if (rel.startsWith("http")) rel else NetworkModule.resolveApiUrl(rel)
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
                    id = hw.id,
                    groupId = hw.groupId,
                    studyDate = hw.studyDate,
                    dueDate = hw.dueDate,
                    subjectId = hw.subjectId,
                    title = hw.title,
                    details = hw.details,
                    pageNumbers = hw.pageNumbers,
                    questionNumbers = hw.questionNumbers,
                    taskType = hw.taskType,
                    mediaUrls = finalRemoteUrls,
                    isOfficial = hw.isOfficial
                )
            )
            if (isConfirmedServerWrite(res.isSuccessful, res.body()?.success)) {
                val serverId = res.body()?.homeworkId ?: res.body()?.id
                if (serverId != null && serverId != hw.id) {
                    val updated = hw.copy(
                        id = serverId,
                        mediaUrlsJson = serializeStringList(finalRemoteUrls),
                        syncStatus = "SYNCED"
                    )
                    db.homeworkDao().replaceHomeworkId(hw.id, updated)
                    db.outboxDao().updateEntityId(hw.id, serverId)
                } else {
                    db.homeworkDao().insertHomework(
                        hw.copy(
                            mediaUrlsJson = serializeStringList(finalRemoteUrls),
                            syncStatus = "SYNCED"
                        )
                    )
                }
                for (url in currentUrls) {
                    if (!url.startsWith("http")) {
                        try { java.io.File(url).delete() } catch (_: Exception) {}
                    }
                }
                Result.success(Unit)
            } else {
                val statusCode = res.code()
                val responseBody = res.body()
                val message = responseBody?.error?.message ?: responseBody?.message ?: when {
                    res.isSuccessful -> "الخادم لم يؤكد الحفظ (success=false)"
                    statusCode in listOf(401, 403) -> "لا تملك صلاحية النشر (HTTP $statusCode)"
                    else -> "رفض الخادم النشر (HTTP $statusCode)"
                }
                db.homeworkDao().updateSyncStatus(
                    homeworkId,
                    if (isRetryableHttpStatus(statusCode)) "PENDING" else "FAILED"
                )
                if (isRetryableHttpStatus(statusCode)) {
                    Result.failure(Exception(message))
                } else {
                    Result.failure(PermanentHttpFailureException(statusCode, message))
                }
            }
        } catch (e: Exception) {
            db.homeworkDao().updateSyncStatus(homeworkId, "FAILED")
            Result.failure(e)
        }
    }

    suspend fun toggleHomeworkCompletion(homeworkId: String, currentStatus: Boolean): HomeworkToggleResult = withContext(Dispatchers.IO) {
        val currentUserId = db.userDao().getUserSync()?.id ?: "local_user"
        val targetStatus = !currentStatus
        if (currentStatus) {
            db.homeworkDao().deleteCompletion(homeworkId, currentUserId)
        } else {
            db.homeworkDao().insertCompletion(HomeworkCompletionEntity(homeworkId = homeworkId, userId = currentUserId))
        }

        // Check if homework is still locally pending sync to server
        val hw = db.homeworkDao().getHomeworkById(homeworkId)
        val isNotSyncedYet = hw != null && hw.syncStatus != "SYNCED"
        if (isNotSyncedYet) {
            // Queue toggle in Outbox for when homework creation completes
            val outboxItem = OutboxEntity(
                id = "ob_hwt_${homeworkId}_${System.currentTimeMillis()}",
                entityType = "HOMEWORK_TOGGLE",
                entityId = homeworkId,
                operation = "UPDATE",
                payloadJson = "{\"isCompleted\": $targetStatus}"
            )
            db.outboxDao().insertOutbox(outboxItem)
            return@withContext HomeworkToggleResult.QueuedOffline(targetStatus, "تم حفظ حالة الواجب محلياً (بانتظار مزامنة الواجب مع السيرفر) ⏳")
        }

        try {
            val res = api.toggleHomework(homeworkId, ToggleCompletionRequest(isCompleted = targetStatus))
            if (res.isSuccessful) {
                db.outboxDao().deleteByEntityId(homeworkId)
                HomeworkToggleResult.SyncedWithServer(targetStatus)
            } else if (res.code() in listOf(401, 403)) {
                // Rollback on rejection or authorization failure
                if (currentStatus) {
                    db.homeworkDao().insertCompletion(HomeworkCompletionEntity(homeworkId = homeworkId, userId = currentUserId))
                } else {
                    db.homeworkDao().deleteCompletion(homeworkId, currentUserId)
                }
                HomeworkToggleResult.Failed(currentStatus, "لا تملك صلاحية تغيير حالة الواجب في السيرفر (كود ${res.code()})")
            } else if (res.code() == 404) {
                // If 404 on server, preserve local user action and queue Outbox retry
                val outboxItem = OutboxEntity(
                    id = "ob_hwt_${homeworkId}_${System.currentTimeMillis()}",
                    entityType = "HOMEWORK_TOGGLE",
                    entityId = homeworkId,
                    operation = "UPDATE",
                    payloadJson = "{\"isCompleted\": $targetStatus}"
                )
                db.outboxDao().insertOutbox(outboxItem)
                HomeworkToggleResult.QueuedOffline(targetStatus, "تم حفظ الحالة محلياً، بانتظار تأكيد السيرفر 🟡")
            } else {
                // Server temporary error -> queue for background retry
                val outboxItem = OutboxEntity(
                    id = "ob_hwt_${homeworkId}_${System.currentTimeMillis()}",
                    entityType = "HOMEWORK_TOGGLE",
                    entityId = homeworkId,
                    operation = "UPDATE",
                    payloadJson = "{\"isCompleted\": $targetStatus}"
                )
                db.outboxDao().insertOutbox(outboxItem)
                HomeworkToggleResult.QueuedOffline(targetStatus)
            }
        } catch (e: Exception) {
            // Offline or network exception -> queue for background retry
            val outboxItem = OutboxEntity(
                id = "ob_hwt_${homeworkId}_${System.currentTimeMillis()}",
                entityType = "HOMEWORK_TOGGLE",
                entityId = homeworkId,
                operation = "UPDATE",
                payloadJson = "{\"isCompleted\": $targetStatus}"
            )
            db.outboxDao().insertOutbox(outboxItem)
            HomeworkToggleResult.QueuedOffline(targetStatus)
        }
    }

    suspend fun addExam(
        groupId: String,
        examDate: String,
        subjectId: String,
        title: String,
        requiredChapters: String?,
        notes: String?,
        examType: String = "MONTHLY",
        isOfficial: Boolean = false
    ): Result<ExamAddResult> = withContext(Dispatchers.IO) {
        examMutationMutex.lock()
        try {
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
            examType = examType,
            syncStatus = "SYNCING",
            isOfficial = isOfficial
        )
        db.examDao().insertExam(entity)

        try {
            val res = api.createExam(
                CreateExamRequest(
                    id = id,
                    groupId = groupId,
                    examDate = examDate,
                    subjectId = subjectId,
                    title = title,
                    requiredChapters = requiredChapters,
                    notes = notes,
                    examType = examType,
                    isOfficial = isOfficial
                )
            )
            if (isConfirmedServerWrite(res.isSuccessful, res.body()?.success)) {
                val serverId = res.body()?.examId ?: res.body()?.id
                if (serverId != null && serverId != id) {
                    val updatedEntity = entity.copy(id = serverId, syncStatus = "SYNCED")
                    db.examDao().replaceExamId(id, updatedEntity)
                    db.outboxDao().updateEntityId(id, serverId)
                    Result.success(ExamAddResult.Synced(serverId))
                } else {
                    db.examDao().updateSyncStatus(id, "SYNCED")
                    Result.success(ExamAddResult.Synced(id))
                }
            } else if (!isRetryableHttpStatus(res.code())) {
                db.examDao().updateSyncStatus(id, "FAILED")
                val body = res.body()
                val message = body?.error?.message ?: body?.message ?: if (res.isSuccessful) {
                    "الخادم لم يؤكد حفظ الاختبار (success=false)"
                } else if (res.code() in listOf(401, 403)) {
                    "لا تملك صلاحية حفظ هذا الاختبار (HTTP ${res.code()})"
                } else {
                    "رفض الخادم حفظ الاختبار بسبب بيانات غير مقبولة (HTTP ${res.code()})"
                }
                Result.failure(Exception(message))
            } else {
                val examJson = try {
                    org.json.JSONObject().apply {
                        put("id", id)
                        put("groupId", groupId)
                        put("examDate", examDate)
                        put("subjectId", subjectId)
                        put("title", title)
                        put("requiredChapters", requiredChapters)
                        put("notes", notes)
                        put("examType", examType)
                        put("isOfficial", isOfficial)
                    }.toString()
                } catch (_: Exception) { "{}" }
                val outboxItem = OutboxEntity(
                    id = "ob_exc_${id}_${System.currentTimeMillis()}",
                    entityType = "EXAM_CREATE",
                    entityId = id,
                    operation = "CREATE",
                    payloadJson = examJson
                )
                db.outboxDao().insertOutbox(outboxItem)
                db.examDao().updateSyncStatus(id, "PENDING")
                Result.success(ExamAddResult.QueuedOffline(id))
            }
        } catch (e: Exception) {
            // Queue in Outbox for offline synchronization with full metadata
            val examJson = try {
                org.json.JSONObject().apply {
                    put("id", id)
                    put("groupId", groupId)
                    put("examDate", examDate)
                    put("subjectId", subjectId)
                    put("title", title)
                    put("requiredChapters", requiredChapters)
                    put("notes", notes)
                    put("examType", examType)
                    put("isOfficial", isOfficial)
                }.toString()
            } catch (_: Exception) { "{}" }
            val outboxItem = OutboxEntity(
                id = "ob_exc_${id}_${System.currentTimeMillis()}",
                entityType = "EXAM_CREATE",
                entityId = id,
                operation = "CREATE",
                payloadJson = examJson
            )
            db.outboxDao().insertOutbox(outboxItem)
            db.examDao().updateSyncStatus(id, "PENDING")
            Result.success(ExamAddResult.QueuedOffline(id))
        }
        } finally {
            examMutationMutex.unlock()
        }
    }

    suspend fun toggleExamCompletion(examId: String, targetStatus: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        examMutationMutex.lock()
        try {
            val pendingDelete = db.outboxDao().getPendingMutation(examId, "EXAM_DELETE")
            if (pendingDelete != null) {
                return@withContext Result.failure(Exception("لا يمكن تغيير حالة اختبار قيد الحذف"))
            }

            val exam = db.examDao().getExamById(examId)
                ?: return@withContext Result.failure(Exception("الاختبار غير موجود"))
            if (exam.syncStatus == "DELETE_PENDING") {
                return@withContext Result.failure(Exception("طلب حذف هذا الاختبار قيد المزامنة"))
            }

            val pendingToggle = db.outboxDao().getPendingMutation(examId, "EXAM_TOGGLE")
            val mustQueue = exam.syncStatus != "SYNCED" || pendingToggle != null
            db.examDao().updateExamCompletion(examId, targetStatus)

            if (mustQueue) {
                val outboxItem = OutboxEntity(
                    id = "ob_examt_${examId}_${System.currentTimeMillis()}",
                    entityType = "EXAM_TOGGLE",
                    entityId = examId,
                    operation = "UPDATE",
                    payloadJson = "{\"isCompleted\": $targetStatus}"
                )
                db.outboxDao().insertOutbox(outboxItem)
                return@withContext Result.success(targetStatus)
            }

            try {
                val res = api.toggleExamCompletion(examId, ToggleCompletionRequest(isCompleted = targetStatus))
                if (res.isSuccessful) {
                    db.outboxDao().deleteByEntityIdAndType(examId, "EXAM_TOGGLE")
                    val serverCompleted = res.body()?.isCompleted ?: targetStatus
                    db.examDao().updateExamCompletion(examId, serverCompleted)
                    Result.success(serverCompleted)
                } else if (res.code() in listOf(401, 403)) {
                    // Rollback on rejection or authorization failure.
                    db.examDao().updateExamCompletion(examId, !targetStatus)
                    Result.failure(Exception("لا تملك صلاحية تغيير حالة الاختبار (كود ${res.code()})"))
                } else {
                    val outboxItem = OutboxEntity(
                        id = "ob_examt_${examId}_${System.currentTimeMillis()}",
                        entityType = "EXAM_TOGGLE",
                        entityId = examId,
                        operation = "UPDATE",
                        payloadJson = "{\"isCompleted\": $targetStatus}"
                    )
                    db.outboxDao().insertOutbox(outboxItem)
                    Result.success(targetStatus)
                }
            } catch (_: Exception) {
                // Network failure: retain the desired state for reliable retry.
                val outboxItem = OutboxEntity(
                    id = "ob_examt_${examId}_${System.currentTimeMillis()}",
                    entityType = "EXAM_TOGGLE",
                    entityId = examId,
                    operation = "UPDATE",
                    payloadJson = "{\"isCompleted\": $targetStatus}"
                )
                db.outboxDao().insertOutbox(outboxItem)
                Result.success(targetStatus)
            }
        } finally {
            examMutationMutex.unlock()
        }
    }

    suspend fun addEvent(
        groupId: String,
        eventDate: String,
        title: String,
        timeStr: String?,
        category: String,
        description: String?,
        location: String? = null
    ): Result<EventAddResult> = withContext(Dispatchers.IO) {
        val id = "evt_${System.currentTimeMillis()}"
        val entity = EventEntity(
            id = id,
            groupId = groupId,
            eventDate = eventDate,
            timeStr = timeStr,
            title = title,
            description = description,
            category = category,
            location = location,
            createdAt = System.currentTimeMillis(),
            syncStatus = "SYNCING",
            sourceName = if (groupId == "all") "إدارة المدرسة" else null,
            isOfficial = groupId == "all" && category.equals("ANNOUNCEMENT", ignoreCase = true)
        )
        db.eventDao().insertEvent(entity)

        try {
            val res = api.createEvent(
                CreateEventRequest(
                    id = id,
                    groupId = groupId,
                    eventDate = eventDate,
                    timeStr = timeStr,
                    title = title,
                    description = description,
                    category = category,
                    location = location
                )
            )
            if (isConfirmedServerWrite(res.isSuccessful, res.body()?.success)) {
                val serverId = res.body()?.eventId ?: res.body()?.id
                if (serverId != null && serverId != id) {
                    val updatedEntity = entity.copy(id = serverId, syncStatus = "SYNCED")
                    db.eventDao().replaceEventId(id, updatedEntity)
                    db.outboxDao().updateEntityId(id, serverId)
                    Result.success(EventAddResult.Synced(serverId))
                } else {
                    db.eventDao().updateSyncStatus(id, "SYNCED")
                    Result.success(EventAddResult.Synced(id))
                }
            } else if (!isRetryableHttpStatus(res.code())) {
                if (groupId == "all" && category.equals("ANNOUNCEMENT", ignoreCase = true)) {
                    db.eventDao().deleteEventById(id)
                } else {
                    db.eventDao().updateSyncStatus(id, "FAILED")
                }
                val body = res.body()
                val message = body?.error?.message ?: body?.message ?: if (res.isSuccessful) {
                    "الخادم لم يؤكد نشر الفعالية (success=false)"
                } else if (res.code() in listOf(401, 403)) {
                    "لا تملك صلاحية نشر هذه الفعالية (HTTP ${res.code()})"
                } else {
                    "رفض الخادم إنشاء الفعالية بسبب بيانات غير مقبولة (HTTP ${res.code()})"
                }
                Result.failure(Exception(message))
            } else if (groupId == "all" && category.equals("ANNOUNCEMENT", ignoreCase = true)) {
                db.eventDao().deleteEventById(id)
                Result.failure(Exception("يلزم الاتصال بالإنترنت لنشر التعميم الرسمي. لم يتم تسجيله كمنشور ناجح."))
            } else {
                val outboxItem = OutboxEntity(
                    id = "ob_evc_${id}_${System.currentTimeMillis()}",
                    entityType = "EVENT_CREATE",
                    entityId = id,
                    operation = "CREATE",
                    payloadJson = "{}"
                )
                db.outboxDao().insertOutbox(outboxItem)
                db.eventDao().updateSyncStatus(id, "PENDING")
                Result.success(EventAddResult.QueuedOffline(id))
            }
        } catch (e: Exception) {
            if (groupId == "all" && category.equals("ANNOUNCEMENT", ignoreCase = true)) {
                db.eventDao().deleteEventById(id)
                Result.failure(Exception("تعذر نشر التعميم دون الاتصال بالخادم. لم يُحفظ محلياً كمنشور ناجح. تحقق من الإنترنت وحاول مجدداً."))
            } else {
                // Queue ordinary local events for later synchronization.
                val outboxItem = OutboxEntity(
                    id = "ob_evc_${id}_${System.currentTimeMillis()}",
                    entityType = "EVENT_CREATE",
                    entityId = id,
                    operation = "CREATE",
                    payloadJson = "{}"
                )
                db.outboxDao().insertOutbox(outboxItem)
                db.eventDao().updateSyncStatus(id, "PENDING")
                Result.success(EventAddResult.QueuedOffline(id))
            }
        }
    }

    suspend fun dismissSchoolAnnouncement(eventId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.dismissSchoolAnnouncement(eventId)
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                // Remove only this local copy after the server confirms the account-scoped dismissal.
                db.eventDao().deleteEventById(eventId)
                Result.success(Unit)
            } else {
                Result.failure(Exception(body?.error?.message ?: body?.message ?: "تعذر إخفاء التعميم من حسابك"))
            }
        } catch (error: Exception) {
            Result.failure(Exception(error.localizedMessage ?: "تعذر الاتصال بالخادم لإخفاء التعميم"))
        }
    }
    suspend fun createHoliday(
        groupId: String,
        eventDate: String,
        title: String,
        reason: String,
        isSchoolWide: Boolean
    ): Result<EventAddResult> {
        val targetGroupId = if (isSchoolWide) "all" else groupId
        return addEvent(
            groupId = targetGroupId,
            eventDate = eventDate,
            title = title,
            timeStr = null,
            category = "HOLIDAY",
            description = reason,
            location = if (isSchoolWide) "كامل المدرسة" else "الشعبة"
        )
    }

    suspend fun checkAndApplyDataGeneration(context: android.content.Context, serverGeneration: String?): Boolean = withContext(Dispatchers.IO) {
        if (serverGeneration.isNullOrBlank()) return@withContext false

        val prefs = context.getSharedPreferences("tanweer_prefs", android.content.Context.MODE_PRIVATE)
        val accountId = db.userDao().getUserSync()?.id?.takeIf { it.isNotBlank() } ?: "anonymous"
        val serverScope = NetworkModule.apiBaseUrl.trim().trimEnd('/').lowercase(java.util.Locale.ROOT)
        // A Staging/Production switch or a different signed-in account must never be
        // interpreted as proof that this installation's Room database is disposable.
        val markerKey = "data_generation|$serverScope|$accountId"
        val previousGeneration = prefs.getString(markerKey, null)

        if (previousGeneration.isNullOrBlank()) {
            Log.i(
                "TanweerRepo",
                "Initializing generation marker for account $accountId at $serverScope; retaining all local data."
            )
        } else if (DataGenerationPolicy.hasChanged(previousGeneration, serverGeneration)) {
            Log.w(
                "TanweerRepo",
                "Server generation changed from $previousGeneration to $serverGeneration for account $accountId. " +
                    "Keeping Room, notebook files, auth session, and pending outbox; the next server snapshot will reconcile data."
            )
        }

        // Generation values are synchronization metadata only. They must never trigger
        // clearAllTables(), token removal, logout, or deletion of pending mutations.
        prefs.edit().putString(markerKey, serverGeneration).apply()
        false
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

    suspend fun addIssueComment(issueId: String, comment: String): Result<CommentAddResult> = withContext(Dispatchers.IO) {
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
            val res = api.addIssueComment(issueId, AddCommentRequest(id = commentId, comment = comment))
            if (res.isSuccessful) {
                db.issueDao().updateCommentSyncStatus(commentId, "SYNCED")
                Result.success(CommentAddResult.Synced())
            } else if (res.code() in listOf(401, 403)) {
                db.issueDao().updateCommentSyncStatus(commentId, "FAILED")
                Result.failure(Exception(res.message() ?: "فشل إرسال التعليق إلى السيرفر"))
            } else {
                val outboxItem = OutboxEntity(
                    id = "ob_com_${commentId}_${System.currentTimeMillis()}",
                    entityType = "ISSUE_COMMENT_CREATE",
                    entityId = commentId,
                    operation = "CREATE",
                    payloadJson = "{\"issueId\":\"$issueId\",\"comment\":\"$comment\"}"
                )
                db.outboxDao().insertOutbox(outboxItem)
                db.issueDao().updateCommentSyncStatus(commentId, "PENDING")
                Result.success(CommentAddResult.QueuedOffline())
            }
        } catch (e: Exception) {
            val outboxItem = OutboxEntity(
                id = "ob_com_${commentId}_${System.currentTimeMillis()}",
                entityType = "ISSUE_COMMENT_CREATE",
                entityId = commentId,
                operation = "CREATE",
                payloadJson = "{\"issueId\":\"$issueId\",\"comment\":\"$comment\"}"
            )
            db.outboxDao().insertOutbox(outboxItem)
            db.issueDao().updateCommentSyncStatus(commentId, "PENDING")
            Result.success(CommentAddResult.QueuedOffline())
        }
    }

    suspend fun retrySyncIssueComment(issueId: String, commentId: String, commentText: String): Result<Unit> = withContext(Dispatchers.IO) {
        db.issueDao().updateCommentSyncStatus(commentId, "SYNCING")
        try {
            val res = api.addIssueComment(issueId, AddCommentRequest(id = commentId, comment = commentText))
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
        val existingIssue = db.issueDao().getIssueSync(issueId)
        val prevBestCommentId = existingIssue?.bestCommentId
        val prevStatus = existingIssue?.status ?: "OPEN"

        db.issueDao().setBestAnswer(issueId, commentId)
        db.issueDao().markIssueSolved(issueId, commentId)
        try {
            val res = api.markBestAnswer(issueId, commentId)
            if (res.isSuccessful) {
                Result.success(Unit)
            } else {
                db.issueDao().revertIssueStatus(issueId, prevBestCommentId, prevStatus)
                db.issueDao().revertCommentBestAnswer(issueId, prevBestCommentId)
                val errMsg = if (res.code() == 403) "عذرًا، فقط صاحب السؤال أو المعلم يمكنه اعتماد أفضل إجابة"
                             else (res.message()?.ifBlank { null } ?: "فشل اعتماد أفضل إجابة في السيرفر (${res.code()})")
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            db.issueDao().revertIssueStatus(issueId, prevBestCommentId, prevStatus)
            db.issueDao().revertCommentBestAnswer(issueId, prevBestCommentId)
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
    ): Result<IssueAddResult> = withContext(Dispatchers.IO) {
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
                    id = id,
                    groupId = groupId,
                    subjectId = subjectId,
                    homeworkId = homeworkId,
                    examId = examId,
                    title = title,
                    description = description
                )
            )
            if (res.isSuccessful) {
                val serverId = res.body()?.issueId ?: res.body()?.id
                if (serverId != null && serverId != id) {
                    val updatedEntity = entity.copy(id = serverId, syncStatus = "SYNCED")
                    db.issueDao().replaceIssueId(id, updatedEntity)
                    db.outboxDao().updateEntityId(id, serverId)
                    Result.success(IssueAddResult.Synced(serverId))
                } else {
                    db.issueDao().updateSyncStatus(id, "SYNCED")
                    Result.success(IssueAddResult.Synced(id))
                }
            } else if (res.code() in listOf(401, 403)) {
                db.issueDao().updateSyncStatus(id, "FAILED")
                Result.failure(Exception(res.message() ?: "فشل إنشاء الاستفسار في السيرفر"))
            } else {
                val outboxItem = OutboxEntity(
                    id = "ob_isc_${id}_${System.currentTimeMillis()}",
                    entityType = "ISSUE_CREATE",
                    entityId = id,
                    operation = "CREATE",
                    payloadJson = "{}"
                )
                db.outboxDao().insertOutbox(outboxItem)
                db.issueDao().updateSyncStatus(id, "PENDING")
                Result.success(IssueAddResult.QueuedOffline(id))
            }
        } catch (e: Exception) {
            val outboxItem = OutboxEntity(
                id = "ob_isc_${id}_${System.currentTimeMillis()}",
                entityType = "ISSUE_CREATE",
                entityId = id,
                operation = "CREATE",
                payloadJson = "{}"
            )
            db.outboxDao().insertOutbox(outboxItem)
            db.issueDao().updateSyncStatus(id, "PENDING")
            Result.success(IssueAddResult.QueuedOffline(id))
        }
    }

    suspend fun voteUsefulContent(contentId: String) = withContext(Dispatchers.IO) {
        try {
            api.voteUseful(contentId)
        } catch (_: Exception) {}
    }

    private suspend fun queueExamDeletion(examId: String) {
        val existing = db.outboxDao().getPendingMutation(examId, "EXAM_DELETE")
        if (existing == null) {
            db.outboxDao().insertOutbox(
                OutboxEntity(
                    id = "ob_exd_${examId}_${System.currentTimeMillis()}",
                    entityType = "EXAM_DELETE",
                    entityId = examId,
                    operation = "DELETE",
                    payloadJson = "{}"
                )
            )
        }
        db.examDao().updateSyncStatus(examId, "DELETE_PENDING")
        mutationQueue.triggerDrain()
    }

    suspend fun deleteExamById(examId: String): Result<ExamDeleteResult> = withContext(Dispatchers.IO) {
        examMutationMutex.lock()
        try {
            val exam = db.examDao().getExamById(examId)
                ?: return@withContext Result.failure(Exception("الاختبار غير موجود"))

            val pendingCreate = db.outboxDao().getPendingMutation(examId, "EXAM_CREATE")
            val pendingDelete = db.outboxDao().getPendingMutation(examId, "EXAM_DELETE")
            val pendingToggle = db.outboxDao().getPendingMutation(examId, "EXAM_TOGGLE")
            if (pendingDelete != null) {
                return@withContext Result.success(ExamDeleteResult.QueuedOffline())
            }

            // Cancel any unclaimed create entry, but still confirm deletion on the server:
            // even a failed/uncertain POST may have committed before its response was lost.
            if (pendingCreate != null && pendingCreate.status != "SYNCING") {
                db.outboxDao().deleteUnclaimedMutation(examId, "EXAM_CREATE")
                if (db.outboxDao().getPendingMutation(examId, "EXAM_CREATE") != null) {
                    queueExamDeletion(examId)
                    return@withContext Result.success(ExamDeleteResult.QueuedOffline())
                }
            }

            // A running create or review mutation must settle before the delete is sent.
            if (pendingCreate?.status == "SYNCING" || pendingToggle != null) {
                queueExamDeletion(examId)
                return@withContext Result.success(ExamDeleteResult.QueuedOffline())
            }

            try {
                val res = api.deleteExam(examId)
                when {
                    res.isSuccessful || res.code() == 404 -> {
                        db.examDao().deleteExamById(examId)
                        db.outboxDao().deleteByEntityId(examId)
                        Result.success(ExamDeleteResult.Synced())
                    }
                    res.code() in 400..499 && res.code() !in listOf(408, 429) -> {
                        val message = when (res.code()) {
                            401, 403 -> "لا تملك صلاحية حذف هذا الاختبار (HTTP ${res.code()})"
                            else -> "رفض الخادم حذف الاختبار (HTTP ${res.code()})"
                        }
                        Result.failure(Exception(message))
                    }
                    else -> {
                        queueExamDeletion(examId)
                        Result.success(ExamDeleteResult.QueuedOffline())
                    }
                }
            } catch (_: Exception) {
                queueExamDeletion(examId)
                Result.success(ExamDeleteResult.QueuedOffline())
            }
        } finally {
            examMutationMutex.unlock()
        }
    }

    suspend fun deleteContentById(contentId: String) = withContext(Dispatchers.IO) {
        db.contentDao().deleteContentById(contentId)
    }

    fun getPendingOutboxCount(): Flow<Int> {
        return db.outboxDao().getPendingItems().map { it.size }
    }

    fun getPendingOnlyOutboxCount(): Flow<Int> {
        return db.outboxDao().getPendingOnlyCount()
    }

    fun getSyncingOutboxCount(): Flow<Int> {
        return db.outboxDao().getSyncingCount()
    }

    fun getFailedOutboxCount(): Flow<Int> {
        return db.outboxDao().getFailedCount()
    }

    fun getTotalActiveOutboxCount(): Flow<Int> {
        return db.outboxDao().getTotalActiveCount()
    }

    suspend fun getPendingOutboxCountSync(): Int = withContext(Dispatchers.IO) {
        try {
            db.outboxDao().getPendingItemsSync().size
        } catch (_: Exception) {
            0
        }
    }

    suspend fun processOutboxQueue(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // drainQueue itself enforces the generation-verification gate; callers cannot bypass it.
            val count = mutationQueue.drainQueue()
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // DAILY PLAN MANAGEMENT (ROOM PERSISTENCE)
    // =========================================================================

    fun getDailyPlan(userId: String, date: String): Flow<List<DailyPlanItem>> {
        return db.dailyPlanDao().getPlanForDate(userId, date).map { list ->
            list.map {
                DailyPlanItem(
                    id = it.id,
                    userId = it.userId,
                    homeworkId = it.homeworkId,
                    title = it.title,
                    subjectName = it.subjectName,
                    planDate = it.planDate,
                    isCompleted = it.isCompleted,
                    createdAt = it.createdAt
                )
            }
        }
    }

    fun getPlannedHomeworkIds(userId: String, date: String): Flow<List<String>> {
        return db.dailyPlanDao().getPlannedHomeworkIds(userId, date)
    }

    suspend fun addToDailyPlan(
        userId: String,
        homeworkId: String?,
        title: String,
        subjectName: String?,
        planDate: String
    ): Boolean = withContext(Dispatchers.IO) {
        val id = "plan_${System.currentTimeMillis()}_${homeworkId ?: "custom"}"
        val entity = DailyPlanEntity(
            id = id,
            userId = userId,
            homeworkId = homeworkId,
            title = title,
            subjectName = subjectName,
            planDate = planDate,
            isCompleted = false
        )
        db.dailyPlanDao().insertPlanItem(entity)
        true
    }

    suspend fun toggleDailyPlanItem(id: String, completed: Boolean) = withContext(Dispatchers.IO) {
        db.dailyPlanDao().setItemCompleted(id, completed)
    }

    suspend fun removeDailyPlanItem(id: String) = withContext(Dispatchers.IO) {
        db.dailyPlanDao().deletePlanItem(id)
    }

    suspend fun removeDailyPlanByHomework(homeworkId: String, userId: String) = withContext(Dispatchers.IO) {
        db.dailyPlanDao().deleteByHomeworkId(homeworkId, userId)
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

    suspend fun getSystemVersion(): Result<com.magd.tanweer.data.remote.SystemVersionResponse> = withContext(Dispatchers.IO) {
        try {
            val res = api.getSystemVersion()
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception(res.message().ifBlank { "تعذر جلب بيانات التحديثات من الخادم" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getBestAnswersCountForUser(userId: String): Flow<Int> {
        return db.issueDao().countBestAnswersForUser(userId)
    }

    fun getCommentsCountForUser(userId: String): Flow<Int> {
        return db.issueDao().countCommentsForUser(userId)
    }

    fun countContentsForAuthorBetween(authorName: String, startDate: String, endDate: String): Flow<Int> {
        return db.contentDao().countContentsForAuthorBetween(authorName, startDate, endDate)
    }

    fun countContentsForUserBetween(userId: String, authorName: String, startDate: String, endDate: String): Flow<Int> {
        return db.contentDao().countContentsForUserBetween(userId, authorName, startDate, endDate)
    }
}
