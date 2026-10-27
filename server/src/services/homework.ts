import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapHomework } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';
import { Permission, hasPermission } from '../lib/permissions';
import { isValidIsoDate } from '../lib/validation';
import { cleanupOrphanMedia } from './media';

export async function handleGetHomeworks(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const homeworks = await env.DB.prepare(
    `SELECT h.id, h.group_id, h.study_date, h.due_date, h.subject_id, h.title, h.details,
            h.page_numbers, h.question_numbers, h.task_type, h.media_urls, h.is_official, h.created_by, h.created_at,
            COALESCE(s.name_ar, h.subject_id) as subject_name,
            COALESCE(s.icon, '📝') as subject_icon,
            s.color_hex,
            (SELECT 1 FROM homework_completions hc WHERE hc.homework_id = h.id AND hc.user_id = ?) as is_completed
     FROM homeworks h
     LEFT JOIN subjects s ON h.subject_id = s.id
     WHERE h.group_id = ?
     ORDER BY h.is_official DESC, h.due_date ASC, h.created_at DESC`
  ).bind(user.userId, groupId).all();

  return jsonResponse({
    success: true,
    homeworks: (homeworks.results || []).map(mapHomework),
  });
}

export async function handleCreateHomework(user: UserContext, request: Request, env: Env): Promise<Response> {
  const body = await request.json() as {
    id?: string;
    groupId?: string;
    studyDate?: string;
    dueDate?: string;
    subjectId?: string;
    title?: string;
    details?: string;
    pageNumbers?: string;
    questionNumbers?: string;
    taskType?: string;
    mediaUrls?: string[];
    isOfficial?: boolean;
  };

  if (!body.groupId || !body.dueDate || !body.subjectId || !body.title) {
    return errorResponse('INVALID_INPUT', 'يرجى ملء تفاصيل الواجب وتاريخ التسليم والمادة');
  }

  if (!isValidIsoDate(body.dueDate)) {
    return errorResponse('INVALID_INPUT', 'تاريخ تسليم الواجب غير صالح، يرجى اختيار تاريخ تقويمي صحيح بصيغة YYYY-MM-DD');
  }

  if (body.studyDate && !isValidIsoDate(body.studyDate)) {
    return errorResponse('INVALID_INPUT', 'تاريخ دراسة الواجب غير صالح، يرجى اختيار تاريخ تقويمي صحيح بصيغة YYYY-MM-DD');
  }

  const memberCheck = await requireGroupMember(user, body.groupId, env.DB);
  if (memberCheck) return memberCheck;

  // Check if publishing official homework is authorized
  const canPublishOfficial = await hasPermission(user, Permission.PUBLISH_OFFICIAL_HOMEWORK, {
    db: env.DB,
    groupId: body.groupId,
  });

  const isOfficial = (body.isOfficial && canPublishOfficial) || canPublishOfficial ? 1 : 0;

  const hwId = (body.id && typeof body.id === 'string' && body.id.trim())
    ? body.id.trim()
    : generateId('hw');

  // Idempotency check with ownership & scope validation
  const existing = await env.DB.prepare(`SELECT id, is_official, group_id, created_by FROM homeworks WHERE id = ?`).bind(hwId).first<{
    id: string;
    is_official: number;
    group_id: string;
    created_by: string;
  }>();
  if (existing) {
    if (existing.group_id !== body.groupId || existing.created_by !== user.userId) {
      return errorResponse('CONFLICT', 'المعرّف مستخدم مسبقاً في نطاق آخر أو بواسطة مستخدم آخر', 409);
    }
    return jsonResponse({
      success: true,
      homeworkId: existing.id,
      isOfficial: existing.is_official === 1,
      message: existing.is_official === 1
        ? 'تم نشر الواجب الرسمي المعتمد للشعبة بنجاح 🎓'
        : 'تمت إضافة مساهمة الواجب بنجاح ومشاركتها مع الزملاء 📝',
    });
  }

  const now = Date.now();
  const mediaUrlsJson = (body.mediaUrls && Array.isArray(body.mediaUrls) && body.mediaUrls.length > 0)
    ? JSON.stringify(body.mediaUrls)
    : null;

  await env.DB.prepare(
    `INSERT INTO homeworks (id, group_id, study_date, due_date, subject_id, title, details, page_numbers, question_numbers, task_type, media_urls, is_official, created_by, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
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
    mediaUrlsJson,
    isOfficial,
    user.userId,
    now
  ).run();

  // Populate relational homework_media table
  if (body.mediaUrls && Array.isArray(body.mediaUrls)) {
    for (let i = 0; i < body.mediaUrls.length; i++) {
      const url = body.mediaUrls[i];
      if (!url || typeof url !== 'string') continue;
      const mediaIdMatch = url.match(/([a-zA-Z0-9_\-]+)$/);
      const extractedMediaId = mediaIdMatch ? mediaIdMatch[1].replace(/\.[a-zA-Z0-9]+$/, '') : generateId('med');
      const hwMediaId = generateId('hwm');
      try {
        await env.DB.prepare(
          `INSERT INTO homework_media (id, homework_id, media_id, url, page_order, created_at)
           VALUES (?, ?, ?, ?, ?, ?)`
        ).bind(hwMediaId, hwId, extractedMediaId, url, i + 1, now).run();
      } catch {}
    }
  }

  const msg = isOfficial === 1
    ? 'تم نشر الواجب الرسمي المعتمد للشعبة بنجاح 🎓'
    : 'تمت إضافة مساهمة الواجب بنجاح ومشاركتها مع الزملاء 📝';

  return jsonResponse({
    success: true,
    homeworkId: hwId,
    isOfficial: isOfficial === 1,
    message: msg,
  });
}

export async function handleUpdateHomework(
  homeworkId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const hw = await env.DB.prepare(`SELECT * FROM homeworks WHERE id = ?`).bind(homeworkId).first<{
    id: string;
    group_id: string;
    created_by: string;
  }>();

  if (!hw) {
    return errorResponse('HOMEWORK_NOT_FOUND', 'الواجب غير موجود', 404);
  }

  const memberCheck = await requireGroupMember(user, hw.group_id, env.DB);
  if (memberCheck) return memberCheck;

  const isOwner = hw.created_by === user.userId;
  const canManage = await hasPermission(user, Permission.MANAGE_HOMEWORK, {
    db: env.DB,
    groupId: hw.group_id,
    resourceOwnerId: hw.created_by,
  });

  if (!isOwner && !canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية تعديل هذا الواجب', 403);
  }

  const body = await request.json() as {
    dueDate?: string;
    title?: string;
    details?: string;
    pageNumbers?: string;
    questionNumbers?: string;
    isOfficial?: boolean;
  };

  const canPublishOfficial = await hasPermission(user, Permission.PUBLISH_OFFICIAL_HOMEWORK, {
    db: env.DB,
    groupId: hw.group_id,
  });

  let officialVal: number | null = null;
  if (body.isOfficial !== undefined && canPublishOfficial) {
    officialVal = body.isOfficial ? 1 : 0;
  }

  await env.DB.prepare(
    `UPDATE homeworks
     SET title = COALESCE(?, title),
         details = COALESCE(?, details),
         due_date = COALESCE(?, due_date),
         page_numbers = COALESCE(?, page_numbers),
         question_numbers = COALESCE(?, question_numbers),
         is_official = COALESCE(?, is_official)
     WHERE id = ?`
  ).bind(
    body.title?.trim() || null,
    body.details?.trim() || null,
    body.dueDate || null,
    body.pageNumbers?.trim() || null,
    body.questionNumbers?.trim() || null,
    officialVal,
    homeworkId
  ).run();

  return jsonResponse({
    success: true,
    message: 'تم تحديث الواجب بنجاح',
  });
}

export async function handleDeleteHomework(
  homeworkId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const hw = await env.DB.prepare(`SELECT * FROM homeworks WHERE id = ?`).bind(homeworkId).first<{
    id: string;
    group_id: string;
    created_by: string;
  }>();

  if (!hw) {
    return errorResponse('HOMEWORK_NOT_FOUND', 'الواجب غير موجود', 404);
  }

  const memberCheck = await requireGroupMember(user, hw.group_id, env.DB);
  if (memberCheck) return memberCheck;

  const isOwner = hw.created_by === user.userId;
  const canManage = await hasPermission(user, Permission.MANAGE_HOMEWORK, {
    db: env.DB,
    groupId: hw.group_id,
    resourceOwnerId: hw.created_by,
  });

  if (!isOwner && !canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية حذف هذا الواجب', 403);
  }

  const candidateMediaIds: string[] = [];
  try {
    const hwMediaRows = await env.DB.prepare(
      `SELECT media_id FROM homework_media WHERE homework_id = ?`
    ).bind(homeworkId).all<{ media_id: string }>();
    for (const r of hwMediaRows.results || []) {
      if (r.media_id) candidateMediaIds.push(r.media_id);
    }
  } catch {}

  if (hw.media_urls) {
    try {
      const urls = JSON.parse(hw.media_urls);
      if (Array.isArray(urls)) {
        for (const u of urls) {
          const match = String(u).match(/([a-zA-Z0-9_\-]+)$/);
          if (match) candidateMediaIds.push(match[1].replace(/\.[a-zA-Z0-9]+$/, ''));
        }
      }
    } catch {}
  }

  await env.DB.prepare(`DELETE FROM homeworks WHERE id = ?`).bind(homeworkId).run();
  await env.DB.prepare(`DELETE FROM homework_completions WHERE homework_id = ?`).bind(homeworkId).run();
  try {
    await env.DB.prepare(`DELETE FROM homework_media WHERE homework_id = ?`).bind(homeworkId).run();
  } catch {}

  // Safely cleanup orphan media blobs that are no longer referenced anywhere
  await cleanupOrphanMedia(env.DB, candidateMediaIds);

  return jsonResponse({
    success: true,
    message: 'تم حذف الواجب بنجاح',
  });
}

export async function handleToggleHomeworkCompletion(
  homeworkId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const hw = await env.DB.prepare(`SELECT group_id FROM homeworks WHERE id = ?`).bind(homeworkId).first<{ group_id: string }>();
  if (!hw) {
    return errorResponse('HOMEWORK_NOT_FOUND', 'الواجب غير موجود');
  }

  const memberCheck = await requireGroupMember(user, hw.group_id, env.DB);
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
        `INSERT OR IGNORE INTO homework_completions (homework_id, user_id, completed_at) VALUES (?, ?, ?)`
      ).bind(homeworkId, user.userId, Date.now()).run();
    } else {
      await env.DB.prepare(
        `DELETE FROM homework_completions WHERE homework_id = ? AND user_id = ?`
      ).bind(homeworkId, user.userId).run();
    }
    return jsonResponse({ success: true, isCompleted: requestedStatus });
  }

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
