import { Env, UserContext } from '../env';
import { validateArabicFullName, isValidGradeSection } from '../lib/validation';
import { errorResponse, jsonResponse } from '../lib/response';
import { getCurrentAcademicYearId } from '../lib/school';
import { Role, normalizeRole, sanitizePublicRole } from '../lib/roles';
import { Permission, hasPermission } from '../lib/permissions';

export async function handleGetProfile(user: UserContext, env: Env): Promise<Response> {
  const contributions = await env.DB.prepare(
    `SELECT 
       (SELECT COUNT(*) FROM contents WHERE created_by = ?) as lessons_count,
       (SELECT COUNT(*) FROM homeworks WHERE created_by = ?) as homeworks_count,
       (SELECT COUNT(*) FROM issues WHERE created_by = ?) as issues_count,
       (SELECT COUNT(*) FROM content_media cm JOIN contents c ON cm.content_id = c.id WHERE c.created_by = ?) as photos_count`
  ).bind(user.userId, user.userId, user.userId, user.userId).first<{
    lessons_count: number;
    homeworks_count: number;
    issues_count: number;
    photos_count: number;
  }>();

  const gradeNameMap: Record<number, string> = {
    7: 'الصف السابع',
    8: 'الصف الثامن',
    9: 'الصف التاسع',
    10: 'الأول الثانوي',
    11: 'الثاني الثانوي',
    12: 'الثالث الثانوي',
  };

  return jsonResponse({
    success: true,
    user: {
      id: user.userId,
      fullName: user.fullName,
      phoneNumber: user.phoneNumber,
      gradeId: user.gradeId,
      gradeName: gradeNameMap[user.gradeId] || `الصف ${user.gradeId}`,
      sectionId: user.sectionId,
      role: sanitizePublicRole(user.role),
      defaultGroupId: `class_${user.gradeId}_${user.sectionId}`,
      stats: {
        lessonsCount: contributions?.lessons_count || 0,
        homeworksCount: contributions?.homeworks_count || 0,
        issuesCount: contributions?.issues_count || 0,
        photosCount: contributions?.photos_count || 0,
      },
    },
  });
}

