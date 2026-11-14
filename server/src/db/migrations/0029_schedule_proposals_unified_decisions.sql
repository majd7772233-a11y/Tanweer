-- 0029: Backfill schedule proposal history into the unified community decision engine.
-- New schedule proposals and votes are created through community_decisions/community_votes only.

INSERT OR IGNORE INTO community_decisions (
  id, group_id, request_type, target_id, title, description,
  requested_by, requester_name, total_eligible_voters, threshold_percent,
  votes_for, votes_against, status, expires_at, created_at
)
SELECT
  'dec_sched_' || sp.id,
  sp.group_id,
  'SCHEDULE',
  sp.id,
  'اقتراح تعديل الجدول • الحصة ' || sp.slot_order,
  json_object(
    'text', COALESCE(sp.reason, ''),
    'voters', json((SELECT json_group_array(gm.user_id)
                    FROM group_members gm
                    WHERE gm.group_id = sp.group_id AND gm.status = 'ACTIVE'))
  ),
  sp.proposed_by,
  COALESCE(u.full_name, 'عضو في المجموعة'),
  MAX(1, (SELECT COUNT(*) FROM group_members gm
          WHERE gm.group_id = sp.group_id AND gm.status = 'ACTIVE')),
  60,
  COALESCE(sp.votes_for, 0),
  COALESCE(sp.votes_against, 0),
  CASE
    WHEN UPPER(sp.status) IN ('ACCEPTED', 'APPROVED', 'APPLIED') THEN 'APPLIED'
    WHEN UPPER(sp.status) = 'EXPIRED' THEN 'EXPIRED'
    WHEN UPPER(sp.status) = 'PENDING' THEN 'PENDING'
    ELSE 'REJECTED'
  END,
  COALESCE(sp.created_at, 0) + 172800000,
  COALESCE(sp.created_at, 0)
FROM schedule_proposals sp
LEFT JOIN users u ON u.id = sp.proposed_by
WHERE NOT EXISTS (
  SELECT 1 FROM community_decisions cd
  WHERE cd.request_type IN ('SCHEDULE', 'SCHEDULE_CHANGE')
    AND cd.target_id = sp.id
);

INSERT OR IGNORE INTO community_votes (decision_id, user_id, vote_choice, created_at)
SELECT
  cd.id,
  spv.user_id,
  CASE WHEN UPPER(spv.vote_type) = 'AGAINST' THEN 0 ELSE 1 END,
  spv.created_at
FROM schedule_proposal_votes spv
JOIN community_decisions cd
  ON cd.request_type IN ('SCHEDULE', 'SCHEDULE_CHANGE')
 AND cd.target_id = spv.proposal_id;
