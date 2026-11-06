import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { handleCreateContent, handleGetContents } from '../src/services/content';

type Row = Record<string, any>;

class ContentMediaStatement {
  constructor(
    private readonly db: ContentMediaDb,
    private readonly sql: string,
    private readonly params: any[] = []
  ) {}

  bind(...params: any[]) {
    return new ContentMediaStatement(this.db, this.sql, params);
  }

  async first<T = unknown>(): Promise<T | null> {
    const sql = this.sql.replace(/\s+/g, ' ').trim();

    if (/FROM group_members/i.test(sql)) {
      const [groupId, userId] = this.params;
      const member = this.db.tables.group_members.find(
        row => row.group_id === groupId && row.user_id === userId && row.status === 'ACTIVE'
      );
      return (member ? ({ 1: 1 } as any) : null) as T | null;
    }

    if (/FROM media_blobs/i.test(sql)) {
      const blob = this.db.tables.media_blobs.find(row => row.id === this.params[0]);
      return (blob || null) as T | null;
    }

    if (/FROM content_media cm\s+JOIN contents/i.test(sql)) return null;

    if (/FROM contents WHERE id = \?/i.test(sql)) {
      return (this.db.tables.contents.find(row => row.id === this.params[0]) || null) as T | null;
    }

    return null;
  }

  async all<T = unknown>(): Promise<{ results: T[] }> {
    const sql = this.sql.replace(/\s+/g, ' ').trim();

    if (/FROM contents c\s+LEFT JOIN subjects/i.test(sql)) {
      const groupId = this.params[0];
      const rows = this.db.tables.contents
        .filter(row => row.group_id === groupId && row.status === 'PUBLISHED')
        .map(row => {
          const subject = this.db.tables.subjects.find(item => item.id === row.subject_id);
          return {
            ...row,
            subject_name: subject?.name_ar ?? row.subject_id,
            subject_icon: subject?.icon ?? '📚',
            color_hex: subject?.color_hex ?? '#00E5FF',
          };
        });
      return { results: rows as T[] };
    }

    if (/FROM content_media WHERE content_id = \?/i.test(sql)) {
      const contentId = this.params[0];
      const rows = this.db.tables.content_media
        .filter(row => row.content_id === contentId)
        .sort((a, b) => a.page_order - b.page_order);
      return { results: rows as T[] };
    }

    return { results: [] };
  }

