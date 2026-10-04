/**
 * Tanweer Platform Permissions Core
 * Centralized authorization engine evaluating permissions based on philosophy & roles.
 */

import { Role, normalizeRole } from './roles';

export enum Permission {
  // --- Universal Student & Member Contributions (Everyone Can Contribute) ---
  CONTRIBUTE_CONTENT = 'CONTRIBUTE_CONTENT', // Create lessons, summaries, board notes
  PROPOSE_SCHEDULE = 'PROPOSE_SCHEDULE',     // Propose schedule changes
  CREATE_HOMEWORK = 'CREATE_HOMEWORK',       // Add homework or task
  CREATE_EXAM = 'CREATE_EXAM',               // Add exam dates & curriculum
  CREATE_EVENT = 'CREATE_EVENT',             // Add school activity or event
  ASK_QUESTION = 'ASK_QUESTION',             // Ask a study question
  ANSWER_QUESTION = 'ANSWER_QUESTION',       // Add answer or discussion comment
  SEND_CHAT_MESSAGE = 'SEND_CHAT_MESSAGE',   // Participate in class chat
  UPLOAD_MEDIA = 'UPLOAD_MEDIA',             // Upload board pictures or documents
  REQUEST_ROLE_UPGRADE = 'REQUEST_ROLE_UPGRADE', // Request teacher/moderator status

  // --- Official Governance & Moderation (Moderator, Teacher, Admin, Owner) ---
  MANAGE_SCHEDULE = 'MANAGE_SCHEDULE',       // Directly create, modify, or delete official schedule slots
  MODERATE_CONTENT = 'MODERATE_CONTENT',     // Edit, verify, or review content created by others
  MANAGE_CONTENT = 'MODERATE_CONTENT',       // Alias for MODERATE_CONTENT
  DELETE_ANY_CONTENT = 'DELETE_ANY_CONTENT', // Force delete content across group/platform
  PIN_CONTENT = 'PIN_CONTENT',               // Pin key lessons or announcements
  MODERATE_HOMEWORK = 'MODERATE_HOMEWORK',   // Edit or remove homework of others
  MANAGE_HOMEWORK = 'MODERATE_HOMEWORK',     // Alias for MODERATE_HOMEWORK
  PUBLISH_OFFICIAL_HOMEWORK = 'MODERATE_HOMEWORK', // Alias for official teacher homework
  MODERATE_EXAM = 'MODERATE_EXAM',           // Edit or remove exams of others
  MANAGE_EXAMS = 'MODERATE_EXAM',            // Alias for MODERATE_EXAM
  PUBLISH_OFFICIAL_EXAM = 'MODERATE_EXAM',   // Alias for official teacher exam
  MODERATE_EVENT = 'MODERATE_EVENT',         // Edit or remove events of others
  MANAGE_EVENTS = 'MODERATE_EVENT',          // Alias for MODERATE_EVENT
  PUBLISH_OFFICIAL_EVENT = 'MODERATE_EVENT', // Alias for official teacher event
  MODERATE_ISSUES = 'MODERATE_ISSUES',       // Close, reopen, or moderate questions
  MANAGE_ISSUES = 'MODERATE_ISSUES',         // Alias for MODERATE_ISSUES
  VERIFY_BEST_ANSWER = 'VERIFY_BEST_ANSWER', // Mark an official certified answer (Teacher / Moderator / Author)
  ANSWER_AS_TEACHER = 'VERIFY_BEST_ANSWER',  // Alias for teacher answer verification
  MODERATE_CHAT = 'MODERATE_CHAT',           // Delete chat messages or silence members
  DELETE_ANY_CHAT_MESSAGE = 'MODERATE_CHAT', // Alias for chat deletion

  // --- Group Membership Administration ---
  MANAGE_GROUP_MEMBERS = 'MANAGE_GROUP_MEMBERS', // Change roles in class group, moderate members
  MANAGE_GROUP_SETTINGS = 'MANAGE_GROUP_SETTINGS', // Change group description, title

  // --- Administrative & System Governance ---
  REVIEW_ROLE_REQUESTS = 'REVIEW_ROLE_REQUESTS', // Approve or reject teacher/moderator requests
  MANAGE_USERS = 'MANAGE_USERS',                 // Change global user roles, suspend/enable
  REVOKE_SESSIONS = 'REVOKE_SESSIONS',           // Force terminate sessions
  VIEW_AUDIT_LOG = 'VIEW_AUDIT_LOG',             // Inspect administrative action logs
  ACCESS_OWNER_DASHBOARD = 'ACCESS_OWNER_DASHBOARD', // Access Server Control Center
}

