import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { errorResponse, jsonResponse } from '../lib/response';

export async function handleGetExams(groupId: string, env: Env): Promise<Response> {
  const exams = await env.DB.prepare(
    `SELECT e.id, e.group_id, e.exam_date, e.subject_id, e.title, e.required_chapters, e.notes, e.study_package_info,
            s.name_ar as subject_name, s.icon as subject_icon, s.color_hex
     FROM exams e
     JOIN subjects s ON e.subject_id = s.id
     WHERE e.group_id = ?
     ORDER BY e.exam_date ASC`
  ).bind(groupId).all();

  return jsonResponse({
    success: true,
    exams: exams.results,
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
    message: 'تم جدول الاختبار وإضافته للتقويم الدراسي',
  });
}
