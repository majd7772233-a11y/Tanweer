import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapSchoolEvent } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';
import { Permission, hasPermission } from '../lib/permissions';

export async function handleGetEvents(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const events = await env.DB.prepare(
    `SELECT id, group_id, event_date, time_str, title, description, category, location, is_official, created_by, created_at
     FROM events
     WHERE group_id = ?
     ORDER BY is_official DESC, event_date ASC`
  ).bind(groupId).all();

  return jsonResponse({
    success: true,
    events: (events.results || []).map(mapSchoolEvent),
  });
}

export async function handleCreateEvent(user: UserContext, request: Request, env: Env): Promise<Response> {
  const body = await request.json() as {
    groupId?: string;
    eventDate?: string;
    timeStr?: string;
    title?: string;
    description?: string;
    category?: string;
    location?: string;
    isOfficial?: boolean;
  };

  if (!body.groupId || !body.eventDate || !body.title) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد عنوان الفعالية وتاريخها');
  }

  const memberCheck = await requireGroupMember(user, body.groupId, env.DB);
  if (memberCheck) return memberCheck;

  const canPublishOfficial = await hasPermission(user, Permission.PUBLISH_OFFICIAL_EVENT, {
    db: env.DB,
    groupId: body.groupId,
  });

  const isOfficial = (body.isOfficial && canPublishOfficial) || canPublishOfficial ? 1 : 0;
  const eventId = (body.id && typeof body.id === 'string' && body.id.trim())
    ? body.id.trim()
    : generateId('evt');

  await env.DB.prepare(
    `INSERT INTO events (id, group_id, event_date, time_str, title, description, category, location, is_official, created_by, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    eventId,
    body.groupId,
    body.eventDate,
    body.timeStr || null,
    body.title.trim(),
    body.description?.trim() || null,
    body.category || 'ACTIVITY',
    body.location?.trim() || null,
    isOfficial,
    user.userId,
    Date.now()
  ).run();

  const msg = isOfficial === 1
    ? 'تم نشر الفعالية المدرسية الرسمية بنجاح 🎪'
    : 'تمت إضافة مقترح الفعالية ومشاركته مع الزملاء 📅';

  return jsonResponse({
    success: true,
    eventId,
    isOfficial: isOfficial === 1,
    message: msg,
  });
}

export async function handleUpdateEvent(
  eventId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const event = await env.DB.prepare(`SELECT * FROM events WHERE id = ?`).bind(eventId).first<{
    id: string;
    group_id: string;
    created_by: string;
  }>();

  if (!event) {
    return errorResponse('EVENT_NOT_FOUND', 'الفعالية غير موجودة', 404);
  }

  const memberCheck = await requireGroupMember(user, event.group_id, env.DB);
  if (memberCheck) return memberCheck;

  const isOwner = event.created_by === user.userId;
  const canManage = await hasPermission(user, Permission.MANAGE_EVENTS, {
    db: env.DB,
    groupId: event.group_id,
    resourceOwnerId: event.created_by,
  });

  if (!isOwner && !canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية تعديل هذه الفعالية', 403);
  }

  const body = await request.json() as {
    eventDate?: string;
    timeStr?: string;
    title?: string;
    description?: string;
    category?: string;
    location?: string;
    isOfficial?: boolean;
  };

  const canPublishOfficial = await hasPermission(user, Permission.PUBLISH_OFFICIAL_EVENT, {
    db: env.DB,
    groupId: event.group_id,
  });

  let officialVal: number | null = null;
  if (body.isOfficial !== undefined && canPublishOfficial) {
    officialVal = body.isOfficial ? 1 : 0;
  }

  await env.DB.prepare(
    `UPDATE events
     SET event_date = COALESCE(?, event_date),
         time_str = COALESCE(?, time_str),
         title = COALESCE(?, title),
         description = COALESCE(?, description),
         category = COALESCE(?, category),
         location = COALESCE(?, location),
         is_official = COALESCE(?, is_official)
     WHERE id = ?`
  ).bind(
    body.eventDate || null,
    body.timeStr || null,
    body.title?.trim() || null,
    body.description?.trim() || null,
    body.category || null,
    body.location?.trim() || null,
    officialVal,
    eventId
  ).run();

  return jsonResponse({
    success: true,
    message: 'تم تحديث بيانات الفعالية بنجاح',
  });
}

export async function handleDeleteEvent(
  eventId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const event = await env.DB.prepare(`SELECT * FROM events WHERE id = ?`).bind(eventId).first<{
    id: string;
    group_id: string;
    created_by: string;
  }>();

  if (!event) {
    return errorResponse('EVENT_NOT_FOUND', 'الفعالية غير موجودة', 404);
  }

  const memberCheck = await requireGroupMember(user, event.group_id, env.DB);
  if (memberCheck) return memberCheck;

  const isOwner = event.created_by === user.userId;
  const canManage = await hasPermission(user, Permission.MANAGE_EVENTS, {
    db: env.DB,
    groupId: event.group_id,
    resourceOwnerId: event.created_by,
  });

  if (!isOwner && !canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية حذف هذه الفعالية', 403);
  }

  await env.DB.prepare(`DELETE FROM events WHERE id = ?`).bind(eventId).run();

  return jsonResponse({
    success: true,
    message: 'تم حذف الفعالية بنجاح',
  });
}
