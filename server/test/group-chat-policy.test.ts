import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import {
  getGroupChatSendPermission,
  handleDeleteGroupMessage,
  handlePostGroupMessage,
  MAX_CHAT_MESSAGE_CHARACTERS,
} from '../src/services/chat';

class GroupChatPolicyDb {
  messages: Array<{ sql: string; params: unknown[] }> = [];
  persistedRows: Array<{ id: string; group_id: string; sender_id: string; text: string; timestamp: number }> = [];
  constructor(
    readonly chatPolicy: string,
    readonly memberRole: string,
    readonly globalRole: string,
    readonly memberActive: boolean = true
  ) {}

  prepare(sql: string) {
    const db = this;
    let params: unknown[] = [];
    const normalized = sql.trim().replace(/\\s+/g, ' ');
    const statement = {
      bind(...values: unknown[]) {
        params = values;
        return statement;
      },
      async first<T = unknown>(): Promise<T | null> {
        if (normalized.includes("SELECT role, status FROM group_members")) {
          return { role: db.memberRole, status: 'ACTIVE' } as T;
        }
        if (normalized.includes("SELECT g.chat_policy, gm.role AS member_role")) {
          if (!db.memberActive) return null;
          return {
            chat_policy: db.chatPolicy,
            member_role: db.memberRole,
            global_role: db.globalRole,
          } as T;
        }
        if (normalized.includes("SELECT group_id, sender_id, text, timestamp") &&
            normalized.includes("FROM chat_messages WHERE id = ?")) {
          return (db.persistedRows.find(row => row.id === params[0]) || null) as T | null;
        }
        if (normalized.includes("SELECT sender_id FROM chat_messages")) {
          return { sender_id: 'student-1' } as T;
        }
        return null;
      },
      async all<T = unknown>(): Promise<{ results: T[] }> {
        return { results: [] };
      },
      async run() {
        if (normalized.includes("INTO chat_messages")) {
          const [id, groupId, senderId, , , text, timestamp] = params;
          if (db.persistedRows.some(row => row.id === id)) {
            return { success: true, meta: { changes: 0 } };
          }
          db.persistedRows.push({
            id: String(id),
            group_id: String(groupId),
            sender_id: String(senderId),
            text: String(text),
            timestamp: Number(timestamp),
          });
          db.messages.push({ sql: normalized, params });
          return { success: true, meta: { changes: 1 } };
        }
        if (normalized.includes("DELETE FROM chat_messages")) {
          db.messages = db.messages.filter(row => row.params[0] !== params[0]);
          return { success: true, meta: { changes: 1 } };
        }
        return { success: true, meta: { changes: 0 } };
      },
    };
    return statement;
  }
}

function makeUser(role: string, userId = 'student-1'): UserContext {
  return {
    userId,
    phoneNumber: '777000001',
    fullName: 'مستخدم اختبار',
    gradeId: 10,
    sectionId: 'A',
    role,
  };
}

