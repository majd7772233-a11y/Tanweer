package com.magd.tanweer.data.sync

import android.content.Context
import android.util.Log
import com.magd.tanweer.data.local.OutboxDao
import com.magd.tanweer.data.local.OutboxEntity
import com.magd.tanweer.data.local.TanweerDatabase
import com.magd.tanweer.data.remote.NetworkModule
import com.magd.tanweer.data.remote.PostMessageRequest
import com.magd.tanweer.data.remote.TanweerApiService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class SyncErrorInfo(
    val operation: String,
    val entityId: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Unified Mutation Queue (Point 41, 42, 43):
 * Provides a single, deterministic engine for all offline actions, mutations,
 * retries with exponential backoff, chat timeout reconciliation, and real error reporting.
 */
class UnifiedMutationQueue private constructor(
    private val db: TanweerDatabase,
    private val api: TanweerApiService = NetworkModule.apiService
) {
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var periodicJob: Job? = null

    private val _isDraining = MutableStateFlow(false)
    val isDraining: StateFlow<Boolean> = _isDraining.asStateFlow()

    private val _syncErrors = MutableSharedFlow<SyncErrorInfo>(extraBufferCapacity = 64)
    val syncErrors: SharedFlow<SyncErrorInfo> = _syncErrors.asSharedFlow()

    private val outboxDao: OutboxDao = db.outboxDao()

    @Volatile
    var isAutoSyncEnabled: Boolean = true

    @Volatile
    var isDrainAllowed: Boolean = true

    var onRetryHomework: (suspend (String) -> Result<Unit>)? = null
    var onRetryContent: (suspend (String) -> Result<Unit>)? = null

    init {
        start()
        scope.launch {
            try {
                val recovered = outboxDao.resetAllStuckSyncingOnStartup()
                if (recovered > 0) {
                    Log.i("UnifiedMutationQueue", "Recovered $recovered stuck SYNCING items on queue startup")
                }
            } catch (e: Exception) {
                Log.e("UnifiedMutationQueue", "Error recovering stuck syncing items on startup", e)
            }
        }
    }

    @Synchronized
    fun start() {
        if (periodicJob?.isActive == true) return
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
        periodicJob = scope.launch {
            while (isActive) {
                delay(30_000) // Every 30 seconds
                if (isAutoSyncEnabled) {
                    drainQueue()
                    reconcileChatMessages()
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        periodicJob?.cancel()
        periodicJob = null
        _isDraining.value = false
    }

    companion object {
        @Volatile
        private var INSTANCE: UnifiedMutationQueue? = null

        fun getInstance(
            db: TanweerDatabase,
            api: TanweerApiService = NetworkModule.apiService
        ): UnifiedMutationQueue {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UnifiedMutationQueue(db, api).also { INSTANCE = it }
            }
        }

        fun normalizeLegacyOperation(item: OutboxEntity): OutboxEntity {
            val payload = runCatching { JSONObject(item.payloadJson) }.getOrNull()

            val normalizedType = when {
                item.entityType == "HOMEWORK" && (payload?.has("isCompleted") == true || item.operation == "TOGGLE" || item.operation == "UPDATE") -> "HOMEWORK_TOGGLE"
                item.entityType == "HOMEWORK" && (item.operation == "CREATE" || payload?.has("title") == true) -> "HOMEWORK_CREATE"
                item.entityType == "EXAM" && (payload?.has("isCompleted") == true || item.operation == "TOGGLE") -> "EXAM_TOGGLE"
                item.entityType == "EXAM" && item.operation == "DELETE" -> "EXAM_DELETE"
                item.entityType == "EXAM" && (item.operation == "CREATE" || payload?.has("examDate") == true) -> "EXAM_CREATE"
                item.entityType == "CONTENT" || item.entityType == "LESSON" -> "LESSON_CREATE"
                item.entityType == "COMMENT" -> "ISSUE_COMMENT_CREATE"
                item.entityType == "ISSUE" -> "ISSUE_CREATE"
                item.entityType == "EVENT" -> "EVENT_CREATE"
                item.entityType == "CORRECTION" -> "CORRECTION_REQUEST"
                item.entityType == "SCHEDULE_SLOT" && item.operation == "DELETE" -> "SCHEDULE_SLOT_DELETE"
                item.entityType == "SCHEDULE_SLOT" -> "SCHEDULE_SLOT_UPDATE"
                item.entityType == "CHAT" || item.entityType == "MESSAGE" -> "CHAT_MESSAGE"
                else -> item.entityType
            }

            return if (normalizedType != item.entityType) {
                item.copy(entityType = normalizedType)
            } else {
                item
            }
        }
    }

    /**
     * Enqueues an offline action into the outbox with real error monitoring.
     */
    suspend fun enqueue(
        entityType: String,
        entityId: String,
        operation: String,
        payloadJson: String
    ): OutboxEntity = withContext(Dispatchers.IO) {
        val outboxItem = OutboxEntity(
            id = "ob_${entityType.lowercase()}_${entityId}_${System.currentTimeMillis()}",
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            payloadJson = payloadJson,
            status = "PENDING",
            attempts = 0,
            createdAt = System.currentTimeMillis()
        )
        outboxDao.insertOutbox(outboxItem)
        if (isAutoSyncEnabled) {
            triggerDrain()
        }
        outboxItem
    }

    fun triggerDrain() {
        scope.launch {
            drainQueue()
        }
    }

    /**
     * Drains all pending items in the outbox queue using unified execution and atomic reservation.
     */
    suspend fun drainQueue(): Int = withContext(Dispatchers.IO) {
        if (!isDrainAllowed) {
            Log.d("UnifiedMutationQueue", "Drain blocked: awaiting data_generation verification")
            return@withContext 0
        }
        if (_isDraining.value) return@withContext 0
        _isDraining.value = true
        var processedCount = 0

        try {
            // Recover any items that were reserved in SYNCING state by an interrupted worker
            // and exceeded the safe 60-second lease window.
            outboxDao.recoverStuckSyncingItems(now = System.currentTimeMillis(), timeoutMs = 60_000L)

            val pendingItems = outboxDao.getPendingItemsSync()
            for (rawItem in pendingItems) {
                val item = normalizeLegacyOperation(rawItem)

                // Exponential backoff check: 2s, 4s, 8s, 16s, max 60s
                val backoffMs = (1000L * (1 shl item.attempts.coerceAtMost(5))).coerceIn(2000L, 60_000L)
                val now = System.currentTimeMillis()
                if (item.attempts > 0 && (now - item.lastAttemptAt < backoffMs)) {
                    continue // Skip until backoff window elapses
                }

                // Atomic reservation: only proceed if this worker successfully claims the item
                val claimed = outboxDao.claimPendingItem(item.id, now)
                if (claimed <= 0) {
                    continue // Already claimed by concurrent worker
                }

                val success = executeMutation(item)
                if (success) processedCount++
            }
        } catch (e: Exception) {
            Log.e("UnifiedMutationQueue", "Error during outbox drain", e)
            _syncErrors.emit(
                SyncErrorInfo(
                    operation = "DRAIN_QUEUE",
                    entityId = "all",
                    message = e.localizedMessage ?: "حدث خطأ أثناء مزامنة البيانات غير المعلقة"
                )
            )
        } finally {
            _isDraining.value = false
        }
        processedCount
    }

    private suspend fun executeMutation(item: OutboxEntity): Boolean {
        return try {
            when (item.entityType) {
                "CHAT_MESSAGE" -> {
                    val obj = JSONObject(item.payloadJson)
                    val text = obj.optString("text", "")
                    val groupId = obj.optString("groupId", "")
                    val res = api.postGroupMessage(groupId, PostMessageRequest(id = item.entityId, text = text))
                    if (res.isSuccessful) {
                        db.chatDao().updateMessageStatus(item.entityId, "SENT")
                        outboxDao.deleteOutbox(item.id)
                        true
                    } else {
                        handleMutationFailure(item, "خطأ في إرسال الرسالة (${res.code()})")
                        false
                    }
                }
                "HOMEWORK_UPLOAD", "HOMEWORK_CREATE" -> {
                    val retryFunc = onRetryHomework
                    if (retryFunc != null) {
                        val res = retryFunc(item.entityId)
                        if (res.isSuccess) {
                            outboxDao.deleteOutbox(item.id)
                            true
                        } else {
                            handleMutationFailure(item, res.exceptionOrNull()?.message ?: "فشل مزامنة الواجب مع الخادم")
                            false
                        }
                    } else {
                        handleMutationFailure(item, "محرك رفع الواجبات غير مهيأ بعد")
                        false
                    }
                }
                "CONTENT_UPLOAD", "LESSON_CREATE" -> {
                    val retryFunc = onRetryContent
                    if (retryFunc != null) {
                        val res = retryFunc(item.entityId)
                        if (res.isSuccess) {
                            outboxDao.deleteOutbox(item.id)
                            true
                        } else {
                            handleMutationFailure(item, res.exceptionOrNull()?.message ?: "فشل مزامنة الدرس مع الخادم")
                            false
                        }
                    } else {
                        handleMutationFailure(item, "محرك رفع الدروس غير مهيأ بعد")
                        false
                    }
                }
                "HOMEWORK_TOGGLE" -> {
                    val obj = runCatching { JSONObject(item.payloadJson) }.getOrNull()
                    val isCompleted = if (obj != null && obj.has("isCompleted")) obj.optBoolean("isCompleted") else null
                    val req = isCompleted?.let { com.magd.tanweer.data.remote.ToggleCompletionRequest(it) }
                    val res = api.toggleHomework(item.entityId, req)
                    if (res.isSuccessful || res.code() == 404) {
                        outboxDao.deleteOutbox(item.id)
                        true
                    } else if (res.code() in listOf(401, 403)) {
                        val currentUserId = db.userDao().getUserSync()?.id ?: ""
                        if (currentUserId.isNotBlank() && isCompleted != null) {
                            if (isCompleted) {
                                db.homeworkDao().deleteCompletion(item.entityId, currentUserId)
                            } else {
                                db.homeworkDao().insertCompletion(com.magd.tanweer.data.local.HomeworkCompletionEntity(homeworkId = item.entityId, userId = currentUserId))
                            }
                        }
                        outboxDao.deleteOutbox(item.id)
                        false
                    } else {
                        handleMutationFailure(item, "فشل مزامنة حالة الواجب مع الخادم (${res.code()})")
                        false
                    }
                }
                "EXAM_TOGGLE" -> {
                    val obj = runCatching { JSONObject(item.payloadJson) }.getOrNull()
                    val isCompleted = if (obj != null && obj.has("isCompleted")) obj.optBoolean("isCompleted") else null
                    val req = isCompleted?.let { com.magd.tanweer.data.remote.ToggleCompletionRequest(it) }
                    val res = api.toggleExamCompletion(item.entityId, req)
                    if (res.isSuccessful || res.code() == 404) {
                        if (res.isSuccessful && isCompleted != null) {
                            db.examDao().updateExamCompletion(item.entityId, isCompleted)
                        }
                        outboxDao.deleteOutbox(item.id)
                        true
                    } else if (res.code() in listOf(401, 403)) {
                        if (isCompleted != null) {
                            db.examDao().updateExamCompletion(item.entityId, !isCompleted)
                        }
                        outboxDao.deleteOutbox(item.id)
                        false
                    } else {
                        handleMutationFailure(item, "فشل مزامنة حالة الاختبار مع الخادم (${res.code()})")
                        false
                    }
                }
                "EXAM_CREATE" -> {
                    val ex = db.examDao().getExamById(item.entityId)
                    val req: com.magd.tanweer.data.remote.CreateExamRequest? = if (ex != null) {
                        com.magd.tanweer.data.remote.CreateExamRequest(
                            id = ex.id,
                            groupId = ex.groupId,
                            examDate = ex.examDate,
                            subjectId = ex.subjectId,
                            title = ex.title,
                            requiredChapters = ex.requiredChapters,
                            notes = ex.notes,
                            examType = ex.examType
                        )
                    } else if (item.payloadJson.isNotBlank() && item.payloadJson != "{}") {
                        try {
                            val obj = JSONObject(item.payloadJson)
                            com.magd.tanweer.data.remote.CreateExamRequest(
                                id = item.entityId,
                                groupId = obj.optString("groupId", ""),
                                examDate = obj.optString("examDate", ""),
                                subjectId = obj.optString("subjectId", ""),
                                title = obj.optString("title", ""),
                                requiredChapters = obj.optString("requiredChapters", null),
                                notes = obj.optString("notes", null),
                                examType = obj.optString("examType", "MONTHLY")
                            )
                        } catch (_: Exception) { null }
                    } else {
                        null
                    }

                    if (req != null && req.groupId.isNotBlank() && req.title.isNotBlank()) {
                        val res = api.createExam(req)
                        if (res.isSuccessful) {
                            val serverId = res.body()?.examId ?: res.body()?.id
                            if (serverId != null && serverId != item.entityId) {
                                if (ex != null) {
                                    val updated = ex.copy(id = serverId, syncStatus = "SYNCED")
                                    db.examDao().replaceExamId(ex.id, updated)
                                }
                            } else {
                                db.examDao().updateSyncStatus(item.entityId, "SYNCED")
                            }
                            outboxDao.deleteOutbox(item.id)
                            true
                        } else {
                            handleMutationFailure(item, "فشل رفع الاختبار إلى الخادم (${res.code()})")
                            false
                        }
                    } else {
                        handleMutationFailure(item, "تعذر العثور على بيانات الاختبار محلياً (id: ${item.entityId})")
                        false
                    }
                }
                "EVENT_CREATE" -> {
                    val ev = db.eventDao().getEventById(item.entityId)
                    if (ev != null) {
                        val res = api.createEvent(
                            com.magd.tanweer.data.remote.CreateEventRequest(
                                id = ev.id,
                                groupId = ev.groupId,
                                title = ev.title,
                                description = ev.description ?: "",
                                category = ev.category ?: "",
                                eventDate = ev.eventDate,
                                timeStr = ev.timeStr ?: "",
                                location = ev.location
                            )
                        )
                        if (res.isSuccessful) {
                            db.eventDao().updateSyncStatus(ev.id, "SYNCED")
                            outboxDao.deleteOutbox(item.id)
                            true
                        } else {
                            handleMutationFailure(item, "فشل رفع الفعالية إلى الخادم (${res.code()})")
                            false
                        }
                    } else {
                        handleMutationFailure(item, "تعذر العثور على الفعالية محلياً للمزامنة (id: ${item.entityId})")
                        false
                    }
                }
                "ISSUE_CREATE" -> {
                    val issue = db.issueDao().getIssueSync(item.entityId)
                    if (issue != null) {
                        val res = api.createIssue(
                            com.magd.tanweer.data.remote.CreateIssueRequest(
                                id = issue.id,
                                groupId = issue.groupId,
                                subjectId = issue.subjectId,
                                homeworkId = issue.homeworkId,
                                examId = issue.examId,
                                title = issue.title,
                                description = issue.description
                            )
                        )
                        if (res.isSuccessful) {
                            db.issueDao().updateSyncStatus(issue.id, "SYNCED")
                            outboxDao.deleteOutbox(item.id)
                            true
                        } else {
                            handleMutationFailure(item, "فشل رفع السؤال إلى الخادم (${res.code()})")
                            false
                        }
                    } else {
                        handleMutationFailure(item, "تعذر العثور على السؤال محلياً للمزامنة (id: ${item.entityId})")
                        false
                    }
                }
                "ISSUE_COMMENT", "ISSUE_COMMENT_CREATE" -> {
                    val comment = db.issueDao().getCommentById(item.entityId)
                    if (comment != null) {
                        val res = api.addIssueComment(
                            issueId = comment.issueId,
                            req = com.magd.tanweer.data.remote.AddCommentRequest(id = comment.id, comment = comment.comment)
                        )
                        if (res.isSuccessful) {
                            db.issueDao().updateCommentSyncStatus(comment.id, "SYNCED")
                            outboxDao.deleteOutbox(item.id)
                            true
                        } else {
                            handleMutationFailure(item, "فشل إرسال التعليق (${res.code()})")
                            false
                        }
                    } else {
                        try {
                            val obj = JSONObject(item.payloadJson)
                            val issueId = obj.optString("issueId", "")
                            val commentText = obj.optString("comment", "")
                            if (issueId.isNotBlank() && commentText.isNotBlank()) {
                                val res = api.addIssueComment(issueId, com.magd.tanweer.data.remote.AddCommentRequest(id = item.entityId, comment = commentText))
                                if (res.isSuccessful) {
                                    outboxDao.deleteOutbox(item.id)
                                    true
                                } else {
                                    handleMutationFailure(item, "فشل إرسال التعليق (${res.code()})")
                                    false
                                }
                            } else {
                                handleMutationFailure(item, "تعذر قراءة بيانات التعليق للمزامنة")
                                false
                            }
                        } catch (_: Exception) {
                            handleMutationFailure(item, "صيغة بيانات التعليق غير صالحة")
                            false
                        }
                    }
                }
                "CORRECTION", "CORRECTION_REQUEST" -> {
                    val corr = db.correctionDao().getCorrectionById(item.entityId)
                    if (corr != null) {
                        val res = api.submitCorrection(
                            contentId = corr.contentId,
                            req = com.magd.tanweer.data.remote.SubmitCorrectionRequest(
                                fieldName = corr.fieldName,
                                originalValue = corr.originalValue,
                                proposedValue = corr.proposedValue,
                                reason = corr.reason
                            )
                        )
                        if (res.isSuccessful) {
                            outboxDao.deleteOutbox(item.id)
                            true
                        } else {
                            handleMutationFailure(item, "فشل رفع طلب التصحيح (${res.code()})")
                            false
                        }
                    } else {
                        handleMutationFailure(item, "تعذر العثور على طلب التصحيح محلياً (id: ${item.entityId})")
                        false
                    }
                }
                "EXAM_DELETE" -> {
                    val res = api.deleteExam(item.entityId)
                    if (res.isSuccessful || res.code() == 404) {
                        outboxDao.deleteOutbox(item.id)
                        true
                    } else {
                        handleMutationFailure(item, "فشل حذف الامتحان من الخادم (${res.code()})")
                        false
                    }
                }
                "SCHEDULE_SLOT_DELETE" -> {
                    val parts = item.entityId.split(":")
                    var gId = parts.getOrNull(0) ?: ""
                    var day = parts.getOrNull(1)?.toIntOrNull()
                    var order = parts.getOrNull(2)?.toIntOrNull()

                    if (day == null || order == null) {
                        try {
                            val obj = JSONObject(item.payloadJson)
                            gId = obj.optString("groupId", gId)
                            day = obj.optInt("dayOfWeek", 0)
                            order = obj.optInt("slotOrder", 0)
                        } catch (_: Exception) {}
                    }

                    if (gId.isNotBlank() && day != null && order != null) {
                        val res = api.deleteScheduleSlot(gId, day, order)
                        if (res.isSuccessful || res.code() == 404) {
                            outboxDao.deleteOutbox(item.id)
                            true
                        } else {
                            handleMutationFailure(item, "فشل حذف الحصة من الخادم (${res.code()})")
                            false
                        }
                    } else {
                        handleMutationFailure(item, "معرّف الحصة غير صالح للحذف: ${item.entityId}")
                        false
                    }
                }
                "SCHEDULE_SLOT_UPDATE" -> {
                    val slot = db.scheduleDao().getSlotById(item.entityId)
                    var gId = ""
                    var req: com.magd.tanweer.data.remote.CreateScheduleSlotRequest? = null
                    if (slot != null) {
                        gId = slot.groupId
                        req = com.magd.tanweer.data.remote.CreateScheduleSlotRequest(
                            dayOfWeek = slot.dayOfWeek,
                            slotOrder = slot.slotOrder,
                            subjectId = slot.subjectId,
                            subjectName = slot.subjectName,
                            subjectIcon = slot.subjectIcon,
                            colorHex = slot.colorHex,
                            startTime = slot.startTime,
                            endTime = slot.endTime
                        )
                    } else if (item.payloadJson.isNotBlank() && item.payloadJson != "{}") {
                        try {
                            val obj = JSONObject(item.payloadJson)
                            gId = obj.optString("groupId", "")
                            req = com.magd.tanweer.data.remote.CreateScheduleSlotRequest(
                                dayOfWeek = obj.optInt("dayOfWeek", 0),
                                slotOrder = obj.optInt("slotOrder", 0),
                                subjectId = obj.optString("subjectId", ""),
                                subjectName = obj.optString("subjectName", ""),
                                subjectIcon = obj.optString("subjectIcon", "📚"),
                                colorHex = obj.optString("colorHex", null),
                                startTime = obj.optString("startTime", null),
                                endTime = obj.optString("endTime", null)
                            )
                        } catch (_: Exception) {
                            gId = ""
                            req = null
                        }
                    } else {
                        gId = ""
                        req = null
                    }

                    if (req != null && gId.isNotBlank()) {
                        val res = api.createScheduleSlot(groupId = gId, req = req)
                        if (res.isSuccessful) {
                            if (slot != null) {
                                db.scheduleDao().insertSlots(listOf(slot.copy(syncStatus = "SYNCED")))
                            }
                            outboxDao.deleteOutbox(item.id)
                            true
                        } else {
                            handleMutationFailure(item, "فشل حفظ الحصة في الخادم (${res.code()})")
                            false
                        }
                    } else {
                        handleMutationFailure(item, "تعذر العثور على بيانات الحصة محلياً (id: ${item.entityId})")
                        false
                    }
                }
                else -> {
                    // CRITICAL: NEVER delete unhandled outbox items silently!
                    // Retain them with an explicit failure state, error logging, and exponential backoff retry.
                    Log.w("UnifiedMutationQueue", "Unknown mutation operation type: ${item.entityType} (id: ${item.id}). Preserving in outbox.")
                    handleMutationFailure(item, "نوع العملية غير معرّف في المحرك الموحد: ${item.entityType}")
                    false
                }
            }
        } catch (e: Exception) {
            handleMutationFailure(item, e.localizedMessage ?: "خطأ في الاتصال بالشبكة")
            false
        }
    }

    private suspend fun handleMutationFailure(item: OutboxEntity, errorMsg: String) {
        val nextAttempts = item.attempts + 1
        val status = if (nextAttempts >= 5) "FAILED" else "PENDING"
        outboxDao.updateOutboxStatus(item.id, status = status, now = System.currentTimeMillis(), error = errorMsg)

        if (item.entityType == "CHAT_MESSAGE") {
            db.chatDao().updateMessageStatus(item.entityId, "FAILED")
        }

        _syncErrors.emit(
            SyncErrorInfo(
                operation = item.operation,
                entityId = item.entityId,
                message = errorMsg
            )
        )
    }

    /**
     * Point 42: Reconciles messages stuck in SENDING state for longer than 15 seconds.
     * Prevents messages from staying in SENDING forever if WebSocket ACK was lost.
     */
    suspend fun reconcileChatMessages() = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val stuckThreshold = now - 15_000L // 15 seconds
            val sendingMessages = db.chatDao().getSendingMessagesSync(stuckThreshold)
            for (msg in sendingMessages) {
                // If message has exceeded 15s in SENDING, try fallback HTTP send or mark as FAILED
                try {
                    val res = api.postGroupMessage(msg.groupId, PostMessageRequest(id = msg.id, text = msg.text))
                    if (res.isSuccessful) {
                        db.chatDao().updateMessageStatus(msg.id, "SENT")
                    } else {
                        db.chatDao().updateMessageStatus(msg.id, "FAILED")
                    }
                } catch (e: Exception) {
                    db.chatDao().updateMessageStatus(msg.id, "FAILED")
                    Log.w("UnifiedMutationQueue", "Message ${msg.id} timed out and marked as FAILED for retry", e)
                }
            }
        } catch (e: Exception) {
            Log.e("UnifiedMutationQueue", "Error during chat message reconciliation", e)
        }
    }
}
