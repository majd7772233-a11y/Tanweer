import { describe, it, expect } from 'vitest';
import { handlePreviewPurge, handleExecutePurge } from '../src/services/dashboard';
import { handleUpdateHomework } from '../src/services/homework';
import { handleUpdateExam } from '../src/services/exams';
import { handleUpdateEvent } from '../src/services/events';
import { Env, UserContext } from '../src/env';

describe('Audit Fixes 11-15 (TA-11 to TA-15)', () => {
  it('TA-15: handlePreviewPurge counts all tables including missing user and activity tables', async () => {
    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          bind: () => ({
            first: async () => ({ c: 5 }),
            all: async () => ({ results: [] }),
            run: async () => ({ success: true }),
          }),
          first: async () => ({ c: 7 }),
          all: async () => ({ results: [] }),
          run: async () => ({ success: true }),
        }),
      },
      JWT_SECRET: 'test-secret',
    } as unknown as Env;

    const res = await handlePreviewPurge('admin_1', mockEnv);
    expect(res.status).toBe(200);
    const data = await res.json() as any;
    expect(data.success).toBe(true);
    expect(data.counts).toHaveProperty('userBookmarks');
    expect(data.counts).toHaveProperty('votes');
    expect(data.counts).toHaveProperty('roleVerificationTokens');
    expect(data.counts).toHaveProperty('examCompletions');
    expect(data.counts).toHaveProperty('homeworkCompletions');
    expect(data.counts).toHaveProperty('contentUsefulVotes');
    expect(data.counts).toHaveProperty('contentCorrections');
    expect(data.totalRecords).toBeGreaterThan(0);
  });

  it('TA-11: handleExecutePurge atomically executes deletes AND data_generation in same batch', async () => {
    let batchCalledWith: any[] = [];
    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          sql,
          bind: (...params: any[]) => ({ sql, params, run: async () => ({ success: true }) }),
          run: async () => ({ success: true }),
        }),
        batch: async (statements: any[]) => {
          batchCalledWith = statements;
          return statements.map(() => ({ success: true }));
        },
      },
      TANWEER_OWNER_SECRET: 'super-owner-secret',
    } as unknown as Env;

    const req = new Request('http://localhost/api/dashboard/system/purge', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        secret: 'super-owner-secret',
        confirmation: 'YES',
      }),
    });

    const res = await handleExecutePurge(req, 'owner_1', mockEnv);
    expect(res.status).toBe(200);
    const data = await res.json() as any;
    expect(data.success).toBe(true);
    expect(data.newGeneration).toMatch(/^gen_\d+_/);
    expect(data.tablesPurged).toContain('content_useful_votes');
    expect(data.tablesPurged).toContain('user_bookmarks');
    expect(data.tablesPurged).toContain('exam_completions');
    expect(data.tablesPurged).toContain('homework_completions');

    // Verify batch included system_settings data_generation update
    expect(batchCalledWith.length).toBeGreaterThan(20);
    const lastStmt = batchCalledWith[batchCalledWith.length - 1];
    expect(lastStmt.sql).toContain('system_settings');
    expect(lastStmt.params[0]).toBe(data.newGeneration);
  });

  it('TA-14: handleUpdateHomework rejects invalid ISO dueDate', async () => {
    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          bind: () => ({
            first: async () => ({
              id: 'hw_1',
              group_id: 'grp_1',
              created_by: 'user_1',
            }),
            all: async () => ({ results: [{ role: 'ADMIN' }] }),
            run: async () => ({ success: true }),
          }),
        }),
      },
    } as unknown as Env;

    const user: UserContext = {
      userId: 'user_1',
      phoneNumber: '0500000000',
      fullName: 'أستاذ مجد',
      gradeId: 1,
      sectionId: 'A',
      role: 'ADMIN',
    };

    const req = new Request('http://localhost/api/homework/hw_1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        dueDate: 'invalid-date-format',
      }),
    });

    const res = await handleUpdateHomework('hw_1', user, req, mockEnv);
    expect(res.status).toBe(400);
    const data = await res.json() as any;
    expect(data.success).toBe(false);
    expect(data.error.code).toBe('INVALID_INPUT');
  });

  it('TA-14: handleUpdateExam rejects invalid ISO examDate', async () => {
    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          bind: () => ({
            first: async () => ({
              id: 'exam_1',
              group_id: 'grp_1',
              created_by: 'user_1',
            }),
            all: async () => ({ results: [{ role: 'ADMIN' }] }),
            run: async () => ({ success: true }),
          }),
        }),
      },
    } as unknown as Env;

    const user: UserContext = {
      userId: 'user_1',
      phoneNumber: '0500000000',
      fullName: 'أستاذ مجد',
      gradeId: 1,
      sectionId: 'A',
      role: 'ADMIN',
    };

    const req = new Request('http://localhost/api/exams/exam_1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        examDate: '2026/13/45',
      }),
    });

    const res = await handleUpdateExam('exam_1', user, req, mockEnv);
    expect(res.status).toBe(400);
    const data = await res.json() as any;
    expect(data.success).toBe(false);
    expect(data.error.code).toBe('INVALID_INPUT');
  });

  it('TA-14: handleUpdateEvent rejects invalid ISO eventDate', async () => {
    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          bind: () => ({
            first: async () => ({
              id: 'event_1',
              group_id: 'grp_1',
              created_by: 'user_1',
            }),
            all: async () => ({ results: [{ role: 'ADMIN' }] }),
            run: async () => ({ success: true }),
          }),
        }),
      },
    } as unknown as Env;

    const user: UserContext = {
      userId: 'user_1',
      phoneNumber: '0500000000',
      fullName: 'أستاذ مجد',
      gradeId: 1,
      sectionId: 'A',
      role: 'ADMIN',
    };

    const req = new Request('http://localhost/api/events/event_1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        eventDate: 'not-a-date',
      }),
    });

    const res = await handleUpdateEvent('event_1', user, req, mockEnv);
    expect(res.status).toBe(400);
    const data = await res.json() as any;
    expect(data.success).toBe(false);
    expect(data.error.code).toBe('INVALID_INPUT');
  });
});
