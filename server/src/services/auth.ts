import { Env } from '../env';
import { hashPassword, verifyPassword, verifyPasswordWithDetails, hashString, generateRecoveryCode, generateRandomToken, LEGACY_DEFAULT_PEPPER, encryptSensitivePayload } from '../lib/crypto';
import { generateId } from '../lib/ids';
import { validateArabicFullName, validateYemenPhoneNumber, evaluatePasswordStrength, isValidGradeSection } from '../lib/validation';
import { errorResponse, jsonResponse } from '../lib/response';
import { getCurrentAcademicYearId } from '../lib/school';
import { Role, normalizeRole, sanitizePublicRole } from '../lib/roles';
import { buildNewDeviceSessionStatements, createDeviceSession, revokeCreatedDeviceSession } from './devices';
import { buildRecoveryCodeRotationStatements } from './notifications';

function resolveDeviceIdentity(request: Request, requestedId?: string, requestedPlatform?: string, requestedVersion?: string) {
  const rawDeviceId = (requestedId || request.headers.get('X-Device-Id') || generateId('dev')).trim();
  if (!/^[A-Za-z0-9._:-]{8,128}$/.test(rawDeviceId)) return null;

  const platform = (requestedPlatform || 'Android').trim().slice(0, 40) || 'Android';
  const appVersion = (requestedVersion || 'Unknown').trim().slice(0, 40) || 'Unknown';
  return { deviceId: rawDeviceId, platform, appVersion };
}

