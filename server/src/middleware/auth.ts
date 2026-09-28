import { Env, UserContext } from '../env';
import { hashString } from '../lib/crypto';
import { errorResponse } from '../lib/response';

export async function authenticateRequest(
  request: Request,
  env: Env
): Promise<{ user: UserContext | null; error?: Response }> {
  const authHeader = request.headers.get('Authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return { user: null, error: errorResponse('UNAUTHORIZED', 'يرجى تسجيل الدخول أولاً', 401) };
  }

  const token = authHeader.substring(7);
  const tokenHash = await hashString(token, env.SESSION_PEPPER || 'tanweer-pepper');

  const session = await env.DB.prepare(
    `SELECT s.id, s.user_id, s.device_id, s.expires_at, u.phone_number, u.full_name, u.grade_id, u.section_id, u.role
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
  }>();

  if (!session) {
    return { user: null, error: errorResponse('SESSION_EXPIRED', 'انتهت صلاحية الجلسة، يرجى تسجيل الدخول مجددًا', 401) };
  }

  return {
    user: {
      userId: session.user_id,
      phoneNumber: session.phone_number,
      fullName: session.full_name,
      gradeId: session.grade_id,
      sectionId: session.section_id,
      role: session.role,
      deviceId: session.device_id,
    },
  };
}
