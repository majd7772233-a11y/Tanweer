import { Env, UserContext } from '../env';
import { errorResponse } from '../lib/response';
import { Permission, hasPermission, PermissionContext } from '../lib/permissions';
import { Role, normalizeRole } from '../lib/roles';

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
  // Admin & Owner role bypass
  const normRole = normalizeRole(user.role);
  if (normRole === Role.ADMIN || normRole === Role.SYSTEM_OWNER) return null;

  const isMember = await isGroupMember(db, groupId, user.userId);
  if (!isMember) {
    return errorResponse('FORBIDDEN_NOT_MEMBER', 'يجب أن تكون عضوًا فعالاً في المجموعة للوصول إلى بياناتها أو التفاعل معها', 403);
  }
  return null;
}

export async function requireScheduleManager(
  user: UserContext,
  groupId: string,
  db: D1Database,
  allowInitialSetup = false
): Promise<Response | null> {
  // First ensure they are a member or administrative bypass
  const memberCheck = await requireGroupMember(user, groupId, db);
  if (memberCheck) return memberCheck;

  if (allowInitialSetup) {
    return null; // Initial setup allowed for group members
  }

  // Check if group already has active slots configured
  // In accordance with Tanweer philosophy: all students can contribute to the initial schedule (المساهمة في الجدول الأول)
  try {
    const existingSlotCount = await db.prepare(
      `SELECT COUNT(*) as count FROM schedule_slots ss
       JOIN schedule_versions sv ON ss.version_id = sv.id
       WHERE sv.group_id = ? AND sv.is_active = 1`
    ).bind(groupId).first<{ count: number }>();

    if (!existingSlotCount || existingSlotCount.count === 0) {
      return null; // Schedule is unpopulated; all members can help set it up initially!
    }
  } catch {
    // If table query fails, fallback to standard permission check
  }

  const allowed = await hasPermission(user, Permission.MANAGE_SCHEDULE, { db, groupId });
  if (allowed) {
    return null;
  }

  return errorResponse(
    'FORBIDDEN_SCHEDULE_MANAGEMENT',
    'الجدول الرسمي معتمد ومثبت. لتعديل الحصة يرجى تقديم مقترح تعديل ليصوت عليه الزملاء ويعتمده المشرف أو الأستاذ.',
    403
  );
}

export async function canEditContent(db: D1Database, contentId: string, user: UserContext): Promise<boolean> {
  const content = await db.prepare(
    `SELECT created_by, group_id FROM contents WHERE id = ?`
  ).bind(contentId).first<{ created_by: string; group_id: string }>();

  if (!content) return false;

  return hasPermission(user, Permission.MODERATE_CONTENT, {
    db,
    groupId: content.group_id,
    resourceOwnerId: content.created_by,
  });
}

export async function requireUserPermission(
  user: UserContext,
  permission: Permission,
  context?: PermissionContext | string
): Promise<Response | null> {
  const allowed = await hasPermission(user, permission, context);
  if (!allowed) {
    return errorResponse('FORBIDDEN_PERMISSION', 'ليست لديك الصلاحية الكافية لإتمام هذا الإجراء', 403);
  }
  return null;
}
