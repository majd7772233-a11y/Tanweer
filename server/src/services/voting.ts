import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
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

  const totalMembers = await env.DB.prepare(
    `SELECT COUNT(*) as count FROM group_members WHERE group_id = ? AND status = 'ACTIVE'`
  ).bind(content.group_id).first<{ count: number }>();

  const requestId = generateId('del_req');
  await env.DB.prepare(
    `INSERT INTO deletion_requests (id, content_id, requested_by, reason, total_eligible_voters, votes_in_favor, votes_against, status, created_at)
     VALUES (?, ?, ?, ?, ?, 1, 0, 'PENDING', ?)`
  ).bind(
    requestId,
    body.contentId,
    user.userId,
    body.reason.trim(),
    totalMembers?.count || 1,
    Date.now()
  ).run();

  await env.DB.prepare(
    `INSERT INTO votes (request_id, user_id, request_type, vote_choice, created_at) VALUES (?, ?, 'DELETION', 1, ?)`
  ).bind(requestId, user.userId, Date.now()).run();

  await env.DB.prepare(`UPDATE contents SET status = 'PENDING_DELETION' WHERE id = ?`).bind(body.contentId).run();

  return jsonResponse({
    success: true,
    requestId,
    message: 'تم طرح طلب الحذف لتصويت المجموعة. يتطلب الحذف إجماع الأعضاء المؤهلين.',
  });
}

export async function handleVote(requestId: string, user: UserContext, request: Request, env: Env): Promise<Response> {
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
    await env.DB.prepare(`UPDATE deletion_requests SET votes_in_favor = votes_in_favor + 1 WHERE id = ?`).bind(requestId).run();
  } else {
    // A single rejection prevents deletion under pure consensus rule
    await env.DB.prepare(`UPDATE deletion_requests SET votes_against = votes_against + 1, status = 'REJECTED' WHERE id = ?`).bind(requestId).run();
    
    // Restore content status
    const req = await env.DB.prepare(`SELECT content_id FROM deletion_requests WHERE id = ?`).bind(requestId).first<{ content_id: string }>();
    if (req) {
      await env.DB.prepare(`UPDATE contents SET status = 'PUBLISHED' WHERE id = ?`).bind(req.content_id).run();
    }
  }

  return jsonResponse({
    success: true,
    message: 'تم تسجيل تصويتك بنجاح',
  });
}
