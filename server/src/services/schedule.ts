import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapScheduleSlot } from '../lib/mappers';
import { requireGroupMember, requireScheduleManager } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';
import { Permission, hasPermission } from '../lib/permissions';
import { sanitizePublicRole } from '../lib/roles';

export interface ScheduleSlotInput {
  dayOfWeek: number;
  slotOrder: number;
  subjectId: string;
  subjectName?: string;
  subjectIcon?: string;
  colorHex?: string;
  startTime?: string;
  endTime?: string;
}

/**
 * Returns the active schedule and its slots, along with permission metadata.
 */
export async function handleGetSchedule(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const activeVersion = await env.DB.prepare(
    `SELECT id, version_number, valid_from, valid_until, created_by, created_at
     FROM schedule_versions
     WHERE group_id = ? AND is_active = 1
     ORDER BY version_number DESC LIMIT 1`
  ).bind(groupId).first<{
    id: string;
    version_number: number;
    valid_from: string;
    valid_until: string | null;
    created_by: string;
    created_at: number;
  }>();

  let slots: any[] = [];
  let isInitialSetup = true;

  if (activeVersion) {
    const slotsQuery = await env.DB.prepare(
      `SELECT ss.id, ss.day_of_week, ss.slot_order, ss.subject_id, ss.start_time, ss.end_time,
              COALESCE(s.name_ar, ss.subject_id) as subject_name,
              COALESCE(s.icon, '📚') as subject_icon,
              s.color_hex
       FROM schedule_slots ss
       LEFT JOIN subjects s ON ss.subject_id = s.id
       WHERE ss.version_id = ?
       ORDER BY ss.day_of_week ASC, ss.slot_order ASC`
    ).bind(activeVersion.id).all();

    slots = (slotsQuery.results || []).map(mapScheduleSlot);
    isInitialSetup = slots.length === 0;
  }

  const isScheduleManager = await hasPermission(user, Permission.MANAGE_SCHEDULE, {
    db: env.DB,
    groupId,
  });

  const canEditDirectly = isInitialSetup || isScheduleManager;

  // Count pending proposals
  const pendingProps = await env.DB.prepare(
    `SELECT COUNT(*) as count FROM schedule_proposals WHERE group_id = ? AND status = 'PENDING'`
  ).bind(groupId).first<{ count: number }>();

  return jsonResponse({
    success: true,
    version: activeVersion ? {
      id: activeVersion.id,
      versionNumber: activeVersion.version_number,
      validFrom: activeVersion.valid_from,
      validUntil: activeVersion.valid_until,
      createdBy: activeVersion.created_by,
      createdAt: activeVersion.created_at,
    } : null,
    slots,
    meta: {
      isInitialSetup,
      canEditDirectly,
      canPropose: true,
      pendingProposalsCount: pendingProps?.count || 0,
    }
  });
}

/**
 * Saves a single schedule slot.
 * - If no schedule exists (Version 0), ANY member (including STUDENT) can set it up.
 * - If schedule exists (Version 1+), only schedule managers can directly edit.
 */
