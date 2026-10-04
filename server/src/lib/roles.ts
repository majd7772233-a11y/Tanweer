/**
 * Tanweer Platform Unified Role Management Core
 * Source of truth for all user ranks and roles.
 */

export enum Role {
  STUDENT = 'STUDENT',
  MODERATOR = 'MODERATOR',
  TEACHER = 'TEACHER',
  ADMIN = 'ADMIN',
  SYSTEM_OWNER = 'SYSTEM_OWNER',
}

export enum RoleScope {
  GLOBAL = 'GLOBAL',
  GROUP = 'GROUP',
  SUBJECT = 'SUBJECT',
}

export type RoleRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface RoleRequest {
  id: string;
  userId: string;
  requestedRole: Role;
  groupId?: string | null;
  reason?: string | null;
  phoneNumber?: string | null;
  fullName?: string | null;
  status: RoleRequestStatus;
  reviewedBy?: string | null;
  reviewNotes?: string | null;
  reviewedAt?: number | null;
  createdAt: number;
}

export interface RoleMetadata {
  role: Role;
  nameAr: string;
  badge: string;
  level: number;
  descriptionAr: string;
}

export const ROLE_HIERARCHY: Record<Role, number> = {
  [Role.STUDENT]: 1,
  [Role.MODERATOR]: 2,
  [Role.TEACHER]: 3,
  [Role.ADMIN]: 4,
  [Role.SYSTEM_OWNER]: 5,
};

export const ROLE_METADATA: Record<Role, RoleMetadata> = {
  [Role.STUDENT]: {
    role: Role.STUDENT,
    nameAr: 'طالب',
    badge: '👤',
    level: 1,
    descriptionAr: 'عضو مساهم في الشعبة، يستطيع رفع الدروس، طرح الأسئلة، وإضافة الواجبات والاختبارات والمشاركة في الجدول.',
  },
  [Role.MODERATOR]: {
    role: Role.MODERATOR,
    nameAr: 'مشرف / مسؤول',
    badge: '🛡️',
    level: 2,
    descriptionAr: 'مسؤول الشعبة، يملك صلاحيات مراجعة المحتوى والجدول والواجبات وإدارة الأعضاء غير اللائقين.',
  },
  [Role.TEACHER]: {
    role: Role.TEACHER,
    nameAr: 'أستاذ',
    badge: '🎓',
    level: 3,
    descriptionAr: 'معلم معتمد، يملك اعتماد وتثبيت المحتوى الدراسي الرسمي والجدول والواجبات وتوجيه الشعب.',
  },
  [Role.ADMIN]: {
    role: Role.ADMIN,
    nameAr: 'مدير',
    badge: '👑',
    level: 4,
    descriptionAr: 'مدير النظام المدرسي، يملك كامل الصلاحيات الإدارية على الشعب والمحتوى وتعيين المشرفين.',
  },
  [Role.SYSTEM_OWNER]: {
    role: Role.SYSTEM_OWNER,
    nameAr: 'مالك المنظومة',
    badge: '🔐',
    level: 5,
    descriptionAr: 'حساب مالك الخادم وغرفة التحكم، إدارة البنية التحتية، الرتب العليا، وإعدادات النظام الحساسة.',
  },
};

/**
 * Normalizes legacy or raw role strings into canonical Role enum.
 * Maps 'MEMBER' -> Role.STUDENT, 'VERIFIED_TEACHER' -> Role.TEACHER.
 */
export function normalizeRole(roleStr?: string | null): Role {
  if (!roleStr) return Role.STUDENT;
  const upper = roleStr.toUpperCase().trim();
  switch (upper) {
    case 'SYSTEM_OWNER':
    case 'OWNER':
      return Role.SYSTEM_OWNER;
    case 'ADMIN':
    case 'ADMINISTRATOR':
      return Role.ADMIN;
    case 'TEACHER':
    case 'VERIFIED_TEACHER':
      return Role.TEACHER;
    case 'MODERATOR':
      return Role.MODERATOR;
    case 'STUDENT':
    case 'MEMBER':
    default:
      return Role.STUDENT;
  }
}

/**
 * Checks if a role has at least the minimum required rank level.
 */
export function hasMinimumRoleLevel(userRole: string | Role, requiredRole: Role): boolean {
  const normUser = normalizeRole(userRole);
  return ROLE_HIERARCHY[normUser] >= ROLE_HIERARCHY[requiredRole];
}
