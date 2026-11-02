import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapIssue, mapIssueComment } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';
import { Permission, hasPermission } from '../lib/permissions';
import { Role, normalizeRole } from '../lib/roles';

async function isApprovedTeacherForGroup(user: UserContext, groupId: string, env: Env): Promise<boolean> {
  if (normalizeRole(user.role) === Role.TEACHER) return true;
  const membership = await env.DB.prepare(
    `SELECT role FROM group_members WHERE group_id = ? AND user_id = ? AND status = 'ACTIVE'`
  ).bind(groupId, user.userId).first<{ role: string }>();
  return !!membership && normalizeRole(membership.role) === Role.TEACHER;
}

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
    id?: string;
    groupId?: string;
    subjectId?: string;
    homeworkId?: string;
    examId?: string;
    title?: string;
    description?: string;
  };

  if (!body.groupId || typeof body.title !== 'string' || !body.title.trim()) {
    return errorResponse('INVALID_INPUT', 'يرجى كتابة عنوان الاستفسار');
  }

  const memberCheck = await requireGroupMember(user, body.groupId, env.DB);
  if (memberCheck) return memberCheck;

  // Linked academic records must belong to the same group as the issue.
  // Issues has no foreign keys for these optional references, so enforce scope here.
  if (body.subjectId) {
    const subject = await env.DB.prepare(
      `SELECT id FROM subjects WHERE id = ?`
    ).bind(body.subjectId).first<{ id: string }>();
    if (!subject) {
      return errorResponse('SUBJECT_NOT_FOUND', 'المادة المحددة غير موجودة', 404);
    }
  }

  if (body.homeworkId) {
    const homework = await env.DB.prepare(
      `SELECT id FROM homeworks WHERE id = ? AND group_id = ?`
    ).bind(body.homeworkId, body.groupId).first<{ id: string }>();
    if (!homework) {
      return errorResponse('LINKED_HOMEWORK_NOT_FOUND', 'الواجب المرتبط غير موجود في هذه المجموعة', 404);
    }
  }

  if (body.examId) {
    const exam = await env.DB.prepare(
      `SELECT id FROM exams WHERE id = ? AND group_id = ?`
    ).bind(body.examId, body.groupId).first<{ id: string }>();
    if (!exam) {
      return errorResponse('LINKED_EXAM_NOT_FOUND', 'الاختبار المرتبط غير موجود في هذه المجموعة', 404);
    }
  }

  const issueId = (body.id && typeof body.id === 'string' && body.id.trim())
    ? body.id.trim()
    : generateId('iss');

  // Idempotency check with ownership & scope validation
  const existing = await env.DB.prepare(`SELECT id, group_id, created_by FROM issues WHERE id = ?`).bind(issueId).first<{
    id: string;
    group_id: string;
    created_by: string;
  }>();
  if (existing) {
    if (existing.group_id !== body.groupId || existing.created_by !== user.userId) {
      return errorResponse('CONFLICT', 'المعرّف مستخدم مسبقاً في نطاق آخر أو بواسطة مستخدم آخر', 409);
    }
    return jsonResponse({
      success: true,
      issueId: existing.id,
      message: 'تم طرح الاستفسار بنجاح',
    });
  }

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

  const body = await request.json() as { id?: string; comment?: string };
  if (!body.comment || !body.comment.trim()) {
    return errorResponse('INVALID_INPUT', 'يرجى كتابة نص الإجابة أو التعليق');
  }

  // The teacher marker is reserved for a globally or group-approved TEACHER.
  const isTeacher = await isApprovedTeacherForGroup(user, issue.group_id, env);

  const commentId = (body.id && typeof body.id === 'string' && body.id.trim())
    ? body.id.trim()
    : generateId('com');
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO issue_comments (id, issue_id, user_id, author_name, comment, is_best_answer, is_teacher_answer, author_role, created_at)
     VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?)`
  ).bind(
    commentId,
    issueId,
    user.userId,
    user.fullName,
    body.comment.trim(),
    isTeacher ? 1 : 0,
    user.role,
    now
  ).run();

  await env.DB.prepare(
    `UPDATE issues SET status = 'IN_DISCUSSION', updated_at = ? WHERE id = ? AND status = 'OPEN'`
  ).bind(now, issueId).run();

  const msg = isTeacher
    ? 'تمت إضافة إجابة الأستاذ المعتمدة بنجاح 🎓'
    : 'تمت إضافة التعليق بنجاح 💬';

  return jsonResponse({
    success: true,
    commentId,
    isTeacherAnswer: isTeacher,
    message: msg,
  });
}

export async function handleDeleteIssueComment(
  issueId: string,
  commentId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const comment = await env.DB.prepare(
    `SELECT ic.*, i.group_id FROM issue_comments ic JOIN issues i ON ic.issue_id = i.id WHERE ic.id = ? AND ic.issue_id = ?`
  ).bind(commentId, issueId).first<{
    id: string;
    user_id: string;
    group_id: string;
  }>();

  if (!comment) {
    return errorResponse('COMMENT_NOT_FOUND', 'التعليق غير موجود', 404);
  }

  const memberCheck = await requireGroupMember(user, comment.group_id, env.DB);
  if (memberCheck) return memberCheck;

  const isOwner = comment.user_id === user.userId;
  const canManage = await hasPermission(user, Permission.MANAGE_ISSUES, {
    db: env.DB,
    groupId: comment.group_id,
    resourceOwnerId: comment.user_id,
  });

  if (!isOwner && !canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية حذف هذا التعليق', 403);
  }

  await env.DB.prepare(`DELETE FROM issue_comments WHERE id = ?`).bind(commentId).run();

  return jsonResponse({
    success: true,
    message: 'تم حذف التعليق بنجاح',
  });
}

export async function handleDeleteIssue(
  issueId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const issue = await env.DB.prepare(`SELECT * FROM issues WHERE id = ?`).bind(issueId).first<{
    id: string;
    group_id: string;
    created_by: string;
  }>();

  if (!issue) {
    return errorResponse('ISSUE_NOT_FOUND', 'الاستفسار غير موجود', 404);
  }

  const memberCheck = await requireGroupMember(user, issue.group_id, env.DB);
  if (memberCheck) return memberCheck;

  const isOwner = issue.created_by === user.userId;
  const canManage = await hasPermission(user, Permission.MANAGE_ISSUES, {
    db: env.DB,
    groupId: issue.group_id,
    resourceOwnerId: issue.created_by,
  });

  if (!isOwner && !canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية حذف هذا الاستفسار', 403);
  }

  await env.DB.prepare(`DELETE FROM issues WHERE id = ?`).bind(issueId).run();
  await env.DB.prepare(`DELETE FROM issue_comments WHERE issue_id = ?`).bind(issueId).run();

  return jsonResponse({
    success: true,
    message: 'تم حذف الاستفسار بنجاح',
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

  const actorRole = normalizeRole(user.role);
  const isIssueAuthor = issue.created_by === user.userId;
  const isSchoolAdmin = actorRole === Role.ADMIN || actorRole === Role.SYSTEM_OWNER;
  const isApprovedTeacher = isSchoolAdmin ? false : await isApprovedTeacherForGroup(user, issue.group_id, env);
  if (!isIssueAuthor && !isSchoolAdmin && !isApprovedTeacher) {
    return errorResponse('FORBIDDEN', 'اعتماد الحل متاح لصاحب الاستفسار أو الأستاذ المعتمد أو المدير فقط', 403);
  }

  // Verify that the comment belongs to this issue
  const comment = await env.DB.prepare(
    `SELECT id FROM issue_comments WHERE id = ? AND issue_id = ?`
  ).bind(commentId, issueId).first();

  if (!comment) {
    return errorResponse('COMMENT_NOT_FOUND', 'التعليق غير موجود أو لا ينتمي لهذا الاستفسار', 404);
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