export async function handleCreateScheduleSlot(
  groupId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  // 1. Ensure active version exists or initialize Version 1
  let activeVersion = await env.DB.prepare(
    `SELECT id, version_number FROM schedule_versions WHERE group_id = ? AND is_active = 1 ORDER BY version_number DESC LIMIT 1`
  ).bind(groupId).first<{ id: string; version_number: number }>();

  let hasSlots = false;
  if (activeVersion) {
    const slotCount = await env.DB.prepare(
      `SELECT COUNT(*) as count FROM schedule_slots WHERE version_id = ?`
    ).bind(activeVersion.id).first<{ count: number }>();
    hasSlots = (slotCount?.count || 0) > 0;
  }

  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const body = await request.json() as ScheduleSlotInput;

  // TA-23: Strict unified validation matching batch schedule rules
  if (
    body.dayOfWeek === undefined ||
    typeof body.dayOfWeek !== 'number' ||
    body.dayOfWeek < 0 ||
    body.dayOfWeek > 6 ||
    !body.slotOrder ||
    typeof body.slotOrder !== 'number' ||
    body.slotOrder < 1 ||
    body.slotOrder > 12 ||
    !body.subjectId ||
    typeof body.subjectId !== 'string' ||
    !body.subjectId.trim()
  ) {
    return errorResponse(
      'INVALID_INPUT',
      'بيانات الحصة غير صالحة، يرجى التأكد من اليوم (0-6) ورقم الحصة (1-12) والمادة وعدم ترك حقول فارغة'
    );
  }

  const isScheduleManager = await hasPermission(user, Permission.MANAGE_SCHEDULE, {
    db: env.DB,
    groupId,
  });

  // 2. Check if a slot already exists at this position in the active version
  let existingSlot: { id: string; start_time: string | null; end_time: string | null } | null = null;
  if (activeVersion) {
    existingSlot = await env.DB.prepare(
      `SELECT id, start_time, end_time FROM schedule_slots WHERE version_id = ? AND day_of_week = ? AND slot_order = ?`
    ).bind(activeVersion.id, body.dayOfWeek, body.slotOrder).first<{ id: string; start_time: string | null; end_time: string | null }>();
  }

  // Points 13 & 14 Policy:
  // - If slot is occupied: only authorized managers (teachers/admins) can directly edit without voting.
  //   Students must propose a change and go through voting.
  // - If slot is empty: any active group member can fill it directly.
  if (existingSlot && !isScheduleManager) {
    return errorResponse(
      'SCHEDULE_SLOT_OCCUPIED_REQUIRES_PROPOSAL',
      'الحصة مشغولة بالفعل. لتعديلها يرجى تقديم مقترح تعديل ليصوت عليه الطلاب والزملاء.',
      403
    );
  }

  const slotStatements: any[] = [];
  let targetVersionId = activeVersion?.id;
  let targetVersionNumber = activeVersion?.version_number || 1;

  // TA-23: Atomicity - if no active version exists yet, add its creation to the SAME atomic batch!
  // This prevents orphaned empty active versions if inserting the slot subsequently fails.
  if (!activeVersion) {
    const latestVersion = await env.DB.prepare(
      `SELECT MAX(version_number) as version_number FROM schedule_versions WHERE group_id = ?`
    ).bind(groupId).first<{ version_number: number | null }>();

    targetVersionId = generateId('sch_ver');
    targetVersionNumber = (latestVersion?.version_number || 0) + 1;
    const today = new Date().toISOString().slice(0, 10);
    slotStatements.push(
      env.DB.prepare(
        `INSERT INTO schedule_versions
           (id, group_id, version_number, valid_from, is_active, created_at, created_by)
         SELECT ?, ?, ?, ?, 1, ?, ?
         WHERE NOT EXISTS (
           SELECT 1 FROM schedule_versions WHERE group_id = ? AND is_active = 1
         )`
      ).bind(targetVersionId, groupId, targetVersionNumber, today, Date.now(), user.userId, groupId)
    );
  }

  const finalStartTime = (body.startTime !== undefined && body.startTime !== null && body.startTime.trim() !== '')
    ? body.startTime.trim()
    : (existingSlot?.start_time || null);
  const finalEndTime = (body.endTime !== undefined && body.endTime !== null && body.endTime.trim() !== '')
    ? body.endTime.trim()
    : (existingSlot?.end_time || null);

  const slotId = generateId('slot');

  // 3. Ensure subject exists
  if (body.subjectName) {
    slotStatements.push(
      env.DB.prepare(
        `INSERT OR IGNORE INTO subjects (id, name_ar, icon, color_hex)
         SELECT ?, ?, ?, ?
         WHERE EXISTS (
           SELECT 1 FROM schedule_versions WHERE id = ? AND group_id = ? AND is_active = 1
         )`
      ).bind(
        body.subjectId,
        body.subjectName,
        body.subjectIcon || '📚',
        body.colorHex || '#00E5FF',
        targetVersionId,
        groupId
      )
    );
  }

  // If replacing an occupied slot, delete only the exact row we read.
  // A concurrent request that already replaced it must not be overwritten.
  if (existingSlot) {
    slotStatements.push(
      env.DB.prepare(
        `DELETE FROM schedule_slots
         WHERE id = ? AND version_id = ? AND day_of_week = ? AND slot_order = ?
           AND EXISTS (
             SELECT 1 FROM schedule_versions
             WHERE id = ? AND group_id = ? AND is_active = 1
           )`
      ).bind(existingSlot.id, targetVersionId, body.dayOfWeek, body.slotOrder, targetVersionId, groupId)
    );
  }

  // Insert only while the target version remains active and this position remains empty.
  slotStatements.push(
    env.DB.prepare(
      `INSERT INTO schedule_slots
         (id, version_id, day_of_week, slot_order, subject_id, start_time, end_time)
       SELECT ?, ?, ?, ?, ?, ?, ?
       WHERE EXISTS (
         SELECT 1 FROM schedule_versions
         WHERE id = ? AND group_id = ? AND is_active = 1
       )
       AND NOT EXISTS (
         SELECT 1 FROM schedule_slots
         WHERE version_id = ? AND day_of_week = ? AND slot_order = ?
       )`
    ).bind(
      slotId,
      targetVersionId,
      body.dayOfWeek,
      body.slotOrder,
      body.subjectId.trim(),
      finalStartTime,
      finalEndTime,
      targetVersionId,
      groupId,
      targetVersionId,
      body.dayOfWeek,
      body.slotOrder
    )
  );

  if (typeof env.DB.batch !== 'function') {
    return errorResponse('SCHEDULE_WRITE_UNAVAILABLE', 'قاعدة البيانات لا تدعم حفظ الجدول الذري حالياً', 503);
  }

  const writeResults = await env.DB.batch(slotStatements);
  const versionInsertIndex = activeVersion ? -1 : 0;
  if (versionInsertIndex >= 0 && writeResults[versionInsertIndex]?.meta?.changes !== 1) {
    return errorResponse('SCHEDULE_VERSION_CHANGED', 'أنشأ طلب آخر نسخة نشطة للجدول. حدّث الجدول ثم أعد المحاولة.', 409);
  }

  const slotInsert = writeResults[writeResults.length - 1];
  if (slotInsert?.meta?.changes !== 1) {
    return errorResponse('SCHEDULE_SLOT_CHANGED', 'تغيرت النسخة النشطة أو موضع الحصة أثناء الحفظ. حدّث الجدول وحاول مجدداً.', 409);
  }

  return jsonResponse({
    success: true,
    slotId,
    versionNumber: targetVersionNumber,
    message: 'تم حفظ الحصة في الجدول بنجاح',
  });
}

/**
 * Saves a full batch of schedule slots (e.g. initializing the full week grid at once).
 */
