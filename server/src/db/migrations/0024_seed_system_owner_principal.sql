-- 0024: Seed a non-login internal SYSTEM_OWNER principal.
-- Owner Dashboard authentication remains controlled by TANWEER_OWNER_SECRET.
-- This reserved row provides a valid creator reference for owner-originated records
-- (for example official school holidays) and is retained by the system purge flow.
INSERT OR IGNORE INTO users (
  id,
  phone_number,
  password_hash,
  full_name,
  grade_id,
  section_id,
  email,
  recovery_code_hash,
  role,
  last_seen,
  created_at,
  updated_at
) VALUES (
  'SYSTEM_OWNER',
  '__TANWEER_INTERNAL_SYSTEM_ACTOR__',
  '!SYSTEM_ACCOUNT_LOGIN_DISABLED!',
  'مستخدم النظام الداخلي',
  0,
  'SYSTEM',
  NULL,
  '!SYSTEM_ACCOUNT_RECOVERY_DISABLED!',
  'SYSTEM_OWNER',
  0,
  0,
  0
);
