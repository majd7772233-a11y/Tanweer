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
class UnifiedMutationQueue(
    private val db: TanweerDatabase,
    private val api: TanweerApiService = NetworkModule.apiService
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isDraining = MutableStateFlow(false)
    val isDraining: StateFlow<Boolean> = _isDraining.asStateFlow()

    private val _syncErrors = MutableSharedFlow<SyncErrorInfo>(extraBufferCapacity = 64)
    val syncErrors: SharedFlow<SyncErrorInfo> = _syncErrors.asSharedFlow()

    private val outboxDao: OutboxDao = db.outboxDao()

    init {
        // Periodic background drain and reconciliation
        scope.launch {
            while (isActive) {
                delay(30_000) // Every 30 seconds
                drainQueue()
                reconcileChatMessages()
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
        triggerDrain()
        outboxItem
    }

    fun triggerDrain() {
        scope.launch {
            drainQueue()
        }
    }

    /**
     * Drains all pending items in the outbox queue using unified execution and exponential backoff.
     */
    suspend fun drainQueue() = withContext(Dispatchers.IO) {
        if (_isDraining.value) return@withContext
        _isDraining.value = true

        try {
            val pendingItems = outboxDao.getPendingItemsSync()
            for (item in pendingItems) {
                // Exponential backoff check: 2s, 4s, 8s, 16s, max 60s
                val backoffMs = (1000L * (1 shl item.attempts.coerceAtMost(5))).coerceIn(2000L, 60_000L)
                val now = System.currentTimeMillis()
                if (item.attempts > 0 && (now - item.lastAttemptAt < backoffMs)) {
                    continue // Skip until backoff window elapses
                }

                executeMutation(item)
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
    }

    private suspend fun executeMutation(item: OutboxEntity) {
        try {
            when (item.entityType) {
                "CHAT_MESSAGE" -> {
                    val obj = JSONObject(item.payloadJson)
                    val text = obj.optString("text", "")
                    val groupId = obj.optString("groupId", "")
                    val res = api.postGroupMessage(groupId, PostMessageRequest(id = item.entityId, text = text))
                    if (res.isSuccessful) {
                        db.chatDao().updateMessageStatus(item.entityId, "SENT")
                        outboxDao.deleteOutbox(item.id)
                    } else {
                        handleMutationFailure(item, "خطأ في إرسال الرسالة (${res.code()})")
                    }
                }
                "HOMEWORK_UPLOAD" -> {
                    // Handled by TanweerRepository sync engine or custom uploader
                    outboxDao.deleteOutbox(item.id)
                }
                "CONTENT_UPLOAD" -> {
                    outboxDao.deleteOutbox(item.id)
                }
                "ISSUE_COMMENT" -> {
                    val obj = JSONObject(item.payloadJson)
                    val issueId = obj.optString("issueId", "")
                    val comment = obj.optString("comment", "")
                    val res = api.addIssueComment(issueId, com.magd.tanweer.data.remote.AddCommentRequest(comment = comment))
                    if (res.isSuccessful) {
                        db.issueDao().updateCommentSyncStatus(item.entityId, "SYNCED")
                        outboxDao.deleteOutbox(item.id)
                    } else {
                        handleMutationFailure(item, "فشل إرسال التعليق (${res.code()})")
                    }
                }
                "EXAM_DELETE" -> {
                    val res = api.deleteExam(item.entityId)
                    if (res.isSuccessful || res.code() == 404) {
                        outboxDao.deleteOutbox(item.id)
                    } else {
                        handleMutationFailure(item, "فشل حذف الامتحان من الخادم (${res.code()})")
                    }
                }
                else -> {
                    outboxDao.deleteOutbox(item.id)
                }
            }
        } catch (e: Exception) {
            handleMutationFailure(item, e.localizedMessage ?: "خطأ في الاتصال بالشبكة")
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