function makeEnv(db: GroupChatPolicyDb, rateLimitAllowed = true): Env {
  const stub = { async fetch(request: Request) {
    if (new URL(request.url).pathname === '/rate-limit') return new Response(JSON.stringify({ success: true, allowed: rateLimitAllowed }), { status: rateLimitAllowed ? 200 : 429, headers: { 'Content-Type': 'application/json' } });
    return new Response(JSON.stringify({ success: true }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  } };
  return { DB: db as any, CHAT: { idFromName: (_name: string) => 'chat-test', get: (_id: unknown) => stub } as any } as Env;
}

describe('Group chat policy is enforced by server routes', () => {
  it('allows normal active members to send in public member-chat groups', async () => {
    const db = new GroupChatPolicyDb('MEMBERS', 'STUDENT', 'STUDENT');
    const permission = await getGroupChatSendPermission('all', 'student-1', 'STUDENT', makeEnv(db));
    expect(permission.allowed).toBe(true);

    const response = await handlePostGroupMessage(
      'all',
      makeUser('STUDENT'),
      new Request('https://tanweer.test/api/v1/groups/all/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: 'رسالة عامة' }),
      }),
      makeEnv(db),
    );
    expect(response.status).toBe(200);
    expect(db.messages).toHaveLength(1);
  });

  it('allows students to read staff-only groups but blocks message creation on the REST endpoint', async () => {
    const db = new GroupChatPolicyDb('STAFF_ONLY', 'STUDENT', 'STUDENT');
    const permission = await getGroupChatSendPermission('staff_official', 'student-1', 'STUDENT', makeEnv(db));
    expect(permission.allowed).toBe(false);

    const response = await handlePostGroupMessage(
      'staff_official',
      makeUser('STUDENT'),
      new Request('https://tanweer.test/api/v1/groups/staff_official/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: 'محاولة إرسال طالب' }),
      }),
      makeEnv(db),
    );
    expect(response.status).toBe(403);
    expect((await response.json() as any).error.code).toBe('READ_ONLY_GROUP');
    expect(db.messages).toHaveLength(0);
  });

  it('blocks student deletion of a staff-only group message at the REST endpoint', async () => {
    const db = new GroupChatPolicyDb('STAFF_ONLY', 'STUDENT', 'STUDENT');
    const response = await handleDeleteGroupMessage(
      'staff_official',
      'message-1',
      makeUser('STUDENT'),
      makeEnv(db),
    );
    expect(response.status).toBe(403);
    expect((await response.json() as any).error.code).toBe('READ_ONLY_GROUP');
    expect(db.messages).toHaveLength(0);
  });

  it('rejects overlong messages before persistence', async () => {
    const db = new GroupChatPolicyDb('MEMBERS', 'STUDENT', 'STUDENT');
    const response = await handlePostGroupMessage('all', makeUser('STUDENT'), new Request('https://tanweer.test/api/v1/groups/all/messages', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text: 'x'.repeat(MAX_CHAT_MESSAGE_CHARACTERS + 1) }) }), makeEnv(db));
    expect(response.status).toBe(400); expect((await response.json() as any).error.code).toBe('MESSAGE_TOO_LONG'); expect(db.messages).toHaveLength(0);
  });
  it('rejects oversized REST bodies before persistence', async () => {
    const db = new GroupChatPolicyDb('MEMBERS', 'STUDENT', 'STUDENT');
    const response = await handlePostGroupMessage('all', makeUser('STUDENT'), new Request('https://tanweer.test/api/v1/groups/all/messages', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text: 'ok', unused: 'x'.repeat(17 * 1024) }) }), makeEnv(db));
    expect(response.status).toBe(413); expect((await response.json() as any).error.code).toBe('MESSAGE_BODY_TOO_LARGE'); expect(db.messages).toHaveLength(0);
  });
  it('rejects REST sends when the Durable Object quota is exhausted', async () => {
    const db = new GroupChatPolicyDb('MEMBERS', 'STUDENT', 'STUDENT');
    const response = await handlePostGroupMessage('all', makeUser('STUDENT'), new Request('https://tanweer.test/api/v1/groups/all/messages', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text: 'رسالة تتجاوز الحد' }) }), makeEnv(db, false));
    expect(response.status).toBe(429); expect((await response.json() as any).error.code).toBe('RATE_LIMIT_EXCEEDED'); expect(db.messages).toHaveLength(0);
  });
  it('treats an exact persisted message retry as idempotent without inserting twice', async () => {
    const db = new GroupChatPolicyDb('MEMBERS', 'STUDENT', 'STUDENT');
    db.persistedRows.push({
      id: 'message-same-id',
      group_id: 'all',
      sender_id: 'student-1',
      text: 'رسالة محفوظة',
      timestamp: 1762041600000,
    });

    const response = await handlePostGroupMessage(
      'all',
      makeUser('STUDENT'),
      new Request('https://tanweer.test/api/v1/groups/all/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: 'message-same-id', text: 'رسالة محفوظة' }),
      }),
      makeEnv(db),
    );

    expect(response.status).toBe(200);
    expect((await response.json() as any).success).toBe(true);
    expect(db.persistedRows).toHaveLength(1);
    expect(db.messages).toHaveLength(0);
  });

  it('rejects a conflicting message ID instead of reporting false delivery success', async () => {
    const db = new GroupChatPolicyDb('MEMBERS', 'STUDENT', 'STUDENT');
    db.persistedRows.push({
      id: 'message-same-id',
      group_id: 'other-group',
      sender_id: 'another-user',
      text: 'نص مختلف',
      timestamp: 1762041600000,
    });

    const response = await handlePostGroupMessage(
      'all',
      makeUser('STUDENT'),
      new Request('https://tanweer.test/api/v1/groups/all/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: 'message-same-id', text: 'رسالة جديدة' }),
      }),
      makeEnv(db),
    );

    expect(response.status).toBe(409);
    expect((await response.json() as any).error.code).toBe('MESSAGE_ID_CONFLICT');
    expect(db.messages).toHaveLength(0);
  });

  it('allows a verified teacher to send in the official staff group', async () => {
    const db = new GroupChatPolicyDb('STAFF_ONLY', 'TEACHER', 'TEACHER');
    const permission = await getGroupChatSendPermission('staff_official', 'teacher-1', 'TEACHER', makeEnv(db));
    expect(permission.allowed).toBe(true);

    const response = await handlePostGroupMessage(
      'staff_official',
      makeUser('TEACHER', 'teacher-1'),
      new Request('https://tanweer.test/api/v1/groups/staff_official/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: 'تعميم للهيئة التعليمية' }),
      }),
      makeEnv(db),
    );
    expect(response.status).toBe(200);
    expect(db.messages).toHaveLength(1);
  });

  it('allows the school admin to send in the official staff group', async () => {
    const db = new GroupChatPolicyDb('STAFF_ONLY', 'ADMIN', 'ADMIN');
    const permission = await getGroupChatSendPermission('staff_official', 'admin-1', 'ADMIN', makeEnv(db));
    expect(permission.allowed).toBe(true);
  });

  it('revokes send permission immediately when the active group membership disappears', async () => {
    const db = new GroupChatPolicyDb('MEMBERS', 'STUDENT', 'STUDENT', false);
    const permission = await getGroupChatSendPermission('all', 'student-1', 'STUDENT', makeEnv(db));
    expect(permission.allowed).toBe(false);
    expect(permission.chatPolicy).toBe('UNKNOWN');
  });

  it('does not grant send permission to a student who is only a group moderator', async () => {
    const db = new GroupChatPolicyDb('STAFF_ONLY', 'MODERATOR', 'STUDENT');
    const permission = await getGroupChatSendPermission('staff_official', 'student-1', 'STUDENT', makeEnv(db));
    expect(permission.allowed).toBe(false);
  });
});
