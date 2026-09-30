import { describe, it, expect } from 'vitest';
import {
  validateArabicFullName,
  validateYemenPhoneNumber,
  evaluatePasswordStrength,
  isValidGradeSection,
} from '../src/lib/validation';

describe('Server Validation Suite', () => {
  it('validates 3-part Arabic full name', () => {
    expect(validateArabicFullName('محمد علي أحمد').isValid).toBe(true);
    expect(validateArabicFullName('أحمد').isValid).toBe(false);
    expect(validateArabicFullName('محمد علي Smith').isValid).toBe(false); // Mixed is forbidden
  });

  it('validates Yemen phone numbers with normalization', () => {
    const res1 = validateYemenPhoneNumber('777123456');
    expect(res1.isValid).toBe(true);
    expect(res1.normalized).toBe('+967777123456');

    const res2 = validateYemenPhoneNumber('+967 733 987 654');
    expect(res2.isValid).toBe(true);
    expect(res2.normalized).toBe('+967733987654');

    const res3 = validateYemenPhoneNumber('123456');
    expect(res3.isValid).toBe(false);
  });

  it('evaluates password strength', () => {
    expect(evaluatePasswordStrength('123').isValid).toBe(false);
    expect(evaluatePasswordStrength('Pass@12345').isValid).toBe(true);
  });

  it('validates grade and section combinations', () => {
    expect(isValidGradeSection(10, 'A')).toBe(true);
    expect(isValidGradeSection(11, 'B')).toBe(true);
    expect(isValidGradeSection(99, 'A')).toBe(false);
  });
});
