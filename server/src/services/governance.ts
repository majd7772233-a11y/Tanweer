import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';
import { Permission, hasPermission } from '../lib/permissions';
import { Role, normalizeRole } from '../lib/roles';

/**
 * -------------------------------------------------------------
 * 1. Content Correction Submissions & Moderation
 * -------------------------------------------------------------
 */
export async function handleCreateContentCorrection(
  contentId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const content = await env.DB.prepare(
    `SELECT id, group_id, title, description, created_by FROM contents WHERE id = ?`
  ).bind(contentId).first<{
    id: string;
    group_id: string;
    title: string;
    description: string;
    created_by: string;
  }>();

  if (!content) {
    return errorResponse('CONTENT_NOT_FOUND', 'الدرس غير موجود', 404);
  }

  const memberCheck = await requireGroupMember(user, content.group_id, env.DB);
  if (memberCheck) return memberCheck;

  const body = await request.json() as {
    fieldName?: string;
    originalValue?: string;
    proposedValue?: string;
    reason?: string;
  };

  if (!body.fieldName || !body.proposedValue || !body.reason) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد الحقل، القيمة المصوبة وسبب التعديل');
  }

  const corrId = generateId('cor');
  const now = Date.now();

  await env.DB.prepare(
    `INSERT INTO content_corrections (id, content_id, group_id, user_id, author_name, field_name, original_value, proposed_value, reason, status, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    corrId,
    contentId,
    content.group_id,
    user.userId,
    user.fullName,
    body.fieldName,
    body.originalValue || null,
    body.proposedValue.trim(),
    body.reason.trim(),
    'PENDING',
    now
  ).run();

  return jsonResponse({
    success: true,
    correctionId: corrId,
    message: 'تم إرسال طلب التصحيح بنجاح وسيتم مراجعته وتدقيقه من قِبل المشرفين والأساتذة ✍️',
  });
}

export async function handleGetGroupCorrections(
  groupId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const corrections = await env.DB.prepare(
    `SELECT cc.*, c.title as content_title
     FROM content_corrections cc
     LEFT JOIN contents c ON cc.content_id = c.id
     WHERE cc.group_id = ?
     ORDER BY CASE WHEN cc.status = 'PENDING' THEN 0 ELSE 1 END, cc.created_at DESC`
  ).bind(groupId).all();

  return jsonResponse({
    success: true,
    corrections: corrections.results || [],
  });
}

export async function handleApproveCorrection(
  correctionId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const corr = await env.DB.prepare(
    `SELECT * FROM content_corrections WHERE id = ?`
  ).bind(correctionId).first<{
    id: string;
    content_id: string;
    group_id: string;
    field_name: string;
    proposed_value: string;
    status: string;
  }>();

  if (!corr) {
    return errorResponse('CORRECTION_NOT_FOUND', 'طلب التصحيح غير موجود', 404);
  }

  if (corr.status !== 'PENDING') {
    return errorResponse('CORRECTION_RESOLVED', 'تم حسم طلب التصحيح مسبقاً');
  }

  const canManage = await hasPermission(user, Permission.MANAGE_CONTENT, { db: env.DB, groupId: corr.group_id }) ||
                    ['TEACHER', 'MODERATOR', 'ADMIN', 'SYSTEM_OWNER'].includes(normalizeRole(user.role));

  if (!canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية مراجعة وتطبيق التصحيحات', 403);
  }

  const now = Date.now();

  // Apply to content table
  if (corr.field_name === 'TITLE') {
    await env.DB.prepare(`UPDATE contents SET title = ?, updated_at = ? WHERE id = ?`).bind(corr.proposed_value, now, corr.content_id).run();
  } else if (corr.field_name === 'DESCRIPTION') {
    await env.DB.prepare(`UPDATE contents SET description = ?, updated_at = ? WHERE id = ?`).bind(corr.proposed_value, now, corr.content_id).run();
  }

  await env.DB.prepare(
    `UPDATE content_corrections SET status = 'APPROVED', reviewed_by = ?, reviewed_at = ? WHERE id = ?`
  ).bind(user.userId, now, correctionId).run();

  return jsonResponse({
    success: true,
    message: 'تم اعتماد وتطبيق التصحيح على الدرس بنجاح ✅',
  });
}

export async function handleRejectCorrection(
  correctionId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const corr = await env.DB.prepare(
    `SELECT * FROM content_corrections WHERE id = ?`
  ).bind(correctionId).first<{ id: string; group_id: string; status: string }>();

  if (!corr) {
    return errorResponse('CORRECTION_NOT_FOUND', 'طلب التصحيح غير موجود', 404);
  }

  if (corr.status !== 'PENDING') {
    return errorResponse('CORRECTION_RESOLVED', 'تم حسم طلب التصحيح مسبقاً');
  }

  const canManage = await hasPermission(user, Permission.MANAGE_CONTENT, { db: env.DB, groupId: corr.group_id }) ||
                    ['TEACHER', 'MODERATOR', 'ADMIN', 'SYSTEM_OWNER'].includes(normalizeRole(user.role));

  if (!canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية رفض التصحيحات', 403);
  }

  const now = Date.now();
  await env.DB.prepare(
    `UPDATE content_corrections SET status = 'REJECTED', reviewed_by = ?, reviewed_at = ? WHERE id = ?`
  ).bind(user.userId, now, correctionId).run();

  return jsonResponse({
    success: true,
    message: 'تم رفض طلب التصحيح ❌',
  });
}

/**
 * -------------------------------------------------------------
 * 2. Unified Community Decisions Engine (التصويت والقرارات الموحدة)
 * -------------------------------------------------------------
 */

/**
 * Automatically applies the outcome of a passed community decision across the database.
 */
export async function applyDecisionExecution(
  decisionId: string,
  requestType: string,
  targetId: string,
  groupId: string,
  actorId: string,
  env: Env
): Promise<{ success: boolean; message: string }> {
  const now = Date.now();
  const normType = requestType.toUpperCase();

  try {
    if (normType === 'DELETION' || normType === 'CONTENT_DELETION') {
      await env.DB.prepare(`DELETE FROM content_media WHERE content_id = ?`).bind(targetId).run();
      await env.DB.prepare(`DELETE FROM content_corrections WHERE content_id = ?`).bind(targetId).run();
      await env.DB.prepare(`DELETE FROM contents WHERE id = ?`).bind(targetId).run();
      await env.DB.prepare(`DELETE FROM homeworks WHERE id = ?`).bind(targetId).run();
      await env.DB.prepare(`DELETE FROM exams WHERE id = ?`).bind(targetId).run();
      await env.DB.prepare(`DELETE FROM issues WHERE id = ?`).bind(targetId).run();
    } else if (normType === 'CORRECTION') {
      const corr = await env.DB.prepare(
        `SELECT * FROM content_corrections WHERE id = ?`
      ).bind(targetId).first<{
        content_id: string;
        field_name: string;
        proposed_value: string;
      }>();
      if (corr) {
        if (corr.field_name === 'TITLE') {
          await env.DB.prepare(`UPDATE contents SET title = ?, updated_at = ? WHERE id = ?`).bind(corr.proposed_value, now, corr.content_id).run();
        } else if (corr.field_name === 'DESCRIPTION') {
          await env.DB.prepare(`UPDATE contents SET description = ?, updated_at = ? WHERE id = ?`).bind(corr.proposed_value, now, corr.content_id).run();
        }
        await env.DB.prepare(
          `UPDATE content_corrections SET status = 'APPROVED', reviewed_by = 'COMMUNITY_CONSENSUS', reviewed_at = ? WHERE id = ?`
        ).bind(now, targetId).run();
      }
    } else if (normType === 'SCHEDULE' || normType === 'SCHEDULE_CHANGE') {
      const prop = await env.DB.prepare(
        `SELECT * FROM schedule_proposals WHERE id = ?`
      ).bind(targetId).first<{
        group_id: string;
        day_of_week: number;
        slot_order: number;
        new_subject_id: string;
      }>();
      if (prop) {
        const activeVer = await env.DB.prepare(
          `SELECT id, version_number FROM schedule_versions WHERE group_id = ? AND is_active = 1 ORDER BY version_number DESC LIMIT 1`
        ).bind(prop.group_id).first<{ id: string; version_number: number }>();
        if (activeVer) {
          await env.DB.prepare(
            `UPDATE schedule_slots SET subject_id = ? WHERE version_id = ? AND day_of_week = ? AND slot_order = ?`
          ).bind(prop.new_subject_id, activeVer.id, prop.day_of_week, prop.slot_order).run();
          await env.DB.prepare(
            `UPDATE schedule_proposals SET status = 'ACCEPTED', reviewed_by = 'COMMUNITY_CONSENSUS', reviewed_at = ? WHERE id = ?`
          ).bind(now, targetId).run();
        }
      }
    } else if (normType === 'VERIFICATION' || normType === 'OFFICIAL_VERIFICATION') {
      await env.DB.prepare(
        `UPDATE contents SET is_verified = 1, verified_by = 'COMMUNITY_CONSENSUS', updated_at = ? WHERE id = ?`
      ).bind(now, targetId).run();
    } else if (normType === 'PIN_CONTENT') {
      await env.DB.prepare(
        `UPDATE contents SET is_pinned = 1, updated_at = ? WHERE id = ?`
      ).bind(now, targetId).run();
    }

    await env.DB.prepare(
      `UPDATE community_decisions SET status = 'APPLIED', applied_at = ?, applied_by = ? WHERE id = ?`
    ).bind(now, actorId || 'COMMUNITY_CONSENSUS', decisionId).run();

    return { success: true, message: 'تم تطبيق القرار الجماعي بنجاح' };
  } catch (err: any) {
    console.error('Failed to execute decision:', err);
    return { success: false, message: err?.message || 'فشل تطبيق القرار الجماعي' };
  }
}

/**
 * Creates a new community decision proposal with exact eligible voters snapshot.
 */
export async function handleCreateCommunityDecision(
  groupId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const body = await request.json() as {
    requestType?: string; // 'DELETION', 'CORRECTION', 'SCHEDULE', 'VERIFICATION', 'PIN_CONTENT'
    targetId?: string;
    title?: string;
    description?: string;
    thresholdPercent?: number;
    expiresInHours?: number;
  };

  if (!body.requestType || !body.targetId || !body.title) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد نوع القرار، المعرّف المستهدف وعنوان القرار');
  }

  // Calculate exact total eligible voters in this group right now
  const eligibleMembers = await env.DB.prepare(
    `SELECT COUNT(*) as count FROM group_members WHERE group_id = ? AND status = 'ACTIVE' AND role != 'BANNED'`
  ).bind(groupId).first<{ count: number }>();

  const totalEligible = Math.max(1, eligibleMembers?.count || 1);
  const threshold = Math.min(100, Math.max(50, body.thresholdPercent || 60)); // default 60% quorum
  const decisionId = generateId('dec');
  const now = Date.now();
  const expiresAt = now + (body.expiresInHours || 48) * 3600 * 1000; // default 48h

  await env.DB.prepare(
    `INSERT INTO community_decisions (
       id, group_id, request_type, target_id, title, description,
       requested_by, requester_name, total_eligible_voters, threshold_percent,
       votes_for, votes_against, status, expires_at, created_at
     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, 'PENDING', ?, ?)`
  ).bind(
    decisionId,
    groupId,
    body.requestType.toUpperCase(),
    body.targetId,
    body.title.trim(),
    body.description?.trim() || null,
    user.userId,
    user.fullName,
    totalEligible,
    threshold,
    expiresAt,
    now
  ).run();

  // Requester votes FOR automatically
  await env.DB.prepare(
    `INSERT INTO community_votes (decision_id, user_id, vote_choice, created_at)
     VALUES (?, ?, 1, ?)`
  ).bind(decisionId, user.userId, now).run();

  // If only 1 voter, auto-apply immediately
  if (totalEligible <= 1) {
    await applyDecisionExecution(decisionId, body.requestType.toUpperCase(), body.targetId, groupId, user.userId, env);
    return jsonResponse({
      success: true,
      decisionId,
      status: 'APPLIED',
      message: 'تم اعتماد وتطبيق القرار الجماعي تلقائياً (إجماع لعدم وجود أعضاء آخرين في المجموعة) ✨',
    });
  }

  return jsonResponse({
    success: true,
    decisionId,
    status: 'PENDING',
    totalEligibleVoters: totalEligible,
    thresholdPercent: threshold,
    message: 'تم طرح القرار الجماعي للتصويت بنجاح 🗳️',
  });
}

export async function handleGetCommunityDecisions(
  groupId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const decisions = await env.DB.prepare(
    `SELECT cd.*,
            (SELECT vote_choice FROM community_votes cv WHERE cv.decision_id = cd.id AND cv.user_id = ?) as my_vote
     FROM community_decisions cd
     WHERE cd.group_id = ?
     ORDER BY CASE WHEN cd.status = 'PENDING' THEN 0 ELSE 1 END, cd.created_at DESC`
  ).bind(user.userId, groupId).all();

  return jsonResponse({
    success: true,
    decisions: (decisions.results || []).map((d: any) => ({
      id: d.id,
      groupId: d.group_id,
      requestType: d.request_type,
      targetId: d.target_id,
      title: d.title,
      description: d.description,
      requestedBy: d.requested_by,
      requesterName: d.requester_name || 'عضو في الشعبة',
      totalEligibleVoters: d.total_eligible_voters,
      thresholdPercent: d.threshold_percent,
      votesFor: d.votes_for,
      votesAgainst: d.votes_against,
      status: d.status,
      myVote: d.my_vote,
      expiresAt: d.expires_at,
      createdAt: d.created_at,
    })),
  });
}

export async function handleVoteCommunityDecision(
  decisionId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const decision = await env.DB.prepare(
    `SELECT * FROM community_decisions WHERE id = ?`
  ).bind(decisionId).first<{
    id: string;
    group_id: string;
    request_type: string;
    target_id: string;
    status: string;
    total_eligible_voters: number;
    threshold_percent: number;
    votes_for: number;
    votes_against: number;
    expires_at: number | null;
  }>();

  if (!decision) {
    return errorResponse('DECISION_NOT_FOUND', 'القرار الجماعي غير موجود', 404);
  }

  if (decision.status !== 'PENDING') {
    return errorResponse('DECISION_CLOSED', 'تم إغلاق التصويت على هذا القرار');
  }

  // Check expiration
  if (decision.expires_at && decision.expires_at < Date.now()) {
    await env.DB.prepare(`UPDATE community_decisions SET status = 'EXPIRED' WHERE id = ?`).bind(decisionId).run();
    return errorResponse('DECISION_EXPIRED', 'انتهت المهلة المحددة للتصويت على هذا القرار', 410);
  }

  // Strict voter eligibility check: user must be active member of this exact group
  const memberCheck = await requireGroupMember(user, decision.group_id, env.DB);
  if (memberCheck) return memberCheck;

  const body = await request.json() as { voteChoice: number }; // 1 = For, 0 = Against
  const choice = body.voteChoice === 1 ? 1 : 0;

  // Insert or replace vote
  await env.DB.prepare(
    `INSERT OR REPLACE INTO community_votes (decision_id, user_id, vote_choice, created_at)
     VALUES (?, ?, ?, ?)`
  ).bind(decisionId, user.userId, choice, Date.now()).run();

  // Recount
  const counts = await env.DB.prepare(
    `SELECT
       SUM(CASE WHEN vote_choice = 1 THEN 1 ELSE 0 END) as v_for,
       SUM(CASE WHEN vote_choice = 0 THEN 1 ELSE 0 END) as v_against
     FROM community_votes WHERE decision_id = ?`
  ).bind(decisionId).first<{ v_for: number; v_against: number }>();

  const vFor = counts?.v_for || 0;
  const vAgainst = counts?.v_against || 0;

  // Threshold check
  const requiredVotes = Math.max(1, Math.ceil((decision.total_eligible_voters * decision.threshold_percent) / 100));
  let newStatus = 'PENDING';
  let appliedMsg = '';

  if (vFor >= requiredVotes) {
    // Quorum reached -> Execute automated outcome
    await applyDecisionExecution(decisionId, decision.request_type, decision.target_id, decision.group_id, user.userId, env);
    newStatus = 'APPLIED';
    appliedMsg = ' — واكتمل النصاب وتم اعتماد وتطبيق القرار تلقائياً بنجاح ✨';
  } else if (vAgainst > (decision.total_eligible_voters - requiredVotes)) {
    // Rejection mathematically guaranteed
    newStatus = 'REJECTED';
    await env.DB.prepare(
      `UPDATE community_decisions SET status = 'REJECTED' WHERE id = ?`
    ).bind(decisionId).run();
    appliedMsg = ' — وتم رفض القرار لعدم اكتمال النصاب المطلوب ❌';
  }

  await env.DB.prepare(
    `UPDATE community_decisions SET votes_for = ?, votes_against = ?, status = ? WHERE id = ?`
  ).bind(vFor, vAgainst, newStatus, decisionId).run();

  return jsonResponse({
    success: true,
    votesFor: vFor,
    votesAgainst: vAgainst,
    status: newStatus,
    message: `تم تسجيل صوتك بنجاح (${vFor}/${requiredVotes} أصوات موافقة)${appliedMsg}`,
  });
}

/**
 * -------------------------------------------------------------
 * 3. In-App Role Dashboards Data Endpoints
 * -------------------------------------------------------------
 */

// Teacher Teaching Center
export async function handleGetTeacherDashboardData(user: UserContext, env: Env): Promise<Response> {
  const normRole = normalizeRole(user.role);
  if (normRole !== Role.TEACHER && normRole !== Role.ADMIN && normRole !== Role.SYSTEM_OWNER) {
    return errorResponse('FORBIDDEN', 'هذا المركز مخصص للأساتذة المعتمدين', 403);
  }

  const officialHomeworksCount = await env.DB.prepare(
    `SELECT COUNT(*) as count FROM homeworks WHERE created_by = ? AND is_official = 1`
  ).bind(user.userId).first<{ count: number }>();

  const officialExamsCount = await env.DB.prepare(
    `SELECT COUNT(*) as count FROM exams WHERE created_by = ? AND is_official = 1`
  ).bind(user.userId).first<{ count: number }>();

  const teacherAnswersCount = await env.DB.prepare(
    `SELECT COUNT(*) as count FROM issue_comments WHERE user_id = ? AND is_teacher_answer = 1`
  ).bind(user.userId).first<{ count: number }>();

  const myRecentHomeworks = await env.DB.prepare(
    `SELECT h.*, s.name_ar as subject_name FROM homeworks h LEFT JOIN subjects s ON h.subject_id = s.id WHERE h.created_by = ? ORDER BY h.created_at DESC LIMIT 5`
  ).bind(user.userId).all();

  const myRecentExams = await env.DB.prepare(
    `SELECT e.*, s.name_ar as subject_name FROM exams e LEFT JOIN subjects s ON e.subject_id = s.id WHERE e.created_by = ? ORDER BY e.created_at DESC LIMIT 5`
  ).bind(user.userId).all();

  return jsonResponse({
    success: true,
    teacher: {
      fullName: user.fullName,
      phoneNumber: user.phoneNumber,
      role: user.role,
      verified: true,
    },
    stats: {
      officialHomeworksCount: officialHomeworksCount?.count || 0,
      officialExamsCount: officialExamsCount?.count || 0,
      teacherAnswersCount: teacherAnswersCount?.count || 0,
    },
    recentHomeworks: myRecentHomeworks.results || [],
    recentExams: myRecentExams.results || [],
  });
}

// Moderator Group Moderation Center
export async function handleGetModeratorDashboardData(user: UserContext, env: Env): Promise<Response> {
  const normRole = normalizeRole(user.role);
  if (normRole === Role.STUDENT) {
    return errorResponse('FORBIDDEN', 'هذا المركز مخصص للمشرفين والمسؤولين', 403);
  }

  const defaultGroupId = user.defaultGroupId || `class_${user.gradeId}_${user.sectionId}`;

  const pendingCorrections = await env.DB.prepare(
    `SELECT cc.*, c.title as content_title FROM content_corrections cc LEFT JOIN contents c ON cc.content_id = c.id WHERE cc.group_id = ? AND cc.status = 'PENDING'`
  ).bind(defaultGroupId).all();

  const pendingScheduleProposals = await env.DB.prepare(
    `SELECT * FROM schedule_proposals WHERE group_id = ? AND status = 'PENDING'`
  ).bind(defaultGroupId).all();

  const pendingDecisions = await env.DB.prepare(
    `SELECT * FROM community_decisions WHERE group_id = ? AND status = 'PENDING'`
  ).bind(defaultGroupId).all();

  const membersCount = await env.DB.prepare(
    `SELECT COUNT(*) as count FROM group_members WHERE group_id = ? AND status = 'ACTIVE'`
  ).bind(defaultGroupId).first<{ count: number }>();

  return jsonResponse({
    success: true,
    groupId: defaultGroupId,
    stats: {
      pendingCorrectionsCount: (pendingCorrections.results || []).length,
      pendingProposalsCount: (pendingScheduleProposals.results || []).length,
      pendingDecisionsCount: (pendingDecisions.results || []).length,
      totalMembers: membersCount?.count || 0,
    },
    pendingCorrections: pendingCorrections.results || [],
    pendingProposals: pendingScheduleProposals.results || [],
    pendingDecisions: pendingDecisions.results || [],
  });
}

// School Admin School Management Center
export async function handleGetAdminDashboardData(user: UserContext, env: Env): Promise<Response> {
  const normRole = normalizeRole(user.role);
  if (normRole !== Role.ADMIN && normRole !== Role.SYSTEM_OWNER) {
    return errorResponse('FORBIDDEN', 'هذا المركز مخصص لمدير المدرسة', 403);
  }

  const totalUsers = await env.DB.prepare(`SELECT COUNT(*) as count FROM users`).first<{ count: number }>();
  const totalTeachers = await env.DB.prepare(`SELECT COUNT(*) as count FROM users WHERE role = 'TEACHER'`).first<{ count: number }>();
  const totalModerators = await env.DB.prepare(`SELECT COUNT(*) as count FROM users WHERE role = 'MODERATOR'`).first<{ count: number }>();
  const totalGroups = await env.DB.prepare(`SELECT COUNT(*) as count FROM groups`).first<{ count: number }>();
  const totalLessons = await env.DB.prepare(`SELECT COUNT(*) as count FROM contents`).first<{ count: number }>();
  const totalHomeworks = await env.DB.prepare(`SELECT COUNT(*) as count FROM homeworks`).first<{ count: number }>();
  const totalExams = await env.DB.prepare(`SELECT COUNT(*) as count FROM exams`).first<{ count: number }>();

  const teachersList = await env.DB.prepare(
    `SELECT id, full_name, phone_number, role, grade_id, section_id FROM users WHERE role = 'TEACHER' LIMIT 20`
  ).all();

  const moderatorsList = await env.DB.prepare(
    `SELECT id, full_name, phone_number, role, grade_id, section_id FROM users WHERE role = 'MODERATOR' LIMIT 20`
  ).all();

  return jsonResponse({
    success: true,
    school: {
      name: 'مدرسة تنوير النموذجية',
      adminName: user.fullName,
      adminPhone: user.phoneNumber,
    },
    stats: {
      totalUsers: totalUsers?.count || 0,
      totalTeachers: totalTeachers?.count || 0,
      totalModerators: totalModerators?.count || 0,
      totalGroups: totalGroups?.count || 0,
      totalLessons: totalLessons?.count || 0,
      totalHomeworks: totalHomeworks?.count || 0,
      totalExams: totalExams?.count || 0,
    },
    teachers: teachersList.results || [],
    moderators: moderatorsList.results || [],
  });
}
