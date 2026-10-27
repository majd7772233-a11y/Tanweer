import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapSchoolEvent } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';
import { Permission, hasPermission } from '../lib/permissions';
import { isValidIsoDate } from '../lib/validation';

export async function handleGetEvents(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const events = await env.DB.prepare(
    `SELECT id, group_id, event_date, time_str, title, description, category, location, is_official, created_by, created_at
     FROM events
     WHERE group_id = ? OR group_id = 'all'
     ORDER BY is_official DESC, event_date ASC`
  ).bind(groupId).all();

  return jsonResponse({
    success: true,
    events: (events.results || []).map(mapSchoolEvent),
  });
}

export async function handleCreateEvent(user: UserContext, request: Request, env: Env): Promise<Response> {
  const body = await request.json() as {
    id?: string;
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

  if (!isValidIsoDate(body.eventDate)) {
    return errorResponse('INVALID_INPUT', 'تاريخ الفعالية غير صالح، يرجى اختيار تاريخ تقويمي صحيح بصيغة YYYY-MM-DD');
  }

  const isHoliday = body.category?.toUpperCase() === 'HOLIDAY';
  const isSchoolWide = body.groupId === 'all';

  if (isHoliday) {
    if (!body.description || !body.description.trim()) {
      return errorResponse('INVALID_INPUT', 'يرجى توضيح سبب الإجازة المدرسية في حقل الوصف');
    }
  }

  let canPublishOfficial = false;
  const isPrivilegedRole = user.role === 'ADMIN' || user.role === 'SYSTEM_OWNER' || user.role === 'TEACHER' || user.role === 'VERIFIED_TEACHER';

  if (isSchoolWide) {
    if (!isPrivilegedRole) {
      return errorResponse('FORBIDDEN', 'صلاحية الإجازات أو الفعاليات العامة على مستوى المدرسة محصورة بالمعلمين وإدارة المدرسة', 403);
    }
    canPublishOfficial = true;
  } else {
    const memberCheck = await requireGroupMember(user, body.groupId, env.DB);
    if (memberCheck) return memberCheck;

    canPublishOfficial = await hasPermission(user, Permission.PUBLISH_OFFICIAL_EVENT, {
      db: env.DB,
      groupId: body.groupId,
    });
  }

  if (isHoliday && !canPublishOfficial && !isPrivilegedRole) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية تحديد إجازة مدرسية معتمدة', 403);
  }

  const isOfficial = (isHoliday || (body.isOfficial && canPublishOfficial) || canPublishOfficial) ? 1 : 0;
  const eventId = (body.id && typeof body.id === 'string' && body.id.trim())
    ? body.id.trim()
    : generateId('evt');

  // Idempotency check with ownership & scope validation
  const existing = await env.DB.prepare(`SELECT id, is_official, group_id, created_by FROM events WHERE id = ?`).bind(eventId).first<{
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
      eventId: existing.id,
      isOfficial: existing.is_official === 1,
      message: isHoliday
        ? 'تم تسجيل الإجازة المدرسية بنجاح 🌴'
        : (existing.is_official === 1
          ? 'تم نشر الفعالية المدرسية الرسمية بنجاح 🎪'
          : 'تمت إضافة مقترح الفعالية ومشاركته مع الزملاء 📅'),
    });
  }

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

  const msg = isHoliday
    ? 'تم تسجيل الإجازة المدرسية المعتمدة بنجاح 🌴'
    : (isOfficial === 1
      ? 'تم نشر الفعالية المدرسية الرسمية بنجاح 🎪'
      : 'تمت إضافة مقترح الفعالية ومشاركته مع الزملاء 📅');

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

  const isSchoolWide = event.group_id === 'all';
  const isPrivilegedRole = user.role === 'ADMIN' || user.role === 'SYSTEM_OWNER' || user.role === 'TEACHER' || user.role === 'VERIFIED_TEACHER';

  if (!isSchoolWide) {
    const memberCheck = await requireGroupMember(user, event.group_id, env.DB);
    if (memberCheck) return memberCheck;
  }

  const isOwner = event.created_by === user.userId;
  const canManage = isSchoolWide
    ? isPrivilegedRole
    : await hasPermission(user, Permission.MANAGE_EVENTS, {
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
