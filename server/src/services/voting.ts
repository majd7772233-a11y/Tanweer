import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';

export async function handleRequestDeletion(user: UserContext, request: Request, env: Env): Promise<Response> {
  const body = await request.json() as { contentId?: string; reason?: string };
  if (!body.contentId || !body.reason) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد سبب طلب الحذف');
  }

  const content = await env.DB.prepare(`SELECT group_id FROM contents WHERE id = ?`).bind(body.contentId).first<{ group_id: string }>();
  if (!content) {
    return errorResponse('CONTENT_NOT_FOUND', 'المحتوى غير موجود');
  }

  const memberCheck = await requireGroupMember(user, content.group_id, env.DB);
  if (memberCheck) return memberCheck;

  const totalMembers = await env.DB.prepare(
    `SELECT COUNT(*) as count FROM group_members WHERE group_id = ? AND status = 'ACTIVE'`
  ).bind(content.group_id).first<{ count: number }>();

  const eligibleCount = Math.max(1, totalMembers?.count || 1);
  const requestId = generateId('del_req');

  // If there is only 1 member in the group, immediate consensus
  if (eligibleCount <= 1) {
    await env.DB.prepare(
      `INSERT INTO deletion_requests (id, content_id, requested_by, reason, total_eligible_voters, votes_in_favor, votes_against, status, created_at)
       VALUES (?, ?, ?, ?, ?, 1, 0, 'ACCEPTED', ?)`
    ).bind(requestId, body.contentId, user.userId, body.reason.trim(), eligibleCount, Date.now()).run();

    await env.DB.prepare(`DELETE FROM content_media WHERE content_id = ?`).bind(body.contentId).run();
    await env.DB.prepare(`DELETE FROM contents WHERE id = ?`).bind(body.contentId).run();

    return jsonResponse({
      success: true,
      requestId,
      status: 'ACCEPTED',
      message: 'تم قبول طلب الحذف وحذف المحتوى بنجاح نظراً لعدم وجود أعضاء آخرين في المجموعة.',
    });
  }

  await env.DB.prepare(
    `INSERT INTO deletion_requests (id, content_id, requested_by, reason, total_eligible_voters, votes_in_favor, votes_against, status, created_at)
     VALUES (?, ?, ?, ?, ?, 1, 0, 'PENDING', ?)`
  ).bind(
    requestId,
    body.contentId,
    user.userId,
    body.reason.trim(),
    eligibleCount,
    Date.now()
  ).run();

  await env.DB.prepare(
    `INSERT INTO votes (request_id, user_id, request_type, vote_choice, created_at) VALUES (?, ?, 'DELETION', 1, ?)`
  ).bind(requestId, user.userId, Date.now()).run();

  await env.DB.prepare(`UPDATE contents SET status = 'PENDING_DELETION' WHERE id = ?`).bind(body.contentId).run();

  return jsonResponse({
    success: true,
    requestId,
    status: 'PENDING',
    message: 'تم طرح طلب الحذف لتصويت المجموعة. يتطلب الحذف إجماع الأعضاء المؤهلين.',
  });
}

export async function handleVote(requestId: string, user: UserContext, request: Request, env: Env): Promise<Response> {
  const delReq = await env.DB.prepare(
    `SELECT dr.id, dr.content_id, dr.status, dr.total_eligible_voters, dr.votes_in_favor, dr.votes_against, c.group_id
     FROM deletion_requests dr
     LEFT JOIN contents c ON dr.content_id = c.id
     WHERE dr.id = ?`
  ).bind(requestId).first<{
    id: string;
    content_id: string;
    status: string;
    total_eligible_voters: number;
    votes_in_favor: number;
    votes_against: number;
    group_id: string | null;
  }>();

  if (!delReq) {
    return errorResponse('REQUEST_NOT_FOUND', 'طلب الحذف غير موجود');
  }

  if (delReq.status !== 'PENDING') {
    return errorResponse('REQUEST_CLOSED', 'طلب الحذف منتهٍ بالفعل');
  }

  if (delReq.group_id) {
    const memberCheck = await requireGroupMember(user, delReq.group_id, env.DB);
    if (memberCheck) return memberCheck;
  }

  const body = await request.json() as { voteChoice: number }; // 1 = Approve, 0 = Reject
  const choice = body.voteChoice === 1 ? 1 : 0;

  const existingVote = await env.DB.prepare(`SELECT vote_choice FROM votes WHERE request_id = ? AND user_id = ?`)
    .bind(requestId, user.userId).first();

  if (existingVote) {
    return errorResponse('ALREADY_VOTED', 'لقد قمت بالتصويت مسبقًا على هذا الطلب');
  }

  await env.DB.prepare(
    `INSERT INTO votes (request_id, user_id, request_type, vote_choice, created_at) VALUES (?, ?, 'DELETION', ?, ?)`
  ).bind(requestId, user.userId, choice, Date.now()).run();

  if (choice === 1) {
    const newVotesInFavor = delReq.votes_in_favor + 1;
    const requiredVotes = Math.max(2, delReq.total_eligible_voters);

    // If consensus is reached
    if (newVotesInFavor >= requiredVotes) {
      await env.DB.prepare(
        `UPDATE deletion_requests SET votes_in_favor = ?, status = 'ACCEPTED' WHERE id = ?`
      ).bind(newVotesInFavor, requestId).run();

      // Delete content and media permanently
      await env.DB.prepare(`DELETE FROM content_media WHERE content_id = ?`).bind(delReq.content_id).run();
      await env.DB.prepare(`DELETE FROM contents WHERE id = ?`).bind(delReq.content_id).run();

      return jsonResponse({
        success: true,
        status: 'ACCEPTED',
        message: 'اكتمل إجماع الأعضاء وتم قبول طلب الحذف وحذف المحتوى نهائياً بنجاح 🗑️',
      });
    } else {
      await env.DB.prepare(`UPDATE deletion_requests SET votes_in_favor = ? WHERE id = ?`).bind(newVotesInFavor, requestId).run();
      return jsonResponse({
        success: true,
        status: 'PENDING',
        message: `تم تسجيل تصويتك بالموافقة (${newVotesInFavor}/${requiredVotes})`,
      });
    }
  } else {
    // A single rejection prevents deletion under consensus rule
    await env.DB.prepare(
      `UPDATE deletion_requests SET votes_against = votes_against + 1, status = 'REJECTED' WHERE id = ?`
    ).bind(requestId).run();

    // Restore content status to PUBLISHED
    await env.DB.prepare(`UPDATE contents SET status = 'PUBLISHED' WHERE id = ?`).bind(delReq.content_id).run();

    return jsonResponse({
      success: true,
      status: 'REJECTED',
      message: 'تم تسجيل رفضك لطلب الحذف وإلغاء الطلب واستعادة المحتوى بنجاح',
    });
  }
}
