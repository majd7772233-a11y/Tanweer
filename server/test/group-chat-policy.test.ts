import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import {
  getGroupChatSendPermission,
  handleDeleteGroupMessage,
  handlePostGroupMessage,
} from '../src/services/chat';

class GroupChatPolicyDb {
  messages: Array<Record<string, unknown>> = [];
  constructor(
    readonly chatPolicy: string,
    readonly memberRole: string,
    readonly globalRole: string
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
          return {
            chat_policy: db.chatPolicy,
            member_role: db.memberRole,
            global_role: db.globalRole,
          } as T;
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
        if (normalized.includes("INSERT INTO chat_messages")) {
          db.messages.push({ sql: normalized, params });
          return { success: true, meta: { changes: 1 } };
        }
        if (normalized.includes("DELETE FROM chat_messages")) {
          db.messages = db.messages.filter(row => row.params?.[0] !== params[0]);
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

function makeEnv(db: GroupChatPolicyDb): Env {
  return { DB: db as any } as Env;
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

  it('does not grant send permission to a student who is only a group moderator', async () => {
    const db = new GroupChatPolicyDb('STAFF_ONLY', 'MODERATOR', 'STUDENT');
    const permission = await getGroupChatSendPermission('staff_official', 'student-1', 'STUDENT', makeEnv(db));
    expect(permission.allowed).toBe(false);
  });
});
