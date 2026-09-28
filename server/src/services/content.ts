import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { errorResponse, jsonResponse } from '../lib/response';

export async function handleCreateContent(user: UserContext, request: Request, env: Env): Promise<Response> {
  const body = await request.json() as {
    groupId?: string;
    studyDate?: string;
    subjectId?: string;
    type?: string;
    title?: string;
    description?: string;
    media?: Array<{
      url: string;
      objectKey?: string;
      mimeType?: string;
      fileSize?: number;
      checksum?: string;
    }>;
  };

  if (!body.groupId || !body.studyDate || !body.subjectId || !body.title) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد المجموعة، تاريخ الحصة، المادة وعنوان الدرس');
  }

  // Check duplicate checksum if media provided
  if (body.media && body.media.length > 0 && body.media[0].checksum) {
    const duplicate = await env.DB.prepare(
      `SELECT cm.content_id, c.title, c.study_date
       FROM content_media cm
       JOIN contents c ON cm.content_id = c.id
       WHERE cm.checksum = ? AND c.group_id = ? AND c.study_date = ? AND c.subject_id = ?`
    ).bind(body.media[0].checksum, body.groupId, body.studyDate, body.subjectId).first<{
      content_id: string;
      title: string;
    }>();

    if (duplicate) {
      return jsonResponse({
        success: true,
        isDuplicate: true,
        duplicateContentId: duplicate.content_id,
        message: 'تم رصد هذه الصورة مسبقًا في هذا الدرس وجرى تجميعها معه',
      });
    }
  }

  const contentId = generateId('cnt');
  const now = Date.now();
  const gradeNameMap: Record<number, string> = {
    7: 'سابع', 8: 'ثامن', 9: 'تاسع', 10: 'أول ثانوي', 11: 'ثاني ثانوي', 12: 'ثالث ثانوي'
  };
  const authorGradeSection = `${gradeNameMap[user.gradeId] || user.gradeId} — ${user.sectionId}`;

  await env.DB.prepare(
    `INSERT INTO contents (id, group_id, study_date, subject_id, type, title, description, created_by, author_name, author_grade_section, status, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, ?)`
  ).bind(
    contentId,
    body.groupId,
    body.studyDate,
    body.subjectId,
    body.type || 'LESSON',
    body.title.trim(),
    body.description?.trim() || null,
    user.userId,
    user.fullName,
    authorGradeSection,
    now,
    now
  ).run();

  if (body.media && body.media.length > 0) {
    let pageOrder = 1;
    for (const m of body.media) {
      const mediaId = generateId('med');
      await env.DB.prepare(
        `INSERT INTO content_media (id, content_id, page_order, object_key, url, mime_type, file_size, checksum, is_primary, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      ).bind(
        mediaId,
        contentId,
        pageOrder,
        m.objectKey || '',
        m.url,
        m.mimeType || 'image/jpeg',
        m.fileSize || 0,
        m.checksum || null,
        pageOrder === 1 ? 1 : 0,
        now
      ).run();
      pageOrder++;
    }
  }

  return jsonResponse({
    success: true,
    contentId,
    message: 'تم توثيق الدرس بنجاح ومشاركته مع المجموعة',
  });
}

export async function handleVoteUseful(contentId: string, user: UserContext, env: Env): Promise<Response> {
  await env.DB.prepare(`UPDATE contents SET useful_count = useful_count + 1 WHERE id = ?`).bind(contentId).run();
  return jsonResponse({ success: true, message: 'شكرًا لمساهمتك' });
}
