import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { errorResponse, jsonResponse } from '../lib/response';
import { getUserFromToken } from '../middleware/auth';
import { normalizeRole, Role } from '../lib/roles';

function detectImageMimeType(buffer: ArrayBuffer): string | null {
  if (buffer.byteLength < 12) return null;
  const bytes = new Uint8Array(buffer);

  // JPEG: FF D8 FF
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) {
    return 'image/jpeg';
  }

  // PNG: 89 50 4E 47 0D 0A 1A 0A
  if (
    bytes[0] === 0x89 &&
    bytes[1] === 0x50 &&
    bytes[2] === 0x4e &&
    bytes[3] === 0x47 &&
    bytes[4] === 0x0d &&
    bytes[5] === 0x0a &&
    bytes[6] === 0x1a &&
    bytes[7] === 0x0a
  ) {
    return 'image/png';
  }

  // WebP: RIFF .... WEBP
  if (
    bytes[0] === 0x52 &&
    bytes[1] === 0x49 &&
    bytes[2] === 0x46 &&
    bytes[3] === 0x46 &&
    bytes[8] === 0x57 &&
    bytes[9] === 0x45 &&
    bytes[10] === 0x42 &&
    bytes[11] === 0x50
  ) {
    return 'image/webp';
  }

  // GIF: GIF87a or GIF89a
  if (
    bytes[0] === 0x47 &&
    bytes[1] === 0x49 &&
    bytes[2] === 0x46 &&
    bytes[3] === 0x38 &&
    (bytes[4] === 0x37 || bytes[4] === 0x39) &&
    bytes[5] === 0x61
  ) {
    return 'image/gif';
  }

  return null;
}

/**
 * Extracts the identifier from this server's private media endpoint.
 * External HTTP(S) image URLs are not subject to local blob ownership checks.
 */
function extractInternalMediaId(value: string): string | null {
  try {
    const parsed = new URL(value, 'https://tanweer.invalid');
    const match = parsed.pathname.match(/^\/api\/v1\/media\/([A-Za-z0-9_-]+)(?:\.[A-Za-z0-9]+)?\/?$/);
    return match ? match[1] : null;
  } catch {
    return null;
  }
}

/**
 * Resolves source groups using exact media identifiers. SQL LIKE is used only
 * to shortlist candidate rows; it is never treated as proof of linkage because
 * an ID may be a substring of another blob's ID or URL.
 */
async function getMediaLinkedGroupIds(mediaId: string, db: D1Database): Promise<Set<string>> {
  const [contentLinks, homeworkLinks, legacyHomeworkLinks] = await Promise.all([
    db.prepare(
      `SELECT c.group_id, cm.id, cm.url, cm.object_key
       FROM content_media cm
       JOIN contents c ON cm.content_id = c.id
       WHERE cm.url LIKE ? OR cm.id = ? OR cm.object_key = ?`
    ).bind('%' + mediaId + '%', mediaId, mediaId).all<{
      group_id: string;
      id: string;
      url: string | null;
      object_key: string | null;
    }>(),
    db.prepare(
      `SELECT h.group_id, hm.media_id, hm.url
       FROM homework_media hm
       JOIN homeworks h ON hm.homework_id = h.id
       WHERE hm.media_id = ? OR hm.url LIKE ?`
    ).bind(mediaId, '%' + mediaId + '%').all<{
      group_id: string;
      media_id: string;
      url: string | null;
    }>(),
    db.prepare(
      `SELECT group_id, media_urls
       FROM homeworks
       WHERE media_urls LIKE ?`
    ).bind('%' + mediaId + '%').all<{
      group_id: string;
      media_urls: string | null;
    }>(),
  ]);

  const linkedGroupIds = new Set<string>();

  for (const row of contentLinks.results || []) {
    const exactMatch =
      row.id === mediaId ||
      row.object_key === mediaId ||
      (!!row.url && extractInternalMediaId(row.url) === mediaId);
    if (exactMatch && row.group_id) linkedGroupIds.add(row.group_id);
  }

  for (const row of homeworkLinks.results || []) {
    const exactMatch =
      row.media_id === mediaId ||
      (!!row.url && extractInternalMediaId(row.url) === mediaId);
    if (exactMatch && row.group_id) linkedGroupIds.add(row.group_id);
  }

  for (const row of legacyHomeworkLinks.results || []) {
    if (!row.group_id || !row.media_urls) continue;
    let references: unknown;
    try {
      references = JSON.parse(row.media_urls);
    } catch {
      references = [row.media_urls];
    }

    const values = Array.isArray(references) ? references : [references];
    const exactMatch = values.some(value =>
      typeof value === 'string' && extractInternalMediaId(value) === mediaId
    );
    if (exactMatch) linkedGroupIds.add(row.group_id);
  }

  return linkedGroupIds;
}

