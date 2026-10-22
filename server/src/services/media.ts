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
  const existing = await env.DB.prepare(
    `SELECT id, mime_type, file_size FROM media_blobs WHERE checksum = ? LIMIT 1`
  ).bind(checksum).first<{ id: string; mime_type: string; file_size: number }>();

  const targetId = existing ? existing.id : mediaId;

  if (!existing) {
    await env.DB.prepare(
      `INSERT INTO media_blobs (id, mime_type, data, file_size, checksum, created_at)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(
      mediaId,
      mimeType,
      mediaBuffer,
      fileSize,
      checksum,
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

  // 1. Authenticate user from Header or Query Param
  const authHeader = request.headers.get('Authorization') || request.headers.get('authorization') || '';
  const url = new URL(request.url);
  const queryToken = url.searchParams.get('token');
  const token = authHeader.replace(/^Bearer\s+/i, '').trim() || queryToken?.trim();

  let user: UserContext | null = null;
  if (token) {
    try {
      user = await getUserFromToken(token, env);
    } catch (_: any) {}
  }

  // 2. Check if the media blob exists
  const row = await env.DB.prepare(
    `SELECT mime_type, data FROM media_blobs WHERE id = ? LIMIT 1`
  ).bind(cleanId).first<{ mime_type: string; data: ArrayBuffer | number[] | Uint8Array }>();

  if (!row || !row.data) {
    return errorResponse('NOT_FOUND', 'الصورة غير موجودة', 404);
  }

  // 3. Enforce Group Membership & School Scope
  // If user is unauthenticated, deny access to protect school whiteboard and class media
  if (!user) {
    return errorResponse('UNAUTHORIZED', 'يجب تسجيل الدخول بحساب مدرسي مصرح له للوصول إلى صور ومحتويات الشعبة الدراسية', 401);
  }

  // Check if this media is linked to a specific classroom group
  const contentLink = await env.DB.prepare(
    `SELECT c.group_id, c.status
     FROM content_media cm
     JOIN contents c ON cm.content_id = c.id
     WHERE cm.url LIKE ? OR cm.id = ? OR cm.object_key = ?
     LIMIT 1`
  ).bind(`%${cleanId}%`, cleanId, cleanId).first<{ group_id: string; status: string }>();

  if (contentLink && contentLink.group_id) {
    const userRole = normalizeRole(user.role);
    const isPrivileged = [Role.ADMIN, Role.SYSTEM_OWNER, Role.TEACHER].includes(userRole);

    if (!isPrivileged) {
      // Check if user is a member of this specific group
      const member = await env.DB.prepare(
        `SELECT 1 FROM group_members WHERE group_id = ? AND user_id = ? LIMIT 1`
      ).bind(contentLink.group_id, user.userId).first();

      if (!member) {
        return errorResponse('FORBIDDEN', 'لا تملك صلاحية الوصول إلى صور هذا الدرس (مخصصة لأعضاء الشعبة فقط)', 403);
      }
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
      'Cache-Control': 'private, max-age=86400',
      'Access-Control-Allow-Origin': '*',
    },
  });
}
