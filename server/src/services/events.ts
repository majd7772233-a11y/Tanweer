import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { errorResponse, jsonResponse } from '../lib/response';

export async function handleGetEvents(groupId: string, env: Env): Promise<Response> {
  const events = await env.DB.prepare(
    `SELECT id, group_id, event_date, time_str, title, description, category, location, created_at
     FROM events
     WHERE group_id = ?
     ORDER BY event_date ASC`
  ).bind(groupId).all();

  return jsonResponse({
    success: true,
    events: events.results,
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
    message: 'تم إضافة الحدث بنجاح',
  });
}