export async function handleUpdateProfile(user: UserContext, request: Request, env: Env): Promise<Response> {
  const body = await request.json() as {
    fullName?: string;
    gradeId?: number;
    sectionId?: string;
  };

  const newFullName = body.fullName ? body.fullName.trim().replace(/\s+/g, ' ') : user.fullName;
  const gradeChanged = body.gradeId !== undefined && body.gradeId !== user.gradeId;
  const sectionChanged = body.sectionId !== undefined && body.sectionId.toUpperCase() !== user.sectionId;

  if (body.fullName) {
    const valName = validateArabicFullName(newFullName);
    if (!valName.isValid) {
      return errorResponse('INVALID_NAME', valName.error || 'الاسم الثلاثي غير صالح');
    }
  }

  // Academic transfer (Grade or Section change) must be authorized by School Admin / Supervisor
  if (gradeChanged || sectionChanged) {
    const canManageUsers = user.role === Role.ADMIN || 
      user.role === Role.SYSTEM_OWNER || 
      await hasPermission(user, Permission.MANAGE_USERS);

    if (!canManageUsers) {
      return errorResponse(
        'FORBIDDEN_ACADEMIC_TRANSFER',
        'تغيير الصف أو الشعبة يتطلب موافقة إدارة المدرسة أو اعتماد المشرف المخول'
      );
    }

    const newGradeId = body.gradeId !== undefined ? body.gradeId : user.gradeId;
    const newSectionId = body.sectionId ? body.sectionId.toUpperCase() : user.sectionId;

    if (!isValidGradeSection(newGradeId, newSectionId)) {
      return errorResponse('INVALID_SECTION', 'الشعبة غير متوافقة مع الصف المحدد');
    }

    const now = Date.now();
    const classGroupId = `class_${newGradeId}_${newSectionId}`;
    const oldClassGroupId = `class_${user.gradeId}_${user.sectionId}`;

    const gradeNameMap: Record<number, string> = {
      7: 'الصف السابع',
      8: 'الصف الثامن',
      9: 'الصف التاسع',
      10: 'الأول الثانوي',
      11: 'الثاني الثانوي',
      12: 'الثالث الثانوي'
    };
    const groupName = `${gradeNameMap[newGradeId]} — شعبة (${newSectionId})`;
    const academicYear = await getCurrentAcademicYearId(env.DB);

    const targetGroupRole = (user.role === Role.TEACHER)
      ? Role.TEACHER
      : (user.role === Role.MODERATOR ? Role.MODERATOR : Role.STUDENT);

    // Atomic batch update of user record and group memberships
    const userUpdateStmts = [
      env.DB.prepare(
        `UPDATE users SET full_name = ?, grade_id = ?, section_id = ?, updated_at = ? WHERE id = ?`
      ).bind(newFullName, newGradeId, newSectionId, now, user.userId),
      env.DB.prepare(
        `INSERT OR IGNORE INTO groups (id, name, type, description, academic_year_id, created_at)
         VALUES (?, ?, 'CLASS', ?, ?, ?)`
      ).bind(classGroupId, groupName, `المجموعة الدراسية الرسمية لـ ${groupName}`, academicYear, now),
      env.DB.prepare(
        `INSERT OR IGNORE INTO group_sections (group_id, grade_id, section_id)
         VALUES (?, ?, ?)`
      ).bind(classGroupId, newGradeId, newSectionId),
      env.DB.prepare(
        `DELETE FROM group_members
         WHERE user_id = ?
           AND (group_id = ? OR group_id IN (SELECT id FROM groups WHERE type = 'CLASS' AND id != ?))`
      ).bind(user.userId, oldClassGroupId, classGroupId),
      env.DB.prepare(
        `INSERT OR IGNORE INTO group_members (group_id, user_id, role, status, joined_at)
         VALUES (?, ?, ?, 'ACTIVE', ?)`
      ).bind(classGroupId, user.userId, targetGroupRole, now)
    ];

    if (typeof env.DB.batch === 'function') {
      await env.DB.batch(userUpdateStmts);
    } else {
      for (const stmt of userUpdateStmts) {
        await stmt.run();
      }
    }

    return jsonResponse({
      success: true,
      message: 'تم تحديث بيانات الحساب والانتقال الأكاديمي بنجاح',
      user: {
        id: user.userId,
        fullName: newFullName,
        phoneNumber: user.phoneNumber,
        gradeId: newGradeId,
        gradeName: gradeNameMap[newGradeId] || `الصف ${newGradeId}`,
        sectionId: newSectionId,
        role: sanitizePublicRole(user.role),
        defaultGroupId: classGroupId,
      },
    });
  }

  // Self-update of name only
  const now = Date.now();
  await env.DB.prepare(
    `UPDATE users SET full_name = ?, updated_at = ? WHERE id = ?`
  ).bind(newFullName, now, user.userId).run();

  const gradeNameMap: Record<number, string> = {
    7: 'الصف السابع',
    8: 'الصف الثامن',
    9: 'الصف التاسع',
    10: 'الأول الثانوي',
    11: 'الثاني الثانوي',
    12: 'الثالث الثانوي'
  };

  return jsonResponse({
    success: true,
    message: 'تم تحديث الاسم بنجاح',
    user: {
      id: user.userId,
      fullName: newFullName,
      phoneNumber: user.phoneNumber,
      gradeId: user.gradeId,
      gradeName: gradeNameMap[user.gradeId] || `الصف ${user.gradeId}`,
      sectionId: user.sectionId,
      role: sanitizePublicRole(user.role),
      defaultGroupId: `class_${user.gradeId}_${user.sectionId}`,
    },
  });
}

/**
 * School admins may freeze or reactivate student accounts. Staff, admins, and the
 * concealed system owner are deliberately outside this lower-privilege operation.
 */
