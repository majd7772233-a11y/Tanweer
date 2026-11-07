import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { handleCreateHomework } from '../src/services/homework';

type Row = Record<string, any>;

class HomeworkStatement {
  constructor(
    private readonly db: HomeworkDb,
    private readonly sql: string,
    private readonly params: any[] = []
  ) {}

  bind(...params: any[]) {
    return new HomeworkStatement(this.db, this.sql, params);
  }

  async first<T = unknown>(): Promise<T | null> {
    const sql = this.sql.replace(/\\s+/g, ' ').trim();

    if (/FROM group_members/i.test(sql)) {
      const [groupId, userId] = this.params;
      const member = this.db.tables.group_members.find(row =>
        row.group_id === groupId && row.user_id === userId && row.status === 'ACTIVE'
      );
      return (member ? { role: member.role, status: member.status } as any : null) as T | null;
    }

    if (/FROM media_blobs/i.test(sql)) {
      const blob = this.db.tables.media_blobs.find(row => row.id === this.params[0]);
      return (blob || null) as T | null;
    }

    if (/FROM homeworks WHERE id = \\? LIMIT 1/i.test(sql)) {
      return (this.db.tables.homeworks.find(row => row.id === this.params[0]) || null) as T | null;
    }

    return null;
  }

  async all<T = unknown>(): Promise<{ results: T[] }> {
    const sql = this.sql.replace(/\\s+/g, ' ').trim();
    if (/FROM homework_media WHERE homework_id = \\?/i.test(sql)) {
      const rows = this.db.tables.homework_media
        .filter(row => row.homework_id === this.params[0])
        .sort((a, b) => a.page_order - b.page_order);
      return { results: rows as T[] };
    }
    return { results: [] };
  }

  async run(): Promise<{ success: boolean; meta: { changes: number } }> {
    const sql = this.sql.replace(/\\s+/g, ' ').trim();

    if (/^INSERT OR IGNORE INTO homeworks/i.test(sql)) {
      const [
        id, group_id, study_date, due_date, subject_id, title, details,
        page_numbers, question_numbers, task_type, media_urls, is_official,
        created_by, created_at,
      ] = this.params;

      if (this.db.tables.homeworks.some(row => row.id === id)) {
        return { success: true, meta: { changes: 0 } };
      }
      this.db.tables.homeworks.push({
        id, group_id, study_date, due_date, subject_id, title, details,
        page_numbers, question_numbers, task_type, media_urls, is_official,
        created_by, created_at,
      });
      return { success: true, meta: { changes: 1 } };
    }

    if (/^INSERT OR IGNORE INTO homework_media/i.test(sql)) {
      const [
        id, homework_id, media_id, url, page_order, created_at,
        guardHomeworkId, guardGroupId, guardCreatorId, pageHomeworkId, guardedPageOrder,
      ] = this.params;

      const parent = this.db.tables.homeworks.find(row =>
        row.id === guardHomeworkId &&
        row.id === homework_id &&
        row.group_id === guardGroupId &&
        row.created_by === guardCreatorId
      );
      if (!parent || pageHomeworkId !== homework_id || guardedPageOrder !== page_order) {
        return { success: true, meta: { changes: 0 } };
      }

      if (this.db.tables.homework_media.some(row =>
        row.homework_id === homework_id && row.page_order === page_order
      )) {
        return { success: true, meta: { changes: 0 } };
      }

      this.db.mediaInsertAttempts += 1;
      if (this.db.failOnMediaInsertAt === this.db.mediaInsertAttempts) {
        throw new Error('simulated homework_media insert failure');
      }

      this.db.tables.homework_media.push({
        id, homework_id, media_id, url, page_order, created_at,
      });
      return { success: true, meta: { changes: 1 } };
    }

    return { success: true, meta: { changes: 0 } };
  }
}

class HomeworkDb {
  tables: Record<string, Row[]> = {
    group_members: [
      { group_id: 'class_10_A', user_id: 'student_author', role: 'STUDENT', status: 'ACTIVE' },
    ],
    media_blobs: [
      { id: 'med_blob_1', uploaded_by: 'student_author' },
      { id: 'med_blob_2', uploaded_by: 'student_author' },
    ],
    homeworks: [],
    homework_media: [],
  };

  failOnMediaInsertAt = 0;
  mediaInsertAttempts = 0;

  prepare(sql: string) {
    return new HomeworkStatement(this, sql);
  }

  async batch(statements: HomeworkStatement[]) {
    const snapshot = Object.fromEntries(
      Object.entries(this.tables).map(([name, rows]) => [name, rows.map(row => ({ ...row }))])
    );
    try {
      const results = [];
      for (const statement of statements) {
        results.push(await statement.run());
      }
      return results;
    } catch (error) {
      this.tables = snapshot;
      throw error;
    }
  }
}

const author: UserContext = {
  userId: 'student_author',
  phoneNumber: '777123456',
  fullName: 'صاحب الواجب',
  gradeId: 10,
  sectionId: 'A',
  role: 'STUDENT',
};

const mediaUrls = [
  'https://tanweer-staging.magd.workers.dev/api/v1/media/med_blob_1',
  'https://tanweer-staging.magd.workers.dev/api/v1/media/med_blob_2',
];

