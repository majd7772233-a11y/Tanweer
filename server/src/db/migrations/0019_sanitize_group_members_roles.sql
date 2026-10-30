-- Migration 0019: Sanitize any legacy group_members records with SYSTEM_OWNER role (TA-28)
UPDATE group_members
SET role = 'ADMIN'
WHERE role IN ('SYSTEM_OWNER', 'OWNER');
