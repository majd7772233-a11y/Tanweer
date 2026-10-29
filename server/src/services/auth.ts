import { Env } from '../env';
import { hashPassword, verifyPassword, verifyPasswordWithDetails, hashString, generateRecoveryCode, generateRandomToken, LEGACY_DEFAULT_PEPPER } from '../lib/crypto';
import { generateId } from '../lib/ids';
import { validateArabicFullName, validateYemenPhoneNumber, evaluatePasswordStrength, isValidGradeSection } from '../lib/validation';
import { errorResponse, jsonResponse } from '../lib/response';
import { getCurrentAcademicYearId } from '../lib/school';
import { Role, normalizeRole, sanitizePublicRole } from '../lib/roles';

export async function handleRegister(request: Request, env: Env): Promise<Response> {
  const pepper = env.SESSION_PEPPER || env.PASSWORD_PEPPER;
  if (!pepper) {
    return errorResponse('CONFIG_ERROR', 'مفتاح التشفير السري غير مهيأ في الخادم', 500);
  }

  const body = await request.json() as {
    phoneNumber?: string;
    password?: string;
    fullName?: string;
    gradeId?: number;
    sectionId?: string;
    email?: string;
    deviceId?: string;
  };

  if (!body.phoneNumber || !body.password || !body.fullName || !body.gradeId || !body.sectionId) {
    return errorResponse('INVALID_INPUT', 'يرجى ملء جميع الحقول المطلوبة');
  }

  // 1. Validate Arabic 3-part name
  const nameValidation = validateArabicFullName(body.fullName);
  if (!nameValidation.isValid) {
    return errorResponse('INVALID_NAME', nameValidation.error || 'الاسم الثلاثي غير صالح');
  }

  // 2. Validate Yemen Phone Number (+967 7XX XXX XXX)
  const phoneValidation = validateYemenPhoneNumber(body.phoneNumber);
  if (!phoneValidation.isValid) {
    return errorResponse('INVALID_PHONE', phoneValidation.error || 'رقم الهاتف غير صالح');
  }
  const normalizedPhone = phoneValidation.normalized;

  // 3. Evaluate Password Strength
  const pwdEval = evaluatePasswordStrength(body.password);
  if (!pwdEval.isValid) {
    return errorResponse('WEAK_PASSWORD', pwdEval.error || 'كلمة المرور ضعيفة جداً');
  }

  if (!isValidGradeSection(body.gradeId, body.sectionId)) {
    return errorResponse('INVALID_SECTION', 'الشعبة غير متوافقة مع الصف المحدد');
  }

  const existingUser = await env.DB.prepare(
    `SELECT id, password_hash, grade_id, section_id FROM users WHERE phone_number = ?`
  ).bind(normalizedPhone).first<{ id: string; password_hash: string; grade_id: number; section_id: string }>();

  let userId: string;
  let isRepairingIncompleteRegistration = false;

  if (existingUser) {
    // Check if user has an active membership and a valid session
    const membership = await env.DB.prepare(
      `SELECT group_id FROM group_members WHERE user_id = ? AND status = 'ACTIVE' LIMIT 1`
    ).bind(existingUser.id).first();
    const session = await env.DB.prepare(
      `SELECT id FROM sessions WHERE user_id = ? AND expires_at > ? LIMIT 1`
    ).bind(existingUser.id, Date.now()).first();

    if (!membership || !session) {
      // Incomplete registration: User record exists, but memberships or sessions failed/interrupted
      const isPwdValid = await verifyPassword(body.password, existingUser.password_hash, pepper);
      if (!isPwdValid) {
        return errorResponse(
          'INCOMPLETE_REGISTRATION',
          'رقم الهاتف مسجل ولكن إنشاء الحساب لم يكتمل سابقاً. يرجى إدخال كلمة المرور المستخدمة سابقاً لإتمام تسجيل الحساب، أو استخدام رمز الاسترداد.'
        );
      }
      userId = existingUser.id;
      isRepairingIncompleteRegistration = true;
    } else {
      return errorResponse('PHONE_EXISTS', 'رقم الهاتف مسجل مسبقًا. يمكنك تسجيل الدخول بنفس الرقم وكلمة المرور أو برمز الاسترداد.');
    }
  } else {
    userId = generateId('user');
  }

  const now = Date.now();
  const { hash: passwordHash } = await hashPassword(body.password, pepper);
  const recoveryCode = generateRecoveryCode();
  const recoveryCodeHash = await hashString(recoveryCode.replace(/[\s\-]/g, '').toUpperCase(), pepper);

  const classGroupId = `class_${body.gradeId}_${body.sectionId.toUpperCase()}`;
  const gradeNameMap: Record<number, string> = {
    7: 'الصف السابع',
    8: 'الصف الثامن',
    9: 'الصف التاسع',
    10: 'الأول الثانوي',
    11: 'الثاني الثانوي',
    12: 'الثالث الثانوي'
  };
  const groupName = `${gradeNameMap[body.gradeId]} — شعبة (${body.sectionId.toUpperCase()})`;
  const academicYear = await getCurrentAcademicYearId(env.DB);

  const token = generateRandomToken();
  const tokenHash = await hashString(token, pepper);
  const sessionId = generateId('sess');
  const deviceId = body.deviceId || generateId('dev');
  const expiresAt = now + 90 * 24 * 3600 * 1000;

  // Single atomic batch execution for all registration entities
  const batchStatements = [];

  if (isRepairingIncompleteRegistration) {
    batchStatements.push(
      env.DB.prepare(
        `UPDATE users SET full_name = ?, grade_id = ?, section_id = ?, email = ?, updated_at = ?, last_seen = ? WHERE id = ?`
      ).bind(
        body.fullName.trim().replace(/\s+/g, ' '),
        body.gradeId,
        body.sectionId.toUpperCase(),
        body.email?.trim() || null,
        now,
        now,
        userId
      )
    );
  } else {
    batchStatements.push(
      env.DB.prepare(
        `INSERT INTO users (id, phone_number, password_hash, full_name, grade_id, section_id, email, recovery_code_hash, role, last_seen, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      ).bind(
        userId,
        normalizedPhone,
        passwordHash,
        body.fullName.trim().replace(/\s+/g, ' '),
        body.gradeId,
        body.sectionId.toUpperCase(),
        body.email?.trim() || null,
        recoveryCodeHash,
        Role.STUDENT,
        now,
        now,
        now
      )
    );
  }

  batchStatements.push(
    env.DB.prepare(
      `INSERT OR IGNORE INTO groups (id, name, type, description, academic_year_id, created_at)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(classGroupId, groupName, 'CLASS', `المجموعة الدراسية الرسمية لـ ${groupName}`, academicYear, now)
  );

  batchStatements.push(
    env.DB.prepare(
      `INSERT OR IGNORE INTO group_sections (group_id, grade_id, section_id)
       VALUES (?, ?, ?)`
    ).bind(classGroupId, body.gradeId, body.sectionId.toUpperCase())
  );

  batchStatements.push(
    env.DB.prepare(
      `INSERT OR REPLACE INTO group_members (group_id, user_id, role, status, joined_at)
       VALUES (?, ?, ?, 'ACTIVE', ?)`
    ).bind(classGroupId, userId, Role.STUDENT, now)
  );

  batchStatements.push(
    env.DB.prepare(
      `INSERT INTO sessions (id, user_id, device_id, refresh_token_hash, expires_at, created_at)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(sessionId, userId, deviceId, tokenHash, expiresAt, now)
  );

  if (typeof env.DB.batch === 'function') {
    await env.DB.batch(batchStatements);
  } else {
    for (const stmt of batchStatements) {
      await stmt.run();
    }
  }

  return jsonResponse({
    success: true,
    token,
    recoveryCode,
    user: {
      id: userId,
      fullName: body.fullName.trim().replace(/\s+/g, ' '),
      phoneNumber: normalizedPhone,
      gradeId: body.gradeId,
      sectionId: body.sectionId.toUpperCase(),
      role: Role.STUDENT,
      defaultGroupId: classGroupId,
    },
  });
}

export async function handleLogin(request: Request, env: Env): Promise<Response> {
  const pepper = env.SESSION_PEPPER || env.PASSWORD_PEPPER;
  if (!pepper) {
    return errorResponse('CONFIG_ERROR', 'مفتاح التشفير السري غير مهيأ في الخادم', 500);
  }

  const body = await request.json() as {
    phoneNumber?: string;
    password?: string;
    deviceId?: string;
    appVersion?: string;
  };

  if (!body.phoneNumber || !body.password) {
    return errorResponse('INVALID_INPUT', 'يرجى إدخال رقم الهاتف وكلمة المرور');
  }

  const phoneVal = validateYemenPhoneNumber(body.phoneNumber);
  const normalizedPhone = phoneVal.isValid ? phoneVal.normalized : body.phoneNumber.trim();

  const user = await env.DB.prepare(
    `SELECT id, phone_number, full_name, grade_id, section_id, role, password_hash
     FROM users WHERE phone_number = ?`
  ).bind(normalizedPhone).first<{
    id: string;
    phone_number: string;
    full_name: string;
    grade_id: number;
    section_id: string;
    role: string;
    password_hash: string;
  }>();

  if (!user) {
    return errorResponse('INVALID_CREDENTIALS', 'رقم الهاتف أو كلمة المرور غير صحيحة');
  }

  const verification = await verifyPasswordWithDetails(body.password, user.password_hash, pepper);
  if (!verification.isValid) {
    return errorResponse('INVALID_CREDENTIALS', 'رقم الهاتف أو كلمة المرور غير صحيحة');
  }

  // Transparently upgrade legacy hash or rehash with current configured secret pepper
  if (verification.needsRehash || !user.password_hash.startsWith('pbkdf2$')) {
    const { hash: newPbkdf2Hash } = await hashPassword(body.password, pepper);
    await env.DB.prepare(`UPDATE users SET password_hash = ? WHERE id = ?`).bind(newPbkdf2Hash, user.id).run();
  }

  const now = Date.now();
  await env.DB.prepare(`UPDATE users SET last_seen = ? WHERE id = ?`).bind(now, user.id).run();

  const token = generateRandomToken();
  const tokenHash = await hashString(token, pepper);
  const sessionId = generateId('sess');
  const deviceId = body.deviceId || generateId('dev');
  const expiresAt = now + 90 * 24 * 3600 * 1000;

  await env.DB.prepare(
    `INSERT INTO sessions (id, user_id, device_id, refresh_token_hash, expires_at, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`
  ).bind(sessionId, user.id, deviceId, tokenHash, expiresAt, now).run();

  const classGroupId = `class_${user.grade_id}_${user.section_id}`;

  return jsonResponse({
    success: true,
    token,
    user: {
      id: user.id,
      fullName: user.full_name,
      phoneNumber: user.phone_number,
      gradeId: user.grade_id,
      sectionId: user.section_id,
      role: sanitizePublicRole(user.role),
      defaultGroupId: classGroupId,
    },
  });
}

export async function handleLoginWithRecoveryCode(request: Request, env: Env): Promise<Response> {
  const pepper = env.SESSION_PEPPER || env.PASSWORD_PEPPER;
  if (!pepper) {
    return errorResponse('CONFIG_ERROR', 'مفتاح التشفير السري غير مهيأ في الخادم', 500);
  }

  const body = await request.json() as {
    recoveryCode?: string;
    deviceId?: string;
  };

  if (!body.recoveryCode || !body.recoveryCode.trim()) {
    return errorResponse('INVALID_INPUT', 'يرجى إدخال رمز الاسترداد السري');
  }

  const cleanCode = body.recoveryCode.replace(/[\s\-]/g, '').toUpperCase();
  const recoveryHash = await hashString(cleanCode, pepper);

  let user = await env.DB.prepare(
    `SELECT id, phone_number, full_name, grade_id, section_id, role
     FROM users WHERE recovery_code_hash = ?`
  ).bind(recoveryHash).first<{
    id: string;
    phone_number: string;
    full_name: string;
    grade_id: number;
    section_id: string;
    role: string;
  }>();

  // Backward compatibility fallback: verify against legacy default pepper if secret changed
  if (!user && pepper !== LEGACY_DEFAULT_PEPPER) {
    const legacyRecoveryHash = await hashString(cleanCode, LEGACY_DEFAULT_PEPPER);
    user = await env.DB.prepare(
      `SELECT id, phone_number, full_name, grade_id, section_id, role
       FROM users WHERE recovery_code_hash = ?`
    ).bind(legacyRecoveryHash).first<{
      id: string;
      phone_number: string;
      full_name: string;
      grade_id: number;
      section_id: string;
      role: string;
    }>();

    if (user) {
      // Seamlessly migrate recovery code hash to current configured pepper
      await env.DB.prepare(`UPDATE users SET recovery_code_hash = ? WHERE id = ?`).bind(recoveryHash, user.id).run();
    }
  }

  if (!user) {
    return errorResponse('INVALID_RECOVERY_CODE', 'رمز الاسترداد السري غير صحيح أو غير موجود');
  }

  const now = Date.now();

  // Enforce single-use and expiration check if issued via user_recovery_tokens (TA-16)
  try {
    const recoveryToken = await env.DB.prepare(`
      SELECT id, max_uses, uses_count, expires_at, revoked
      FROM user_recovery_tokens
      WHERE token_hash = ? AND user_id = ?
      ORDER BY created_at DESC LIMIT 1
    `).bind(recoveryHash, user.id).first<{ id: string; max_uses: number; uses_count: number; expires_at: number; revoked: number }>();

    if (recoveryToken) {
      if (recoveryToken.revoked === 1) {
        return errorResponse('RECOVERY_CODE_REVOKED', 'تم إبطال رمز الاسترداد هذا، يرجى طلب رمز جديد', 400);
      }
      if (recoveryToken.expires_at < now) {
        return errorResponse('RECOVERY_CODE_EXPIRED', 'رمز الاسترداد منتهي الصلاحية، يرجى طلب رمز جديد', 400);
      }
      if (recoveryToken.uses_count >= recoveryToken.max_uses) {
        return errorResponse('RECOVERY_CODE_USED', 'تم استخدام رمز الاسترداد هذا مسبقاً، ولا يمكن استخدامه أكثر من مرة واحدة', 400);
      }
      await env.DB.prepare(`
        UPDATE user_recovery_tokens SET uses_count = uses_count + 1, used_at = ? WHERE id = ?
      `).bind(now, recoveryToken.id).run();
    }
  } catch {}

  // Single-use enforcement: burn recovery_code_hash on users table so it cannot be used again
  const burnedHash = 'USED_' + now + '_' + generateRandomToken().slice(0, 12);
  await env.DB.prepare(`UPDATE users SET recovery_code_hash = ?, last_seen = ? WHERE id = ?`).bind(burnedHash, now, user.id).run();

  const token = generateRandomToken();
  const tokenHash = await hashString(token, pepper);
  const sessionId = generateId('sess');
  const deviceId = body.deviceId || generateId('dev');
  const expiresAt = now + 90 * 24 * 3600 * 1000;

  await env.DB.prepare(
    `INSERT INTO sessions (id, user_id, device_id, refresh_token_hash, expires_at, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`
  ).bind(sessionId, user.id, deviceId, tokenHash, expiresAt, now).run();

  const classGroupId = `class_${user.grade_id}_${user.section_id}`;

  return jsonResponse({
    success: true,
    token,
    user: {
      id: user.id,
      fullName: user.full_name,
      phoneNumber: user.phone_number,
      gradeId: user.grade_id,
      sectionId: user.section_id,
      role: sanitizePublicRole(user.role),
      defaultGroupId: classGroupId,
    },
  });
}