function createRequest(overrides: Record<string, unknown> = {}) {
  return new Request('https://tanweer-staging.magd.workers.dev/api/v1/homeworks', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      id: 'homework_atomic_retry',
      groupId: 'class_10_A',
      studyDate: '2026-11-06',
      dueDate: '2026-11-10',
      subjectId: 'math',
      title: 'واجب مع صفحتين',
      details: 'حل الصفحتين المرفقتين',
      pageNumbers: '12-13',
      questionNumbers: '1-8',
      taskType: 'HOMEWORK',
      mediaUrls,
      isOfficial: false,
      ...overrides,
    }),
  });
}

const envFor = (db: HomeworkDb) => ({ DB: db as any } as Env);

describe('Atomic homework media publication', () => {
  it('rolls back the parent and every page if one media row fails, then allows the same-ID retry', async () => {
    const db = new HomeworkDb();
    db.failOnMediaInsertAt = 2;

    const failed = await handleCreateHomework(author, createRequest(), envFor(db));
    expect(failed.status).toBe(500);
    expect(db.tables.homeworks).toHaveLength(0);
    expect(db.tables.homework_media).toHaveLength(0);

    db.failOnMediaInsertAt = 0;
    db.mediaInsertAttempts = 0;
    const retried = await handleCreateHomework(author, createRequest(), envFor(db));
    const body = await retried.json() as any;

    expect(retried.status).toBe(200);
    expect(body.success).toBe(true);
    expect(db.tables.homeworks).toHaveLength(1);
    expect(db.tables.homework_media).toHaveLength(2);
    expect(db.tables.homework_media.map(row => row.page_order)).toEqual([1, 2]);
  });

  it('repairs missing pages on a legacy partial homework without duplicating existing rows', async () => {
    const db = new HomeworkDb();
    db.tables.homeworks.push({
      id: 'homework_atomic_retry',
      group_id: 'class_10_A',
      study_date: '2026-11-06',
      due_date: '2026-11-10',
      subject_id: 'math',
      title: 'واجب مع صفحتين',
      details: 'حل الصفحتين المرفقتين',
      page_numbers: '12-13',
      question_numbers: '1-8',
      task_type: 'HOMEWORK',
      media_urls: JSON.stringify(mediaUrls),
      is_official: 0,
      created_by: 'student_author',
      created_at: 1762387200000,
    });
    db.tables.homework_media.push({
      id: 'legacy_hwm_page_1',
      homework_id: 'homework_atomic_retry',
      media_id: 'med_blob_1',
      url: mediaUrls[0],
      page_order: 1,
      created_at: 1762387200000,
    });

    const response = await handleCreateHomework(author, createRequest(), envFor(db));
    const body = await response.json() as any;

    expect(response.status).toBe(200);
    expect(body.success).toBe(true);
    expect(db.tables.homeworks).toHaveLength(1);
    expect(db.tables.homework_media).toHaveLength(2);
    expect(db.tables.homework_media.filter(row => row.page_order === 1)).toHaveLength(1);
    expect(db.tables.homework_media.map(row => row.page_order)).toEqual([1, 2]);
  });

  it('rejects same-ID retries whose homework metadata differs without mutating saved data', async () => {
    const db = new HomeworkDb();
    const first = await handleCreateHomework(author, createRequest(), envFor(db));
    expect(first.status).toBe(200);

    const beforeHomework = db.tables.homeworks.map(row => ({ ...row }));
    const beforeMedia = db.tables.homework_media.map(row => ({ ...row }));
    const conflict = await handleCreateHomework(
      author,
      createRequest({ title: 'واجب مختلف تحت المعرّف نفسه' }),
      envFor(db)
    );

    expect(conflict.status).toBe(409);
    expect(db.tables.homeworks).toEqual(beforeHomework);
    expect(db.tables.homework_media).toEqual(beforeMedia);
  });

  it('rejects duplicated page-order corruption without hiding it as success', async () => {
    const db = new HomeworkDb();
    db.tables.homeworks.push({
      id: 'homework_atomic_retry',
      group_id: 'class_10_A',
      study_date: '2026-11-06',
      due_date: '2026-11-10',
      subject_id: 'math',
      title: 'واجب مع صفحتين',
      details: 'حل الصفحتين المرفقتين',
      page_numbers: '12-13',
      question_numbers: '1-8',
      task_type: 'HOMEWORK',
      media_urls: JSON.stringify(mediaUrls),
      is_official: 0,
      created_by: 'student_author',
      created_at: 1762387200000,
    });
    db.tables.homework_media.push(
      { id: 'legacy_one', homework_id: 'homework_atomic_retry', media_id: 'med_blob_1', url: mediaUrls[0], page_order: 1, created_at: 1 },
      { id: 'legacy_two', homework_id: 'homework_atomic_retry', media_id: 'med_blob_1', url: mediaUrls[0], page_order: 1, created_at: 2 },
    );

    const response = await handleCreateHomework(author, createRequest(), envFor(db));
    expect(response.status).toBe(409);
    expect(db.tables.homework_media).toHaveLength(2);
  });
});
