import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { handleCreateExam, SUPPORTED_EXAM_TYPES } from '../src/services/exams';

const student: UserContext = {
  userId: 'student_1',
  phoneNumber: '777123456',
  fullName: 'طالب الاختبار',
  gradeId: 10,
  sectionId: 'A',
  role: 'STUDENT',
};

describe('Supported school exam categories', () => {
  it('keeps exactly the four categories used by the school', () => {
    expect(SUPPORTED_EXAM_TYPES).toEqual(['MONTHLY', 'ORAL', 'MIDTERM', 'FINAL']);
  });

  it('rejects retired QUIZ records at the API boundary', async () => {
    const request = new Request('https://tanweer-staging.magd.workers.dev/api/v1/exams', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        groupId: 'class_10_A',
        examDate: '2026-11-10',
        subjectId: 'math',
        title: 'محاولة إنشاء كويز',
        examType: 'QUIZ',
      }),
    });

    // Invalid exam types are rejected before the database is accessed.
    const response = await handleCreateExam(student, request, {} as Env);
    const body = await response.json() as any;

    expect(response.status).toBe(400);
    expect(body.error?.code).toBe('INVALID_EXAM_TYPE');
  });
});
