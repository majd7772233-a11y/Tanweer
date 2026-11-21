import { describe, expect, it } from 'vitest';
import { resolveAdminClassAssignment } from '../src/services/dashboard';

describe('School ADMIN academic-scope restoration policy', () => {
  const saved = { grade_id: 10, section_id: 'c' };

  it('restores the saved academic scope when the ADMIN role is removed without an override', () => {
    expect(resolveAdminClassAssignment(saved, undefined, undefined)).toEqual({
      assignment: {
        gradeId: 10,
        sectionId: 'C',
        restoreSavedMemberships: true,
      },
    });
  });

  it('accepts an explicit replacement class for legacy ADMIN accounts without a restore point', () => {
    expect(resolveAdminClassAssignment(null, 11, 'b')).toEqual({
      assignment: {
        gradeId: 11,
        sectionId: 'B',
        restoreSavedMemberships: false,
      },
    });
  });

  it('restores original memberships when an explicit assignment matches the saved class', () => {
    expect(resolveAdminClassAssignment(saved, 10, ' C ')).toEqual({
      assignment: {
        gradeId: 10,
        sectionId: 'C',
        restoreSavedMemberships: true,
      },
    });
  });

  it('uses a new class instead of restoring memberships when the explicit class differs', () => {
    expect(resolveAdminClassAssignment(saved, 12, 'a')).toEqual({
      assignment: {
        gradeId: 12,
        sectionId: 'A',
        restoreSavedMemberships: false,
      },
    });
  });

  it('requires an explicit class when no valid prior assignment exists', () => {
    expect(resolveAdminClassAssignment(null, undefined, undefined)).toEqual({
      error: 'ADMIN_CLASS_RESTORE_UNAVAILABLE',
    });
    expect(resolveAdminClassAssignment({ grade_id: 0, section_id: '' }, undefined, undefined)).toEqual({
      error: 'ADMIN_CLASS_RESTORE_UNAVAILABLE',
    });
  });

  it('rejects partial, non-integer, non-positive, and incorrectly typed class input safely', () => {
    expect(resolveAdminClassAssignment(saved, 11, undefined)).toEqual({
      error: 'INVALID_CLASS_ASSIGNMENT',
    });
    expect(resolveAdminClassAssignment(saved, undefined, 'A')).toEqual({
      error: 'INVALID_CLASS_ASSIGNMENT',
    });
    expect(resolveAdminClassAssignment(saved, 10.5, 'A')).toEqual({
      error: 'INVALID_CLASS_ASSIGNMENT',
    });
    expect(resolveAdminClassAssignment(saved, 0, 'A')).toEqual({
      error: 'INVALID_CLASS_ASSIGNMENT',
    });
    expect(resolveAdminClassAssignment(saved, 11, 123)).toEqual({
      error: 'INVALID_CLASS_ASSIGNMENT',
    });
  });
});