/**
 * Prevents a user from linking another group's private media blob into a group
 * they can access. The uploader may reuse their own upload; other users must
 * already have active access to at least one group where the blob is linked.
 */
export async function validateMediaAttachmentAccess(
  user: UserContext,
  urls: string[],
  db: D1Database
): Promise<Response | null> {
  const uniqueUrls = Array.from(new Set(urls));
  for (const value of uniqueUrls) {
    if (typeof value !== 'string' || !value.trim()) {
      return errorResponse('INVALID_MEDIA_URL', 'يوجد رابط صورة فارغ أو غير صالح');
    }

    const mediaId = extractInternalMediaId(value);
    if (!mediaId) {
      if (value.includes('/api/v1/media/')) {
        return errorResponse('INVALID_MEDIA_URL', 'رابط صورة تنوير الداخلي غير صالح');
      }

      try {
        const external = new URL(value);
        if (external.protocol !== 'https:' && external.protocol !== 'http:') {
          return errorResponse('INVALID_MEDIA_URL', 'يجب أن يكون رابط الصورة من نوع HTTP أو HTTPS');
        }
      } catch {
        return errorResponse('INVALID_MEDIA_URL', 'رابط الصورة غير صالح');
      }
      continue;
    }

    let blob: { id: string; uploaded_by: string | null } | null;
    try {
      blob = await db.prepare(
        `SELECT id, uploaded_by FROM media_blobs WHERE id = ? LIMIT 1`
      ).bind(mediaId).first<{ id: string; uploaded_by: string | null }>();
    } catch (error) {
      console.error('Failed to validate media blob ownership:', error);
      return errorResponse('MEDIA_VALIDATION_UNAVAILABLE', 'تعذر التحقق من صلاحية الصورة حالياً. حاول مجدداً.', 503);
    }

    if (!blob) {
      return errorResponse('MEDIA_NOT_FOUND', 'الصورة المرتبطة غير موجودة على الخادم', 404);
    }

    if (blob.uploaded_by === user.userId) {
      continue;
    }

    try {
      const linkedGroupIds = await getMediaLinkedGroupIds(mediaId, db);

      if (linkedGroupIds.size === 0) {
        return errorResponse(
          'FORBIDDEN_UNLINKED_MEDIA',
          'لا يمكنك إعادة ربط صورة لا تملكها ولم تُنشر في مجموعة تملك عضوية فعالة فيها',
          403
        );
      }

      let sourceAccess = false;
      for (const groupId of linkedGroupIds) {
        const membership = await db.prepare(
          `SELECT 1 FROM group_members
           WHERE group_id = ? AND user_id = ? AND status = 'ACTIVE'
           LIMIT 1`
        ).bind(groupId, user.userId).first();
        if (membership) {
          sourceAccess = true;
          break;
        }
      }

      if (!sourceAccess) {
        return errorResponse(
          'FORBIDDEN_MEDIA_SOURCE',
          'لا يمكنك إعادة استخدام صورة من مجموعة لا تملك عضوية فعالة فيها',
          403
        );
      }
    } catch (error) {
      console.error('Failed to validate media source-group access:', error);
      return errorResponse('MEDIA_VALIDATION_UNAVAILABLE', 'تعذر التحقق من صلاحية الصورة حالياً. حاول مجدداً.', 503);
    }
  }

  return null;
}

