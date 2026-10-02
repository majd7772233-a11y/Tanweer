import { Env } from '../env';
import { hashPassword, verifyPassword, hashString, generateRecoveryCode, generateRandomToken } from '../lib/crypto';
import { generateId } from '../lib/ids';
import { validateArabicFullName, validateYemenPhoneNumber, evaluatePasswordStrength, isValidGradeSection } from '../lib/validation';
import { errorResponse, jsonResponse } from '../lib/response';
import { getCurrentAcademicYearId } from '../lib/school';

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
    `SELECT id FROM users WHERE phone_number = ?`
  ).bind(normalizedPhone).first();

  if (existingUser) {
    return errorResponse('PHONE_EXISTS', 'رقم الهاتف مسجل مسبقًا. يمكنك تسجيل الدخول بنفس الرقم وكلمة المرور أو برمز الاسترداد.');
  }

  const userId = generateId('user');
  const now = Date.now();
  const { hash: passwordHash } = await hashPassword(body.password, pepper);
  const recoveryCode = generateRecoveryCode();
  const recoveryCodeHash = await hashString(recoveryCode.replace(/[\s\-]/g, '').toUpperCase(), pepper);

  await env.DB.prepare(
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
    'MEMBER',
    now,
    now,
    now
  ).run();

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
  await env.DB.prepare(
    `INSERT OR IGNORE INTO groups (id, name, type, description, academic_year_id, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`
  ).bind(classGroupId, groupName, 'CLASS', `المجموعة الدراسية الرسمية لـ ${groupName}`, academicYear, now).run();

  await env.DB.prepare(
    `INSERT OR IGNORE INTO group_sections (group_id, grade_id, section_id)
     VALUES (?, ?, ?)`
  ).bind(classGroupId, body.gradeId, body.sectionId.toUpperCase()).run();

  await env.DB.prepare(
    `INSERT OR IGNORE INTO group_members (group_id, user_id, role, status, joined_at)
     VALUES (?, ?, ?, ?, ?)`
  ).bind(classGroupId, userId, 'MEMBER', 'ACTIVE', now).run();

  const token = generateRandomToken();
  const tokenHash = await hashString(token, pepper);
  const sessionId = generateId('sess');
  const deviceId = body.deviceId || generateId('dev');
  const expiresAt = now + 90 * 24 * 3600 * 1000;

  await env.DB.prepare(
    `INSERT INTO sessions (id, user_id, device_id, refresh_token_hash, expires_at, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`
  ).bind(sessionId, userId, deviceId, tokenHash, expiresAt, now).run();

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
      role: 'MEMBER',
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

  const isPasswordValid = await verifyPassword(body.password, user.password_hash, pepper);
  if (!isPasswordValid) {
    return errorResponse('INVALID_CREDENTIALS', 'رقم الهاتف أو كلمة المرور غير صحيحة');
  }

  // Transparently upgrade legacy SHA-256 hash to PBKDF2
  if (!user.password_hash.startsWith('pbkdf2$')) {
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
      role: user.role,
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

  const user = await env.DB.prepare(
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

  if (!user) {
    return errorResponse('INVALID_RECOVERY_CODE', 'رمز الاسترداد السري غير صحيح أو غير موجود');
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
      role: user.role,
      defaultGroupId: classGroupId,
    },
  });
}
