import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { Role } from '../src/lib/roles';
import { handleUploadMedia, validateMediaAttachmentAccess } from '../src/services/media';
import { handleCreateContent } from '../src/services/content';
import { handleCreateHomework } from '../src/services/homework';

type BlobRecord = { id: string; uploaded_by: string | null };
type MediaLink = { media_id?: string; url?: string; object_key?: string; group_id: string };

class MediaAccessDb {
  blobs: BlobRecord[] = [];
  contentLinks: MediaLink[] = [];
  homeworkLinks: MediaLink[] = [];
  legacyHomeworkLinks: Array<{ group_id: string; media_urls: string }> = [];
  memberships = new Set<string>();
  writes: string[] = [];

  prepare(sql: string) {
    const db = this;
    let params: unknown[] = [];
    const statement = {
      bind(...values: unknown[]) {
        params = values;
        return statement;
      },
      async first<T = unknown>(): Promise<T | null> {
        if (sql.includes('FROM media_blobs WHERE id = ?')) {
          return (db.blobs.find(row => row.id === params[0]) as T | undefined) ?? null;
        }
        if (sql.includes('FROM group_members')) {
          const key = String(params[0]) + ':' + String(params[1]);
          return db.memberships.has(key) ? ({ ok: 1 } as T) : null;
        }
        if (sql.includes('FROM media_blobs WHERE checksum = ?')) {
          return null;
        }
        return null;
      },
      async all<T = unknown>(): Promise<{ results: T[] }> {
        if (sql.includes('FROM content_media cm')) {
          const needle = String(params[0]).replaceAll('%', '');
          return {
            results: db.contentLinks.filter(row =>
              (row.url?.includes(needle) || row.object_key === params[1])
            ).map(row => ({ group_id: row.group_id })) as T[],
          };
        }
        if (sql.includes('FROM homework_media hm')) {
          const needle = String(params[1]).replaceAll('%', '');
          return {
            results: db.homeworkLinks.filter(row =>
              row.media_id === params[0] || row.url?.includes(needle)
            ).map(row => ({ group_id: row.group_id })) as T[],
          };
        }
        if (sql.includes('FROM homeworks WHERE media_urls LIKE ?')) {
          const needle = String(params[0]).replaceAll('%', '');
          return {
            results: db.legacyHomeworkLinks.filter(row => row.media_urls.includes(needle))
              .map(row => ({ group_id: row.group_id })) as T[],
          };
        }
        return { results: [] };
      },
      async run() {
        db.writes.push(sql);
        return { success: true, meta: { changes: 1 } };
      },
    };
    return statement;
  }
}

const user: UserContext = {
  userId: 'student-b',
  phoneNumber: '777000002',
  fullName: 'طالب الشعبة ب',
  gradeId: 10,
  sectionId: 'B',
  role: Role.STUDENT,
};