async function isDeviceLimitReached(
  env: Env,
  userId: string,
  deviceId: string,
  now: number
): Promise<boolean> {
  const count = await env.DB.prepare(
    `SELECT COUNT(DISTINCT device_id) as count
     FROM sessions
     WHERE user_id = ? AND expires_at > ? AND device_id != ?`
  ).bind(userId, now, deviceId).first<{ count: number }>();
  return (count?.count || 0) >= 3;
}

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
    platform?: string;
    appVersion?: string;
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

  const classGroupId = `class_${body.gradeId}_${body.sectionId.toUpperCase()}`;
  const deviceIdentity = resolveDeviceIdentity(request, body.deviceId, body.platform, body.appVersion);
  if (!deviceIdentity) {
    return errorResponse('INVALID_DEVICE_ID', 'معرّف الجهاز غير صالح. حدّث التطبيق وحاول مجدداً.', 400);
  }

  const existingUser = await env.DB.prepare(
    `SELECT id, password_hash, grade_id, section_id, status FROM users WHERE phone_number = ?`
  ).bind(normalizedPhone).first<{ id: string; password_hash: string; grade_id: number; section_id: string; status?: string }>();

  let userId: string;
  let isRepairingIncompleteRegistration = false;

  if (existingUser) {
    if (existingUser.status === 'DISABLED') {
      return errorResponse('ACCOUNT_DISABLED', 'هذا الحساب موقوف من قِبل إدارة المنظومة، يرجى التواصل مع الإدارة', 403);
    }

    const targetMembership = await env.DB.prepare(
      `SELECT status FROM group_members WHERE group_id = ? AND user_id = ?`
    ).bind(classGroupId, existingUser.id).first<{ status: string }>();

    if (targetMembership?.status === 'BANNED') {
      return errorResponse('GROUP_BANNED', 'تم حظرك من هذه المجموعة، ولا يمكن استعادة عضويتك عبر إعادة التسجيل', 403);
    }

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
  // Fail early for incomplete-registration repair when this account already uses
  // all three other device slots. The final insert also checks the limit atomically.
  if (await isDeviceLimitReached(env, userId, deviceIdentity.deviceId, now)) {
    return errorResponse(
      'DEVICE_LIMIT_REACHED',
      'وصلت إلى الحد الأقصى وهو 3 أجهزة نشطة للحساب. أزل جهازاً قديماً من الإعدادات ثم أعد المحاولة.',
      403
    );
  }

  const { hash: passwordHash } = await hashPassword(body.password, pepper);
  const recoveryCode = generateRecoveryCode();
  const recoveryCodeHash = await hashString(recoveryCode.replace(/[\s\-]/g, '').toUpperCase(), pepper);

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

  // Registration also stores a durable credential record and encrypted private notification.
  const recoveryTokenId = generateId('rec_tok');
  const recoveryPayload = await encryptSensitivePayload(JSON.stringify({ recoveryCode }), pepper);

  // Single atomic batch execution for all registration entities
  const batchStatements = [];

  if (isRepairingIncompleteRegistration) {
    batchStatements.push(
      env.DB.prepare(
        `UPDATE users
         SET password_hash = ?, recovery_code_hash = ?, full_name = ?, grade_id = ?,
             section_id = ?, email = ?, updated_at = ?, last_seen = ?
         WHERE id = ?`
      ).bind(
        passwordHash,
        recoveryCodeHash,
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

  batchStatements.push(...buildRecoveryCodeRotationStatements(
    env.DB, userId, recoveryTokenId, recoveryCodeHash, recoveryPayload, userId, now
  ));

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
      `INSERT INTO group_members (group_id, user_id, role, status, joined_at)
       VALUES (?, ?, ?, 'ACTIVE', ?)
       ON CONFLICT(group_id, user_id) DO UPDATE SET
         role = excluded.role,
         status = 'ACTIVE',
         joined_at = excluded.joined_at
       WHERE group_members.status != 'BANNED'`
    ).bind(classGroupId, userId, Role.STUDENT, now)
  );

  const deviceSessionStatements = buildNewDeviceSessionStatements(env, {
    userId,
    deviceId: deviceIdentity.deviceId,
    sessionId,
    tokenHash,
    expiresAt,
    platform: deviceIdentity.platform,
    appVersion: deviceIdentity.appVersion,
    now,
  });
  const sessionInsertResultIndex = batchStatements.length + 1;
  batchStatements.push(...deviceSessionStatements);

  let registrationResults: any[] = [];
  if (typeof env.DB.batch === 'function') {
    registrationResults = await env.DB.batch(batchStatements);
  } else {
    for (const stmt of batchStatements) {
      registrationResults.push(await stmt.run());
    }
  }

  if (registrationResults[sessionInsertResultIndex]?.meta?.changes !== 1) {
    return errorResponse(
      'DEVICE_LIMIT_REACHED',
      'وصلت إلى الحد الأقصى وهو 3 أجهزة نشطة للحساب. أزل جهازاً قديماً من أحد أجهزتك ثم أعد المحاولة.',
      403
    );
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
    platform?: string;
  };

  if (!body.phoneNumber || !body.password) {
    return errorResponse('INVALID_INPUT', 'يرجى إدخال رقم الهاتف وكلمة المرور');
  }

  const phoneVal = validateYemenPhoneNumber(body.phoneNumber);
  const normalizedPhone = phoneVal.isValid ? phoneVal.normalized : body.phoneNumber.trim();

  const user = await env.DB.prepare(
    `SELECT id, phone_number, full_name, grade_id, section_id, role, password_hash, status
     FROM users WHERE phone_number = ?`
  ).bind(normalizedPhone).first<{
    id: string;
    phone_number: string;
    full_name: string;
    grade_id: number;
    section_id: string;
    role: string;
    password_hash: string;
    status?: string;
  }>();

  if (!user) {
    return errorResponse('INVALID_CREDENTIALS', 'رقم الهاتف أو كلمة المرور غير صحيحة');
  }

  const verification = await verifyPasswordWithDetails(body.password, user.password_hash, pepper);
  if (!verification.isValid) {
    return errorResponse('INVALID_CREDENTIALS', 'رقم الهاتف أو كلمة المرور غير صحيحة');
  }

  if (user.status === 'DISABLED') {
    return errorResponse('ACCOUNT_DISABLED', 'تم تجميد هذا الحساب من قِبل إدارة المنظومة', 403);
  }

  const now = Date.now();

  // TA-21: Check and atomically consume a single-use temporary password.
  // Fail closed: a storage error must never turn an unchecked temporary credential into a login.
  try {
    const tempToken = await env.DB.prepare(
      `SELECT id, expires_at, max_uses, uses_count, revoked FROM user_recovery_tokens
       WHERE user_id = ? AND type = 'TEMP_PASSWORD'
       ORDER BY created_at DESC LIMIT 1`
    ).bind(user.id).first<{
      id: string;
      expires_at: number;
      max_uses: number;
      uses_count: number;
      revoked: number;
    }>();

    if (tempToken) {
      if (tempToken.revoked || now >= tempToken.expires_at || tempToken.uses_count >= tempToken.max_uses) {
        return errorResponse('CREDENTIALS_EXPIRED', 'انتهت صلاحية كلمة المرور المؤقتة، يرجى طلب إعادة إصدار جديدة من إدارة المنظومة', 401);
      }

      // Conditional UPDATE makes consumption safe against concurrent login attempts.
      const consumeResult = await env.DB.prepare(
        `UPDATE user_recovery_tokens
         SET uses_count = uses_count + 1, used_at = ?, revoked = 1
         WHERE id = ? AND revoked = 0 AND expires_at > ? AND uses_count < max_uses`
      ).bind(now, tempToken.id, now).run();

      if (consumeResult.meta?.changes !== 1) {
        return errorResponse('CREDENTIALS_EXPIRED', 'انتهت صلاحية كلمة المرور المؤقتة أو تم استخدامها بالفعل، يرجى طلب إعادة إصدار جديدة من إدارة المنظومة', 401);
      }
    }
  } catch (err) {
    console.error('Failed to validate or consume temporary login credential:', err);
    return errorResponse(
      'AUTHENTICATION_UNAVAILABLE',
      'تعذر التحقق من صلاحية بيانات الدخول المؤقتة حالياً. يرجى المحاولة لاحقاً.',
      503
    );
  }

  // Transparently upgrade legacy hash or rehash with current configured secret pepper
  if (verification.needsRehash || !user.password_hash.startsWith('pbkdf2$')) {
    const { hash: newPbkdf2Hash } = await hashPassword(body.password, pepper);
    await env.DB.prepare(`UPDATE users SET password_hash = ? WHERE id = ?`).bind(newPbkdf2Hash, user.id).run();
  }

  await env.DB.prepare(`UPDATE users SET last_seen = ? WHERE id = ?`).bind(now, user.id).run();

  const deviceIdentity = resolveDeviceIdentity(request, body.deviceId, body.platform, body.appVersion);
  if (!deviceIdentity) {
    return errorResponse('INVALID_DEVICE_ID', 'معرّف الجهاز غير صالح. حدّث التطبيق وحاول مجدداً.', 400);
  }

  const token = generateRandomToken();
  const tokenHash = await hashString(token, pepper);
  const sessionId = generateId('sess');
  const expiresAt = now + 90 * 24 * 3600 * 1000;

  let sessionCreated = false;
  try {
    sessionCreated = await createDeviceSession(env, {
      userId: user.id,
      deviceId: deviceIdentity.deviceId,
      sessionId,
      tokenHash,
      expiresAt,
      platform: deviceIdentity.platform,
      appVersion: deviceIdentity.appVersion,
      now,
    });
  } catch (error) {
    console.error('Failed to create device session:', error);
    return errorResponse('SESSION_STORE_UNAVAILABLE', 'تعذر إنشاء جلسة الجهاز حالياً. يرجى المحاولة لاحقاً.', 503);
  }

  if (!sessionCreated) {
    return errorResponse(
      'DEVICE_LIMIT_REACHED',
      'وصلت إلى الحد الأقصى وهو 3 أجهزة نشطة للحساب. افتح الإعدادات من جهاز مسجل، وأزل جهازاً قديماً ثم أعد تسجيل الدخول.',
      403
    );
  }

  const classGroupId = (user.grade_id && user.section_id && user.role !== Role.ADMIN) ? `class_${user.grade_id}_${user.section_id}` : '';

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
    platform?: string;
    appVersion?: string;
  };

  if (!body.recoveryCode || !body.recoveryCode.trim()) {
    return errorResponse('INVALID_INPUT', 'يرجى إدخال رمز الاسترداد السري');
  }

  const cleanCode = body.recoveryCode.replace(/[\s\-]/g, '').toUpperCase();
  const recoveryHash = await hashString(cleanCode, pepper);
  // Preserve the exact hash format that matched the user row; issuance metadata can
  // remain keyed by the legacy hash until the credential is consumed.
  let matchedRecoveryHash = recoveryHash;

  let user = await env.DB.prepare(
    `SELECT id, phone_number, full_name, grade_id, section_id, role, status
     FROM users WHERE recovery_code_hash = ?`
  ).bind(recoveryHash).first<{
    id: string;
    phone_number: string;
    full_name: string;
    grade_id: number;
    section_id: string;
    role: string;
    status?: string;
  }>();

  // Backward compatibility fallback: verify against legacy default pepper if secret changed
  if (!user && pepper !== LEGACY_DEFAULT_PEPPER) {
    const legacyRecoveryHash = await hashString(cleanCode, LEGACY_DEFAULT_PEPPER);
    user = await env.DB.prepare(
      `SELECT id, phone_number, full_name, grade_id, section_id, role, status
       FROM users WHERE recovery_code_hash = ?`
    ).bind(legacyRecoveryHash).first<{
      id: string;
      phone_number: string;
      full_name: string;
      grade_id: number;
      section_id: string;
      role: string;
      status?: string;
    }>();

    if (user) {
      matchedRecoveryHash = legacyRecoveryHash;
    }
  }

  if (!user) {
    return errorResponse('INVALID_RECOVERY_CODE', 'رمز الاسترداد السري غير صحيح أو غير موجود');
  }

  if (user.status === 'DISABLED') {
    return errorResponse('ACCOUNT_DISABLED', 'تم تجميد هذا الحساب من قِبل إدارة المنظومة', 403);
  }

  const now = Date.now();
  const deviceIdentity = resolveDeviceIdentity(request, body.deviceId, body.platform, body.appVersion);
  if (!deviceIdentity) {
    return errorResponse('INVALID_DEVICE_ID', 'معرّف الجهاز غير صالح. حدّث التطبيق وحاول مجدداً.', 400);
  }
  if (await isDeviceLimitReached(env, user.id, deviceIdentity.deviceId, now)) {
    return errorResponse(
      'DEVICE_LIMIT_REACHED',
      'وصلت إلى الحد الأقصى وهو 3 أجهزة نشطة للحساب. أزل جهازاً قديماً من الإعدادات قبل استخدام رمز الاسترداد.',
      403
    );
  }

  // Recovery codes are permanent and reusable until changed or revoked. A legacy hash without
  // an issuance row remains valid because users.recovery_code_hash is the authoritative match.
  let recoveryToken: { id: string; type: string; revoked: number } | null;
  try {
    recoveryToken = await env.DB.prepare(`
      SELECT id, type, revoked FROM user_recovery_tokens
      WHERE token_hash = ? AND user_id = ? ORDER BY created_at DESC LIMIT 1
    `).bind(matchedRecoveryHash, user.id).first<{ id: string; type: string; revoked: number }>();
  } catch (error) {
    console.error('Failed to validate recovery-token state:', error);
    return errorResponse('AUTHENTICATION_UNAVAILABLE', 'تعذر التحقق من صلاحية رمز الاسترداد حالياً. يرجى المحاولة لاحقاً.', 503);
  }
  if (recoveryToken && recoveryToken.revoked === 1) {
    return errorResponse('RECOVERY_CODE_REVOKED', 'تم إلغاء رمز الاسترداد هذا. استخدم الرمز الأحدث أو اطلب رمزاً جديداً.', 400);
  }
  if (recoveryToken && recoveryToken.type !== 'RECOVERY_CODE') {
    return errorResponse('INVALID_RECOVERY_CODE', 'بيانات الاعتماد المقدمة ليست رمز استرداد صالحاً', 400);
  }

  const token = generateRandomToken();
  const tokenHash = await hashString(token, pepper);
  const sessionId = generateId('sess');
  const expiresAt = now + 90 * 24 * 3600 * 1000;
  let reservedSession = false;
  try {
    reservedSession = await createDeviceSession(env, {
      userId: user.id, deviceId: deviceIdentity.deviceId, sessionId, tokenHash, expiresAt,
      platform: deviceIdentity.platform, appVersion: deviceIdentity.appVersion, now,
    });
  } catch (error) {
    console.error('Failed to reserve a device slot for recovery login:', error);
    return errorResponse('SESSION_STORE_UNAVAILABLE', 'تعذر إنشاء جلسة الجهاز حالياً. يرجى المحاولة لاحقاً.', 503);
  }
  if (!reservedSession) {
    return errorResponse('DEVICE_LIMIT_REACHED', 'وصلت إلى الحد الأقصى وهو 3 أجهزة نشطة للحساب. أزل جهازاً قديماً من الإعدادات قبل استخدام رمز الاسترداد.', 403);
  }
  let credentialStillCurrent;
  try {
    credentialStillCurrent = await env.DB.prepare(`
      UPDATE users
      SET last_seen = ?
      WHERE id = ? AND recovery_code_hash = ?
        AND (? IS NULL OR EXISTS (
          SELECT 1 FROM user_recovery_tokens
          WHERE id = ? AND user_id = ? AND token_hash = ?
            AND type = 'RECOVERY_CODE' AND revoked = 0
        ))
    `).bind(
      now,
      user.id,
      matchedRecoveryHash,
      recoveryToken?.id ?? null,
      recoveryToken?.id ?? null,
      user.id,
      matchedRecoveryHash
    ).run();
  } catch (error) {
    await revokeCreatedDeviceSession(env, user.id, deviceIdentity.deviceId, sessionId).catch(cleanupError =>
      console.error('Failed to clean up recovery session after credential revalidation error:', cleanupError)
    );
    console.error('Failed to revalidate permanent recovery credential:', error);
    return errorResponse('AUTHENTICATION_UNAVAILABLE', 'تعذر إكمال التحقق من رمز الاسترداد بأمان. يرجى المحاولة لاحقاً.', 503);
  }

  if (credentialStillCurrent.meta?.changes !== 1) {
    await revokeCreatedDeviceSession(env, user.id, deviceIdentity.deviceId, sessionId).catch(cleanupError =>
      console.error('Failed to clean up recovery session after credential rotation:', cleanupError)
    );
    return errorResponse('RECOVERY_CODE_REVOKED', 'تم تغيير أو إلغاء رمز الاسترداد أثناء تسجيل الدخول. استخدم الرمز الأحدث.', 409);
  }

  const classGroupId = (user.grade_id && user.section_id && user.role !== Role.ADMIN) ? `class_${user.grade_id}_${user.section_id}` : '';

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