export async function handleUpdateSchoolStudentStatus(
  targetUserId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  if (normalizeRole(user.role) !== Role.ADMIN) {
    return errorResponse('FORBIDDEN', 'تغيير حالة الحسابات متاح لمدير المدرسة فقط', 403);
  }
  if (!targetUserId || targetUserId === user.userId) {
    return errorResponse('INVALID_ACTION', 'لا يمكنك تغيير حالة حسابك من هذه اللوحة', 400);
  }

  let requestedStatus = '';
  try {
    const body = await request.json() as { status?: unknown };
    requestedStatus = typeof body.status === 'string' ? body.status.trim().toUpperCase() : '';
  } catch {
    return errorResponse('INVALID_INPUT', 'صيغة طلب تغيير الحالة غير صالحة', 400);
  }
  if (requestedStatus !== 'ACTIVE' && requestedStatus !== 'DISABLED') {
    return errorResponse('INVALID_STATUS', 'الحالات المسموحة هي ACTIVE أو DISABLED فقط', 400);
  }

  const target = await env.DB.prepare(
    "SELECT id, role, COALESCE(status, 'ACTIVE') AS status FROM users WHERE id = ?"
  ).bind(targetUserId).first<{ id: string; role: string; status: string }>();
  if (!target) return errorResponse('USER_NOT_FOUND', 'الطالب غير موجود', 404);
  if (normalizeRole(target.role) !== Role.STUDENT) {
    return errorResponse('ROLE_PROTECTED', 'هذه العملية مخصصة لحسابات الطلاب فقط، ولا تغيّر رتب المعلمين أو الإدارة', 403);
  }

  if (target.status.toUpperCase() === requestedStatus) {
    return jsonResponse({ success: true, status: requestedStatus, message: 'الحساب بالفعل على الحالة المطلوبة' });
  }

  const update = await env.DB.prepare(
    "UPDATE users SET status = ?, updated_at = ? WHERE id = ? AND UPPER(role) NOT IN ('ADMIN', 'SYSTEM_OWNER', 'OWNER')"
  ).bind(requestedStatus, Date.now(), targetUserId).run();
  if (update.meta?.changes !== 1) {
    return errorResponse('USER_STATE_CHANGED', 'تغيرت حالة الحساب قبل إتمام العملية. حدّث لوحة الإدارة وحاول مجدداً.', 409);
  }

  if (requestedStatus === 'DISABLED') {
    // Existing bearer tokens are backed by server sessions; revoke them immediately.
    await env.DB.prepare('DELETE FROM sessions WHERE user_id = ?').bind(targetUserId).run();
  }

  return jsonResponse({
    success: true,
    status: requestedStatus,
    message: requestedStatus === 'DISABLED'
      ? 'تم إيقاف حساب الطالب وإنهاء جلساته. لن يستطيع الدخول حتى إعادة تفعيله.'
      : 'تم تفعيل حساب الطالب. سيحتاج إلى تسجيل الدخول من جديد.',
  });
}

/**
 * Removes a student from the class that matches their current grade and section.
 * Their account, contributions, messages, and academic history are not deleted.
 */
export async function handleRemoveSchoolStudentFromClass(
  targetUserId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  if (normalizeRole(user.role) !== Role.ADMIN) {
    return errorResponse('FORBIDDEN', 'فصل الطلاب من الشعب متاح لمدير المدرسة فقط', 403);
  }
  if (!targetUserId || targetUserId === user.userId) {
    return errorResponse('INVALID_ACTION', 'لا يمكنك فصل حسابك من هذه اللوحة', 400);
  }

  const target = await env.DB.prepare(
    'SELECT id, role, grade_id, section_id FROM users WHERE id = ?'
  ).bind(targetUserId).first<{ id: string; role: string; grade_id: number; section_id: string }>();
  if (!target) return errorResponse('USER_NOT_FOUND', 'الطالب غير موجود', 404);
  if (normalizeRole(target.role) !== Role.STUDENT) {
    return errorResponse('ROLE_PROTECTED', 'فصل العضوية بهذه الطريقة مخصص لحسابات الطلاب فقط', 403);
  }

  const groupId = `class_${target.grade_id}_${target.section_id}`;
  const removal = await env.DB.prepare(
    "DELETE FROM group_members WHERE group_id = ? AND user_id = ? AND status = 'ACTIVE'"
  ).bind(groupId, targetUserId).run();
  if (removal.meta?.changes !== 1) {
    return errorResponse('CLASS_MEMBERSHIP_NOT_FOUND', 'لم نعثر على عضوية نشطة لهذا الطالب في شعبته الحالية', 404);
  }

  return jsonResponse({
    success: true,
    groupId,
    message: 'تم فصل الطالب من شعبته. بقي حسابه وسجله الأكاديمي ومساهماته محفوظة.',
  });
}
