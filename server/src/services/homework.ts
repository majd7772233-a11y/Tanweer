import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapHomework } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';
import { Permission, hasPermission } from '../lib/permissions';
import { isValidIsoDate } from '../lib/validation';
import { cleanupOrphanMedia, validateMediaAttachmentAccess } from './media';

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

async function createHomeworkMediaIdentity(
  homeworkId: string,
  pageOrder: number,
  url: string
): Promise<{ linkId: string; mediaId: string }> {
  const digest = await crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(homeworkId + '\u0000' + pageOrder + '\u0000' + url)
  );
  const digestHex = Array.from(new Uint8Array(digest), byte =>
    byte.toString(16).padStart(2, '0')
  ).join('');
  const internalMediaId = url.match(/\/api\/v1\/media\/([A-Za-z0-9_-]+)(?:\.[A-Za-z0-9]+)?\/?$/i)?.[1];

  return {
    linkId: 'hwm_' + digestHex.slice(0, 40),
    mediaId: internalMediaId || 'external_' + digestHex,
  };
}

function parseHomeworkMediaUrls(value: string | null | undefined): string[] | null {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    if (!Array.isArray(parsed) || parsed.some(item => typeof item !== 'string')) return null;
    return parsed;
  } catch {
    return null;
  }
}

function homeworkMetadataMatches(
  existing: {
    group_id: string;
    created_by: string;
    study_date: string;
    due_date: string;
    subject_id: string;
    title: string;
    details: string | null;
    page_numbers: string | null;
    question_numbers: string | null;
    task_type: string;
    media_urls: string | null;
  },
  expected: {
    groupId: string;
    creatorId: string;
    studyDate: string;
    dueDate: string;
    subjectId: string;
    title: string;
    details: string | null;
    pageNumbers: string | null;
    questionNumbers: string | null;
    taskType: string;
    mediaUrlsJson: string | null;
  }
): boolean {
  const storedMediaUrls = parseHomeworkMediaUrls(existing.media_urls);
  const requestedMediaUrls = expected.mediaUrlsJson
    ? parseHomeworkMediaUrls(expected.mediaUrlsJson)
    : [];

  return existing.group_id === expected.groupId &&
    existing.created_by === expected.creatorId &&
    existing.study_date === expected.studyDate &&
    existing.due_date === expected.dueDate &&
    existing.subject_id === expected.subjectId &&
    existing.title === expected.title &&
    (existing.details || null) === expected.details &&
    (existing.page_numbers || null) === expected.pageNumbers &&
    (existing.question_numbers || null) === expected.questionNumbers &&
    existing.task_type === expected.taskType &&
    storedMediaUrls !== null &&
    requestedMediaUrls !== null &&
    JSON.stringify(storedMediaUrls) === JSON.stringify(requestedMediaUrls);
}

async function getHomeworkMediaInsertStatement(
  env: Env,
  homeworkId: string,
  groupId: string,
  creatorId: string,
  pageOrder: number,
  url: string,
  createdAt: number
) {
  const identity = await createHomeworkMediaIdentity(homeworkId, pageOrder, url);
  return env.DB.prepare(
    `INSERT OR IGNORE INTO homework_media (id, homework_id, media_id, url, page_order, created_at)
     SELECT ?, ?, ?, ?, ?, ?
     WHERE EXISTS (
       SELECT 1 FROM homeworks
       WHERE id = ? AND group_id = ? AND created_by = ?
     )
       AND NOT EXISTS (
         SELECT 1 FROM homework_media
         WHERE homework_id = ? AND page_order = ?
       )`
  ).bind(
    identity.linkId,
    homeworkId,
    identity.mediaId,
    url,
    pageOrder,
    createdAt,
    homeworkId,
    groupId,
    creatorId,
    homeworkId,
    pageOrder
  );
}