export async function handleUploadMedia(user: UserContext, request: Request, env: Env): Promise<Response> {
  const contentType = request.headers.get('content-type') || '';
  let mediaBuffer: ArrayBuffer | null = null;
  let mimeType = 'image/jpeg';

  if (contentType.includes('multipart/form-data')) {
    const formData = await request.formData();
    const file = formData.get('file') || formData.get('image');
    if (file && typeof file !== 'string') {
      const fileObj = file as unknown as File;
      mediaBuffer = await fileObj.arrayBuffer();
      mimeType = fileObj.type || 'image/jpeg';
    }
  } else if (contentType.includes('application/json')) {
    const body = await request.json() as { base64?: string; mimeType?: string };
    if (body.base64) {
      const cleanBase64 = body.base64.replace(/^data:image\/\w+;base64,/, '');
      const binaryString = atob(cleanBase64);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      mediaBuffer = bytes.buffer;
      if (body.mimeType) mimeType = body.mimeType;
    }
  } else if (contentType.includes('image/') || contentType.includes('application/octet-stream')) {
    mediaBuffer = await request.arrayBuffer();
    if (contentType.includes('image/')) {
      mimeType = contentType.split(';')[0].trim();
    }
  }

  if (!mediaBuffer || mediaBuffer.byteLength === 0) {
    return errorResponse('INVALID_FILE', 'لم يتم استلام أي ملف صورة صالح');
  }

  const fileSize = mediaBuffer.byteLength;
  if (fileSize > 1_800_000) {
    return errorResponse('FILE_TOO_LARGE', 'حجم الصورة يتجاوز الحد الأقصى المسموح به (1.8 ميجابايت)');
  }

  // Strict Magic Number / Signature Verification
  const detectedMime = detectImageMimeType(mediaBuffer);
  if (!detectedMime) {
    return errorResponse('INVALID_IMAGE_FORMAT', 'الملف المرفوع ليس صورة مدعومة (يجب أن يكون JPEG أو PNG أو WebP أو GIF)');
  }
  mimeType = detectedMime;

  const mediaId = generateId('med');
  const now = Date.now();

  // Compute SHA-256 Checksum
  const digestBuffer = await crypto.subtle.digest('SHA-256', mediaBuffer);
  const hashArray = Array.from(new Uint8Array(digestBuffer));
  const checksum = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');

  // Check if identical media already exists to deduplicate
  let existing = await env.DB.prepare(
    `SELECT id, mime_type, file_size, uploaded_by
     FROM media_blobs
     WHERE checksum = ? AND (uploaded_by = ? OR uploaded_by IS NULL)
     LIMIT 1`
  ).bind(checksum, user.userId).first<{
    id: string;
    mime_type: string;
    file_size: number;
    uploaded_by: string | null;
  }>();

  // Claim an unowned legacy blob only when this upload proves the caller has
  // the actual image bytes. If another uploader wins the race, store a private copy.
  if (existing && !existing.uploaded_by) {
    const claim = await env.DB.prepare(
      `UPDATE media_blobs SET uploaded_by = ? WHERE id = ? AND uploaded_by IS NULL`
    ).bind(user.userId, existing.id).run();
    if (claim.meta?.changes !== 1) existing = null;
  }

  const targetId = existing ? existing.id : mediaId;

  if (!existing) {
    await env.DB.prepare(
      `INSERT INTO media_blobs (id, mime_type, data, file_size, checksum, uploaded_by, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).bind(
      mediaId,
      mimeType,
      mediaBuffer,
      fileSize,
      checksum,
      user.userId,
      now
    ).run();
  }

  const hostUrl = new URL(request.url).origin;
  const relativeUrl = `/api/v1/media/${targetId}`;
  const fullUrl = `${hostUrl}${relativeUrl}`;

  return jsonResponse({
    success: true,
    id: targetId,
    url: fullUrl,
    relativeUrl: relativeUrl,
    fileSize: existing ? existing.file_size : fileSize,
    mimeType: existing ? existing.mime_type : mimeType,
    checksum: checksum,
    message: 'تم رفع وتخزين الصورة بنجاح',
  });
}

export async function handleGetMedia(mediaId: string, request: Request, env: Env): Promise<Response> {
  // Strip extension if present (e.g. med_xxx.jpg -> med_xxx)
  const cleanId = mediaId.replace(/\.[a-zA-Z0-9]+$/, '');

  // 1. Authenticate user strictly from Authorization header (prevent token leak in URLs/logs)
  const authHeader = request.headers.get('Authorization') || request.headers.get('authorization') || '';
  const token = authHeader.replace(/^Bearer\s+/i, '').trim();

  if (!token) {
    return errorResponse('UNAUTHORIZED', 'يجب تسجيل الدخول وتوفير رمز الجلسة في ترويسة الطلب (Authorization: Bearer <token>)', 401);
  }

  let user: UserContext | null = null;
  try {
    user = await getUserFromToken(token, env);
  } catch (_: any) {}

  if (!user) {
    return errorResponse('UNAUTHORIZED', 'رمز الجلسة غير صالح أو منتهي الصلاحية', 401);
  }

  // 2. Check if the media blob exists
  const row = await env.DB.prepare(
    `SELECT mime_type, data FROM media_blobs WHERE id = ? LIMIT 1`
  ).bind(cleanId).first<{ mime_type: string; data: ArrayBuffer | number[] | Uint8Array }>();

  if (!row || !row.data) {
    return errorResponse('NOT_FOUND', 'الصورة غير موجودة', 404);
  }

  // 3. Enforce Content Linkage & Group Membership Verification
  const userRole = normalizeRole(user.role);
  const isPrivileged = userRole === Role.ADMIN || userRole === Role.SYSTEM_OWNER;

  if (!isPrivileged) {
    let linkedGroupIds: Set<string>;
    try {
      linkedGroupIds = await getMediaLinkedGroupIds(cleanId, env.DB);
    } catch (error) {
      console.error('Failed to verify exact media linkage:', error);
      return errorResponse(
        'MEDIA_ACCESS_CHECK_UNAVAILABLE',
        'تعذر التحقق من صلاحية الوصول إلى الصورة حالياً. حاول مجدداً.',
        503
      );
    }

    // Reject unlinked media: media must be connected to an authenticated classroom context
    if (linkedGroupIds.size === 0) {
      return errorResponse('FORBIDDEN_UNLINKED_MEDIA', 'هذا الملف غير مرتبط بأي درس أو واجب معتمد ومتاح في مجموعتك', 403);
    }

    // Verify active membership in at least one of the linked groups
    let isMemberOfAny = false;
    for (const gid of linkedGroupIds) {
      const member = await env.DB.prepare(
        `SELECT 1 FROM group_members WHERE group_id = ? AND user_id = ? AND status = 'ACTIVE' LIMIT 1`
      ).bind(gid, user.userId).first();
      if (member) {
        isMemberOfAny = true;
        break;
      }
    }

    if (!isMemberOfAny) {
      return errorResponse('FORBIDDEN', 'لا تملك صلاحية الوصول إلى صور هذا المحتوى (مخصصة لأعضاء الشعبة فقط)', 403);
    }
  }

  const rawData: any = row.data;
  let bodyData: BodyInit;
  if (rawData instanceof ArrayBuffer || rawData instanceof Uint8Array) {
    bodyData = rawData;
  } else if (Array.isArray(rawData)) {
    bodyData = new Uint8Array(rawData);
  } else {
    bodyData = rawData;
  }

  return new Response(bodyData, {
    status: 200,
    headers: {
      'Content-Type': row.mime_type || 'image/jpeg',
      'Cache-Control': 'private, no-store',
      'X-Content-Type-Options': 'nosniff',
      'Access-Control-Allow-Origin': '*',
    },
  });
}

/**
 * Safely cleans up unreferenced media blobs from media_blobs table.
 * Crucially preserves images that are referenced by any lesson (content_media)
 * or any homework (homework_media / homeworks.media_urls), ensuring deduplicated
 * images shared across lessons or homeworks are never prematurely deleted.
 */
export async function cleanupOrphanMedia(db: D1Database, candidateMediaIds?: string[]): Promise<number> {
  let cleanedCount = 0;

  if (candidateMediaIds && candidateMediaIds.length > 0) {
    for (const rawId of candidateMediaIds) {
      const cleanId = rawId.replace(/\.[a-zA-Z0-9]+$/, '').trim();
      if (!cleanId) continue;

      // 1. Check if referenced in content_media
      const inContent = await db.prepare(
        `SELECT 1 FROM content_media WHERE url LIKE ? OR id = ? OR object_key = ? LIMIT 1`
      ).bind(`%${cleanId}%`, cleanId, cleanId).first();

      if (inContent) continue;

      // 2. Check if referenced in homework_media
      let inHwMedia: any = null;
      try {
        inHwMedia = await db.prepare(
          `SELECT 1 FROM homework_media WHERE media_id = ? OR url LIKE ? LIMIT 1`
        ).bind(cleanId, `%${cleanId}%`).first();
      } catch {}

      if (inHwMedia) continue;

      // 3. Fallback check in homeworks.media_urls
      const inHwJson = await db.prepare(
        `SELECT 1 FROM homeworks WHERE media_urls LIKE ? LIMIT 1`
      ).bind(`%${cleanId}%`).first();

      if (inHwJson) continue;

      // If no references in contents or homeworks, safely purge from media_blobs
      const res = await db.prepare(`DELETE FROM media_blobs WHERE id = ?`).bind(cleanId).run();
      if (res.meta?.changes && res.meta.changes > 0) {
        cleanedCount += res.meta.changes;
      }
    }
  } else {
    // Global scan for orphan media blobs
    const allBlobs = await db.prepare(`SELECT id FROM media_blobs`).all<{ id: string }>();
    const blobIds = (allBlobs.results || []).map(r => r.id);
    for (const bId of blobIds) {
      const inContent = await db.prepare(
        `SELECT 1 FROM content_media WHERE url LIKE ? OR id = ? OR object_key = ? LIMIT 1`
      ).bind(`%${bId}%`, bId, bId).first();
      if (inContent) continue;

      let inHwMedia: any = null;
      try {
        inHwMedia = await db.prepare(
          `SELECT 1 FROM homework_media WHERE media_id = ? OR url LIKE ? LIMIT 1`
        ).bind(bId, `%${bId}%`).first();
      } catch {}
      if (inHwMedia) continue;

      const inHwJson = await db.prepare(
        `SELECT 1 FROM homeworks WHERE media_urls LIKE ? LIMIT 1`
      ).bind(`%${bId}%`).first();
      if (inHwJson) continue;

      const res = await db.prepare(`DELETE FROM media_blobs WHERE id = ?`).bind(bId).run();
      if (res.meta?.changes && res.meta.changes > 0) {
        cleanedCount += res.meta.changes;
      }
    }
  }

  return cleanedCount;
}