export async function handleSaveScheduleBatch(
  groupId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const currentVersion = await env.DB.prepare(
    `SELECT id, version_number
     FROM schedule_versions
     WHERE group_id = ? AND is_active = 1
     ORDER BY version_number DESC
     LIMIT 1`
  ).bind(groupId).first<{ id: string; version_number: number }>();

  let hasSlots = false;
  if (currentVersion) {
    const slotCount = await env.DB.prepare(
      `SELECT COUNT(*) as count FROM schedule_slots WHERE version_id = ?`
    ).bind(currentVersion.id).first<{ count: number }>();
    hasSlots = (slotCount?.count || 0) > 0;
  }

  const isInitial = !currentVersion || !hasSlots;
  const permCheck = await requireScheduleManager(user, groupId, env.DB, isInitial);
  if (permCheck) return permCheck;

  const body = await request.json() as {
    slots?: ScheduleSlotInput[];
    validFrom?: string;
  };

  const slotList = body.slots || [];
  if (!Array.isArray(slotList) || slotList.length === 0) {
    return errorResponse('INVALID_INPUT', 'يرجى تزويد قائمة الحصص لتحديث الجدول');
  }

  const seenPositions = new Set<string>();
  for (let i = 0; i < slotList.length; i++) {
    const slot = slotList[i];
    if (
      slot.dayOfWeek === undefined ||
      typeof slot.dayOfWeek !== 'number' ||
      !Number.isInteger(slot.dayOfWeek) ||
      slot.dayOfWeek < 0 ||
      slot.dayOfWeek > 6 ||
      !Number.isInteger(slot.slotOrder) ||
      slot.slotOrder < 1 ||
      slot.slotOrder > 12 ||
      typeof slot.subjectId !== 'string' ||
      !slot.subjectId.trim()
    ) {
      return errorResponse(
        'INVALID_INPUT',
        `الحصة رقم (${i + 1}) غير صالحة، يرجى التأكد من اليوم ورقم الحصة والمادة لجميع الحصص وعدم ترك حصص ناقصة`
      );
    }

    const positionKey = `${slot.dayOfWeek}:${slot.slotOrder}`;
    if (seenPositions.has(positionKey)) {
      return errorResponse(
        'DUPLICATE_SCHEDULE_SLOT',
        `يوجد أكثر من حصة محددة في الموضع نفسه (اليوم ${slot.dayOfWeek}، الحصة ${slot.slotOrder})`
      );
    }
    seenPositions.add(positionKey);
  }

  let newVersionNum: number;
  if (currentVersion) {
    newVersionNum = currentVersion.version_number + 1;
  } else {
    const latest = await env.DB.prepare(
      `SELECT MAX(version_number) as version_number
       FROM schedule_versions WHERE group_id = ?`
    ).bind(groupId).first<{ version_number: number | null }>();
    newVersionNum = (latest?.version_number || 0) + 1;
  }

  const newVersionId = generateId('sch_ver');
  const now = Date.now();
  const validFrom = body.validFrom || new Date().toISOString().slice(0, 10);
  const lockMarker = `${validFrom}#${generateId('schedule_lock')}`;

  if (typeof env.DB.batch !== 'function') {
    return errorResponse('SCHEDULE_WRITE_UNAVAILABLE', 'قاعدة البيانات لا تدعم حفظ الجدول الذري حالياً', 503);
  }

  const batchStatements: D1PreparedStatement[] = [];
  let deactivateIndex = -1;

  // Compare-and-swap the exact active version observed before validation.
  if (currentVersion) {
    deactivateIndex = batchStatements.length;
    batchStatements.push(
      env.DB.prepare(
        `UPDATE schedule_versions
         SET is_active = 0, valid_until = ?
         WHERE id = ? AND group_id = ? AND is_active = 1`
      ).bind(lockMarker, currentVersion.id, groupId)
    );
  }

  const versionInsertIndex = batchStatements.length;
  const versionInsertSql = currentVersion
    ? `INSERT INTO schedule_versions
         (id, group_id, version_number, valid_from, is_active, created_at, created_by)
       SELECT ?, ?, ?, ?, 1, ?, ?
       WHERE EXISTS (
         SELECT 1 FROM schedule_versions
         WHERE id = ? AND group_id = ? AND is_active = 0 AND valid_until = ?
       )
       AND NOT EXISTS (
         SELECT 1 FROM schedule_versions WHERE group_id = ? AND is_active = 1
       )`
    : `INSERT INTO schedule_versions
         (id, group_id, version_number, valid_from, is_active, created_at, created_by)
       SELECT ?, ?, ?, ?, 1, ?, ?
       WHERE NOT EXISTS (
         SELECT 1 FROM schedule_versions WHERE group_id = ? AND is_active = 1
       )`;

  const versionParams = currentVersion
    ? [newVersionId, groupId, newVersionNum, validFrom, now, user.userId,
       currentVersion.id, groupId, lockMarker, groupId]
    : [newVersionId, groupId, newVersionNum, validFrom, now, user.userId, groupId];

  batchStatements.push(env.DB.prepare(versionInsertSql).bind(...versionParams));

  // Create optional subjects only after the new version has been inserted.
  for (const slot of slotList) {
    if (slot.subjectName) {
      batchStatements.push(
        env.DB.prepare(
          `INSERT OR IGNORE INTO subjects (id, name_ar, icon, color_hex)
           SELECT ?, ?, ?, ?
           WHERE EXISTS (
             SELECT 1 FROM schedule_versions
             WHERE id = ? AND group_id = ? AND is_active = 1
           )`
        ).bind(
          slot.subjectId,
          slot.subjectName,
          slot.subjectIcon || '📚',
          slot.colorHex || '#00E5FF',
          newVersionId,
          groupId
        )
      );
    }
  }

  const slotInsertIndexes: number[] = [];
  for (const slot of slotList) {
    const slotId = generateId('slot');
    slotInsertIndexes.push(batchStatements.length);
    batchStatements.push(
      env.DB.prepare(
        `INSERT INTO schedule_slots
           (id, version_id, day_of_week, slot_order, subject_id, start_time, end_time)
         SELECT ?, ?, ?, ?, ?, ?, ?
         WHERE EXISTS (
           SELECT 1 FROM schedule_versions
           WHERE id = ? AND group_id = ? AND is_active = 1
         )
         AND NOT EXISTS (
           SELECT 1 FROM schedule_slots
           WHERE version_id = ? AND day_of_week = ? AND slot_order = ?
         )`
      ).bind(
        slotId,
        newVersionId,
        slot.dayOfWeek,
        slot.slotOrder,
        slot.subjectId.trim(),
        slot.startTime?.trim() || null,
        slot.endTime?.trim() || null,
        newVersionId,
        groupId,
        newVersionId,
        slot.dayOfWeek,
        slot.slotOrder
      )
    );
  }

  // Restore the externally visible calendar date after using a transaction-unique
  // marker to prove the old active version was claimed by this exact batch.
  if (currentVersion) {
    batchStatements.push(
      env.DB.prepare(
        `UPDATE schedule_versions
         SET valid_until = ?
         WHERE id = ? AND group_id = ? AND is_active = 0 AND valid_until = ?`
      ).bind(validFrom, currentVersion.id, groupId, lockMarker)
    );
  }

  const results = await env.DB.batch(batchStatements);

  if (currentVersion && results[deactivateIndex]?.meta?.changes !== 1) {
    return errorResponse('SCHEDULE_VERSION_CHANGED', 'تغيرت النسخة النشطة أثناء تجهيز الجدول. حدّث الصفحة وحاول مجدداً.', 409);
  }
  if (results[versionInsertIndex]?.meta?.changes !== 1) {
    return errorResponse('SCHEDULE_VERSION_CHANGED', 'أنشأ طلب آخر نسخة نشطة للجدول. حدّث الصفحة ثم أعد المحاولة.', 409);
  }

  const failedSlot = slotInsertIndexes.find(index => results[index]?.meta?.changes !== 1);
  if (failedSlot !== undefined) {
    return errorResponse('SCHEDULE_SLOT_CHANGED', 'تعذر إدراج إحدى الحصص بسبب تغير متزامن في الجدول. أعد تحميل الصفحة قبل المحاولة.', 409);
  }

  return jsonResponse({
    success: true,
    versionId: newVersionId,
    versionNumber: newVersionNum,
    message: newVersionNum === 1
      ? 'تم إنشاء وتثبيت النسخة الأولى من جدول الحصص بنجاح 📅'
      : `تم تحديث الجدول وإصدار النسخة (${newVersionNum}) بنجاح ✨`,
  });
}