  async run(): Promise<{ success: boolean; meta: { changes: number } }> {
    const sql = this.sql.replace(/\s+/g, ' ').trim();
    const match = sql.match(/^INSERT INTO (\w+)/i);
    if (!match) return { success: true, meta: { changes: 0 } };

    const table = match[1];
    if (table === 'content_media' && this.db.failContentMediaInsert) {
      throw new Error('simulated content_media insert failure');
    }
    const columns = sql.match(/\((.*?)\)\s*VALUES/i)?.[1]
      ?.split(',')
      .map(column => column.replace(/["`\s]/g, '')) ?? [];
    const row: Row = {};
    columns.forEach((column, index) => { row[column] = this.params[index]; });
    this.db.tables[table] ||= [];
    this.db.tables[table].push(row);
    return { success: true, meta: { changes: 1 } };
  }
}

class ContentMediaDb {
  tables: Record<string, Row[]> = {
    group_members: [],
    media_blobs: [],
    contents: [],
    content_media: [],
    subjects: [{ id: 'math', name_ar: 'الرياضيات', icon: '📐', color_hex: '#00E5FF' }],
  };
  failContentMediaInsert = false;

  prepare(sql: string) {
    return new ContentMediaStatement(this, sql);
  }

  async batch(statements: ContentMediaStatement[]) {
    const snapshot = Object.fromEntries(
      Object.entries(this.tables).map(([name, rows]) => [name, [...rows]])
    );
    try {
      const results = [];
      for (const statement of statements) results.push(await statement.run());
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
  fullName: 'صاحب الدرس',
  gradeId: 10,
  sectionId: 'A',
  role: 'STUDENT',
};

const secondDeviceUser: UserContext = {
  userId: 'student_second_phone',
  phoneNumber: '777654321',
  fullName: 'طالب الهاتف الثاني',
  gradeId: 10,
  sectionId: 'A',
  role: 'STUDENT',
};

describe('Lesson media cross-device contract', () => {
  it('rolls back the lesson when any media row fails and permits a clean retry', async () => {
    const db = new ContentMediaDb();
    db.tables.group_members.push(
      { group_id: 'class_10_A', user_id: author.userId, status: 'ACTIVE' }
    );
    db.tables.media_blobs.push(
      { id: 'med_blob_1', uploaded_by: author.userId, mime_type: 'image/jpeg', file_size: 1024 },
      { id: 'med_blob_2', uploaded_by: author.userId, mime_type: 'image/jpeg', file_size: 2048 },
    );
    db.failContentMediaInsert = true;

    const makeRequest = () => new Request('https://tanweer-staging.magd.workers.dev/api/v1/contents', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        id: 'content_atomic_retry',
        groupId: 'class_10_A',
        studyDate: '2026-11-02',
        subjectId: 'math',
        title: 'درس ذري مع مرفقين',
        media: [
          {
            pageOrder: 1,
            url: 'https://tanweer-staging.magd.workers.dev/api/v1/media/med_blob_1',
            mimeType: 'image/jpeg',
            fileSize: 1024,
            checksum: 'sha256:atomic_page_1',
          },
          {
            pageOrder: 2,
            url: 'https://tanweer-staging.magd.workers.dev/api/v1/media/med_blob_2',
            mimeType: 'image/jpeg',
            fileSize: 2048,
            checksum: 'sha256:atomic_page_2',
          },
        ],
      }),
    });

    const failedAttempt = await handleCreateContent(author, makeRequest(), { DB: db as any } as Env);
    expect(failedAttempt.status).toBe(500);
    expect(db.tables.contents).toHaveLength(0);
    expect(db.tables.content_media).toHaveLength(0);

    db.failContentMediaInsert = false;
    const retried = await handleCreateContent(author, makeRequest(), { DB: db as any } as Env);
    expect(retried.status).toBe(200);
    expect(db.tables.contents).toHaveLength(1);
    expect(db.tables.content_media).toHaveLength(2);
  });

  it('returns uploaded lesson pages to another active member of the same group', async () => {
    const db = new ContentMediaDb();
    db.tables.group_members.push(
      { group_id: 'class_10_A', user_id: author.userId, status: 'ACTIVE' },
      { group_id: 'class_10_A', user_id: secondDeviceUser.userId, status: 'ACTIVE' },
    );
    db.tables.media_blobs.push({
      id: 'med_blob_1',
      uploaded_by: author.userId,
      mime_type: 'image/jpeg',
      file_size: 1024,
    });

    const env = { DB: db } as unknown as Env;
    const uploadLessonRequest = new Request('https://tanweer-staging.magd.workers.dev/api/v1/contents', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        id: 'content_media_roundtrip',
        groupId: 'class_10_A',
        studyDate: '2026-11-02',
        subjectId: 'math',
        title: 'درس مع صور',
        media: [{
          id: 'local_media_1',
          pageOrder: 1,
          url: 'https://tanweer-staging.magd.workers.dev/api/v1/media/med_blob_1',
          mimeType: 'image/jpeg',
          fileSize: 1024,
          checksum: 'sha256:lesson_page_1',
        }],
      }),
    });

    const createResponse = await handleCreateContent(author, uploadLessonRequest, env);
    expect(createResponse.status).toBe(200);
    expect(db.tables.content_media).toHaveLength(1);

    const listResponse = await handleGetContents('class_10_A', secondDeviceUser, env);
    expect(listResponse.status).toBe(200);
    const body = await listResponse.json() as any;

    expect(body.contents).toHaveLength(1);
    expect(body.contents[0].media).toHaveLength(1);
    expect(body.contents[0].media[0]).toMatchObject({
      pageOrder: 1,
      url: 'https://tanweer-staging.magd.workers.dev/api/v1/media/med_blob_1',
      mimeType: 'image/jpeg',
      fileSize: 1024,
      checksum: 'sha256:lesson_page_1',
    });
  });
});