export interface PermissionContext {
  db?: D1Database;
  groupId?: string;
  resourceOwnerId?: string;
  memberRole?: Role | string;
}

/**
 * Universal evaluation function for permissions.
 * Replaces ad-hoc role checks throughout the platform.
 */
export async function hasPermission(
  user: { userId?: string; id?: string; role: string },
  permission: Permission,
  context?: PermissionContext | string
): Promise<boolean> {
  const globalRole = normalizeRole(user.role);
  const userId = user.userId || user.id || '';

  // 1. SYSTEM_OWNER has absolute master access
  if (globalRole === Role.SYSTEM_OWNER) {
    return true;
  }

  // 2. OWNER-only permission restriction
  if (permission === Permission.ACCESS_OWNER_DASHBOARD) {
    return false;
  }

  // 3. ADMIN has full administrative capabilities
  if (globalRole === Role.ADMIN) {
    return true;
  }

  // 4. Universal Student & Member Contributions:
  // Everyone (including STUDENT) can contribute to lessons, news, homework, exams, questions, and chat!
  const universalPermissions = new Set<Permission>([
    Permission.CONTRIBUTE_CONTENT,
    Permission.PROPOSE_SCHEDULE,
    Permission.CREATE_HOMEWORK,
    Permission.CREATE_EXAM,
    Permission.CREATE_EVENT,
    Permission.ASK_QUESTION,
    Permission.ANSWER_QUESTION,
    Permission.SEND_CHAT_MESSAGE,
    Permission.UPLOAD_MEDIA,
    Permission.REQUEST_ROLE_UPGRADE,
  ]);

  if (universalPermissions.has(permission)) {
    return true;
  }

  // 5. Self-authorship check (Authors can mark best answers or modify their own content)
  const resolvedContext: PermissionContext = typeof context === 'string'
    ? { groupId: context }
    : (context || {});

  if (resolvedContext.resourceOwnerId && resolvedContext.resourceOwnerId === userId) {
    if (permission === Permission.VERIFY_BEST_ANSWER || permission === Permission.MODERATE_CONTENT) {
      return true;
    }
  }

  // 6. Resolve effective group-level role
  let effectiveRole: Role = globalRole;

  if (resolvedContext.memberRole) {
    const contextMemberRole = normalizeRole(resolvedContext.memberRole);
    if (contextMemberRole !== Role.STUDENT) {
      effectiveRole = contextMemberRole;
    }
  } else if (resolvedContext.db && resolvedContext.groupId && userId) {
    try {
      const member = await resolvedContext.db.prepare(
        `SELECT role FROM group_members WHERE group_id = ? AND user_id = ? AND status = 'ACTIVE'`
      ).bind(resolvedContext.groupId, userId).first<{ role: string }>();

      if (member) {
        const groupMemberRole = normalizeRole(member.role);
        // If the group grants a higher role (e.g. Moderator of this class), adopt it
        if (groupMemberRole === Role.MODERATOR || groupMemberRole === Role.TEACHER || groupMemberRole === Role.ADMIN) {
          effectiveRole = groupMemberRole;
        }
      }
    } catch {
      // In case of DB lookup failure, fall back to global role
    }
  }

  // 7. Role-specific privilege evaluation
  switch (effectiveRole) {
    case Role.SYSTEM_OWNER:
    case Role.ADMIN:
      return true;

    case Role.TEACHER:
      return [
        Permission.MANAGE_SCHEDULE,
        Permission.MODERATE_CONTENT,
        Permission.PIN_CONTENT,
        Permission.MODERATE_HOMEWORK,
        Permission.MODERATE_EXAM,
        Permission.MODERATE_EVENT,
        Permission.MODERATE_ISSUES,
        Permission.VERIFY_BEST_ANSWER,
        Permission.MODERATE_CHAT,
        Permission.MANAGE_GROUP_MEMBERS,
      ].includes(permission);

    case Role.MODERATOR:
      return [
        Permission.MODERATE_CHAT,
        Permission.MANAGE_GROUP_MEMBERS,
        Permission.MODERATE_CONTENT,
        Permission.MODERATE_ISSUES,
        Permission.MANAGE_SCHEDULE,
      ].includes(permission);

    case Role.STUDENT:
    default:
      return false;
  }
}