/**
 * Deletes a single slot.
 */
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
    return errorResponse('SCHEDULE_NOT_FOUND', 'لا يوجد جدول فعال لهذه المجموعة', 404);
  }

  const deletion = await env.DB.prepare(
    `DELETE FROM schedule_slots
     WHERE version_id = ? AND day_of_week = ? AND slot_order = ?
       AND EXISTS (
         SELECT 1 FROM schedule_versions
         WHERE id = ? AND group_id = ? AND is_active = 1
       )`
  ).bind(activeVersion.id, dayOfWeek, slotOrder, activeVersion.id, groupId).run();

  if (deletion.meta?.changes !== 1) {
    return errorResponse(
      'SCHEDULE_SLOT_CHANGED',
      'تغيرت النسخة النشطة أو لم تعد الحصة موجودة. حدّث الجدول وحاول مجدداً.',
      409
    );
  }

  return jsonResponse({
    success: true,
    message: 'تم حذف الحصة من الجدول بنجاح',
  });
}

/**
 * Lists all schedule change proposals for a group.
 */
export async function handleGetScheduleProposals(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const now = Date.now();
  await env.DB.prepare(
    `UPDATE community_decisions
     SET status = 'EXPIRED'
     WHERE group_id = ? AND request_type IN ('SCHEDULE', 'SCHEDULE_CHANGE')
       AND status = 'PENDING' AND expires_at IS NOT NULL AND expires_at < ?`
  ).bind(groupId, now).run();
  await env.DB.prepare(
    `UPDATE schedule_proposals
     SET status = 'EXPIRED'
     WHERE group_id = ? AND status = 'PENDING'
       AND id IN (
         SELECT target_id FROM community_decisions
         WHERE group_id = ? AND request_type IN ('SCHEDULE', 'SCHEDULE_CHANGE') AND status = 'EXPIRED'
       )`
  ).bind(groupId, groupId).run();

  const proposals = await env.DB.prepare(
    `SELECT sp.id, sp.group_id, sp.proposed_by, sp.day_of_week, sp.slot_order,
            sp.old_subject_id, sp.new_subject_id, sp.reason,
            CASE WHEN cd.status = 'APPLIED' THEN 'ACCEPTED'
                 WHEN cd.status IS NOT NULL THEN cd.status ELSE sp.status END AS status,
            COALESCE(cd.votes_for, sp.votes_for, 0) AS votes_for,
            COALESCE(cd.votes_against, sp.votes_against, 0) AS votes_against,
            sp.created_at,
            u.full_name as proposer_name,
            u.role as proposer_role,
            COALESCE(s_old.name_ar, sp.old_subject_id) as old_subject_name,
            COALESCE(s_new.name_ar, sp.new_subject_id) as new_subject_name,
            COALESCE(s_new.icon, '📚') as new_subject_icon,
            CASE WHEN cv.vote_choice = 1 THEN 'FOR'
                 WHEN cv.vote_choice = 0 THEN 'AGAINST' ELSE NULL END AS my_vote
     FROM schedule_proposals sp
     LEFT JOIN users u ON sp.proposed_by = u.id
     LEFT JOIN subjects s_old ON sp.old_subject_id = s_old.id
     LEFT JOIN subjects s_new ON sp.new_subject_id = s_new.id
     LEFT JOIN community_decisions cd
       ON cd.target_id = sp.id AND cd.request_type IN ('SCHEDULE', 'SCHEDULE_CHANGE')
     LEFT JOIN community_votes cv ON cv.decision_id = cd.id AND cv.user_id = ?
     WHERE sp.group_id = ?
     ORDER BY CASE WHEN COALESCE(cd.status, sp.status) = 'PENDING' THEN 0 ELSE 1 END, sp.created_at DESC`
  ).bind(user.userId, groupId).all();

  const isScheduleManager = await hasPermission(user, Permission.MANAGE_SCHEDULE, {
    db: env.DB,
    groupId,
  });

  return jsonResponse({
    success: true,
    canReviewProposals: isScheduleManager,
    proposals: (proposals.results || []).map((p: any) => ({
      id: p.id,
      groupId: p.group_id,
      proposedBy: p.proposed_by,
      proposerName: p.proposer_name || 'طالب في الشعبة',
      proposerRole: p.proposer_role || 'STUDENT',
      dayOfWeek: p.day_of_week,
      slotOrder: p.slot_order,
      oldSubjectId: p.old_subject_id,
      oldSubjectName: p.old_subject_name,
      newSubjectId: p.new_subject_id,
      newSubjectName: p.new_subject_name,
      newSubjectIcon: p.new_subject_icon,
      reason: p.reason,
      status: p.status,
      votesFor: p.votes_for || 0,
      votesAgainst: p.votes_against || 0,
      myVote: p.my_vote || null,
      createdAt: p.created_at,
    })),
  });
}

