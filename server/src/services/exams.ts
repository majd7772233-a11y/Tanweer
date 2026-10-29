import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapExam } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';
import { Permission, hasPermission } from '../lib/permissions';
import { isValidIsoDate } from '../lib/validation';

export async function handleGetExams(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const exams = await env.DB.prepare(
    `SELECT e.id, e.group_id, e.exam_date, e.subject_id, e.title, e.required_chapters, e.notes, e.study_package_info, e.is_official, e.created_by, e.created_at,
            e.exam_type,
            CASE WHEN ec.user_id IS NOT NULL THEN 1 ELSE 0 END as is_completed,
            COALESCE(s.name_ar, e.subject_id) as subject_name,
            COALESCE(s.icon, '🔴') as subject_icon,
            s.color_hex
     FROM exams e
     LEFT JOIN subjects s ON e.subject_id = s.id
     LEFT JOIN exam_completions ec ON e.id = ec.exam_id AND ec.user_id = ?
     WHERE e.group_id = ?
     ORDER BY e.is_official DESC, e.exam_date ASC`
  ).bind(user.userId, groupId).all();

  return jsonResponse({
    success: true,
    exams: (exams.results || []).map(mapExam),
  });
}

export async function handleCreateExam(user: UserContext, request: Request, env: Env): Promise<Response> {
  const body = await request.json() as {
    id?: string;
    groupId?: string;
    examDate?: string;
    date?: string;
    subjectId?: string;
    title?: string;
    requiredChapters?: string;
    topics?: string;
    notes?: string;
    isOfficial?: boolean;
    examType?: string;
  };

  const examDate = body.examDate || body.date;
  const chapters = body.requiredChapters || body.topics;
  const rawType = (body.examType || 'MONTHLY').trim().toUpperCase();
  const examType = ['MONTHLY', 'QUIZ', 'MIDTERM', 'FINAL'].includes(rawType) ? rawType : 'MONTHLY';

  if (!body.groupId || !examDate || !body.subjectId || !body.title) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد تفاصيل الاختبار والمادة وتاريخه');
  }

  if (!isValidIsoDate(examDate)) {
    return errorResponse('INVALID_INPUT', 'تاريخ الاختبار غير صالح، يرجى اختيار تاريخ تقويمي صحيح بصيغة YYYY-MM-DD');
  }

  const memberCheck = await requireGroupMember(user, body.groupId, env.DB);
  if (memberCheck) return memberCheck;

  const canPublishOfficial = await hasPermission(user, Permission.PUBLISH_OFFICIAL_EXAM, {
    db: env.DB,
    groupId: body.groupId,
  });

  const isOfficial = (body.isOfficial && canPublishOfficial) || canPublishOfficial ? 1 : 0;
  const examId = (body.id && typeof body.id === 'string' && body.id.trim())
    ? body.id.trim()
    : generateId('exm');

  // Idempotency check with ownership & scope validation
  const existing = await env.DB.prepare(`SELECT id, is_official, group_id, created_by, exam_type FROM exams WHERE id = ?`).bind(examId).first<{
    id: string;
    is_official: number;
    group_id: string;
    created_by: string;
    exam_type: string;
  }>();
  if (existing) {
    if (existing.group_id !== body.groupId || existing.created_by !== user.userId) {
      return errorResponse('CONFLICT', 'المعرّف مستخدم مسبقاً في نطاق آخر أو بواسطة مستخدم آخر', 409);
    }
    return jsonResponse({
      success: true,
      examId: existing.id,
      examType: existing.exam_type || 'MONTHLY',
      isOfficial: existing.is_official === 1,
      message: existing.is_official === 1
        ? 'تم اعتماد وتثبيت موعد الاختبار الرسمي في التقويم الدراسي 🔴'
        : 'تمت إضافة مقترح موعد الاختبار بنجاح ومشاركته مع الزملاء 📅',
    });
  }

  await env.DB.prepare(
    `INSERT INTO exams (id, group_id, exam_date, subject_id, title, required_chapters, notes, is_official, created_by, created_at, exam_type)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
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
    Date.now(),
    examType
  ).run();

  const msg = isOfficial === 1
    ? 'تم اعتماد وتثبيت موعد الاختبار الرسمي في التقويم الدراسي 🔴'
    : 'تمت إضافة مقترح موعد الاختبار بنجاح ومشاركته مع الزملاء 📅';

  return jsonResponse({
    success: true,
    examId,
    examType,
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
    examType?: string;
  };

  if (body.examDate !== undefined && !isValidIsoDate(body.examDate)) {
    return errorResponse('INVALID_INPUT', 'تاريخ الاختبار غير صالح، يرجى اختيار تاريخ تقويمي صحيح بصيغة YYYY-MM-DD');
  }

  const canPublishOfficial = await hasPermission(user, Permission.PUBLISH_OFFICIAL_EXAM, {
    db: env.DB,
    groupId: exam.group_id,
  });

  let officialVal: number | null = null;
  if (body.isOfficial !== undefined && canPublishOfficial) {
    officialVal = body.isOfficial ? 1 : 0;
  }

  const rawType = body.examType ? body.examType.trim().toUpperCase() : null;
  const examTypeVal = rawType && ['MONTHLY', 'QUIZ', 'MIDTERM', 'FINAL'].includes(rawType) ? rawType : null;

  await env.DB.prepare(
    `UPDATE exams
     SET exam_date = COALESCE(?, exam_date),
         title = COALESCE(?, title),
         required_chapters = COALESCE(?, required_chapters),
         notes = COALESCE(?, notes),
         is_official = COALESCE(?, is_official),
         exam_type = COALESCE(?, exam_type)
     WHERE id = ?`
  ).bind(
    body.examDate || null,
    body.title?.trim() || null,
    body.requiredChapters?.trim() || null,
    body.notes?.trim() || null,
    officialVal,
    examTypeVal,
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

export async function handleToggleExamCompletion(
  examId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const exam = await env.DB.prepare(`SELECT id, group_id FROM exams WHERE id = ?`).bind(examId).first<{
    id: string;
    group_id: string;
  }>();

  if (!exam) {
    return errorResponse('EXAM_NOT_FOUND', 'الاختبار غير موجود', 404);
  }

  const memberCheck = await requireGroupMember(user, exam.group_id, env.DB);
  if (memberCheck) return memberCheck;

  let requestedStatus: boolean | null = null;
  const contentType = request.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    try {
      const body = await request.json() as { isCompleted?: boolean };
      if (typeof body.isCompleted === 'boolean') {
        requestedStatus = body.isCompleted;
      }
    } catch (_: any) {}
  }

  if (requestedStatus !== null) {
    if (requestedStatus) {
      await env.DB.prepare(
        `INSERT OR IGNORE INTO exam_completions (exam_id, user_id, completed_at) VALUES (?, ?, ?)`
      ).bind(examId, user.userId, Date.now()).run();
    } else {
      await env.DB.prepare(
        `DELETE FROM exam_completions WHERE exam_id = ? AND user_id = ?`
      ).bind(examId, user.userId).run();
    }
    return jsonResponse({
      success: true,
      isCompleted: requestedStatus,
      message: requestedStatus ? 'تم تعليم الاختبار كمراجع ومكتمل ✓' : 'تمت إعادة الاختبار لقائمة المراجعة القادمة'
    });
  }

  const existing = await env.DB.prepare(
    `SELECT * FROM exam_completions WHERE exam_id = ? AND user_id = ?`
  ).bind(examId, user.userId).first();

  let isCompleted: boolean;
  if (existing) {
    await env.DB.prepare(`DELETE FROM exam_completions WHERE exam_id = ? AND user_id = ?`).bind(examId, user.userId).run();
    isCompleted = false;
  } else {
    await env.DB.prepare(
      `INSERT INTO exam_completions (exam_id, user_id, completed_at) VALUES (?, ?, ?)`
    ).bind(examId, user.userId, Date.now()).run();
    isCompleted = true;
  }

  return jsonResponse({
    success: true,
    isCompleted,
    message: isCompleted ? 'تم تعليم الاختبار كمراجع ومكتمل ✓' : 'تمت إعادة الاختبار لقائمة المراجعة القادمة'
  });
}
