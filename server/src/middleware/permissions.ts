import { Env, UserContext } from '../env';

export async function isGroupMember(db: D1Database, groupId: string, userId: string): Promise<boolean> {
  const member = await db.prepare(
    `SELECT role, status FROM group_members WHERE group_id = ? AND user_id = ?`
  ).bind(groupId, userId).first();
  return !!member;
}

export async function canEditContent(db: D1Database, contentId: string, user: UserContext): Promise<boolean> {
  const content = await db.prepare(
    `SELECT created_by, group_id FROM contents WHERE id = ?`
  ).bind(contentId).first<{ created_by: string; group_id: string }>();

  if (!content) return false;
  if (content.created_by === user.userId) return true;
  if (user.role === 'ADMIN' || user.role === 'MODERATOR') return true;
  return false;
}
