import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { errorResponse, jsonResponse } from '../lib/response';

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

export async function handleGetMedia(mediaId: string, env: Env): Promise<Response> {
  // Strip extension if present (e.g. med_xxx.jpg -> med_xxx)
  const cleanId = mediaId.replace(/\.[a-zA-Z0-9]+$/, '');

  const row = await env.DB.prepare(
    `SELECT mime_type, data FROM media_blobs WHERE id = ? LIMIT 1`
  ).bind(cleanId).first<{ mime_type: string; data: ArrayBuffer }>();

  if (!row || !row.data) {
    return errorResponse('NOT_FOUND', 'الصورة غير موجودة', 404);
  }

  return new Response(row.data, {
    status: 200,
    headers: {
      'Content-Type': row.mime_type || 'image/jpeg',
      'Cache-Control': 'public, max-age=31536000, immutable',
      'Access-Control-Allow-Origin': '*',
    },
  });
}
