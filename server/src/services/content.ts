import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';

interface MediaPayloadItem {
  url: string;
  pageOrder?: number;
  page_order?: number;
  objectKey?: string;
  mimeType?: string;
  mime_type?: string;
  fileSize?: number;
  file_size?: number;
  checksum?: string;
}

export async function handleCreateContent(user: UserContext, request: Request, env: Env): Promise<Response> {
  const body = await request.json() as {
    groupId?: string;
    studyDate?: string;
    date?: string;
    subjectId?: string;
    type?: string;
    title?: string;
    description?: string;
    mediaUrls?: string[];
    media?: MediaPayloadItem[];
  };

  const studyDate = body.studyDate || body.date;
  const mediaList: MediaPayloadItem[] = body.media || (body.mediaUrls ? body.mediaUrls.map((u, i) => ({ url: u, pageOrder: i + 1 })) : []);

  if (!body.groupId || !studyDate || !body.subjectId || !body.title) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد المجموعة، تاريخ الحصة، المادة وعنوان الدرس');
  }

  const memberCheck = await requireGroupMember(user, body.groupId, env.DB);
  if (memberCheck) return memberCheck;

  // Check duplicate checksum if media provided
  if (mediaList && mediaList.length > 0 && mediaList[0].checksum) {
    const duplicate = await env.DB.prepare(
      `SELECT cm.content_id, c.title, c.study_date
       FROM content_media cm
       JOIN contents c ON cm.content_id = c.id
       WHERE cm.checksum = ? AND c.group_id = ? AND c.study_date = ? AND c.subject_id = ?`
    ).bind(mediaList[0].checksum, body.groupId, studyDate, body.subjectId).first<{
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
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    contentId,
    body.groupId,
    studyDate,
    body.subjectId,
    body.type || 'LESSON',
    body.title.trim(),
    body.description?.trim() || null,
    user.userId,
    user.fullName,
    authorGradeSection,
    'PUBLISHED',
    now,
    now
  ).run();

  if (mediaList && mediaList.length > 0) {
    let order = 1;
    for (const m of mediaList) {
      const mediaId = generateId('med');
      const pageOrder = m.pageOrder || m.page_order || order;
      await env.DB.prepare(
        `INSERT INTO content_media (id, content_id, page_order, object_key, url, mime_type, file_size, checksum, is_primary, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      ).bind(
        mediaId,
        contentId,
        pageOrder,
        m.objectKey || '',
        m.url,
        m.mimeType || m.mime_type || 'image/jpeg',
        m.fileSize || m.file_size || 0,
        m.checksum || null,
        order === 1 ? 1 : 0,
        now
      ).run();
      order++;
    }
  }

  return jsonResponse({
    success: true,
    contentId,
    message: 'تم توثيق الدرس بنجاح ومشاركته مع المجموعة',
  });
}

export async function handleVoteUseful(contentId: string, user: UserContext, env: Env): Promise<Response> {
  const content = await env.DB.prepare(`SELECT group_id FROM contents WHERE id = ?`).bind(contentId).first<{ group_id: string }>();
  if (!content) {
    return errorResponse('CONTENT_NOT_FOUND', 'الدرس غير موجود');
  }

  const memberCheck = await requireGroupMember(user, content.group_id, env.DB);
  if (memberCheck) return memberCheck;

  await env.DB.prepare(`UPDATE contents SET useful_count = useful_count + 1 WHERE id = ?`).bind(contentId).run();
  return jsonResponse({ success: true, message: 'شكرًا لمساهمتك' });
}
