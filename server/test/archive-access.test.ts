import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { Role } from '../src/lib/roles';
import { handleGetAcademicArchive } from '../src/services/content';

class AcademicArchiveDb {
  memberships = [
    { group_id: 'active-group', user_id: 'student-1', status: 'ACTIVE' },
    { group_id: 'banned-group', user_id: 'student-1', status: 'BANNED' },
    { group_id: 'pending-group', user_id: 'student-1', status: 'PENDING' },
  ];

  contents = [
    { id: 'own-content', group_id: 'banned-group', created_by: 'student-1', status: 'PUBLISHED', study_date: '2026-10-01', title: 'محتوى صاحبه' },
    { id: 'active-content', group_id: 'active-group', created_by: 'other-user', status: 'PUBLISHED', study_date: '2026-10-02', title: 'محتوى مجموعة نشطة' },
    { id: 'banned-content', group_id: 'banned-group', created_by: 'other-user', status: 'PUBLISHED', study_date: '2026-10-03', title: 'محتوى مجموعة محظورة' },
    { id: 'pending-content', group_id: 'pending-group', created_by: 'other-user', status: 'PUBLISHED', study_date: '2026-10-04', title: 'محتوى طلب معلق' },
  ];

  executedSql: string[] = [];

  prepare(sql: string) {
    const db = this;
    let params: unknown[] = [];
    const statement = {
      bind(...values: unknown[]) {
        params = values;
        return statement;
      },
      async all<T = unknown>(): Promise<{ results: T[] }> {
        const normalized = sql.trim().replace(/\s+/g, ' ');
        db.executedSql.push(normalized);

        if (normalized.includes('FROM contents c')) {
          const userId = String(params[0]);
          const activeOnly = normalized.includes("AND status = 'ACTIVE'");
          const accessibleGroups = new Set(
            db.memberships
              .filter((membership) =>
                membership.user_id === params[1] &&
                (!activeOnly || membership.status === 'ACTIVE')
              )
              .map((membership) => membership.group_id)
          );

          return {
            results: db.contents.filter((row) =>
              row.status === 'PUBLISHED' &&
              (row.created_by === userId || accessibleGroups.has(row.group_id))
            ) as T[],
          };
        }

        if (normalized.includes('FROM content_media')) {
          return { results: [] as T[] };
        }

        return { results: [] as T[] };
      },
    };
    return statement;
  }
}

const user: UserContext = {
  userId: 'student-1',
  phoneNumber: '777000001',
  fullName: 'طالب تنوير',
  gradeId: 10,
  sectionId: 'A',
  role: Role.STUDENT,
};

describe('Academic archive membership access', () => {
  it('includes authored history and active groups, but excludes banned and pending group content', async () => {
    const db = new AcademicArchiveDb();
    const env = { DB: db as any } as Env;

    const response = await handleGetAcademicArchive(user, env);
    expect(response.status).toBe(200);

    const data = await response.json() as any;
    const ids = data.contents.map((item: any) => item.id);

    expect(ids).toContain('own-content');
    expect(ids).toContain('active-content');
    expect(ids).not.toContain('banned-content');
    expect(ids).not.toContain('pending-content');
  });

  it('requires ACTIVE status in the group-membership subquery', async () => {
    const db = new AcademicArchiveDb();
    await handleGetAcademicArchive(user, { DB: db as any } as Env);

    const archiveQuery = db.executedSql.find((sql) => sql.includes('FROM contents c'));
    expect(archiveQuery).toContain("AND status = 'ACTIVE'");
  });
});
