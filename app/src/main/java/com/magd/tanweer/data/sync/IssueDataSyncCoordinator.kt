package com.magd.tanweer.data.sync

import androidx.room.withTransaction
import com.magd.tanweer.data.local.IssueCommentEntity
import com.magd.tanweer.data.local.IssueEntity
import com.magd.tanweer.data.local.TanweerDatabase
import com.magd.tanweer.data.remote.TanweerApiService
import com.magd.tanweer.data.repository.ServerSnapshotValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

internal sealed class IssueDetailsRefreshResult {
    data class Success(val commentsCount: Int) : IssueDetailsRefreshResult()
    data class InvalidSnapshot(val message: String) : IssueDetailsRefreshResult()
    object Offline : IssueDetailsRefreshResult()
}

/**
 * Serializes every issue list/details read with comment and best-answer writes,
 * including outbox retries. The locked refresh is shared so background retries
 * cannot bypass the same snapshot validation/reconciliation used by the UI.
 */
internal object IssueDataSyncCoordinator {
    val mutex = Mutex()

    suspend fun refreshIssueDetailsLocked(
        db: TanweerDatabase,
        api: TanweerApiService,
        issueId: String
    ): IssueDetailsRefreshResult {
        return try {
            val response = api.getIssueDetails(issueId)
            val detail = response.body()
            if (!response.isSuccessful || detail?.success != true) {
                return IssueDetailsRefreshResult.Offline
            }

            val issue = detail.issue
            if (issue.id != issueId ||
                !ServerSnapshotValidation.hasUniqueNonBlankIds(detail.comments.map { it.id }) ||
                detail.comments.any { it.issueId != issueId }
            ) {
                return IssueDetailsRefreshResult.InvalidSnapshot(
                    "تفاصيل الاستفسار لا تطابق المعرف المطلوب أو تحتوي إجابات مكررة؛ احتُفظت البيانات المحلية"
                )
            }

            val comments = detail.comments.map {
                IssueCommentEntity(
                    id = it.id,
                    issueId = it.issueId,
                    userId = it.userId,
                    authorName = it.authorName,
                    comment = it.comment,
                    isBestAnswer = it.isBestAnswer || issue.bestCommentId == it.id,
                    isTeacherAnswer = it.isTeacherAnswer,
                    createdAt = it.createdAt,
                    syncStatus = "SYNCED"
                )
            }

            db.withTransaction {
                db.issueDao().replaceSyncedComments(issueId, comments)
                val current = db.issueDao().getIssueSync(issueId)
                val hasLocalMutation = current?.syncStatus in
                    setOf("PENDING", "SYNCING", "FAILED", "DELETE_PENDING")
                if (!hasLocalMutation) {
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
                            createdAt = issue.createdAt,
                            syncStatus = "SYNCED"
                        )
                    )
                }
            }
            IssueDetailsRefreshResult.Success(comments.size)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            IssueDetailsRefreshResult.Offline
        }
    }
}
