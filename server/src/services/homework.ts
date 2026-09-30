import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapHomework } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';

export async function handleGetHomeworks(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const homeworks = await env.DB.prepare(
    `SELECT h.id, h.group_id, h.study_date, h.due_date, h.subject_id, h.title, h.details,
            h.page_numbers, h.question_numbers, h.task_type, h.created_at,
            COALESCE(s.name_ar, h.subject_id) as subject_name,
            COALESCE(s.icon, '📝') as subject_icon,
            s.color_hex,
            (SELECT 1 FROM homework_completions hc WHERE hc.homework_id = h.id AND hc.user_id = ?) as is_completed
     FROM homeworks h
     LEFT JOIN subjects s ON h.subject_id = s.id
     WHERE h.group_id = ?
     ORDER BY h.due_date ASC, h.created_at DESC`
  ).bind(user.userId, groupId).all();

  return jsonResponse({
    success: true,
    homeworks: (homeworks.results || []).map(mapHomework),
  });
}

export async function handleCreateHomework(user: UserContext, request: Request, env: Env): Promise<Response> {
  const body = await request.json() as {
    groupId?: string;
    studyDate?: string;
    dueDate?: string;
    subjectId?: string;
    title?: string;
    details?: string;
    pageNumbers?: string;
    questionNumbers?: string;
    taskType?: string;
  };

  if (!body.groupId || !body.dueDate || !body.subjectId || !body.title) {
    return errorResponse('INVALID_INPUT', 'يرجى ملء تفاصيل الواجب وتاريخ التسليم');
  }

  const memberCheck = await requireGroupMember(user, body.groupId, env.DB);
  if (memberCheck) return memberCheck;

  const hwId = generateId('hw');
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO homeworks (id, group_id, study_date, due_date, subject_id, title, details, page_numbers, question_numbers, task_type, created_by, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    hwId,
    body.groupId,
    body.studyDate || new Date().toISOString().slice(0, 10),
    body.dueDate,
    body.subjectId,
    body.title.trim(),
    body.details?.trim() || null,
    body.pageNumbers?.trim() || null,
    body.questionNumbers?.trim() || null,
    body.taskType || 'HOMEWORK',
    user.userId,
    now
  ).run();

  return jsonResponse({
    success: true,
    homeworkId: hwId,
    message: 'تمت إضافة الواجب بنجاح',
  });
}

export async function handleToggleHomeworkCompletion(homeworkId: string, user: UserContext, env: Env): Promise<Response> {
  const hw = await env.DB.prepare(`SELECT group_id FROM homeworks WHERE id = ?`).bind(homeworkId).first<{ group_id: string }>();
  if (!hw) {
    return errorResponse('HOMEWORK_NOT_FOUND', 'الواجب غير موجود');
  }

  const memberCheck = await requireGroupMember(user, hw.group_id, env.DB);
  if (memberCheck) return memberCheck;

  const existing = await env.DB.prepare(
    `SELECT 1 FROM homework_completions WHERE homework_id = ? AND user_id = ?`
  ).bind(homeworkId, user.userId).first();

  if (existing) {
    await env.DB.prepare(
      `DELETE FROM homework_completions WHERE homework_id = ? AND user_id = ?`
    ).bind(homeworkId, user.userId).run();
    return jsonResponse({ success: true, isCompleted: false });
  } else {
    await env.DB.prepare(
      `INSERT INTO homework_completions (homework_id, user_id, completed_at) VALUES (?, ?, ?)`
    ).bind(homeworkId, user.userId, Date.now()).run();
    return jsonResponse({ success: true, isCompleted: true });
  }
}