/**
 * Creates a new schedule change proposal.
 */
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

  // TA-19: Derive server truth for the slot's current subject from the active schedule version
  const activeVersion = await env.DB.prepare(
    `SELECT id FROM schedule_versions WHERE group_id = ? AND is_active = 1 ORDER BY version_number DESC LIMIT 1`
  ).bind(body.groupId).first<{ id: string }>();

  let serverCurrentSubjectId: string | null = null;
  if (activeVersion) {
    const currentSlot = await env.DB.prepare(
      `SELECT subject_id FROM schedule_slots WHERE version_id = ? AND day_of_week = ? AND slot_order = ?`
    ).bind(activeVersion.id, body.dayOfWeek, body.slotOrder).first<{ subject_id: string }>();
    serverCurrentSubjectId = currentSlot?.subject_id || null;
  }

  // The active schedule is authoritative even when the requested cell is empty.
  // Never trust a stale client oldSubjectId when the server has no subject in that slot.
  const finalOldSubjectId = serverCurrentSubjectId;
  if (finalOldSubjectId === body.newSubjectId.trim()) {
    return errorResponse('NO_SCHEDULE_CHANGE', 'المادة الجديدة هي نفسها المادة الموجودة حالياً في هذه الحصة؛ لا يوجد تغيير لطرحه للتصويت', 400);
  }

  const proposalId = generateId('prop');
  const decisionId = generateId('dec');
  const now = Date.now();
  const eligibleRows = await env.DB.prepare(
    `SELECT user_id FROM group_members
     WHERE group_id = ? AND status = 'ACTIVE' AND role != 'BANNED'`
  ).bind(body.groupId).all<{ user_id: string }>();
  const eligibleVoters = [...new Set((eligibleRows.results || []).map(row => row.user_id))];
  if (!eligibleVoters.includes(user.userId)) eligibleVoters.push(user.userId);
  const totalEligible = Math.max(1, eligibleVoters.length);
  const thresholdPercent = 60;
  const subjectRows = await env.DB.prepare(
    `SELECT id, name_ar FROM subjects WHERE id IN (?, ?)`
  ).bind(finalOldSubjectId || '', body.newSubjectId.trim()).all<{ id: string; name_ar: string }>();
  const subjectNames = new Map((subjectRows.results || []).map(row => [row.id, row.name_ar]));
  const oldSubjectLabel = finalOldSubjectId
    ? (subjectNames.get(finalOldSubjectId) || finalOldSubjectId)
    : 'فارغة';
  const newSubjectLabel = subjectNames.get(body.newSubjectId.trim()) || body.newSubjectId.trim();
  const voterSnapshot = JSON.stringify({
    text: `تغيير مادة الحصة من ${oldSubjectLabel} إلى ${newSubjectLabel}. السبب: ${body.reason.trim()}`,
    voters: eligibleVoters,
    targetType: 'SCHEDULE',
  });
  const dayNames = ['الأحد', 'الإثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت'];
  const decisionTitle = `اقتراح تعديل الجدول • ${dayNames[body.dayOfWeek] || 'اليوم'} • الحصة ${body.slotOrder}`;

  try {
    if (typeof env.DB.batch !== 'function') {
      return errorResponse('DECISION_STORE_UNAVAILABLE', 'يتطلب ربط مقترحات الجدول بمحرك القرارات دعماً للعمليات الذرية', 503);
    }
    await env.DB.batch([
      env.DB.prepare(
        `INSERT INTO schedule_proposals
         (id, group_id, proposed_by, day_of_week, slot_order, old_subject_id, new_subject_id, reason, status, votes_for, votes_against, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 1, 0, ?)`
      ).bind(
        proposalId, body.groupId, user.userId, body.dayOfWeek, body.slotOrder,
        finalOldSubjectId, body.newSubjectId.trim(), body.reason.trim(), now
      ),
      env.DB.prepare(
        `INSERT INTO community_decisions
         (id, group_id, request_type, target_id, title, description,
          requested_by, requester_name, total_eligible_voters, threshold_percent,
          votes_for, votes_against, status, expires_at, created_at)
         VALUES (?, ?, 'SCHEDULE', ?, ?, ?, ?, ?, ?, ?, 1, 0, 'PENDING', ?, ?)`
      ).bind(
        decisionId, body.groupId, proposalId, decisionTitle, voterSnapshot,
        user.userId, user.fullName, totalEligible, thresholdPercent, now + 48 * 3600 * 1000, now
      ),
      env.DB.prepare(
        `INSERT INTO community_votes (decision_id, user_id, vote_choice, created_at)
         VALUES (?, ?, 1, ?)`
      ).bind(decisionId, user.userId, now),
    ]);
  } catch (error) {
    console.error('Failed to create linked schedule proposal and community decision:', error);
    return errorResponse('SCHEDULE_DECISION_CREATE_FAILED', 'تعذر إنشاء مقترح الجدول والقرار المرتبط به بصورة مكتملة', 503);
  }

  if (totalEligible <= 1) {
    const result = await applyScheduleProposalToActiveSchedule({
      id: proposalId, group_id: body.groupId, day_of_week: body.dayOfWeek,
      slot_order: body.slotOrder, old_subject_id: finalOldSubjectId,
      new_subject_id: body.newSubjectId.trim(), status: 'PENDING', proposed_by: user.userId,
    }, user.userId, env);
    if (result.success) {
      await env.DB.prepare(`UPDATE schedule_proposals SET status = 'ACCEPTED' WHERE id = ?`).bind(proposalId).run();
      await env.DB.prepare(`UPDATE community_decisions SET status = 'APPLIED', applied_at = ?, applied_by = ? WHERE id = ?`)
        .bind(Date.now(), user.userId, decisionId).run();
    } else {
      await env.DB.prepare(`UPDATE schedule_proposals SET status = 'CONFLICTED' WHERE id = ?`).bind(proposalId).run();
      await env.DB.prepare(`UPDATE community_decisions SET status = 'REJECTED' WHERE id = ?`).bind(decisionId).run();
      return errorResponse('SCHEDULE_CONFLICT', result.message, 409);
    }
  }

  return jsonResponse({
    success: true,
    proposalId,
    decisionId,
    status: totalEligible <= 1 ? 'APPLIED' : 'PENDING',
    message: totalEligible <= 1
      ? 'تم اعتماد المقترح تلقائياً لعدم وجود أعضاء آخرين للتصويت'
      : 'تم نشر مقترح الجدول مباشرة في شاشة القرارات والتصويتات الجماعية 🗳️',
  });
}

/**
 * Votes on a schedule proposal (FOR / AGAINST).
 */