describe('Private media attachment authorization', () => {
  it('allows the uploader to attach their own image to a group they can access', async () => {
    const db = new MediaAccessDb();
    db.blobs.push({ id: 'med_owned_1', uploaded_by: user.userId });

    await expect(validateMediaAttachmentAccess(
      user,
      ['https://tanweer.test/api/v1/media/med_owned_1'],
      db as any,
    )).resolves.toBeNull();
  });

  it('rejects linking another group private image when the user has no source-group membership', async () => {
    const db = new MediaAccessDb();
    db.blobs.push({ id: 'med_private_1', uploaded_by: 'student-a' });
    db.contentLinks.push({ group_id: 'group-a', url: 'https://tanweer.test/api/v1/media/med_private_1' });

    const response = await validateMediaAttachmentAccess(
      user,
      ['https://tanweer.test/api/v1/media/med_private_1'],
      db as any,
    );

    expect(response?.status).toBe(403);
    expect((await response?.json() as any).error.code).toBe('FORBIDDEN_MEDIA_SOURCE');
  });

  it('allows reuse when the user is an active member of an original linked group', async () => {
    const db = new MediaAccessDb();
    db.blobs.push({ id: 'med_shared_1', uploaded_by: 'student-a' });
    db.homeworkLinks.push({ group_id: 'group-a', media_id: 'med_shared_1' });
    db.memberships.add('group-a:' + user.userId);

    await expect(validateMediaAttachmentAccess(
      user,
      ['https://tanweer.test/api/v1/media/med_shared_1'],
      db as any,
    )).resolves.toBeNull();
  });

  it('does not allow claiming an unlinked legacy blob that has no recorded uploader', async () => {
    const db = new MediaAccessDb();
    db.blobs.push({ id: 'med_legacy_orphan', uploaded_by: null });

    const response = await validateMediaAttachmentAccess(
      user,
      ['/api/v1/media/med_legacy_orphan'],
      db as any,
    );

    expect(response?.status).toBe(403);
    expect((await response?.json() as any).error.code).toBe('FORBIDDEN_UNLINKED_MEDIA');
  });

  it('rejects missing internal media identifiers and local filesystem paths', async () => {
    const db = new MediaAccessDb();

    const missing = await validateMediaAttachmentAccess(
      user,
      ['https://tanweer.test/api/v1/media/med_missing'],
      db as any,
    );
    const localPath = await validateMediaAttachmentAccess(
      user,
      ['C:\\Users\\Magd\\Pictures\\photo.jpg'],
      db as any,
    );

    expect(missing?.status).toBe(404);
    expect((await missing?.json() as any).error.code).toBe('MEDIA_NOT_FOUND');
    expect(localPath?.status).toBe(400);
    expect((await localPath?.json() as any).error.code).toBe('INVALID_MEDIA_URL');
  });

  it('allows normal external HTTP(S) image URLs', async () => {
    const db = new MediaAccessDb();

    await expect(validateMediaAttachmentAccess(
      user,
      ['https://images.example.test/public/photo.jpg'],
      db as any,
    )).resolves.toBeNull();
  });


  it('blocks a lesson from linking another group private image', async () => {
    const db = new MediaAccessDb();
    db.blobs.push({ id: 'med_private_lesson', uploaded_by: 'student-a' });
    db.contentLinks.push({ group_id: 'group-a', url: 'https://tanweer.test/api/v1/media/med_private_lesson' });
    db.memberships.add('group-b:' + user.userId);

    const response = await handleCreateContent(user, new Request('https://tanweer.test/api/v1/contents', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        groupId: 'group-b',
        studyDate: '2026-11-02',
        subjectId: 'math',
        title: 'درس تجريبي',
        media: [{ url: 'https://tanweer.test/api/v1/media/med_private_lesson' }],
      }),
    }), { DB: db as any } as Env);

    expect(response.status).toBe(403);
    expect((await response.json() as any).error.code).toBe('FORBIDDEN_MEDIA_SOURCE');
    expect(db.writes.some(sql => sql.includes('INSERT INTO contents'))).toBe(false);
    expect(db.writes.some(sql => sql.includes('INSERT INTO content_media'))).toBe(false);
  });

  it('blocks homework from linking another group private image', async () => {
    const db = new MediaAccessDb();
    db.blobs.push({ id: 'med_private_homework', uploaded_by: 'student-a' });
    db.homeworkLinks.push({ group_id: 'group-a', media_id: 'med_private_homework' });
    db.memberships.add('group-b:' + user.userId);

    const response = await handleCreateHomework(user, new Request('https://tanweer.test/api/v1/homeworks', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        groupId: 'group-b',
        studyDate: '2026-11-02',
        dueDate: '2026-11-03',
        subjectId: 'math',
        title: 'واجب تجريبي',
        mediaUrls: ['https://tanweer.test/api/v1/media/med_private_homework'],
      }),
    }), { DB: db as any } as Env);

    expect(response.status).toBe(403);
    expect((await response.json() as any).error.code).toBe('FORBIDDEN_MEDIA_SOURCE');
    expect(db.writes.some(sql => sql.includes('INSERT INTO homeworks'))).toBe(false);
    expect(db.writes.some(sql => sql.includes('INSERT INTO homework_media'))).toBe(false);
  });

  it('records the authenticated uploader on newly stored blobs', async () => {
    let inserted: { sql: string; params: unknown[] } | null = null;

    const env = {
      DB: {
        prepare(sql: string) {
          let params: unknown[] = [];
          return {
            bind(...values: unknown[]) {
              params = values;
              return this;
            },
            async first() {
              return null;
            },
            async run() {
              if (sql.includes('INSERT INTO media_blobs')) inserted = { sql, params };
              return { success: true, meta: { changes: 1 } };
            },
          };
        },
      },
    } as unknown as Env;

    const bytes = new Uint8Array(16);
    bytes[0] = 0xff;
    bytes[1] = 0xd8;
    bytes[2] = 0xff;
    const response = await handleUploadMedia(user, new Request('https://tanweer.test/api/v1/media/upload', {
      method: 'POST',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: bytes,
    }), env);

    expect(response.status).toBe(200);
    expect(inserted).not.toBeNull();
    const statement = inserted as unknown as { sql: string; params: unknown[] };
    expect(statement.sql).toContain('uploaded_by');
    expect(statement.params[5]).toBe(user.userId);
  });
});
