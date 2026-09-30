import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapExam } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';

export async function handleGetExams(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const exams = await env.DB.prepare(
    `SELECT e.id, e.group_id, e.exam_date, e.subject_id, e.title, e.required_chapters, e.notes, e.study_package_info,
            COALESCE(s.name_ar, e.subject_id) as subject_name,
            COALESCE(s.icon, '🔴') as subject_icon,
            s.color_hex
     FROM exams e
     LEFT JOIN subjects s ON e.subject_id = s.id
     WHERE e.group_id = ?
     ORDER BY e.exam_date ASC`
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
    subjectId?: string;
    title?: string;
    requiredChapters?: string;
    notes?: string;
  };

  if (!body.groupId || !body.examDate || !body.subjectId || !body.title) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد تفاصيل الاختبار والمادة وتاريخه');
  }

  const memberCheck = await requireGroupMember(user, body.groupId, env.DB);
  if (memberCheck) return memberCheck;

  const examId = generateId('exm');
  await env.DB.prepare(
    `INSERT INTO exams (id, group_id, exam_date, subject_id, title, required_chapters, notes, created_by, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    examId,
    body.groupId,
    body.examDate,
    body.subjectId,
    body.title.trim(),
    body.requiredChapters?.trim() || null,
    body.notes?.trim() || null,
    user.userId,
    Date.now()
  ).run();

  return jsonResponse({
    success: true,
    examId,
    message: 'تمت جدولة الاختبار وإضافته للتقويم الدراسي بنجاح',
  });
}
