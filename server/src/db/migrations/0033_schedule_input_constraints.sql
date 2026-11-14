-- 0033_schedule_input_constraints.sql
-- Guard schedule positions and proposal reasons at the database boundary as well
-- as in the Worker. Existing rows are preserved; these triggers apply to new writes.

DROP TRIGGER IF EXISTS trg_schedule_slots_validate_insert;
CREATE TRIGGER trg_schedule_slots_validate_insert
BEFORE INSERT ON schedule_slots
WHEN typeof(NEW.day_of_week) <> 'integer'
  OR NEW.day_of_week NOT BETWEEN 0 AND 6
  OR typeof(NEW.slot_order) <> 'integer'
  OR NEW.slot_order NOT BETWEEN 1 AND 12
BEGIN
  SELECT RAISE(ABORT, 'SCHEDULE_SLOT_POSITION_INVALID');
END;

DROP TRIGGER IF EXISTS trg_schedule_slots_validate_update;
CREATE TRIGGER trg_schedule_slots_validate_update
BEFORE UPDATE OF day_of_week, slot_order ON schedule_slots
WHEN typeof(NEW.day_of_week) <> 'integer'
  OR NEW.day_of_week NOT BETWEEN 0 AND 6
  OR typeof(NEW.slot_order) <> 'integer'
  OR NEW.slot_order NOT BETWEEN 1 AND 12
BEGIN
  SELECT RAISE(ABORT, 'SCHEDULE_SLOT_POSITION_INVALID');
END;

DROP TRIGGER IF EXISTS trg_schedule_proposals_validate_insert;
CREATE TRIGGER trg_schedule_proposals_validate_insert
BEFORE INSERT ON schedule_proposals
WHEN typeof(NEW.day_of_week) <> 'integer'
  OR NEW.day_of_week NOT BETWEEN 0 AND 6
  OR typeof(NEW.slot_order) <> 'integer'
  OR NEW.slot_order NOT BETWEEN 1 AND 12
  OR length(trim(COALESCE(NEW.new_subject_id, ''))) = 0
  OR length(NEW.new_subject_id) > 128
  OR length(trim(COALESCE(NEW.reason, ''))) = 0
  OR length(trim(NEW.reason)) > 500
BEGIN
  SELECT RAISE(ABORT, 'SCHEDULE_PROPOSAL_INPUT_INVALID');
END;

DROP TRIGGER IF EXISTS trg_schedule_proposals_validate_update;
CREATE TRIGGER trg_schedule_proposals_validate_update
BEFORE UPDATE OF day_of_week, slot_order, new_subject_id, reason ON schedule_proposals
WHEN typeof(NEW.day_of_week) <> 'integer'
  OR NEW.day_of_week NOT BETWEEN 0 AND 6
  OR typeof(NEW.slot_order) <> 'integer'
  OR NEW.slot_order NOT BETWEEN 1 AND 12
  OR length(trim(COALESCE(NEW.new_subject_id, ''))) = 0
  OR length(NEW.new_subject_id) > 128
  OR length(trim(COALESCE(NEW.reason, ''))) = 0
  OR length(trim(NEW.reason)) > 500
BEGIN
  SELECT RAISE(ABORT, 'SCHEDULE_PROPOSAL_INPUT_INVALID');
END;
