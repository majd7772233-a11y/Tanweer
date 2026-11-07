-- Enforce exactly one active schedule version per group.
-- If historical data already contains multiple active versions, preserve the
-- newest version (version_number, created_at, then id) and deactivate the rest.

UPDATE schedule_versions AS older
SET
    is_active = 0,
    valid_until = COALESCE(older.valid_until, date('now'))
WHERE older.is_active = 1
  AND EXISTS (
      SELECT 1
      FROM schedule_versions AS newer
      WHERE newer.group_id = older.group_id
        AND newer.is_active = 1
        AND (
            newer.version_number > older.version_number
            OR (
                newer.version_number = older.version_number
                AND newer.created_at > older.created_at
            )
            OR (
                newer.version_number = older.version_number
                AND newer.created_at = older.created_at
                AND newer.id > older.id
            )
        )
  );

CREATE UNIQUE INDEX IF NOT EXISTS idx_schedule_versions_single_active_per_group
ON schedule_versions(group_id)
WHERE is_active = 1;