export async function handleVoteScheduleProposal(
  proposalId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const proposal = await env.DB.prepare(
    `SELECT id, group_id, status FROM schedule_proposals WHERE id = ?`
  ).bind(proposalId).first<{ id: string; group_id: string; status: string }>();

  if (!proposal) {
    return errorResponse('PROPOSAL_NOT_FOUND', 'المقترح غير موجود', 404);
  }

  if (proposal.status !== 'PENDING') {
    return errorResponse('PROPOSAL_CLOSED', 'هذا المقترح تم حسمه مسبقاً ولا يمكن التصويت عليه');
  }

  const memberCheck = await requireGroupMember(user, proposal.group_id, env.DB);
  if (memberCheck) return memberCheck;

  const body = await request.json() as { voteType?: 'FOR' | 'AGAINST' };
  const voteType = body.voteType === 'AGAINST' ? 'AGAINST' : 'FOR';

  // Check existing vote
  const existingVote = await env.DB.prepare(
    `SELECT vote_type FROM schedule_proposal_votes WHERE proposal_id = ? AND user_id = ?`
  ).bind(proposalId, user.userId).first<{ vote_type: string }>();

  if (existingVote && existingVote.vote_type === voteType) {
    // Remove vote (toggle)
    await env.DB.prepare(
      `DELETE FROM schedule_proposal_votes WHERE proposal_id = ? AND user_id = ?`
    ).bind(proposalId, user.userId).run();
  } else {
    // Upsert vote
    await env.DB.prepare(
      `INSERT OR REPLACE INTO schedule_proposal_votes (proposal_id, user_id, vote_type, created_at)
       VALUES (?, ?, ?, ?)`
    ).bind(proposalId, user.userId, voteType, Date.now()).run();
  }

  // Recalculate totals
  const counts = await env.DB.prepare(
    `SELECT
       SUM(CASE WHEN vote_type = 'FOR' THEN 1 ELSE 0 END) as votes_for,
       SUM(CASE WHEN vote_type = 'AGAINST' THEN 1 ELSE 0 END) as votes_against
     FROM schedule_proposal_votes WHERE proposal_id = ?`
  ).bind(proposalId).first<{ votes_for: number; votes_against: number }>();

  const votesFor = counts?.votes_for || 0;
  const votesAgainst = counts?.votes_against || 0;

  await env.DB.prepare(
    `UPDATE schedule_proposals SET votes_for = ?, votes_against = ? WHERE id = ?`
  ).bind(votesFor, votesAgainst, proposalId).run();

  // Check group membership count to determine consensus threshold (Point 14 & 20: ACTIVE members only)
  const memberCountResult = await env.DB.prepare(
    `SELECT COUNT(*) as total FROM group_members WHERE group_id = ? AND status = 'ACTIVE'`
  ).bind(proposal.group_id).first<{ total: number }>();
  const totalMembers = memberCountResult?.total || 1;

  // Quorum: between 2 and 5 members or ~35% of group
  const quorum = Math.max(2, Math.min(5, Math.ceil(totalMembers * 0.35)));

  let autoResolvedStatus: 'ACCEPTED' | 'REJECTED' | 'CONFLICTED' | null = null;
  let autoResolvedMessage: string | null = null;

  if (votesFor >= quorum && votesFor > votesAgainst * 1.5) {
    const fullProposal = await env.DB.prepare(
      `SELECT * FROM schedule_proposals WHERE id = ?`
    ).bind(proposalId).first<ScheduleProposalRow>();

    if (fullProposal) {
      const res = await applyScheduleProposalToActiveSchedule(fullProposal, 'SYSTEM_VOTE_CONSENSUS', env);
      if (res.success) {
        autoResolvedStatus = 'ACCEPTED';
        autoResolvedMessage = `اكتمل نصاب تصويت الشعبة (${votesFor}/${quorum}) وتم اعتماد تغيير الحصة تلقائياً وتحديث الجدول للنسخة (${res.versionNumber}) 🎉`;
      } else {
        autoResolvedStatus = res.conflict ? 'CONFLICTED' : 'REJECTED';
        autoResolvedMessage = res.message;
      }
    }
  } else if (votesAgainst >= quorum && votesAgainst > votesFor * 1.5) {
    await env.DB.prepare(
      `UPDATE schedule_proposals SET status = 'REJECTED' WHERE id = ?`
    ).bind(proposalId).run();
    autoResolvedStatus = 'REJECTED';
    autoResolvedMessage = `تم حسم المقترح بالرفض وفق تصويت أغلبية أعضاء الشعبة (${votesAgainst} ضد)`;
  }

  return jsonResponse({
    success: true,
    votesFor,
    votesAgainst,
    status: autoResolvedStatus || 'PENDING',
    myVote: existingVote?.vote_type === voteType ? null : voteType,
    message: autoResolvedMessage || 'تم تسجيل تصويتك على مقترح الجدول بنجاح 👍',
  });
}

interface ScheduleProposalRow {
  id: string;
  group_id: string;
  day_of_week: number;
  slot_order: number;
  old_subject_id: string | null;
  new_subject_id: string;
  status: string;
  proposed_by: string;
}

export type ApplyProposalResult =
  | { success: true; versionNumber: number }
  | { success: false; conflict: true; currentSubjectId: string | null; expectedSubjectId: string | null; message: string };

/**
 * Helper to apply an approved proposal to the active schedule and generate a new version.
 * TA-19: Validates concurrency and verifies that current active slot still matches proposal.old_subject_id before applying.
 */
