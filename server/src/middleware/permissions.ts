import { Env, UserContext } from '../env';
import { errorResponse } from '../lib/response';

export async function isGroupMember(db: D1Database, groupId: string, userId: string): Promise<boolean> {
  const member = await db.prepare(
    `SELECT role, status FROM group_members WHERE group_id = ? AND user_id = ? AND status = 'ACTIVE'`
  ).bind(groupId, userId).first();
  return !!member;
}

export async function requireGroupMember(
  user: UserContext,
  groupId: string,
  db: D1Database
): Promise<Response | null> {
  // Admin role bypass
  if (user.role === 'ADMIN') return null;

  const isMember = await isGroupMember(db, groupId, user.userId);
  if (!isMember) {
    return errorResponse('FORBIDDEN_NOT_MEMBER', 'يجب أن تكون عضوًا فعالاً في المجموعة للوصول إلى بياناتها أو التفاعل معها', 403);
  }
  return null;
}

export async function requireScheduleManager(
  user: UserContext,
  groupId: string,
  db: D1Database
): Promise<Response | null> {
  // Global Admin, Moderator, or Verified Teacher bypass
  if (user.role === 'ADMIN' || user.role === 'MODERATOR' || user.role === 'VERIFIED_TEACHER') {
    return null;
  }

  // Check group member role
  const member = await db.prepare(
    `SELECT role, status FROM group_members WHERE group_id = ? AND user_id = ? AND status = 'ACTIVE'`
  ).bind(groupId, user.userId).first<{ role: string; status: string }>();

  if (!member) {
    return errorResponse('FORBIDDEN_NOT_MEMBER', 'يجب أن تكون عضوًا فعالاً في المجموعة للوصول إلى بياناتها', 403);
  }

  if (member.role === 'ADMIN' || member.role === 'MODERATOR' || member.role === 'TEACHER') {
    return null;
  }

  return errorResponse(
    'FORBIDDEN_SCHEDULE_MANAGEMENT',
    'تعديل الجدول الرسمي متاح فقط للمشرفين أو المعلمين المعتمدين. يمكنك تقديم اقتراح تعديل جدول للمراجعة والاعتماد.',
    403
  );
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
