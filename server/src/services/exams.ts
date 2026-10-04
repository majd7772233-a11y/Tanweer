import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapExam } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';
import { Permission, hasPermission } from '../lib/permissions';

export async function handleGetExams(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const exams = await env.DB.prepare(
    `SELECT e.id, e.group_id, e.exam_date, e.subject_id, e.title, e.required_chapters, e.notes, e.study_package_info, e.is_official, e.created_by, e.created_at,
            COALESCE(s.name_ar, e.subject_id) as subject_name,
            COALESCE(s.icon, '🔴') as subject_icon,
            s.color_hex
     FROM exams e
     LEFT JOIN subjects s ON e.subject_id = s.id
     WHERE e.group_id = ?
     ORDER BY e.is_official DESC, e.exam_date ASC`
  ).bind(groupId).all();

  return jsonResponse({
    success: true,
    exams: (exams.results || []).map(mapExam),
  });
}

export async function handleCreateExam(user: UserContext, request: Request, env: Env): Promise<Response> {
  const body = await request.json() as {
    groupId?: string;
    examDate?: string;
    date?: string;
    subjectId?: string;
    title?: string;
    requiredChapters?: string;
    topics?: string;
    notes?: string;
    isOfficial?: boolean;
  };

  const examDate = body.examDate || body.date;
  const chapters = body.requiredChapters || body.topics;

  if (!body.groupId || !examDate || !body.subjectId || !body.title) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد تفاصيل الاختبار والمادة وتاريخه');
  }

  const memberCheck = await requireGroupMember(user, body.groupId, env.DB);
  if (memberCheck) return memberCheck;

  const canPublishOfficial = await hasPermission(user, Permission.PUBLISH_OFFICIAL_EXAM, {
    db: env.DB,
    groupId: body.groupId,
  });

  const isOfficial = (body.isOfficial && canPublishOfficial) || canPublishOfficial ? 1 : 0;
  const examId = generateId('exm');

  await env.DB.prepare(
    `INSERT INTO exams (id, group_id, exam_date, subject_id, title, required_chapters, notes, is_official, created_by, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    examId,
    body.groupId,
    examDate,
    body.subjectId,
    body.title.trim(),
    chapters?.trim() || null,
    body.notes?.trim() || null,
    isOfficial,
    user.userId,
    Date.now()
  ).run();

  const msg = isOfficial === 1
    ? 'تم اعتماد وتثبيت موعد الاختبار الرسمي في التقويم الدراسي 🔴'
    : 'تمت إضافة مقترح موعد الاختبار بنجاح ومشاركته مع الزملاء 📅';

  return jsonResponse({
    success: true,
    examId,
    isOfficial: isOfficial === 1,
    message: msg,
  });
}

export async function handleUpdateExam(
  examId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const exam = await env.DB.prepare(`SELECT * FROM exams WHERE id = ?`).bind(examId).first<{
    id: string;
    group_id: string;
    created_by: string;
  }>();

  if (!exam) {
    return errorResponse('EXAM_NOT_FOUND', 'الاختبار غير موجود', 404);
  }

  const memberCheck = await requireGroupMember(user, exam.group_id, env.DB);
  if (memberCheck) return memberCheck;

  const isOwner = exam.created_by === user.userId;
  const canManage = await hasPermission(user, Permission.MANAGE_EXAMS, {
    db: env.DB,
    groupId: exam.group_id,
    resourceOwnerId: exam.created_by,
  });

  if (!isOwner && !canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية تعديل هذا الاختبار', 403);
  }

  const body = await request.json() as {
    examDate?: string;
    title?: string;
    requiredChapters?: string;
    notes?: string;
    isOfficial?: boolean;
  };

  const canPublishOfficial = await hasPermission(user, Permission.PUBLISH_OFFICIAL_EXAM, {
    db: env.DB,
    groupId: exam.group_id,
  });

  let officialVal: number | null = null;
  if (body.isOfficial !== undefined && canPublishOfficial) {
    officialVal = body.isOfficial ? 1 : 0;
  }

  await env.DB.prepare(
    `UPDATE exams
     SET exam_date = COALESCE(?, exam_date),
         title = COALESCE(?, title),
         required_chapters = COALESCE(?, required_chapters),
         notes = COALESCE(?, notes),
         is_official = COALESCE(?, is_official)
     WHERE id = ?`
  ).bind(
    body.examDate || null,
    body.title?.trim() || null,
    body.requiredChapters?.trim() || null,
    body.notes?.trim() || null,
    officialVal,
    examId
  ).run();

  return jsonResponse({
    success: true,
    message: 'تم تحديث بيانات الاختبار بنجاح',
  });
}

export async function handleDeleteExam(
  examId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const exam = await env.DB.prepare(`SELECT * FROM exams WHERE id = ?`).bind(examId).first<{
    id: string;
    group_id: string;
    created_by: string;
  }>();

  if (!exam) {
    return errorResponse('EXAM_NOT_FOUND', 'الاختبار غير موجود', 404);
  }

  const memberCheck = await requireGroupMember(user, exam.group_id, env.DB);
  if (memberCheck) return memberCheck;

  const isOwner = exam.created_by === user.userId;
  const canManage = await hasPermission(user, Permission.MANAGE_EXAMS, {
    db: env.DB,
    groupId: exam.group_id,
    resourceOwnerId: exam.created_by,
  });

  if (!isOwner && !canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية حذف هذا الاختبار', 403);
  }

  await env.DB.prepare(`DELETE FROM exams WHERE id = ?`).bind(examId).run();

  return jsonResponse({
    success: true,
    message: 'تم حذف الاختبار بنجاح',
  });
}
