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
        contentId: duplicate.content_id,
        duplicateContentId: duplicate.content_id,
        message: 'هذا الدرس موثق بالفعل — جرى ربط المساهمة بالدرس الموثق مسبقاً',
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

  // Ensure table exists
  await env.DB.prepare(
    `CREATE TABLE IF NOT EXISTS content_useful_votes (
       user_id TEXT NOT NULL,
       content_id TEXT NOT NULL,
       created_at INTEGER NOT NULL,
       PRIMARY KEY (user_id, content_id)
     )`
  ).run();

  // Check if user already voted on this content
  const existingVote = await env.DB.prepare(
    `SELECT 1 FROM content_useful_votes WHERE user_id = ? AND content_id = ?`
  ).bind(user.userId, contentId).first();

  if (existingVote) {
    // Toggle off (remove vote)
    await env.DB.prepare(
      `DELETE FROM content_useful_votes WHERE user_id = ? AND content_id = ?`
    ).bind(user.userId, contentId).run();
    await env.DB.prepare(
      `UPDATE contents SET useful_count = MAX(0, useful_count - 1) WHERE id = ?`
    ).bind(contentId).run();
    return jsonResponse({ success: true, voted: false, message: 'تم إلغاء الإعجاب' });
  }

  // Register new vote
  await env.DB.prepare(
    `INSERT INTO content_useful_votes (user_id, content_id, created_at) VALUES (?, ?, ?)`
  ).bind(user.userId, contentId, Date.now()).run();
  await env.DB.prepare(`UPDATE contents SET useful_count = useful_count + 1 WHERE id = ?`).bind(contentId).run();

  return jsonResponse({ success: true, voted: true, message: 'شكرًا لمساهمتك، تم تسجيل تقييمك بنجاح 👍' });
}
