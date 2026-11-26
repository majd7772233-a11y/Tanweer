import { describe, expect, it } from 'vitest';
import {
  MAX_SCHEDULE_PROPOSAL_REASON_LENGTH,
  isValidSchedulePosition,
  parseCommunityVoteChoice,
  parseScheduleVoteType,
  validateScheduleProposalInput,
  validateScheduleSlotInput,
} from '../src/services/scheduleValidation';

describe('schedule input contract', () => {
  it('accepts only integer day and slot positions in the supported grid', () => {
    expect(isValidSchedulePosition(0, 1)).toBe(true);
    expect(isValidSchedulePosition(6, 12)).toBe(true);
    for (const [day, slot] of [[-1, 1], [7, 1], [1.5, 1], ['1', 1], [1, 0], [1, 13], [1, 1.5], [1, '2']]) {
      expect(isValidSchedulePosition(day, slot)).toBe(false);
    }
  });

  it('rejects malformed and unsafe single-slot payload fields', () => {
    expect(validateScheduleSlotInput({ dayOfWeek: 1, slotOrder: 2, subjectId: 'math' }).valid).toBe(true);
    expect(validateScheduleSlotInput({ dayOfWeek: 1.25, slotOrder: 2, subjectId: 'math' }).valid).toBe(false);
    expect(validateScheduleSlotInput({ dayOfWeek: 1, slotOrder: 2.5, subjectId: 'math' }).valid).toBe(false);
    expect(validateScheduleSlotInput({ dayOfWeek: 1, slotOrder: 2, subjectId: '  ' }).valid).toBe(false);
    expect(validateScheduleSlotInput({ dayOfWeek: 1, slotOrder: 2, subjectId: 'math', startTime: 800 })).toMatchObject({ valid: false });
    expect(validateScheduleSlotInput({ dayOfWeek: 1, slotOrder: 2, subjectId: 'math', subjectName: {} })).toMatchObject({ valid: false });
    expect(validateScheduleSlotInput(null)).toMatchObject({ valid: false });
  });

  it('requires a valid proposal scope, integer position, subject and bounded reason', () => {
    const valid = { groupId: 'class_10_A', dayOfWeek: 6, slotOrder: 12, newSubjectId: 'math', reason: 'تعارض في الحصص' };
    expect(validateScheduleProposalInput(valid)).toMatchObject({ valid: true });
    expect(validateScheduleProposalInput({ ...valid, dayOfWeek: 6.5 })).toMatchObject({ valid: false });
    expect(validateScheduleProposalInput({ ...valid, slotOrder: 13 })).toMatchObject({ valid: false });
    expect(validateScheduleProposalInput({ ...valid, groupId: ' ' })).toMatchObject({ valid: false });
    expect(validateScheduleProposalInput({ ...valid, reason: ' '.repeat(3) })).toMatchObject({ valid: false });
    expect(validateScheduleProposalInput({ ...valid, reason: 'ر'.repeat(MAX_SCHEDULE_PROPOSAL_REASON_LENGTH + 1) })).toMatchObject({ valid: false });
    expect(validateScheduleProposalInput({ ...valid, newSubjectId: {} })).toMatchObject({ valid: false });
  });

  it('never coerces unknown schedule vote types into affirmative votes', () => {
    expect(parseScheduleVoteType('FOR')).toBe(1);
    expect(parseScheduleVoteType('AGAINST')).toBe(0);
    for (const value of [undefined, null, '', 'for', 'YES', 'ABSTAIN', 1, 0, {}]) {
      expect(parseScheduleVoteType(value)).toBeNull();
    }
  });

  it('accepts only exact numeric decision choices', () => {
    expect(parseCommunityVoteChoice(1)).toBe(1);
    expect(parseCommunityVoteChoice(0)).toBe(0);
    for (const value of [undefined, null, '1', '0', -1, 2, true, {}]) {
      expect(parseCommunityVoteChoice(value)).toBeNull();
    }
  });
});
