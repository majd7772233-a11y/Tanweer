import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { errorResponse, jsonResponse } from '../lib/response';

export async function handleGetIssues(groupId: string, env: Env): Promise<Response> {
  const issues = await env.DB.prepare(
    `SELECT i.id, i.group_id, i.subject_id, i.homework_id, i.exam_id, i.title, i.description,
            i.status, i.author_name, i.created_at, i.updated_at,
            s.name_ar as subject_name, s.icon as subject_icon,
            (SELECT COUNT(*) FROM issue_comments ic WHERE ic.issue_id = i.id) as comments_count
     FROM issues i
     LEFT JOIN subjects s ON i.subject_id = s.id
     WHERE i.group_id = ?
     ORDER BY i.created_at DESC`
  ).bind(groupId).all();

  return jsonResponse({
    success: true,
    issues: issues.results,
  });
}

export async function handleGetIssueDetails(issueId: string, env: Env): Promise<Response> {
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

  const comments = await env.DB.prepare(
    `SELECT id, user_id, author_name, comment, is_best_answer, created_at
     FROM issue_comments
     WHERE issue_id = ?
     ORDER BY is_best_answer DESC, created_at ASC`
  ).bind(issueId).all();

  return jsonResponse({
    success: true,
    issue,
    comments: comments.results,
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
  const body = await request.json() as { comment?: string };
  if (!body.comment || !body.comment.trim()) {
    return errorResponse('INVALID_INPUT', 'يرجى كتابة نص الإجابة أو التعليق');
  }

  const commentId = generateId('com');
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO issue_comments (id, issue_id, user_id, author_name, comment, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`
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
    `SELECT created_by FROM issues WHERE id = ?`
  ).bind(issueId).first<{ created_by: string }>();

  if (!issue || (issue.created_by !== user.userId && user.role !== 'ADMIN')) {
    return errorResponse('FORBIDDEN', 'صاحب الاستفسار فقط يستطيع تحديد أفضل إجابة');
  }

  await env.DB.prepare(`UPDATE issue_comments SET is_best_answer = 0 WHERE issue_id = ?`).bind(issueId).run();
  await env.DB.prepare(`UPDATE issue_comments SET is_best_answer = 1 WHERE id = ?`).bind(commentId).run();
  await env.DB.prepare(`UPDATE issues SET status = 'SOLVED', best_comment_id = ?, updated_at = ? WHERE id = ?`)
    .bind(commentId, Date.now(), issueId).run();

  return jsonResponse({
    success: true,
    message: 'تم تمييز الإجابة كأفضل إجابة وحل الاستفسار ⭐',
  });
}
