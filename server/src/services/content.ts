import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';
import { Permission, hasPermission } from '../lib/permissions';
import { mapContent } from '../lib/mappers';
import { cleanupOrphanMedia } from './media';

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
    id?: string;
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

  const contentId = (body.id && typeof body.id === 'string' && body.id.trim())
    ? body.id.trim()
    : generateId('cnt');

  // Idempotency check with ownership & scope validation
  const existing = await env.DB.prepare(`SELECT id, group_id, created_by FROM contents WHERE id = ?`).bind(contentId).first<{
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
      contentId: existing.id,
      message: 'تم توثيق الدرس بنجاح',
    });
  }

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

export async function handleUpdateContent(
  contentId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const content = await env.DB.prepare(
    `SELECT * FROM contents WHERE id = ?`
  ).bind(contentId).first<{
    id: string;
    group_id: string;
    created_by: string;
    title: string;
  }>();

  if (!content) {
    return errorResponse('CONTENT_NOT_FOUND', 'الدرس غير موجود', 404);
  }

  const memberCheck = await requireGroupMember(user, content.group_id, env.DB);
  if (memberCheck) return memberCheck;

  // 1. Ownership principle: Resource owner can edit their own content
  const isOwner = content.created_by === user.userId;
  // 2. Role permission: Teacher / Moderator / Admin can manage content
  const canManage = await hasPermission(user, Permission.MANAGE_CONTENT, {
    db: env.DB,
    groupId: content.group_id,
    resourceOwnerId: content.created_by,
  });

  if (!isOwner && !canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية تعديل هذا الدرس (يمكن لصاحب الدرس أو أستاذ المادة أو المشرف التعديل)', 403);
  }

  const body = await request.json() as {
    title?: string;
    description?: string;
    type?: string;
  };

  const now = Date.now();
  await env.DB.prepare(
    `UPDATE contents
     SET title = COALESCE(?, title),
         description = COALESCE(?, description),
         type = COALESCE(?, type),
         version = version + 1,
         updated_at = ?
     WHERE id = ?`
  ).bind(
    body.title?.trim() || null,
    body.description?.trim() || null,
    body.type || null,
    now,
    contentId
  ).run();

  return jsonResponse({
    success: true,
    message: 'تم تحديث بيانات الدرس بنجاح',
  });
}

export async function handleDeleteContent(
  contentId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const content = await env.DB.prepare(
    `SELECT * FROM contents WHERE id = ?`
  ).bind(contentId).first<{
    id: string;
    group_id: string;
    created_by: string;
  }>();

  if (!content) {
    return errorResponse('CONTENT_NOT_FOUND', 'الدرس غير موجود', 404);
  }

  const memberCheck = await requireGroupMember(user, content.group_id, env.DB);
  if (memberCheck) return memberCheck;

  // 1. Ownership: Resource owner can delete their own content
  const isOwner = content.created_by === user.userId;
  // 2. Role permission: Moderator / Admin / Teacher with DELETE_ANY_CONTENT
  const canDeleteAny = await hasPermission(user, Permission.DELETE_ANY_CONTENT, {
    db: env.DB,
    groupId: content.group_id,
    resourceOwnerId: content.created_by,
  });

  if (!isOwner && !canDeleteAny) {
    return errorResponse('FORBIDDEN', 'لا يمكنك حذف درس منشور من قِبل عضو آخر مباشرة — يمكنك تقديم طلب تصويت لحذفه', 403);
  }

  const candidates: string[] = [];
  try {
    const mediaRows = await env.DB.prepare(
      `SELECT object_key, url FROM content_media WHERE content_id = ?`
    ).bind(contentId).all<{ object_key: string; url: string }>();
    for (const r of mediaRows.results || []) {
      if (r.object_key) candidates.push(r.object_key);
      if (r.url) {
        const match = r.url.match(/([a-zA-Z0-9_\-]+)$/);
        if (match) candidates.push(match[1].replace(/\.[a-zA-Z0-9]+$/, ''));
      }
    }
  } catch {}

  await env.DB.prepare(`DELETE FROM contents WHERE id = ?`).bind(contentId).run();
  await env.DB.prepare(`DELETE FROM content_media WHERE content_id = ?`).bind(contentId).run();
  await env.DB.prepare(`DELETE FROM content_useful_votes WHERE content_id = ?`).bind(contentId).run();

  await cleanupOrphanMedia(env.DB, candidates);

  return jsonResponse({
    success: true,
    message: 'تم حذف الدرس بنجاح',
  });
}

export async function handleGetContents(
  groupId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const contents = await env.DB.prepare(
    `SELECT c.id, c.group_id, c.study_date, c.subject_id, c.type, c.title, c.description,
            c.author_name, c.author_grade_section, c.views_count, c.useful_count, c.created_at,
            s.name_ar as subject_name, s.icon as subject_icon, s.color_hex
     FROM contents c
     LEFT JOIN subjects s ON c.subject_id = s.id
     WHERE c.group_id = ? AND c.status = 'PUBLISHED'
     ORDER BY c.study_date DESC, c.created_at DESC`
  ).bind(groupId).all();

  const contentList = [];
  for (const c of (contents.results as any[])) {
    const media = await env.DB.prepare(
      `SELECT id, page_order, url, mime_type, file_size FROM content_media WHERE content_id = ? ORDER BY page_order ASC`
    ).bind(c.id).all();
    contentList.push(mapContent(c, media.results || []));
  }

  return jsonResponse({
    success: true,
    contents: contentList,
    totalCount: contentList.length,
  });
}

/**
 * Fetches the user's cross-grade academic archive (TA-26).
 * Returns all published contents authored by the user across any group (including historical/previous classes),
 * plus contents from groups where the user has active membership, without requiring current membership in previous groups.
 */
export async function handleGetAcademicArchive(
  user: UserContext,
  env: Env
): Promise<Response> {
  const contents = await env.DB.prepare(
    `SELECT c.id, c.group_id, c.study_date, c.subject_id, c.type, c.title, c.description,
            c.author_name, c.author_grade_section, c.views_count, c.useful_count, c.created_at,
            s.name_ar as subject_name, s.icon as subject_icon, s.color_hex,
            g.name as group_name
     FROM contents c
     LEFT JOIN subjects s ON c.subject_id = s.id
     LEFT JOIN groups g ON c.group_id = g.id
     WHERE c.status = 'PUBLISHED'
       AND (
         c.created_by = ?
         OR c.group_id IN (SELECT group_id FROM group_members WHERE user_id = ?)
       )
     ORDER BY c.study_date DESC, c.created_at DESC`
  ).bind(user.userId, user.userId).all();

  const contentList = [];
  for (const c of (contents.results as any[])) {
    const media = await env.DB.prepare(
      `SELECT id, page_order, url, mime_type, file_size FROM content_media WHERE content_id = ? ORDER BY page_order ASC`
    ).bind(c.id).all();
    contentList.push(mapContent(c, media.results || []));
  }

  return jsonResponse({
    success: true,
    contents: contentList,
    totalCount: contentList.length,
  });
}

