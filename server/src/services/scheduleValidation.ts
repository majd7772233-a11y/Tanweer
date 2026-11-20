export const MAX_SCHEDULE_PROPOSAL_REASON_LENGTH = 500;
export const MAX_SCHEDULE_SUBJECT_ID_LENGTH = 128;

export type ScheduleValidationResult<T> =
  | { valid: true; value: T }
  | { valid: false; code: 'INVALID_INPUT'; message: string };

export interface ValidatedScheduleSlot {
  dayOfWeek: number;
  slotOrder: number;
  subjectId: string;
  subjectName?: string;
  subjectIcon?: string;
  colorHex?: string;
  startTime?: string | null;
  endTime?: string | null;
}

export interface ValidatedScheduleProposal {
  groupId: string;
  dayOfWeek: number;
  slotOrder: number;
  newSubjectId: string;
  reason: string;
}

export function isValidSchedulePosition(dayOfWeek: unknown, slotOrder: unknown): boolean {
  return typeof dayOfWeek === 'number' &&
    Number.isInteger(dayOfWeek) &&
    dayOfWeek >= 0 &&
    dayOfWeek <= 6 &&
    typeof slotOrder === 'number' &&
    Number.isInteger(slotOrder) &&
    slotOrder >= 1 &&
    slotOrder <= 12;
}

function isOptionalString(value: unknown, maxLength: number): boolean {
  return value === undefined || value === null ||
    (typeof value === 'string' && value.length <= maxLength);
}

export function validateScheduleSlotInput(value: unknown): ScheduleValidationResult<ValidatedScheduleSlot> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return { valid: false, code: 'INVALID_INPUT', message: 'صيغة بيانات الحصة غير صالحة.' };
  }
  const body = value as Record<string, unknown>;
  const subjectId = typeof body.subjectId === 'string' ? body.subjectId.trim() : '';
  if (!isValidSchedulePosition(body.dayOfWeek, body.slotOrder) ||
      !subjectId ||
      subjectId.length > MAX_SCHEDULE_SUBJECT_ID_LENGTH ||
      !isOptionalString(body.subjectName, 128) ||
      !isOptionalString(body.subjectIcon, 32) ||
      !isOptionalString(body.colorHex, 32) ||
      !isOptionalString(body.startTime, 32) ||
      !isOptionalString(body.endTime, 32)) {
    return {
      valid: false,
      code: 'INVALID_INPUT',
      message: 'بيانات الحصة غير صالحة؛ اليوم يجب أن يكون عدداً صحيحاً بين 0 و6، والحصة بين 1 و12، والمادة مطلوبة.',
    };
  }

  return {
    valid: true,
    value: {
      dayOfWeek: body.dayOfWeek as number,
      slotOrder: body.slotOrder as number,
      subjectId,
      subjectName: body.subjectName as string | undefined,
      subjectIcon: body.subjectIcon as string | undefined,
      colorHex: body.colorHex as string | undefined,
      startTime: body.startTime as string | null | undefined,
      endTime: body.endTime as string | null | undefined,
    },
  };
}

export function validateScheduleProposalInput(value: unknown): ScheduleValidationResult<ValidatedScheduleProposal> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return { valid: false, code: 'INVALID_INPUT', message: 'صيغة مقترح الجدول غير صالحة.' };
  }
  const body = value as Record<string, unknown>;
  const groupId = typeof body.groupId === 'string' ? body.groupId.trim() : '';
  const newSubjectId = typeof body.newSubjectId === 'string' ? body.newSubjectId.trim() : '';
  const reason = typeof body.reason === 'string' ? body.reason.trim() : '';
  if (!groupId || groupId.length > 128 ||
      !isValidSchedulePosition(body.dayOfWeek, body.slotOrder) ||
      !newSubjectId || newSubjectId.length > MAX_SCHEDULE_SUBJECT_ID_LENGTH ||
      !reason || Array.from(reason).length > MAX_SCHEDULE_PROPOSAL_REASON_LENGTH) {
    return {
      valid: false,
      code: 'INVALID_INPUT',
      message: 'بيانات مقترح الجدول غير صالحة؛ تحقق من اليوم والحصة والمادة والسبب (حتى 500 حرف).',
    };
  }
  return {
    valid: true,
    value: {
      groupId,
      dayOfWeek: body.dayOfWeek as number,
      slotOrder: body.slotOrder as number,
      newSubjectId,
      reason,
    },
  };
}

export function parseScheduleVoteType(value: unknown): 1 | 0 | null {
  if (value === 'FOR') return 1;
  if (value === 'AGAINST') return 0;
  return null;
}

export function parseCommunityVoteChoice(value: unknown): 1 | 0 | null {
  if (value === 1) return 1;
  if (value === 0) return 0;
  return null;
}
