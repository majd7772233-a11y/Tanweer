import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { Role } from '../src/lib/roles';
import {
  handleApproveGroupJoinRequest,
  handleRejectGroupJoinRequest,
  handleUpdateGroupMemberRole,
} from '../src/services/groups';

type Member = {
  group_id: string;
  user_id: string;
  role: string;
  status: string;
  joined_at: number;
};

class GroupsManagementDb {
  members: Member[] = [
    { group_id: 'group-1', user_id: 'moderator-1', role: 'MODERATOR', status: 'ACTIVE', joined_at: 1 },
    { group_id: 'group-1', user_id: 'student-1', role: 'STUDENT', status: 'ACTIVE', joined_at: 2 },
    { group_id: 'group-1', user_id: 'pending-1', role: 'STUDENT', status: 'PENDING', joined_at: 3 },
    { group_id: 'group-1', user_id: 'banned-1', role: 'STUDENT', status: 'BANNED', joined_at: 4 },
  ];

  users = [
    { id: 'moderator-1', role: 'STUDENT', full_name: 'مشرف المجموعة' },
    { id: 'student-1', role: 'STUDENT', full_name: 'طالب نشط' },
    { id: 'pending-1', role: 'STUDENT', full_name: 'طالب ينتظر الموافقة' },
    { id: 'banned-1', role: 'STUDENT', full_name: 'طالب محظور' },
    { id: 'teacher-1', role: 'TEACHER', full_name: 'أستاذ معتمد' },
  ];

  systemMessages: Array<{ group_id: string; text: string }> = [];
  statements: string[] = [];

  prepare(sql: string) {
    const db = this;
    let params: unknown[] = [];
    const normalized = sql.trim().replace(/\s+/g, ' ');

    const statement = {
      bind(...values: unknown[]) {
        params = values;
        return statement;
      },

      async first<T = unknown>(): Promise<T | null> {
        db.statements.push(normalized);

        if (normalized.includes('SELECT role FROM group_members WHERE group_id = ? AND user_id = ?')) {
          const actor = db.members.find((member) =>
            member.group_id === params[0] &&
            member.user_id === params[1] &&
            member.status === 'ACTIVE'
          );
          return (actor ? { role: actor.role } as T : null);
        }

        if (normalized.includes('FROM group_members gm JOIN users u')) {
          const member = db.members.find((row) =>
            row.group_id === params[0] && row.user_id === params[1]
          );
          const user = db.users.find((row) => row.id === params[1]);
          if (!member || !user) return null;
          return {
            member_role: member.role,
            status: member.status,
            global_role: user.role,
          } as T;
        }

        if (normalized.includes('SELECT full_name FROM users WHERE id = ?')) {
          const user = db.users.find((row) => row.id === params[0]);
          return (user ? { full_name: user.full_name } as T : null);
        }

        return null;
      },

      async run(): Promise<{ success: boolean; meta: { changes: number } }> {
        db.statements.push(normalized);

        if (normalized.startsWith('UPDATE group_members SET role = ?')) {
          const member = db.members.find((row) =>
            row.group_id === params[1] &&
            row.user_id === params[2] &&
            row.status === 'ACTIVE'
          );
          if (!member) return { success: true, meta: { changes: 0 } };
          member.role = String(params[0]);
          return { success: true, meta: { changes: 1 } };
        }

        if (normalized.startsWith("UPDATE group_members SET status = 'ACTIVE'")) {
          const member = db.members.find((row) =>
            row.group_id === params[0] &&
            row.user_id === params[1] &&
            row.status === 'PENDING'
          );
          if (!member) return { success: true, meta: { changes: 0 } };
          member.status = 'ACTIVE';
          return { success: true, meta: { changes: 1 } };
        }

        if (normalized.startsWith('DELETE FROM group_members')) {
          const index = db.members.findIndex((row) =>
            row.group_id === params[0] &&
            row.user_id === params[1] &&
            row.status === 'PENDING'
          );
          if (index < 0) return { success: true, meta: { changes: 0 } };
          db.members.splice(index, 1);
          return { success: true, meta: { changes: 1 } };
        }

        if (normalized.startsWith('INSERT INTO chat_messages')) {
          db.systemMessages.push({
            group_id: String(params[1]),
            text: String(params[2]),
          });
          return { success: true, meta: { changes: 1 } };
        }

        return { success: true, meta: { changes: 0 } };
      },
    };

    return statement;
  }
}

