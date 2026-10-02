import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapIssue, mapIssueComment } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';

export async function handleGetIssues(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const issues = await env.DB.prepare(
    `SELECT i.id, i.group_id, i.subject_id, i.homework_id, i.exam_id, i.title, i.description,
            i.status, i.best_comment_id, i.author_name, i.created_at, i.updated_at,
            s.name_ar as subject_name, s.icon as subject_icon,
            (SELECT COUNT(*) FROM issue_comments ic WHERE ic.issue_id = i.id) as comments_count
     FROM issues i
     LEFT JOIN subjects s ON i.subject_id = s.id
     WHERE i.group_id = ?
     ORDER BY i.created_at DESC`
  ).bind(groupId).all();

  return jsonResponse({
    success: true,
    issues: (issues.results || []).map(mapIssue),
  });
}

export async function handleGetIssueDetails(issueId: string, user: UserContext, env: Env): Promise<Response> {
  const issue = await env.DB.prepare(
    `SELECT i.id, i.group_id, i.subject_id, i.homework_id, i.exam_id, i.title, i.description,
            i.status, i.best_comment_id, i.author_name, i.created_by, i.created_at,
            s.name_ar as subject_name, s.icon as subject_icon
     FROM issues i
     LEFT JOIN subjects s ON i.subject_id = s.id
     WHERE i.id = ?`
  ).bind(issueId).first();

  if (!issue) {
    return errorResponse('ISSUE_NOT_FOUND', 'الاستفسار غير موجود');
  }

  const memberCheck = await requireGroupMember(user, (issue as any).group_id, env.DB);
  if (memberCheck) return memberCheck;

  const comments = await env.DB.prepare(
    `SELECT id, issue_id, user_id, author_name, comment, is_best_answer, created_at
     FROM issue_comments
     WHERE issue_id = ?
     ORDER BY is_best_answer DESC, created_at ASC`
  ).bind(issueId).all();

  const countRow = await env.DB.prepare(
    `SELECT COUNT(*) as c FROM issue_comments WHERE issue_id = ?`
  ).bind(issueId).first<{ c: number }>();

  const mappedIssue = mapIssue({
    ...issue,
    comments_count: countRow?.c || 0,
  });

  return jsonResponse({
    success: true,
    issue: mappedIssue,
    comments: (comments.results || []).map(mapIssueComment),
  });
}

export async function handleCreateIssue(user: UserContext, request: Request, env: Env): Promise<Response> {
  const body = await request.json() as {
    groupId?: string;
    subjectId?: string;
    homeworkId?: string;
    examId?: string;
    title?: string;
    description?: string;
  };

  if (!body.groupId || !body.title) {
    return errorResponse('INVALID_INPUT', 'يرجى كتابة عنوان الاستفسار');
  }

  const memberCheck = await requireGroupMember(user, body.groupId, env.DB);
  if (memberCheck) return memberCheck;

  const issueId = generateId('iss');
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO issues (id, group_id, subject_id, homework_id, exam_id, title, description, status, created_by, author_name, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?)`
  ).bind(
    issueId,
    body.groupId,
    body.subjectId || null,
    body.homeworkId || null,
    body.examId || null,
    body.title.trim(),
    body.description?.trim() || null,
    user.userId,
    user.fullName,
    now,
    now
  ).run();

  return jsonResponse({
    success: true,
    issueId,
    message: 'تم طرح الاستفسار بنجاح',
  });
}

export async function handleAddIssueComment(issueId: string, user: UserContext, request: Request, env: Env): Promise<Response> {
  const issue = await env.DB.prepare(`SELECT group_id FROM issues WHERE id = ?`).bind(issueId).first<{ group_id: string }>();
  if (!issue) {
    return errorResponse('ISSUE_NOT_FOUND', 'الاستفسار غير موجود');
  }

  const memberCheck = await requireGroupMember(user, issue.group_id, env.DB);
  if (memberCheck) return memberCheck;

  const body = await request.json() as { comment?: string };
  if (!body.comment || !body.comment.trim()) {
    return errorResponse('INVALID_INPUT', 'يرجى كتابة نص الإجابة أو التعليق');
  }

  const commentId = generateId('com');
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO issue_comments (id, issue_id, user_id, author_name, comment, is_best_answer, created_at)
     VALUES (?, ?, ?, ?, ?, 0, ?)`
  ).bind(
    commentId,
    issueId,
    user.userId,
    user.fullName,
    body.comment.trim(),
    now
  ).run();

  await env.DB.prepare(
    `UPDATE issues SET status = 'IN_DISCUSSION', updated_at = ? WHERE id = ? AND status = 'OPEN'`
  ).bind(now, issueId).run();

  return jsonResponse({
    success: true,
    commentId,
    message: 'تمت إضافة التعليق بنجاح',
  });
}

export async function handleMarkBestAnswer(issueId: string, commentId: string, user: UserContext, env: Env): Promise<Response> {
  const issue = await env.DB.prepare(
    `SELECT group_id, created_by FROM issues WHERE id = ?`
  ).bind(issueId).first<{ group_id: string; created_by: string }>();

  if (!issue) {
    return errorResponse('ISSUE_NOT_FOUND', 'الاستفسار غير موجود');
  }

  const memberCheck = await requireGroupMember(user, issue.group_id, env.DB);
  if (memberCheck) return memberCheck;

  if (issue.created_by !== user.userId && user.role !== 'ADMIN') {
    return errorResponse('FORBIDDEN', 'صاحب الاستفسار فقط يستطيع تحديد أفضل إجابة');
  }

  // Verify that the comment belongs to this issue
  const comment = await env.DB.prepare(
    `SELECT id FROM issue_comments WHERE id = ? AND issue_id = ?`
  ).bind(commentId, issueId).first();

  if (!comment) {
    return errorResponse('COMMENT_NOT_FOUND', 'التعليق غير موجود أو لا ينتمي لهذا الاستفسار');
  }

  await env.DB.prepare(`UPDATE issue_comments SET is_best_answer = 0 WHERE issue_id = ?`).bind(issueId).run();
  await env.DB.prepare(`UPDATE issue_comments SET is_best_answer = 1 WHERE id = ? AND issue_id = ?`).bind(commentId, issueId).run();
  await env.DB.prepare(`UPDATE issues SET status = 'SOLVED', best_comment_id = ?, updated_at = ? WHERE id = ?`)
    .bind(commentId, Date.now(), issueId).run();

  return jsonResponse({
    success: true,
    message: 'تم اعتماد الإجابة كحل معتمد للاستفسار ⭐',
  });
}
