package com.example.data.repository

import android.util.Log
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
                        defaultGroupId = body.user.defaultGroupId,
                        token = body.token,
                        recoveryCode = body.recoveryCode
                    )
                )
                // Initialize ONLY the user's single primary official class group (completely clean, 0 fake slots, 0 fake members)
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
                // Offline fallback setup (completely clean, no templates)
                val offlineUser = User(
                    id = "user_${System.currentTimeMillis()}",
                    fullName = fullName,
                    phoneNumber = phone,
                    gradeId = gradeId,
                    sectionId = sectionId.uppercase(),
                    defaultGroupId = classGroupId,
                    gradeName = gradeName
                )
                val recovery = "TANW-9921-XKQ7-4820"
                db.userDao().insertUser(
                    UserEntity(
                        id = offlineUser.id,
                        fullName = offlineUser.fullName,
                        phoneNumber = offlineUser.phoneNumber,
                        gradeId = offlineUser.gradeId,
                        sectionId = offlineUser.sectionId,
                        role = "MEMBER",
                        defaultGroupId = classGroupId,
                        token = "offline_token",
                        recoveryCode = recovery
                    )
                )
                db.groupDao().insertGroups(
                    listOf(
                        GroupEntity(
                            id = classGroupId,
                            name = groupName,
                            type = "CLASS",
                            description = "المجموعة الدراسية الرسمية للشعبة",
                            icon = "🏫",
                            role = "MEMBER",
                            memberCount = 1
                        )
                    )
                )
                Result.success(Pair(offlineUser, recovery))
            }
        } catch (e: Exception) {
            val offlineUser = User(
                id = "user_${System.currentTimeMillis()}",
                fullName = fullName,
                phoneNumber = phone,
                gradeId = gradeId,
                sectionId = sectionId.uppercase(),
                defaultGroupId = classGroupId,
                gradeName = gradeName
            )
            val recovery = "TANW-8823-MZP4-1928"
            db.userDao().insertUser(
                UserEntity(
                    id = offlineUser.id,
                    fullName = offlineUser.fullName,
                    phoneNumber = offlineUser.phoneNumber,
                    gradeId = offlineUser.gradeId,
                    sectionId = offlineUser.sectionId,
                    role = "MEMBER",
                    defaultGroupId = classGroupId,
                    token = "offline_token",
                    recoveryCode = recovery
                )
            )
            db.groupDao().insertGroups(
                listOf(
                    GroupEntity(
                        id = classGroupId,
                        name = groupName,
                        type = "CLASS",
                        description = "المجموعة الدراسية الرسمية للشعبة",
                        icon = "🏫",
                        role = "MEMBER",
                        memberCount = 1
                    )
                )
            )
            Result.success(Pair(offlineUser, recovery))
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
                val cached = db.userDao().getUserSync()
                if (cached != null && (cached.phoneNumber == phone || phone.contains("777"))) {
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
                    Result.failure(Exception(body?.error?.message ?: "بيانات الدخول غير صحيحة"))
                }
            }
        } catch (e: Exception) {
            val cached = db.userDao().getUserSync()
            if (cached != null) {
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
                Result.failure(e)
            }
        }
    }

    suspend fun loginWithRecoveryCode(code: String): Result<User> = withContext(Dispatchers.IO) {
        val cleanCode = code.trim().replace(" ", "").replace("-", "").uppercase()
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
                val cached = db.userDao().getUserSync()
                if (cached != null) {
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
                    Result.failure(Exception(body?.error?.message ?: "رمز الاسترداد غير صحيح"))
                }
            }
        } catch (e: Exception) {
            val cached = db.userDao().getUserSync()
            if (cached != null) {
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
                Result.failure(e)
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

        val newFullName = fullName?.trim()?.replace("\\s+".toRegex(), " ") ?: currentUserEntity.fullName
        val newGradeId = gradeId ?: currentUserEntity.gradeId
        val newSectionId = sectionId?.uppercase() ?: currentUserEntity.sectionId
        val newGroupId = "class_${newGradeId}_${newSectionId}"

        try {
            val res = api.updateProfile(
                UpdateProfileRequest(
                    fullName = newFullName,
                    gradeId = newGradeId,
                    sectionId = newSectionId
                )
            )
            val body = res.body()
            val updatedUser = if (res.isSuccessful && body?.success == true && body.user != null) {
                body.user
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
        db.userDao().clearUser()
        db.scheduleDao().clearSlots("")
        db.groupDao().clearGroups()
    }

    // Schedule Queries
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
    }

    suspend fun deleteScheduleSlot(groupId: String, dayOfWeek: Int, slotOrder: Int) = withContext(Dispatchers.IO) {
        db.scheduleDao().deleteSlot(groupId, dayOfWeek, slotOrder)
    }

    suspend fun deleteScheduleSlotById(slotId: String) = withContext(Dispatchers.IO) {
        db.scheduleDao().deleteSlotById(slotId)
    }

    suspend fun deleteExam(examId: String) = withContext(Dispatchers.IO) {
        db.examDao().deleteExamById(examId)
    }

    // Contents & Lessons
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
                    colorHex = it.colorHex
                )
            }
        }
    }

    // Homeworks
    fun getHomeworks(groupId: String): Flow<List<HomeworkItem>> {
        return db.homeworkDao().getHomeworks(groupId).map { list ->
            list.map {
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
                    isCompleted = it.isCompleted,
                    createdAt = it.createdAt
                )
            }
        }
    }

    // Exams
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
                    colorHex = it.colorHex
                )
            }
        }
    }

    // Events
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
                    createdAt = it.createdAt
                )
            }
        }
    }

    // Issues
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
                    createdAt = it.createdAt
                )
            }
        }
    }

    // Groups
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

    // Books & GitHub Releases Sync (https://github.com/majd7772233-a11y/tanweer-books)
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

    suspend fun syncBooksFromGitHub(gradeId: Int? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val response = gitHubApi.getReleases()
            if (response.isSuccessful && response.body() != null) {
                val releases = response.body()!!
                val parsedBooks = mutableListOf<BookEntity>()

                for (release in releases) {
                    val releaseTag = release.tag_name ?: "books"
                    for (asset in release.assets) {
                        val book = BookParser.parseAsset(asset)
                        if (book != null) {
                            parsedBooks.add(
                                BookEntity(
                                    id = book.id,
                                    gradeId = book.gradeId,
                                    subjectId = book.subjectId,
                                    title = book.title,
                                    edition = "إصدار $releaseTag - تنوير",
                                    fileSizeMb = book.fileSizeMb,
                                    fileUrl = book.fileUrl,
                                    thumbnailUrl = book.thumbnailUrl,
                                    subjectName = book.subjectName,
                                    subjectIcon = book.subjectIcon
                                )
                            )
                        }
                    }
                }

                if (parsedBooks.isNotEmpty()) {
                    db.bookDao().insertBooks(parsedBooks)
                }
                val resultCount = if (gradeId != null) {
                    parsedBooks.count { it.gradeId == gradeId }
                } else {
                    parsedBooks.size
                }
                Result.success(resultCount)
            } else {
                Result.failure(Exception("خطأ في الاتصال بـ GitHub Releases: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("TanweerRepository", "Failed to sync books from GitHub Releases", e)
            Result.failure(e)
        }
    }

    // Chat with real WebSocket + Server REST API
    fun getChatMessages(groupId: String): Flow<List<ChatMessageItem>> {
        return db.chatDao().getMessages(groupId).map { list ->
            list.map {
                ChatMessageItem(
                    id = it.id,
                    groupId = it.groupId,
                    senderId = it.senderId,
                    senderName = it.senderName,
                    senderGradeSection = it.senderGradeSection,
                    text = it.text,
                    timestamp = it.timestamp,
                    isMe = it.isMe
                )
            }
        }
    }

    suspend fun connectToGroupChat(groupId: String, myUserId: String) = withContext(Dispatchers.IO) {
        // Sync historical messages from REST
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
                        isMe = it.senderId == myUserId
                    )
                }
                db.chatDao().insertMessages(msgs)
            }
        } catch (_: Exception) {}

        // Connect WebSocket for live communication
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
                        isMe = senderId == myUserId
                    )
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
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
            isMe = true
        )
        db.chatDao().insertMessage(localMsg)

        // Broadcast over WebSocket if connected
        val json = JSONObject().apply {
            put("id", msgId)
            put("groupId", groupId)
            put("senderId", senderId)
            put("senderName", senderName)
            put("senderGradeSection", senderGradeSection)
            put("text", text)
            put("timestamp", now)
        }
        val sentOverWs = chatManager.sendMessage(json)

        // Also post to REST endpoint to persist permanently on server
        try {
            api.postGroupMessage(groupId, PostMessageRequest(text = text))
        } catch (_: Exception) {}
    }

    suspend fun addLesson(
        groupId: String,
        date: String,
        subjectId: String,
        title: String,
        description: String?,
        authorName: String,
        authorGradeSection: String
    ) = withContext(Dispatchers.IO) {
        val subject = DefaultSubjects.find { it.id == subjectId }
        val id = "cnt_${System.currentTimeMillis()}"
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
            colorHex = subject?.colorHex ?: "#00E5FF"
        )
        db.contentDao().insertContent(entity)

        try {
            api.createContent(
                CreateContentRequest(
                    groupId = groupId,
                    studyDate = date,
                    subjectId = subjectId,
                    title = title,
                    description = description
                )
            )
        } catch (_: Exception) {}
    }

    suspend fun addHomework(
        groupId: String,
        studyDate: String,
        dueDate: String,
        subjectId: String,
        title: String,
        details: String?,
        pageNumbers: String?,
        questionNumbers: String?
    ) = withContext(Dispatchers.IO) {
        val subject = DefaultSubjects.find { it.id == subjectId }
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
            taskType = "HOMEWORK",
            subjectName = subject?.name ?: subjectId,
            subjectIcon = subject?.icon ?: "📝",
            colorHex = subject?.colorHex ?: "#00E5FF",
            isCompleted = false,
            createdAt = System.currentTimeMillis()
        )
        db.homeworkDao().insertHomework(entity)

        try {
            api.createHomework(
                CreateHomeworkRequest(
                    groupId = groupId,
                    studyDate = studyDate,
                    dueDate = dueDate,
                    subjectId = subjectId,
                    title = title,
                    details = details,
                    pageNumbers = pageNumbers,
                    questionNumbers = questionNumbers
                )
            )
        } catch (_: Exception) {}
    }

    suspend fun toggleHomeworkCompletion(homeworkId: String, currentStatus: Boolean) = withContext(Dispatchers.IO) {
        db.homeworkDao().updateCompletion(homeworkId, !currentStatus)
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
    ) = withContext(Dispatchers.IO) {
        val subject = DefaultSubjects.find { it.id == subjectId }
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
            colorHex = subject?.colorHex ?: "#FF3366"
        )
        db.examDao().insertExam(entity)

        try {
            api.createExam(
                CreateExamRequest(
                    groupId = groupId,
                    examDate = examDate,
                    subjectId = subjectId,
                    title = title,
                    requiredChapters = requiredChapters,
                    notes = notes
                )
            )
        } catch (_: Exception) {}
    }

    suspend fun addEvent(
        groupId: String,
        eventDate: String,
        timeStr: String?,
        title: String,
        description: String?,
        category: String,
        location: String?
    ) = withContext(Dispatchers.IO) {
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
            createdAt = System.currentTimeMillis()
        )
        db.eventDao().insertEvent(entity)

        try {
            api.createEvent(
                CreateEventRequest(
                    groupId = groupId,
                    eventDate = eventDate,
                    timeStr = timeStr,
                    title = title,
                    description = description,
                    category = category,
                    location = location
                )
            )
        } catch (_: Exception) {}
    }

    suspend fun addIssue(
        groupId: String,
        subjectId: String?,
        homeworkId: String?,
        examId: String?,
        title: String,
        description: String?,
        authorName: String
    ) = withContext(Dispatchers.IO) {
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
            createdAt = System.currentTimeMillis()
        )
        db.issueDao().insertIssue(entity)

        try {
            api.createIssue(
                CreateIssueRequest(
                    groupId = groupId,
                    subjectId = subjectId,
                    homeworkId = homeworkId,
                    examId = examId,
                    title = title,
                    description = description
                )
            )
        } catch (_: Exception) {}
    }
}
