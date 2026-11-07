import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { handleGetTeacherDashboardData } from '../src/services/governance';

class TeacherDashboardDb {
  prepare(sql: string) {
    return {
      bind: (..._params: unknown[]) => ({
        first: async () => ({ count: 1 }),
        all: async () => {
          if (/FROM homeworks/i.test(sql)) {
            return {
              results: [{
                id: 'hw_official_1',
                group_id: 'class_10_A',
                study_date: '2026-11-02',
                due_date: '2026-11-05',
                subject_id: 'math',
                title: 'واجب رسمي',
                details: 'حل التمارين',
                page_numbers: '12',
                question_numbers: '1-3',
                task_type: 'HOMEWORK',
                media_urls: JSON.stringify(['/api/v1/media/media_1']),
                is_official: 1,
                created_by: 'teacher_1',
                created_at: 1700000000000,
                subject_name: 'الرياضيات',
                subject_icon: '📐',
                color_hex: '#00E5FF',
              }],
            };
          }
          if (/FROM exams/i.test(sql)) {
            return {
              results: [{
                id: 'exam_official_1',
                group_id: 'class_10_A',
                exam_date: '2026-11-10',
                subject_id: 'math',
                title: 'اختبار شهري رسمي',
                required_chapters: 'الفصل الأول',
                notes: null,
                exam_type: 'MONTHLY',
                is_official: 1,
                is_completed: 0,
                created_by: 'teacher_1',
                created_at: 1700000000001,
                subject_name: 'الرياضيات',
                subject_icon: '📐',
                color_hex: '#00E5FF',
              }],
            };
          }
          return { results: [] };
        },
      }),
    };
  }
}

const teacher: UserContext = {
  userId: 'teacher_1',
  phoneNumber: '777123456',
  fullName: 'أستاذ الرياضيات',
  gradeId: 10,
  sectionId: 'A',
  role: 'TEACHER',
};

describe('Teacher Center API contract', () => {
  it('returns official homeworks and exams in Android camelCase models', async () => {
    const env = { DB: new TeacherDashboardDb() } as unknown as Env;
    const response = await handleGetTeacherDashboardData(teacher, env);
    const body = await response.json() as any;

    expect(response.status).toBe(200);
    expect(body.stats.officialHomeworksCount).toBe(1);
    expect(body.stats.officialExamsCount).toBe(1);

    expect(body.recentHomeworks).toHaveLength(1);
    expect(body.recentHomeworks[0]).toMatchObject({
      id: 'hw_official_1',
      groupId: 'class_10_A',
      studyDate: '2026-11-02',
      dueDate: '2026-11-05',
      isOfficial: true,
      mediaUrls: ['/api/v1/media/media_1'],
    });
    expect(body.recentHomeworks[0].group_id).toBeUndefined();

    expect(body.recentExams).toHaveLength(1);
    expect(body.recentExams[0]).toMatchObject({
      id: 'exam_official_1',
      groupId: 'class_10_A',
      examDate: '2026-11-10',
      examType: 'MONTHLY',
      isOfficial: true,
    });
    expect(body.recentExams[0].group_id).toBeUndefined();
  });

  it('keeps the private SYSTEM_OWNER role out of the in-app Teacher Center', async () => {
    const owner = { ...teacher, role: 'SYSTEM_OWNER' } as UserContext;
    const response = await handleGetTeacherDashboardData(owner, {} as Env);
    expect(response.status).toBe(403);
  });
});
