export interface Env {
  DB: D1Database;
  CHAT: DurableObjectNamespace;
  JWT_SECRET?: string;
  SESSION_PEPPER?: string;
  ENVIRONMENT?: string;
}

export interface UserContext {
  userId: string;
  phoneNumber: string;
  fullName: string;
  gradeId: number;
  sectionId: string;
  role: string;
  deviceId: string;
}
