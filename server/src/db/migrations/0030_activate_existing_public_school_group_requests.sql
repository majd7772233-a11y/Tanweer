-- 0030: Existing requests for the public school-wide group no longer need approval.
-- Only the official "all" group is affected; private groups and class memberships remain unchanged.
UPDATE group_members
SET status = 'ACTIVE'
WHERE group_id = 'all'
  AND status = 'PENDING'
  AND EXISTS (
    SELECT 1
    FROM groups g
    WHERE g.id = 'all'
      AND g.visibility = 'PUBLIC'
      AND g.type != 'CLASS'
  );
