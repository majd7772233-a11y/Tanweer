import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapSchoolEvent } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';

export async function handleGetEvents(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const events = await env.DB.prepare(
    `SELECT id, group_id, event_date, time_str, title, description, category, location, created_at
     FROM events
     WHERE group_id = ?
     ORDER BY event_date ASC`
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
  };

  if (!body.groupId || !body.eventDate || !body.title) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد عنوان الحدث وتاريخه');
  }

  const memberCheck = await requireGroupMember(user, body.groupId, env.DB);
  if (memberCheck) return memberCheck;

  const eventId = generateId('evt');
  await env.DB.prepare(
    `INSERT INTO events (id, group_id, event_date, time_str, title, description, category, location, created_by, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    eventId,
    body.groupId,
    body.eventDate,
    body.timeStr || null,
    body.title.trim(),
    body.description?.trim() || null,
    body.category || 'ACTIVITY',
    body.location?.trim() || null,
    user.userId,
    Date.now()
  ).run();

  return jsonResponse({
    success: true,
    eventId,
    message: 'تمت إضافة الحدث بنجاح',
  });
}
