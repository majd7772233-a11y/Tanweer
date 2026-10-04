import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapScheduleSlot } from '../lib/mappers';
import { requireGroupMember, requireScheduleManager } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';
import { Permission, hasPermission } from '../lib/permissions';

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
  const permCheck = await requireScheduleManager(user, groupId, env.DB);
  if (permCheck) return permCheck;

  const body = await request.json() as ScheduleSlotInput;

  if (body.dayOfWeek === undefined || !body.slotOrder || !body.subjectId) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد اليوم ورقم الحصة والمادة');
  }

  // 1. Ensure active version exists or initialize Version 1
  let activeVersion = await env.DB.prepare(
    `SELECT id, version_number FROM schedule_versions WHERE group_id = ? AND is_active = 1 ORDER BY version_number DESC LIMIT 1`
  ).bind(groupId).first<{ id: string; version_number: number }>();

  if (!activeVersion) {
    const versionId = generateId('sch_ver');
    const today = new Date().toISOString().slice(0, 10);
    await env.DB.prepare(
      `INSERT INTO schedule_versions (id, group_id, version_number, valid_from, is_active, created_at, created_by)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).bind(versionId, groupId, 1, today, 1, Date.now(), user.userId).run();
    activeVersion = { id: versionId, version_number: 1 };
  }

  // 2. Ensure subject exists
  if (body.subjectName) {
    await env.DB.prepare(
      `INSERT OR IGNORE INTO subjects (id, name_ar, icon, color_hex) VALUES (?, ?, ?, ?)`
    ).bind(body.subjectId, body.subjectName, body.subjectIcon || '📚', body.colorHex || '#00E5FF').run();
  }

  // 3. Remove old slot at that day & slot_order in this version
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
    versionNumber: activeVersion.version_number,
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
  const permCheck = await requireScheduleManager(user, groupId, env.DB);
  if (permCheck) return permCheck;

  const body = await request.json() as {
    slots?: ScheduleSlotInput[];
    validFrom?: string;
  };

  const slotList = body.slots || [];
  if (!Array.isArray(slotList) || slotList.length === 0) {
    return errorResponse('INVALID_INPUT', 'يرجى تزويد قائمة الحصص لتحديث الجدول');
  }

  // Determine current active version
  const currentVersion = await env.DB.prepare(
    `SELECT id, version_number FROM schedule_versions WHERE group_id = ? AND is_active = 1 ORDER BY version_number DESC LIMIT 1`
  ).bind(groupId).first<{ id: string; version_number: number }>();

  const newVersionNum = currentVersion ? currentVersion.version_number + 1 : 1;
  const newVersionId = generateId('sch_ver');
  const now = Date.now();
  const validFrom = body.validFrom || new Date().toISOString().slice(0, 10);

  // Deactivate previous versions
  if (currentVersion) {
    await env.DB.prepare(
      `UPDATE schedule_versions SET is_active = 0, valid_until = ? WHERE group_id = ? AND is_active = 1`
    ).bind(validFrom, groupId).run();
  }

  // Insert new version
  await env.DB.prepare(
    `INSERT INTO schedule_versions (id, group_id, version_number, valid_from, is_active, created_at, created_by)
     VALUES (?, ?, ?, ?, 1, ?, ?)`
  ).bind(newVersionId, groupId, newVersionNum, validFrom, now, user.userId).run();

  // Insert all slots
  for (const s of slotList) {
    if (s.dayOfWeek === undefined || !s.slotOrder || !s.subjectId) continue;
    const slotId = generateId('slot');
    await env.DB.prepare(
      `INSERT INTO schedule_slots (id, version_id, day_of_week, slot_order, subject_id, start_time, end_time)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).bind(slotId, newVersionId, s.dayOfWeek, s.slotOrder, s.subjectId, s.startTime || null, s.endTime || null).run();
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

/**
 * Lists all schedule change proposals for a group.
 */
export async function handleGetScheduleProposals(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const proposals = await env.DB.prepare(
    `SELECT sp.id, sp.group_id, sp.proposed_by, sp.day_of_week, sp.slot_order,
            sp.old_subject_id, sp.new_subject_id, sp.reason, sp.status,
            sp.votes_for, sp.votes_against, sp.created_at,
            u.full_name as proposer_name,
            u.role as proposer_role,
            COALESCE(s_old.name_ar, sp.old_subject_id) as old_subject_name,
            COALESCE(s_new.name_ar, sp.new_subject_id) as new_subject_name,
            COALESCE(s_new.icon, '📚') as new_subject_icon,
            (SELECT vote_type FROM schedule_proposal_votes spv WHERE spv.proposal_id = sp.id AND spv.user_id = ?) as my_vote
     FROM schedule_proposals sp
     LEFT JOIN users u ON sp.proposed_by = u.id
     LEFT JOIN subjects s_old ON sp.old_subject_id = s_old.id
     LEFT JOIN subjects s_new ON sp.new_subject_id = s_new.id
     WHERE sp.group_id = ?
     ORDER BY CASE WHEN sp.status = 'PENDING' THEN 0 ELSE 1 END, sp.created_at DESC`
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

  const proposalId = generateId('prop');
  const now = Date.now();

  await env.DB.prepare(
    `INSERT INTO schedule_proposals (id, group_id, proposed_by, day_of_week, slot_order, old_subject_id, new_subject_id, reason, status, votes_for, votes_against, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    proposalId,
    body.groupId,
    user.userId,
    body.dayOfWeek,
    body.slotOrder,
    body.oldSubjectId || null,
    body.newSubjectId,
    body.reason.trim(),
    'PENDING',
    1,
    0,
    now
  ).run();

  // Proposer automatically votes FOR
  try {
    await env.DB.prepare(
      `INSERT OR REPLACE INTO schedule_proposal_votes (proposal_id, user_id, vote_type, created_at)
       VALUES (?, ?, 'FOR', ?)`
    ).bind(proposalId, user.userId, now).run();
  } catch {}

  return jsonResponse({
    success: true,
    proposalId,
    message: 'تم رفع مقترح تعديل الجدول بنجاح وطرحه لتصويت ومراجعة الأعضاء 🗳️',
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

  return jsonResponse({
    success: true,
    votesFor,
    votesAgainst,
    myVote: existingVote?.vote_type === voteType ? null : voteType,
    message: 'تم تسجيل تصويتك على مقترح الجدول بنجاح 👍',
  });
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
  ).bind(proposalId).first<{
    id: string;
    group_id: string;
    day_of_week: number;
    slot_order: number;
    old_subject_id: string;
    new_subject_id: string;
    status: string;
    proposed_by: string;
  }>();

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

  // 1. Get current active version
  const currentVersion = await env.DB.prepare(
    `SELECT id, version_number FROM schedule_versions WHERE group_id = ? AND is_active = 1 ORDER BY version_number DESC LIMIT 1`
  ).bind(proposal.group_id).first<{ id: string; version_number: number }>();

  const newVersionNum = currentVersion ? currentVersion.version_number + 1 : 1;
  const newVersionId = generateId('sch_ver');
  const now = Date.now();
  const today = new Date().toISOString().slice(0, 10);

  // Deactivate old active version
  if (currentVersion) {
    await env.DB.prepare(
      `UPDATE schedule_versions SET is_active = 0, valid_until = ? WHERE id = ?`
    ).bind(today, currentVersion.id).run();
  }

  // Create new active version
  await env.DB.prepare(
    `INSERT INTO schedule_versions (id, group_id, version_number, valid_from, is_active, created_at, created_by)
     VALUES (?, ?, ?, ?, 1, ?, ?)`
  ).bind(newVersionId, proposal.group_id, newVersionNum, today, now, user.userId).run();

  // Copy existing slots to new version, replacing the proposed slot
  if (currentVersion) {
    const oldSlots = await env.DB.prepare(
      `SELECT day_of_week, slot_order, subject_id, start_time, end_time FROM schedule_slots WHERE version_id = ?`
    ).bind(currentVersion.id).all<{
      day_of_week: number;
      slot_order: number;
      subject_id: string;
      start_time: string | null;
      end_time: string | null;
    }>();

    for (const s of (oldSlots.results || [])) {
      if (s.day_of_week === proposal.day_of_week && s.slot_order === proposal.slot_order) {
        continue; // skip the one being replaced
      }
      const newSlotId = generateId('slot');
      await env.DB.prepare(
        `INSERT INTO schedule_slots (id, version_id, day_of_week, slot_order, subject_id, start_time, end_time)
         VALUES (?, ?, ?, ?, ?, ?, ?)`
      ).bind(newSlotId, newVersionId, s.day_of_week, s.slot_order, s.subject_id, s.start_time, s.end_time).run();
    }
  }

  // Insert the approved new subject slot
  const appliedSlotId = generateId('slot');
  await env.DB.prepare(
    `INSERT INTO schedule_slots (id, version_id, day_of_week, slot_order, subject_id)
     VALUES (?, ?, ?, ?, ?)`
  ).bind(appliedSlotId, newVersionId, proposal.day_of_week, proposal.slot_order, proposal.new_subject_id).run();

  // Mark proposal as ACCEPTED
  await env.DB.prepare(
    `UPDATE schedule_proposals SET status = 'ACCEPTED' WHERE id = ?`
  ).bind(proposalId).run();

  return jsonResponse({
    success: true,
    newVersionNumber: newVersionNum,
    message: `تم اعتماد وتطبيق المقترح بنجاح وترقية الجدول إلى النسخة (${newVersionNum}) ✅`,
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
      creatorRole: v.creator_role || 'STUDENT',
    })),
  });
}