export async function applyScheduleProposalToActiveSchedule(
  proposal: ScheduleProposalRow,
  resolvedByUserId: string,
  env: Env
): Promise<ApplyProposalResult> {
  const readFreshProposal = () => env.DB.prepare(
    `SELECT sp.status, sv.version_number
     FROM schedule_proposals sp
     LEFT JOIN schedule_versions sv ON sv.group_id = sp.group_id AND sv.is_active = 1
     WHERE sp.id = ?`
  ).bind(proposal.id).first<{ status: string; version_number: number | null }>();

  // Idempotency check before reading the active schedule.
  const freshProposal = await readFreshProposal();
  if (freshProposal?.status === 'ACCEPTED') {
    return { success: true, versionNumber: freshProposal.version_number || 1 };
  }
  if (!freshProposal || freshProposal.status !== 'PENDING') {
    return {
      success: false,
      conflict: true,
      currentSubjectId: null,
      expectedSubjectId: proposal.old_subject_id || null,
      message: 'هذا المقترح لم يعد معلّقاً أو تم حسمه بواسطة عملية أخرى',
    };
  }

  const currentVersion = await env.DB.prepare(
    `SELECT id, version_number
     FROM schedule_versions
     WHERE group_id = ? AND is_active = 1
     ORDER BY version_number DESC
     LIMIT 1`
  ).bind(proposal.group_id).first<{ id: string; version_number: number }>();

  const newVersionNum = currentVersion ? currentVersion.version_number + 1 : 1;
  const newVersionId = generateId('sch_ver');
  const now = Date.now();
  const today = new Date().toISOString().slice(0, 10);

  let preservedStartTime: string | null = null;
  let preservedEndTime: string | null = null;

  type OldSlotItem = {
    id: string;
    day_of_week: number;
    slot_order: number;
    subject_id: string;
    start_time: string | null;
    end_time: string | null;
  };
  const slotsToCopy: OldSlotItem[] = [];

  if (currentVersion) {
    const oldSlots = await env.DB.prepare(
      `SELECT day_of_week, slot_order, subject_id, start_time, end_time
       FROM schedule_slots WHERE version_id = ?`
    ).bind(currentVersion.id).all<{
      day_of_week: number;
      slot_order: number;
      subject_id: string;
      start_time: string | null;
      end_time: string | null;
    }>();

    // Verify the target slot still matches the state the proposal was based on.
    const activeSlot = (oldSlots.results || []).find(
      slot => slot.day_of_week === proposal.day_of_week && slot.slot_order === proposal.slot_order
    );
    const activeSubjectId = activeSlot?.subject_id || null;
    const expectedSubjectId = proposal.old_subject_id || null;

    if (activeSubjectId !== expectedSubjectId) {
      await env.DB.prepare(
        `UPDATE schedule_proposals SET status = 'CONFLICTED'
         WHERE id = ? AND status = 'PENDING'`
      ).bind(proposal.id).run();

      return {
        success: false,
        conflict: true,
        currentSubjectId: activeSubjectId,
        expectedSubjectId,
        message: expectedSubjectId === null
          ? `تعذر تطبيق المقترح لوجود تعارض: تم إشغال الحصة في الجدول الفعال بالمادة (${activeSubjectId}) بعد إنشاء المقترح الذي افترض أن الحصة فارغة`
          : `تعذر تطبيق المقترح لوجود تعارض: تم تغيير مادة الحصة في الجدول الفعال إلى (${activeSubjectId || 'فارغة'}) بعد إنشاء المقترح المبني على (${expectedSubjectId})`,
      };
    }

    for (const slot of (oldSlots.results || [])) {
      if (slot.day_of_week === proposal.day_of_week && slot.slot_order === proposal.slot_order) {
        preservedStartTime = slot.start_time;
        preservedEndTime = slot.end_time;
        continue;
      }
      slotsToCopy.push({
        id: generateId('slot'),
        day_of_week: slot.day_of_week,
        slot_order: slot.slot_order,
        subject_id: slot.subject_id,
        start_time: slot.start_time,
        end_time: slot.end_time,
      });
    }
  }

  const appliedSlotId = generateId('slot');
  const batchStatements: D1PreparedStatement[] = [];

  // 1. Atomically claim this proposal. Only one application can claim PENDING.
  batchStatements.push(
    env.DB.prepare(
      `UPDATE schedule_proposals SET status = 'APPLYING'
       WHERE id = ? AND group_id = ? AND status = 'PENDING'`
    ).bind(proposal.id, proposal.group_id)
  );

  // 2. Compare-and-swap the active version. A competing schedule update can win only once.
  if (currentVersion) {
    batchStatements.push(
      env.DB.prepare(
        `UPDATE schedule_versions
         SET is_active = 0, valid_until = ?
         WHERE id = ? AND group_id = ? AND is_active = 1
           AND EXISTS (
             SELECT 1 FROM schedule_proposals
             WHERE id = ? AND group_id = ? AND status = 'APPLYING'
           )`
      ).bind(today, currentVersion.id, proposal.group_id, proposal.id, proposal.group_id)
    );
  }

  // 3. Insert a new active version only if the proposal is claimed and the group
  // has no other active version after the compare-and-swap above.
  batchStatements.push(
    env.DB.prepare(
      `INSERT INTO schedule_versions
         (id, group_id, version_number, valid_from, is_active, created_at, created_by)
       SELECT ?, ?, ?, ?, 1, ?, ?
       WHERE EXISTS (
         SELECT 1 FROM schedule_proposals
         WHERE id = ? AND group_id = ? AND status = 'APPLYING'
       )
       AND NOT EXISTS (
         SELECT 1 FROM schedule_versions
         WHERE group_id = ? AND is_active = 1
       )`
    ).bind(
      newVersionId,
      proposal.group_id,
      newVersionNum,
      today,
      now,
      resolvedByUserId,
      proposal.id,
      proposal.group_id,
      proposal.group_id
    )
  );

  // 4. Copy unchanged slots only when this attempt actually created its active version.
  for (const slot of slotsToCopy) {
    batchStatements.push(
      env.DB.prepare(
        `INSERT INTO schedule_slots
           (id, version_id, day_of_week, slot_order, subject_id, start_time, end_time)
         SELECT ?, ?, ?, ?, ?, ?, ?
         WHERE EXISTS (
           SELECT 1 FROM schedule_versions
           WHERE id = ? AND group_id = ? AND is_active = 1
         )`
      ).bind(
        slot.id,
        newVersionId,
        slot.day_of_week,
        slot.slot_order,
        slot.subject_id,
        slot.start_time,
        slot.end_time,
        newVersionId,
        proposal.group_id
      )
    );
  }

  // 5. Insert the changed slot under the same active-version guard.
  batchStatements.push(
    env.DB.prepare(
      `INSERT INTO schedule_slots
         (id, version_id, day_of_week, slot_order, subject_id, start_time, end_time)
       SELECT ?, ?, ?, ?, ?, ?, ?
       WHERE EXISTS (
         SELECT 1 FROM schedule_versions
         WHERE id = ? AND group_id = ? AND is_active = 1
       )`
    ).bind(
      appliedSlotId,
      newVersionId,
      proposal.day_of_week,
      proposal.slot_order,
      proposal.new_subject_id,
      preservedStartTime,
      preservedEndTime,
      newVersionId,
      proposal.group_id
    )
  );

  // 6. Resolve only if our version was successfully created.
  const acceptedIndex = batchStatements.length;
  batchStatements.push(
    env.DB.prepare(
      `UPDATE schedule_proposals SET status = 'ACCEPTED'
       WHERE id = ? AND group_id = ? AND status = 'APPLYING'
         AND EXISTS (
           SELECT 1 FROM schedule_versions
           WHERE id = ? AND group_id = ? AND is_active = 1
         )`
    ).bind(proposal.id, proposal.group_id, newVersionId, proposal.group_id)
  );

  // 7. If another schedule operation won the active-version compare-and-swap,
  // close this proposal as conflicted without creating another active version.
  batchStatements.push(
    env.DB.prepare(
      `UPDATE schedule_proposals SET status = 'CONFLICTED'
       WHERE id = ? AND group_id = ? AND status = 'APPLYING'
         AND NOT EXISTS (
           SELECT 1 FROM schedule_versions
           WHERE id = ? AND group_id = ? AND is_active = 1
         )`
    ).bind(proposal.id, proposal.group_id, newVersionId, proposal.group_id)
  );

  if (typeof env.DB.batch !== 'function') {
    return {
      success: false,
      conflict: true,
      currentSubjectId: null,
      expectedSubjectId: proposal.old_subject_id || null,
      message: 'قاعدة البيانات لا تدعم تنفيذ تحديث الجدول الذري؛ تم إيقاف العملية لحماية النسخ النشطة',
    };
  }

  const results = await env.DB.batch(batchStatements);
  const claimResult = results[0];
  const versionInsertIndex = currentVersion ? 2 : 1;
  const versionResult = results[versionInsertIndex];
  const acceptedResult = results[acceptedIndex];

  if (claimResult?.meta?.changes !== 1 || versionResult?.meta?.changes !== 1 || acceptedResult?.meta?.changes !== 1) {
    // Another worker may have completed this exact proposal while this request was running.
    const latest = await readFreshProposal();
    if (latest?.status === 'ACCEPTED') {
      return { success: true, versionNumber: latest.version_number || newVersionNum };
    }

    return {
      success: false,
      conflict: true,
      currentSubjectId: null,
      expectedSubjectId: proposal.old_subject_id || null,
      message: 'لم نتمكن من حجز نسخة الجدول للمقترح؛ ربما طبّقت عملية أخرى تغييراً متزامناً. راجع الجدول وأعد تقديم المقترح عند الحاجة.',
    };
  }

  return { success: true, versionNumber: newVersionNum };
}

