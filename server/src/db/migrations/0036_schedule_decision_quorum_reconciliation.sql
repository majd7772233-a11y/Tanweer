-- 0036: Reconcile migrated legacy schedule approvals with recorded eligible votes.
-- Migration 0029 mapped old ACCEPTED/APPROVED/APPLIED states to APPLIED without
-- proving the current quorum. This never edits timetable slots or versions.
-- Unprovable imported approvals are surfaced for human review; votes are never fabricated.

UPDATE community_decisions
SET
  votes_for = (
    SELECT COUNT(*) FROM community_votes cv
    WHERE cv.decision_id = community_decisions.id AND cv.vote_choice = 1
      AND cv.user_id IN (
        SELECT CAST(voter.value AS TEXT)
        FROM json_each(community_decisions.description, '$.voters') AS voter
      )
  ),
  votes_against = (
    SELECT COUNT(*) FROM community_votes cv
    WHERE cv.decision_id = community_decisions.id AND cv.vote_choice = 0
      AND cv.user_id IN (
        SELECT CAST(voter.value AS TEXT)
        FROM json_each(community_decisions.description, '$.voters') AS voter
      )
  )
WHERE substr(id, 1, 10) = 'dec_sched_'
  AND request_type IN ('SCHEDULE', 'SCHEDULE_CHANGE');

UPDATE community_decisions
SET status = 'REVIEW_REQUIRED'
WHERE substr(id, 1, 10) = 'dec_sched_'
  AND request_type IN ('SCHEDULE', 'SCHEDULE_CHANGE')
  AND status = 'APPLIED'
  AND (
    votes_for < CASE WHEN total_eligible_voters > 1
      THEN MAX(2, (total_eligible_voters * threshold_percent + 99) / 100)
      ELSE 1 END
    OR votes_for <= votes_against
  );

UPDATE schedule_proposals
SET
  status = 'REVIEW_REQUIRED',
  votes_for = (
    SELECT cd.votes_for FROM community_decisions cd
    WHERE cd.target_id = schedule_proposals.id AND cd.group_id = schedule_proposals.group_id
      AND cd.request_type IN ('SCHEDULE', 'SCHEDULE_CHANGE')
      AND substr(cd.id, 1, 10) = 'dec_sched_' AND cd.status = 'REVIEW_REQUIRED'
    ORDER BY cd.created_at DESC LIMIT 1
  ),
  votes_against = (
    SELECT cd.votes_against FROM community_decisions cd
    WHERE cd.target_id = schedule_proposals.id AND cd.group_id = schedule_proposals.group_id
      AND cd.request_type IN ('SCHEDULE', 'SCHEDULE_CHANGE')
      AND substr(cd.id, 1, 10) = 'dec_sched_' AND cd.status = 'REVIEW_REQUIRED'
    ORDER BY cd.created_at DESC LIMIT 1
  )
WHERE EXISTS (
  SELECT 1 FROM community_decisions cd
  WHERE cd.target_id = schedule_proposals.id AND cd.group_id = schedule_proposals.group_id
    AND cd.request_type IN ('SCHEDULE', 'SCHEDULE_CHANGE')
    AND substr(cd.id, 1, 10) = 'dec_sched_' AND cd.status = 'REVIEW_REQUIRED'
);