async function verifyHomeworkMediaSnapshot(
  env: Env,
  homeworkId: string,
  expectedUrls: string[],
  identities: Array<{ pageOrder: number; mediaId: string; url: string }>
): Promise<boolean> {
  const response = await env.DB.prepare(
    `SELECT media_id, url, page_order
     FROM homework_media
     WHERE homework_id = ?
     ORDER BY page_order ASC`
  ).bind(homeworkId).all<{
    media_id: string;
    url: string;
    page_order: number;
  }>();

  const rows = response.results || [];
  if (rows.length !== expectedUrls.length || rows.length !== identities.length) return false;

  const seenOrders = new Set<number>();
  for (const row of rows) {
    if (!Number.isInteger(row.page_order) || row.page_order < 1 || seenOrders.has(row.page_order)) {
      return false;
    }
    seenOrders.add(row.page_order);
    const expected = identities.find(item => item.pageOrder === row.page_order);
    if (!expected || expected.url !== row.url || expected.mediaId !== row.media_id) return false;
  }
  return true;
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

  if (!body.groupId || !body.dueDate || !body.subjectId || !body.title?.trim()) {
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

  if (body.mediaUrls !== undefined && (
    !Array.isArray(body.mediaUrls) ||
    body.mediaUrls.length > 200 ||
    body.mediaUrls.some(url => typeof url !== 'string' || !url.trim())
  )) {
    return errorResponse('INVALID_MEDIA', 'قائمة مرفقات الواجب غير صالحة أو تتجاوز الحد المسموح');
  }

  const mediaUrls = (body.mediaUrls || []).map(url => url.trim());
  const mediaValidation = await validateMediaAttachmentAccess(user, mediaUrls, env.DB);
  if (mediaValidation) return mediaValidation;

  const canPublishOfficial = await hasPermission(user, Permission.PUBLISH_OFFICIAL_HOMEWORK, {
    db: env.DB,
    groupId: body.groupId,
  });
  const isOfficial = body.isOfficial === true && canPublishOfficial ? 1 : 0;

  const hwId = (body.id && typeof body.id === 'string' && body.id.trim())
    ? body.id.trim()
    : generateId('hw');
  const studyDate = body.studyDate || new Date().toISOString().slice(0, 10);
  const title = body.title.trim();
  const details = body.details?.trim() || null;
  const pageNumbers = body.pageNumbers?.trim() || null;
  const questionNumbers = body.questionNumbers?.trim() || null;
  const taskType = body.taskType || 'HOMEWORK';
  const mediaUrlsJson = mediaUrls.length > 0 ? JSON.stringify(mediaUrls) : null;
  const expectedMetadata = {
    groupId: body.groupId,
    creatorId: user.userId,
    studyDate,
    dueDate: body.dueDate,
    subjectId: body.subjectId,
    title,
    details,
    pageNumbers,
    questionNumbers,
    taskType,
    mediaUrlsJson,
  };

  const identities = await Promise.all(mediaUrls.map(async (url, index) => {
    const identity = await createHomeworkMediaIdentity(hwId, index + 1, url);
    return { pageOrder: index + 1, mediaId: identity.mediaId, url };
  }));

  const findExistingHomework = () => env.DB.prepare(
    `SELECT id, group_id, study_date, due_date, subject_id, title, details,
            page_numbers, question_numbers, task_type, media_urls, is_official, created_by
     FROM homeworks WHERE id = ? LIMIT 1`
  ).bind(hwId).first<{
    id: string;
    group_id: string;
    study_date: string;
    due_date: string;
    subject_id: string;
    title: string;
    details: string | null;
    page_numbers: string | null;
    question_numbers: string | null;
    task_type: string;
    media_urls: string | null;
    is_official: number;
    created_by: string;
  }>();

  const existing = await findExistingHomework();
  if (existing && !homeworkMetadataMatches(existing, expectedMetadata)) {
    return errorResponse(
      'HOMEWORK_ID_CONFLICT',
      'المعرّف مستخدم لواجب مختلف أو في مجموعة أخرى. لم يتم استبدال أي بيانات محفوظة.',
      409
    );
  }

  const verifyOrRepairExisting = async (): Promise<Response> => {
    const mediaResponse = await env.DB.prepare(
      `SELECT media_id, url, page_order
       FROM homework_media WHERE homework_id = ?
       ORDER BY page_order ASC`
    ).bind(hwId).all<{
      media_id: string;
      url: string;
      page_order: number;
    }>();

    const storedRows = mediaResponse.results || [];
    const storedByPage = new Map<number, typeof storedRows[number]>();
    for (const stored of storedRows) {
      if (storedByPage.has(stored.page_order)) {
        return errorResponse(
          'HOMEWORK_MEDIA_CONFLICT',
          'توجد صفحات مكررة في الواجب المحفوظ. لم يتم إعلان اكتمال النشر.',
          409
        );
      }
      storedByPage.set(stored.page_order, stored);
    }

    for (const stored of storedRows) {
      const expected = identities.find(item => item.pageOrder === stored.page_order);
      if (!expected || expected.url !== stored.url || expected.mediaId !== stored.media_id) {
        return errorResponse(
          'HOMEWORK_MEDIA_CONFLICT',
          'مرفقات إعادة المحاولة لا تطابق النسخة المحفوظة. لم يتم استبدال أي مرفق.',
          409
        );
      }
    }

    const missing = identities.filter(item => !storedByPage.has(item.pageOrder));
    if (missing.length > 0) {
      try {
        const repairStatements = await Promise.all(missing.map(item =>
          getHomeworkMediaInsertStatement(env, hwId, body.groupId, user.userId, item.pageOrder, item.url, Date.now())
        ));
        const repairResults = await env.DB.batch(repairStatements);
        if (repairResults.length !== repairStatements.length ||
            repairResults.some(result => !result.success)) {
          throw new Error('D1 did not complete the homework media repair batch');
        }
      } catch (error) {
        console.error('Could not repair incomplete homework media:', error);
        return errorResponse(
          'HOMEWORK_MEDIA_REPAIR_FAILED',
          'تم العثور على واجب محفوظ بمرفقات ناقصة، لكن تعذر استكمال حفظها. أعد المحاولة.',
          500
        );
      }
    }

    const complete = await verifyHomeworkMediaSnapshot(env, hwId, mediaUrls, identities);
    if (!complete) {
      return errorResponse(
        'HOMEWORK_MEDIA_REPAIR_FAILED',
        'لم يؤكد الخادم حفظ جميع صفحات الواجب. أعد المحاولة دون إنشاء واجب جديد.',
        500
      );
    }

    const confirmedHomework = await findExistingHomework();
    if (!confirmedHomework || !homeworkMetadataMatches(confirmedHomework, expectedMetadata)) {
      return errorResponse('HOMEWORK_ID_CONFLICT', 'تغيرت بيانات الواجب أثناء إعادة المحاولة.', 409);
    }

    return jsonResponse({
      success: true,
      homeworkId: confirmedHomework.id,
      isOfficial: confirmedHomework.is_official === 1,
      message: confirmedHomework.is_official === 1
        ? 'تم نشر الواجب الرسمي المعتمد للشعبة بنجاح 🎓'
        : 'تمت إضافة مساهمة الواجب بنجاح ومشاركتها مع الزملاء 📝',
    });
  };

  if (existing) {
    return verifyOrRepairExisting();
  }

  const now = Date.now();
  const publicationStatements = [
    env.DB.prepare(
      `INSERT OR IGNORE INTO homeworks (
         id, group_id, study_date, due_date, subject_id, title, details,
         page_numbers, question_numbers, task_type, media_urls, is_official, created_by, created_at
       ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(
      hwId,
      body.groupId,
      studyDate,
      body.dueDate,
      body.subjectId,
      title,
      details,
      pageNumbers,
      questionNumbers,
      taskType,
      mediaUrlsJson,
      isOfficial,
      user.userId,
      now
    ),
    ...(await Promise.all(identities.map(item =>
      getHomeworkMediaInsertStatement(env, hwId, body.groupId, user.userId, item.pageOrder, item.url, now)
    ))),
  ];

  try {
    const results = await env.DB.batch(publicationStatements);
    if (results.length !== publicationStatements.length ||
        results.some(result => !result.success)) {
      throw new Error('D1 did not confirm every homework publication statement');
    }
  } catch (error) {
    console.error('Atomic homework publication failed:', error);
    return errorResponse(
      'HOMEWORK_CREATE_FAILED',
      'تعذر حفظ الواجب ومرفقاته بصورة مكتملة. لم يتم نشر سجل جزئي؛ أعد المحاولة.',
      500
    );
  }

  const committedHomework = await findExistingHomework();
  if (!committedHomework) {
    return errorResponse('HOMEWORK_CREATE_FAILED', 'لم يؤكد الخادم إنشاء سجل الواجب. أعد المحاولة.', 500);
  }
  if (!homeworkMetadataMatches(committedHomework, expectedMetadata)) {
    return errorResponse(
      'HOMEWORK_ID_CONFLICT',
      'المعرّف مستخدم لواجب مختلف أو في مجموعة أخرى. لم يتم استبدال أي بيانات محفوظة.',
      409
    );
  }

  const allMediaCommitted = await verifyHomeworkMediaSnapshot(env, hwId, mediaUrls, identities);
  if (!allMediaCommitted) {
    return errorResponse(
      'HOMEWORK_MEDIA_CREATE_FAILED',
      'لم يؤكد الخادم حفظ جميع صفحات الواجب. أعد المحاولة باستخدام نفس المعرّف.',
      500
    );
  }

  return jsonResponse({
    success: true,
    homeworkId: committedHomework.id,
    isOfficial: committedHomework.is_official === 1,
    message: committedHomework.is_official === 1
      ? 'تم نشر الواجب الرسمي المعتمد للشعبة بنجاح 🎓'
      : 'تمت إضافة مساهمة الواجب بنجاح ومشاركتها مع الزملاء 📝',
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

  if (body.dueDate !== undefined && !isValidIsoDate(body.dueDate)) {
    return errorResponse('INVALID_INPUT', 'تاريخ تسليم الواجب غير صالح، يرجى اختيار تاريخ تقويمي صحيح بصيغة YYYY-MM-DD');
  }

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
    media_urls?: string;
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