/**
 * Approves a schedule change proposal, applies it to create a new schedule version, and closes the proposal.
 */
export async function handleApproveScheduleProposal(
  proposalId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const proposal = await env.DB.prepare(
    `SELECT * FROM schedule_proposals WHERE id = ?`
  ).bind(proposalId).first<ScheduleProposalRow>();

  if (!proposal) {
    return errorResponse('PROPOSAL_NOT_FOUND', 'المقترح غير موجود', 404);
  }

  if (proposal.status !== 'PENDING') {
    return errorResponse('PROPOSAL_ALREADY_RESOLVED', 'تم حسم هذا المقترح مسبقاً');
  }

  const isScheduleManager = await hasPermission(user, Permission.MANAGE_SCHEDULE, {
    db: env.DB,
    groupId: proposal.group_id,
  });

  if (!isScheduleManager) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية اعتماد وتطبيق مقترحات الجدول', 403);
  }

  const result = await applyScheduleProposalToActiveSchedule(proposal, user.userId, env);

  if (!result.success) {
    return errorResponse('SCHEDULE_CONFLICT', result.message, 409);
  }

  return jsonResponse({
    success: true,
    newVersionNumber: result.versionNumber,
    message: `تم اعتماد وتطبيق المقترح بنجاح وترقية الجدول إلى النسخة (${result.versionNumber}) ✅`,
  });
}

/**
 * Rejects a schedule proposal.
 */
export async function handleRejectScheduleProposal(
  proposalId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const proposal = await env.DB.prepare(
    `SELECT id, group_id, status FROM schedule_proposals WHERE id = ?`
  ).bind(proposalId).first<{ id: string; group_id: string; status: string }>();

  if (!proposal) {
    return errorResponse('PROPOSAL_NOT_FOUND', 'المقترح غير موجود', 404);
  }

  if (proposal.status !== 'PENDING') {
    return errorResponse('PROPOSAL_ALREADY_RESOLVED', 'تم حسم هذا المقترح مسبقاً');
  }

  const isScheduleManager = await hasPermission(user, Permission.MANAGE_SCHEDULE, {
    db: env.DB,
    groupId: proposal.group_id,
  });

  if (!isScheduleManager) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية مراجعة ورفض مقترحات الجدول', 403);
  }

  await env.DB.prepare(
    `UPDATE schedule_proposals SET status = 'REJECTED' WHERE id = ?`
  ).bind(proposalId).run();

  return jsonResponse({
    success: true,
    message: 'تم رفض المقترح وإغلاقه ❌',
  });
}

/**
 * Returns schedule version history for a group (النسخ السابقة ومن أنشأها).
 */
export async function handleGetScheduleVersions(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const versions = await env.DB.prepare(
    `SELECT sv.id, sv.group_id, sv.version_number, sv.valid_from, sv.valid_until,
            sv.is_active, sv.created_at, sv.created_by,
            u.full_name as creator_name, u.role as creator_role
     FROM schedule_versions sv
     LEFT JOIN users u ON sv.created_by = u.id
     WHERE sv.group_id = ?
     ORDER BY sv.version_number DESC`
  ).bind(groupId).all();

  return jsonResponse({
    success: true,
    versions: (versions.results || []).map((v: any) => ({
      id: v.id,
      groupId: v.group_id,
      versionNumber: v.version_number,
      validFrom: v.valid_from,
      validUntil: v.valid_until,
      isActive: v.is_active === 1,
      createdAt: v.created_at,
      createdBy: v.created_by,
      creatorName: v.creator_name || 'عضو في الشعبة',
      creatorRole: sanitizePublicRole(v.creator_role || 'STUDENT'),
    })),
  });
}
