import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapScheduleSlot } from '../lib/mappers';
import { requireGroupMember, requireScheduleManager } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';

export async function handleGetSchedule(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const activeVersion = await env.DB.prepare(
    `SELECT id, version_number, valid_from, valid_until
     FROM schedule_versions
     WHERE group_id = ? AND is_active = 1
     ORDER BY version_number DESC LIMIT 1`
  ).bind(groupId).first<{ id: string; version_number: number; valid_from: string; valid_until: string | null }>();

  if (!activeVersion) {
    return jsonResponse({
      success: true,
      slots: [],
      version: null,
    });
  }

  const slots = await env.DB.prepare(
    `SELECT ss.id, ss.day_of_week, ss.slot_order, ss.subject_id, ss.start_time, ss.end_time,
            COALESCE(s.name_ar, ss.subject_id) as subject_name,
            COALESCE(s.icon, '📚') as subject_icon,
            s.color_hex
     FROM schedule_slots ss
     LEFT JOIN subjects s ON ss.subject_id = s.id
     WHERE ss.version_id = ?
     ORDER BY ss.day_of_week ASC, ss.slot_order ASC`
  ).bind(activeVersion.id).all();

  return jsonResponse({
    success: true,
    version: {
      id: activeVersion.id,
      versionNumber: activeVersion.version_number,
      validFrom: activeVersion.valid_from,
      validUntil: activeVersion.valid_until,
    },
    slots: (slots.results || []).map(mapScheduleSlot),
  });
}

export async function handleCreateScheduleSlot(
  groupId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const permCheck = await requireScheduleManager(user, groupId, env.DB);
  if (permCheck) return permCheck;

  const body = await request.json() as {
    dayOfWeek?: number;
    slotOrder?: number;
    subjectId?: string;
    subjectName?: string;
    subjectIcon?: string;
    colorHex?: string;
    startTime?: string;
    endTime?: string;
  };

  if (body.dayOfWeek === undefined || !body.slotOrder || !body.subjectId) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد اليوم ورقم الحصة والمادة');
  }

  // 1. Ensure an active schedule version exists or create version 1
  let activeVersion = await env.DB.prepare(
    `SELECT id, version_number FROM schedule_versions WHERE group_id = ? AND is_active = 1 ORDER BY version_number DESC LIMIT 1`
  ).bind(groupId).first<{ id: string; version_number: number }>();

  if (!activeVersion) {
    const versionId = generateId('sch_ver');
    const today = new Date().toISOString().slice(0, 10);
    await env.DB.prepare(
      `INSERT INTO schedule_versions (id, group_id, version_number, valid_from, is_active, created_at, created_by)
       VALUES (?, ?, 1, ?, 1, ?, ?)`
    ).bind(versionId, groupId, today, Date.now(), user.userId).run();
    activeVersion = { id: versionId, version_number: 1 };
  }

  // 2. Ensure subject exists in subjects table
  if (body.subjectName) {
    await env.DB.prepare(
      `INSERT OR IGNORE INTO subjects (id, name_ar, icon, color_hex)
       VALUES (?, ?, ?, ?)`
    ).bind(
      body.subjectId,
      body.subjectName,
      body.subjectIcon || '📚',
      body.colorHex || '#00E5FF'
    ).run();
  }

  // 3. Remove existing slot in that version/day/order if any
  await env.DB.prepare(
    `DELETE FROM schedule_slots WHERE version_id = ? AND day_of_week = ? AND slot_order = ?`
  ).bind(activeVersion.id, body.dayOfWeek, body.slotOrder).run();

  // 4. Insert new slot
  const slotId = generateId('slot');
  await env.DB.prepare(
    `INSERT INTO schedule_slots (id, version_id, day_of_week, slot_order, subject_id, start_time, end_time)
     VALUES (?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    slotId,
    activeVersion.id,
    body.dayOfWeek,
    body.slotOrder,
    body.subjectId,
    body.startTime || null,
    body.endTime || null
  ).run();

  return jsonResponse({
    success: true,
    slotId,
    message: 'تمت إضافة الحصة إلى الجدول بنجاح',
  });
}

export async function handleDeleteScheduleSlot(
  groupId: string,
  dayOfWeek: number,
  slotOrder: number,
  user: UserContext,
  env: Env
): Promise<Response> {
  const permCheck = await requireScheduleManager(user, groupId, env.DB);
  if (permCheck) return permCheck;

  const activeVersion = await env.DB.prepare(
    `SELECT id FROM schedule_versions WHERE group_id = ? AND is_active = 1 ORDER BY version_number DESC LIMIT 1`
  ).bind(groupId).first<{ id: string }>();

  if (!activeVersion) {
    return errorResponse('SCHEDULE_NOT_FOUND', 'لا يوجد جدول فعال لهذه المجموعة');
  }

  await env.DB.prepare(
    `DELETE FROM schedule_slots WHERE version_id = ? AND day_of_week = ? AND slot_order = ?`
  ).bind(activeVersion.id, dayOfWeek, slotOrder).run();

  return jsonResponse({
    success: true,
    message: 'تم حذف الحصة من الجدول بنجاح',
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

  const memberCheck = await requireGroupMember(user, body.groupId, env.DB);
  if (memberCheck) return memberCheck;

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
