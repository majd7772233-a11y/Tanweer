import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { errorResponse, jsonResponse } from '../lib/response';

export async function handleGetSchedule(groupId: string, env: Env): Promise<Response> {
  const activeVersion = await env.DB.prepare(
    `SELECT id, version_number, valid_from, valid_until
     FROM schedule_versions
     WHERE group_id = ? AND is_active = 1
     ORDER BY version_number DESC LIMIT 1`
  ).bind(groupId).first<{ id: string; version_number: number }>();

  if (!activeVersion) {
    // Return empty list if timetable is not configured yet
    return jsonResponse({
      success: true,
      slots: [],
      version: null,
    });
  }

  const slots = await env.DB.prepare(
    `SELECT ss.id, ss.day_of_week, ss.slot_order, ss.subject_id, ss.start_time, ss.end_time,
            s.name_ar as subject_name, s.icon as subject_icon, s.color_hex
     FROM schedule_slots ss
     JOIN subjects s ON ss.subject_id = s.id
     WHERE ss.version_id = ?
     ORDER BY ss.day_of_week ASC, ss.slot_order ASC`
  ).bind(activeVersion.id).all();

  return jsonResponse({
    success: true,
    version: activeVersion,
    slots: slots.results,
  });
}

export async function handleProposeScheduleChange(user: UserContext, request: Request, env: Env): Promise<Response> {
  const body = await request.json() as {
    groupId?: string;
    dayOfWeek?: number;
    slotOrder?: number;
    oldSubjectId?: string;
    newSubjectId?: string;
    reason?: string;
  };

  if (!body.groupId || body.dayOfWeek === undefined || !body.slotOrder || !body.newSubjectId || !body.reason) {
    return errorResponse('INVALID_INPUT', 'يرجى إكمال تفاصيل اقتراح تعديل الجدول والسبب');
  }

  const proposalId = generateId('prop');
  await env.DB.prepare(
    `INSERT INTO schedule_proposals (id, group_id, proposed_by, day_of_week, slot_order, old_subject_id, new_subject_id, reason, status, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)`
  ).bind(
    proposalId,
    body.groupId,
    user.userId,
    body.dayOfWeek,
    body.slotOrder,
    body.oldSubjectId || null,
    body.newSubjectId,
    body.reason.trim(),
    Date.now()
  ).run();

  return jsonResponse({
    success: true,
    proposalId,
    message: 'تم رفع مقترح تعديل الجدول وسيتم طرحه لتصويت الأعضاء',
  });
}
