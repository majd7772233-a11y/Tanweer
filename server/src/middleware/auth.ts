import { Env, UserContext } from '../env';
import { hashString } from '../lib/crypto';
import { errorResponse } from '../lib/response';

export async function getUserFromToken(token: string, env: Env): Promise<UserContext | null> {
  const pepper = env.SESSION_PEPPER || env.PASSWORD_PEPPER;
  if (!pepper) {
    throw new Error('Server pepper secret is not configured in environment');
  }

  const tokenHash = await hashString(token, pepper);

  const session = await env.DB.prepare(
    `SELECT s.id, s.user_id, s.device_id, s.expires_at, u.phone_number, u.full_name, u.grade_id, u.section_id, u.role,
            COALESCE(u.status, 'ACTIVE') as status
     FROM sessions s
     JOIN users u ON s.user_id = u.id
     WHERE s.refresh_token_hash = ? AND s.expires_at > ?`
  ).bind(tokenHash, Date.now()).first<{
    id: string;
    user_id: string;
    device_id: string;
    expires_at: number;
    phone_number: string;
    full_name: string;
    grade_id: number;
    section_id: string;
    role: string;
    status: string;
  }>();

  if (!session) return null;
  if (session.status === 'DISABLED') {
    throw new Error('USER_DISABLED');
  }

  return {
    userId: session.user_id,
    phoneNumber: session.phone_number,
    fullName: session.full_name,
    gradeId: session.grade_id,
    sectionId: session.section_id,
    role: session.role,
    deviceId: session.device_id,
  };
}

export async function authenticateRequest(
  request: Request,
  env: Env
): Promise<{ user: UserContext | null; error?: Response }> {
  const pepper = env.SESSION_PEPPER || env.PASSWORD_PEPPER;
  if (!pepper) {
    return { user: null, error: errorResponse('CONFIG_ERROR', 'مفتاح الجلسة السري غير متوفر في الخادم', 500) };
  }

  const authHeader = request.headers.get('Authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return { user: null, error: errorResponse('UNAUTHORIZED', 'يرجى تسجيل الدخول أولاً', 401) };
  }

  const token = authHeader.substring(7);
  try {
    const user = await getUserFromToken(token, env);
    if (!user) {
      return { user: null, error: errorResponse('SESSION_EXPIRED', 'انتهت صلاحية الجلسة، يرجى تسجيل الدخول مجددًا', 401) };
    }
    return { user };
  } catch (err: any) {
    if (err?.message === 'USER_DISABLED') {
      return { user: null, error: errorResponse('ACCOUNT_DISABLED', 'تم تجميد هذا الحساب من قِبل إدارة المنظومة', 403) };
    }
    return { user: null, error: errorResponse('INTERNAL_ERROR', err?.message || 'خطأ في المصادقة', 500) };
  }
}