const moderator: UserContext = {
  userId: 'moderator-1',
  phoneNumber: '777000001',
  fullName: 'مشرف المجموعة',
  gradeId: 10,
  sectionId: 'A',
  role: Role.STUDENT,
};

function createEnv(db: GroupsManagementDb): Env {
  return { DB: db as any } as Env;
}

function roleRequest(newRole: string): Request {
  return new Request('https://tanweer.test/api/v1/groups/group-1/members/student-1/role', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ newRole }),
  });
}

describe('Group member management state and scope', () => {
  it('rejects unknown role values instead of silently normalizing them to STUDENT', async () => {
    const db = new GroupsManagementDb();
    const response = await handleUpdateGroupMemberRole(
      'group-1', 'student-1', moderator, roleRequest('SUPER_ADMIN'), createEnv(db),
    );

    expect(response.status).toBe(400);
    expect(db.members.find((row) => row.user_id === 'student-1')?.role).toBe('STUDENT');
    expect(db.statements.some((sql) => sql.startsWith('UPDATE group_members SET role'))).toBe(false);
  });

  it('returns MEMBER_NOT_FOUND when the target user is not a member of the requested group', async () => {
    const db = new GroupsManagementDb();
    const response = await handleUpdateGroupMemberRole(
      'group-1', 'teacher-1', moderator, roleRequest('MODERATOR'), createEnv(db),
    );

    expect(response.status).toBe(404);
    expect((await response.json() as any).error.code).toBe('MEMBER_NOT_FOUND');
    expect(db.statements.some((sql) => sql.startsWith('UPDATE group_members SET role'))).toBe(false);
  });

  it('does not change the role of a pending or banned member', async () => {
    const db = new GroupsManagementDb();
    const pendingResponse = await handleUpdateGroupMemberRole(
      'group-1', 'pending-1', moderator, roleRequest('MODERATOR'), createEnv(db),
    );
    const bannedResponse = await handleUpdateGroupMemberRole(
      'group-1', 'banned-1', moderator, roleRequest('MODERATOR'), createEnv(db),
    );

    expect(pendingResponse.status).toBe(409);
    expect(bannedResponse.status).toBe(409);
    expect(db.members.find((row) => row.user_id === 'pending-1')?.role).toBe('STUDENT');
    expect(db.members.find((row) => row.user_id === 'banned-1')?.role).toBe('STUDENT');
  });

  it('updates an active member in the specified group', async () => {
    const db = new GroupsManagementDb();
    const response = await handleUpdateGroupMemberRole(
      'group-1', 'student-1', moderator, roleRequest('MODERATOR'), createEnv(db),
    );

    expect(response.status).toBe(200);
    expect((await response.json() as any).success).toBe(true);
    expect(db.members.find((row) => row.user_id === 'student-1')?.role).toBe('MODERATOR');
  });

  it('does not report success or post a system message when an approval is stale', async () => {
    const db = new GroupsManagementDb();
    const response = await handleApproveGroupJoinRequest(
      'group-1', 'missing-request', moderator, createEnv(db),
    );

    expect(response.status).toBe(404);
    expect((await response.json() as any).error.code).toBe('JOIN_REQUEST_NOT_FOUND');
    expect(db.systemMessages).toHaveLength(0);
  });

  it('approves a pending join request and posts exactly one system message', async () => {
    const db = new GroupsManagementDb();
    const response = await handleApproveGroupJoinRequest(
      'group-1', 'pending-1', moderator, createEnv(db),
    );

    expect(response.status).toBe(200);
    expect(db.members.find((row) => row.user_id === 'pending-1')?.status).toBe('ACTIVE');
    expect(db.systemMessages).toHaveLength(1);
    expect(db.systemMessages[0].text).toContain('طالب ينتظر الموافقة');
  });

  it('does not report success when a join request cannot be rejected', async () => {
    const db = new GroupsManagementDb();
    const response = await handleRejectGroupJoinRequest(
      'group-1', 'missing-request', moderator, createEnv(db),
    );

    expect(response.status).toBe(404);
    expect((await response.json() as any).error.code).toBe('JOIN_REQUEST_NOT_FOUND');
  });

  it('rejects a pending join request without touching active members', async () => {
    const db = new GroupsManagementDb();
    const response = await handleRejectGroupJoinRequest(
      'group-1', 'pending-1', moderator, createEnv(db),
    );

    expect(response.status).toBe(200);
    expect(db.members.some((row) => row.user_id === 'pending-1')).toBe(false);
    expect(db.members.find((row) => row.user_id === 'student-1')?.status).toBe('ACTIVE');
  });
});
