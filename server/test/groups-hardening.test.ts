import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { Role } from '../src/lib/roles';
import {
  handleApproveGroupJoinRequest,
  handleCreatePublicGroup,
  handleJoinGroupRequest,
  handleRejectGroupJoinRequest,
  handleGetGroupMembers,
  handleRemoveGroupMember,
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

        if (normalized.includes('SELECT status FROM group_members WHERE group_id = ? AND user_id = ?')) {
          const member = db.members.find((row) =>
            row.group_id === params[0] && row.user_id === params[1]
          );
          return (member ? { status: member.status } as T : null);
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

        if (normalized.startsWith("UPDATE group_members SET status = 'BANNED'")) {
          const member = db.members.find((row) =>
            row.group_id === params[0] &&
            row.user_id === params[1] &&
            row.status !== 'BANNED'
          );
          if (!member) return { success: true, meta: { changes: 0 } };
          member.status = 'BANNED';
          return { success: true, meta: { changes: 1 } };
        }

        if (normalized.startsWith("UPDATE group_members SET status = 'ACTIVE'")) {
          const member = db.members.find((row) =>
            row.group_id === params[1] &&
            row.user_id === params[2] &&
            row.status === 'PENDING'
          );
          if (!member) return { success: true, meta: { changes: 0 } };
          member.status = 'ACTIVE';
          member.joined_at = Number(params[0]);
          return { success: true, meta: { changes: 1 } };
        }

        if (normalized.startsWith("UPDATE group_members SET status = 'REJECTED'")) {
          const member = db.members.find((row) =>
            row.group_id === params[0] &&
            row.user_id === params[1] &&
            row.status === 'PENDING'
          );
          if (!member) return { success: true, meta: { changes: 0 } };
          member.status = 'REJECTED';
          return { success: true, meta: { changes: 1 } };
        }

        if (normalized.startsWith('DELETE FROM group_members')) {
          const index = db.members.findIndex((row) =>
            row.group_id === params[0] &&
            row.user_id === params[1] &&
            (!normalized.includes("AND status = 'PENDING'") || row.status === 'PENDING')
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

  it('returns MEMBER_NOT_FOUND instead of success when banning a non-member', async () => {
    const db = new GroupsManagementDb();
    const response = await handleRemoveGroupMember(
      'group-1', 'missing-user', true, moderator, createEnv(db),
    );

    expect(response.status).toBe(404);
    expect((await response.json() as any).error.code).toBe('MEMBER_NOT_FOUND');
    expect(db.members.some((row) => row.user_id === 'missing-user')).toBe(false);
  });

  it('bans an existing member once and rejects duplicate ban attempts', async () => {
    const db = new GroupsManagementDb();
    const first = await handleRemoveGroupMember(
      'group-1', 'student-1', true, moderator, createEnv(db),
    );
    const second = await handleRemoveGroupMember(
      'group-1', 'student-1', true, moderator, createEnv(db),
    );

    expect(first.status).toBe(200);
    expect(db.members.find((row) => row.user_id === 'student-1')?.status).toBe('BANNED');
    expect(second.status).toBe(409);
    expect((await second.json() as any).error.code).toBe('ALREADY_BANNED');
  });

  it('returns MEMBER_NOT_FOUND instead of success when removing a non-member', async () => {
    const db = new GroupsManagementDb();
    const response = await handleRemoveGroupMember(
      'group-1', 'missing-user', false, moderator, createEnv(db),
    );

    expect(response.status).toBe(404);
    expect((await response.json() as any).error.code).toBe('MEMBER_NOT_FOUND');
  });

  it('removes an existing member from this group only', async () => {
    const db = new GroupsManagementDb();
    const response = await handleRemoveGroupMember(
      'group-1', 'student-1', false, moderator, createEnv(db),
    );

    expect(response.status).toBe(200);
    expect(db.members.some((row) => row.user_id === 'student-1')).toBe(false);
    expect(db.members.some((row) => row.user_id === 'moderator-1')).toBe(true);
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
    expect(db.members.find((row) => row.user_id === 'pending-1')?.status).toBe('REJECTED');
    expect(db.members.find((row) => row.user_id === 'student-1')?.status).toBe('ACTIVE');
  });
});

class PublicGroupCreateDb {
  groups: Array<Record<string, unknown>> = [];
  members: Array<Record<string, unknown>> = [];
  failMemberInsert = false;
  batchCalls = 0;

  prepare(sql: string) {
    const db = this;
    let params: unknown[] = [];
    const statement = {
      sql,
      bind(...values: unknown[]) {
        params = values;
        return statement;
      },
      async run() {
        if (sql.includes('INSERT INTO groups')) {
          db.groups.push({
            id: params[0],
            name: params[1],
            type: 'OPTIONAL',
            description: params[2],
            icon: params[3],
            is_official: 0,
            is_discoverable: 1,
            created_at: params[4],
            created_by: params[5],
            updated_at: params[6],
            visibility: params[7],
          });
          return { success: true, meta: { changes: 1 } };
        }
        if (sql.includes('INSERT INTO group_members')) {
          if (db.failMemberInsert) throw new Error('simulated membership insert failure');
          db.members.push({
            group_id: params[0],
            user_id: params[1],
            role: params[2],
            status: 'ACTIVE',
          });
          return { success: true, meta: { changes: 1 } };
        }
        return { success: true, meta: { changes: 0 } };
      },
    };
    return statement;
  }

  async batch(statements: Array<{ run: () => Promise<{ success: boolean; meta: { changes: number } }> }>) {
    this.batchCalls += 1;
    const groupSnapshot = [...this.groups];
    const memberSnapshot = [...this.members];
    try {
      const results = [];
      for (const statement of statements) results.push(await statement.run());
      return results;
    } catch (error) {
      this.groups = groupSnapshot;
      this.members = memberSnapshot;
      throw error;
    }
  }
}

describe('Public group creation consistency', () => {
  const creator: UserContext = {
    userId: 'global-moderator',
    phoneNumber: '777000099',
    fullName: 'مشرف عام',
    gradeId: 10,
    sectionId: 'A',
    role: Role.MODERATOR,
  };

  const createRequest = (visibility: 'PUBLIC' | 'PRIVATE' = 'PUBLIC') => new Request('https://tanweer.test/api/v1/groups/public', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name: 'مجموعة البرمجة',
      description: 'مجموعة لمشاركة الدروس والمشاريع',
      icon: '💻',
      visibility,
    }),
  });

  it('creates the discoverable group and its creator moderator membership in one D1 batch', async () => {
    const db = new PublicGroupCreateDb();
    const response = await handleCreatePublicGroup(createRequest(), creator, { DB: db as any } as Env);

    expect(response.status).toBe(200);
    const body = await response.json() as any;
    expect(body.success).toBe(true);
    expect(db.batchCalls).toBe(1);
    expect(db.groups).toHaveLength(1);
    expect(db.members).toHaveLength(1);
    expect(db.members[0]).toMatchObject({
      group_id: body.groupId,
      user_id: creator.userId,
      role: 'MODERATOR',
      status: 'ACTIVE',
    });
  });

  it('persists private visibility while keeping the group discoverable and creator as moderator', async () => {
    const db = new PublicGroupCreateDb();
    const response = await handleCreatePublicGroup(
      createRequest('PRIVATE'),
      creator,
      { DB: db as any } as Env,
    );

    expect(response.status).toBe(200);
    const body = await response.json() as any;
    expect(body.visibility).toBe('PRIVATE');
    expect(db.groups[0]).toMatchObject({
      visibility: 'PRIVATE',
      is_discoverable: 1,
      created_by: creator.userId,
    });
    expect(db.members[0]).toMatchObject({
      user_id: creator.userId,
      role: 'MODERATOR',
      status: 'ACTIVE',
    });
  });

  it('does not leave a published group behind if creator membership insertion fails', async () => {
    const db = new PublicGroupCreateDb();
    db.failMemberInsert = true;

    const response = await handleCreatePublicGroup(createRequest(), creator, { DB: db as any } as Env);

    expect(response.status).toBe(500);
    expect((await response.json() as any).error.code).toBe('GROUP_CREATION_FAILED');
    expect(db.batchCalls).toBe(1);
    expect(db.groups).toHaveLength(0);
    expect(db.members).toHaveLength(0);
  });
});

class GroupJoinDb {
  groups = [
    { id: 'optional-group', name: 'نادي البرمجة', type: 'OPTIONAL', visibility: 'PUBLIC' },
    { id: 'private-group', name: 'فريق المسابقة', type: 'OPTIONAL', visibility: 'PRIVATE' },
    { id: 'class-group', name: 'الأول الثانوي أ', type: 'CLASS', visibility: 'PRIVATE' },
  ];
  members: Array<Record<string, unknown>> = [];
  messages: Array<Record<string, unknown>> = [];
  notifications: Array<Record<string, unknown>> = [];
  managerUsers: Array<{ user_id: string }> = [];
  managerQuery = '';

  async batch(statements: Array<{ run: () => Promise<{ success: boolean; meta: { changes: number } }> }>) {
    const results = [];
    for (const statement of statements) results.push(await statement.run());
    return results;
  }

  prepare(sql: string) {
    const db = this;
    let params: unknown[] = [];
    const statement = {
      bind(...values: unknown[]) {
        params = values;
        return statement;
      },
      async first<T = unknown>(): Promise<T | null> {
        if (sql.includes('SELECT id, name, type, visibility FROM groups WHERE id = ?')) {
          return (db.groups.find(row => row.id === params[0]) as T | undefined) ?? null;
        }
        if (sql.includes('SELECT status FROM group_members WHERE group_id = ? AND user_id = ?')) {
          return (db.members.find(row =>
            row.group_id === params[0] && row.user_id === params[1]
          ) as T | undefined) ?? null;
        }
        return null;
      },
      async all<T = unknown>(): Promise<{ results: T[] }> {
        if (sql.includes('SELECT DISTINCT u.id AS user_id')) {
          db.managerQuery = sql;
          return { results: db.managerUsers as T[] };
        }
        return { results: [] };
      },
      async run() {
        if (sql.includes('INSERT INTO user_notifications')) {
          db.notifications.push({
            user_id: params[1],
            type: params[2],
            title: params[3],
            body: params[4],
          });
          return { success: true, meta: { changes: 1 } };
        }
        if (sql.includes("UPDATE group_members") && sql.includes("status = 'REJECTED'")) {
          const member = db.members.find(row =>
            row.group_id === params[4] && row.user_id === params[5] && row.status === 'REJECTED'
          );
          if (!member) return { success: true, meta: { changes: 0 } };
          Object.assign(member, {
            status: params[0],
            role: params[1],
            joined_at: params[2],
            request_reason: params[3],
          });
          return { success: true, meta: { changes: 1 } };
        }
        if (sql.includes('INSERT OR IGNORE INTO group_members')) {
          const exists = db.members.some(row =>
            row.group_id === params[0] && row.user_id === params[1]
          );
          if (exists) return { success: true, meta: { changes: 0 } };
          db.members.push({
            group_id: params[0],
            user_id: params[1],
            role: params[2],
            status: params[3],
            joined_at: params[4],
            request_reason: params[5],
          });
          return { success: true, meta: { changes: 1 } };
        }
        if (sql.includes('INSERT INTO chat_messages')) {
          db.messages.push({ group_id: params[1], text: params[2] });
          return { success: true, meta: { changes: 1 } };
        }
        return { success: true, meta: { changes: 0 } };
      },
    };
    return statement;
  }
}

describe('Group join request concurrency and state', () => {
  const student: UserContext = {
    userId: 'student-join',
    phoneNumber: '777000010',
    fullName: 'طالب جديد',
    gradeId: 10,
    sectionId: 'A',
    role: Role.STUDENT,
  };

  const joinRequest = (reason = 'أرغب في الانضمام') => new Request('https://tanweer.test/api/v1/groups/optional-group/join', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason }),
  });

  it('allows only one of two concurrent optional-group join requests to create membership and welcome message', async () => {
    const db = new GroupJoinDb();
    const [first, second] = await Promise.all([
      handleJoinGroupRequest('optional-group', student, joinRequest(), { DB: db as any } as Env),
      handleJoinGroupRequest('optional-group', student, joinRequest(), { DB: db as any } as Env),
    ]);

    expect([first.status, second.status].sort()).toEqual([200, 400]);
    const success = first.status === 200 ? first : second;
    const conflict = first.status === 400 ? first : second;

    expect((await success.json() as any).status).toBe('ACTIVE');
    expect((await conflict.json() as any).error.code).toBe('ALREADY_MEMBER');
    expect(db.members).toHaveLength(1);
    expect(db.members[0]).toMatchObject({
      group_id: 'optional-group',
      user_id: student.userId,
      status: 'ACTIVE',
    });
    expect(db.messages).toHaveLength(1);
  });

  it('notifies globally authorized teachers/moderators, active group managers, and school admin without duplicates', async () => {
    const db = new GroupJoinDb();
    db.managerUsers = [
      { user_id: 'group-moderator' },
      { user_id: 'group-teacher' },
      { user_id: 'global-moderator' },
      { user_id: 'global-teacher' },
      { user_id: 'school-admin' },
    ];

    const response = await handleJoinGroupRequest(
      'private-group',
      student,
      joinRequest('أرغب في الانضمام'),
      { DB: db as any } as Env,
    );

    expect(response.status).toBe(200);
    expect(db.managerQuery).toContain('SELECT DISTINCT u.id AS user_id');
    expect(db.managerQuery).toContain("gm.status = 'ACTIVE'");
    expect(db.managerQuery).toContain("UPPER(u.role) IN ('ADMIN', 'MODERATOR', 'TEACHER', 'VERIFIED_TEACHER')");
    expect(db.managerQuery).toContain("UPPER(gm.role) IN ('MODERATOR', 'TEACHER', 'ADMIN')");
    expect(db.managerQuery).toContain('u.id != ?');
    expect(db.managerQuery).not.toContain("'SYSTEM_OWNER'");
    expect(db.notifications.map(row => row.user_id).sort()).toEqual([
      'global-moderator',
      'global-teacher',
      'group-moderator',
      'group-teacher',
      'school-admin',
    ]);
    expect(db.notifications).not.toContainEqual(expect.objectContaining({ user_id: student.userId }));
    expect(db.notifications.every(row => row.type === 'GROUP_JOIN_REQUEST')).toBe(true);
  });

  it('creates a pending request for a class group without posting a membership message', async () => {
    const db = new GroupJoinDb();
    const response = await handleJoinGroupRequest(
      'class-group',
      student,
      new Request('https://tanweer.test/api/v1/groups/class-group/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason: 'أدرس في هذه الشعبة' }),
      }),
      { DB: db as any } as Env,
    );

    expect(response.status).toBe(200);
    expect((await response.json() as any).status).toBe('PENDING');
    expect(db.members[0]).toMatchObject({
      group_id: 'class-group',
      user_id: student.userId,
      status: 'PENDING',
      request_reason: 'أدرس في هذه الشعبة',
    });
    expect(db.messages).toHaveLength(0);
  });

  it('rejects an approval-based request when the reason is missing', async () => {
    const db = new GroupJoinDb();
    const response = await handleJoinGroupRequest(
      'private-group',
      student,
      new Request('https://tanweer.test/api/v1/groups/private-group/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}),
      }),
      { DB: db as any } as Env,
    );

    expect(response.status).toBe(400);
    expect((await response.json() as any).error.code).toBe('JOIN_REASON_REQUIRED');
    expect(db.members).toHaveLength(0);
    expect(db.notifications).toHaveLength(0);
  });

  it('rejects a whitespace-only reason for a class join request', async () => {
    const db = new GroupJoinDb();
    const response = await handleJoinGroupRequest(
      'class-group',
      student,
      new Request('https://tanweer.test/api/v1/groups/class-group/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason: '   \n  ' }),
      }),
      { DB: db as any } as Env,
    );

    expect(response.status).toBe(400);
    expect((await response.json() as any).error.code).toBe('JOIN_REASON_REQUIRED');
    expect(db.members).toHaveLength(0);
  });

  it('rejects join reasons longer than 500 characters before creating a request', async () => {
    const db = new GroupJoinDb();
    const response = await handleJoinGroupRequest(
      'private-group',
      student,
      new Request('https://tanweer.test/api/v1/groups/private-group/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason: 'س'.repeat(501) }),
      }),
      { DB: db as any } as Env,
    );

    expect(response.status).toBe(400);
    expect((await response.json() as any).error.code).toBe('INVALID_JOIN_REASON');
    expect(db.members).toHaveLength(0);
  });

  it('still allows a direct public-group join without an approval reason', async () => {
    const db = new GroupJoinDb();
    const response = await handleJoinGroupRequest(
      'optional-group',
      student,
      new Request('https://tanweer.test/api/v1/groups/optional-group/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}),
      }),
      { DB: db as any } as Env,
    );

    expect(response.status).toBe(200);
    expect((await response.json() as any).status).toBe('ACTIVE');
    expect(db.members).toHaveLength(1);
    expect(db.members[0]).toMatchObject({ status: 'ACTIVE', request_reason: null });
  });

  it('creates a pending request for a private discoverable group', async () => {
    const db = new GroupJoinDb();
    const response = await handleJoinGroupRequest(
      'private-group',
      student,
      joinRequest('أرغب في الانضمام للفريق'),
      { DB: db as any } as Env,
    );

    expect(response.status).toBe(200);
    expect((await response.json() as any).status).toBe('PENDING');
    expect(db.members[0]).toMatchObject({
      group_id: 'private-group',
      user_id: student.userId,
      status: 'PENDING',
      request_reason: 'أرغب في الانضمام للفريق',
    });
    expect(db.messages).toHaveLength(0);
  });

  it('allows a student to submit a new request after a prior rejection', async () => {
    const db = new GroupJoinDb();
    db.members.push({
      group_id: 'private-group',
      user_id: student.userId,
      role: 'STUDENT',
      status: 'REJECTED',
      joined_at: 1,
      request_reason: 'طلب سابق',
    });

    const response = await handleJoinGroupRequest(
      'private-group',
      student,
      joinRequest('أعيد تقديم الطلب مع توضيح جديد'),
      { DB: db as any } as Env,
    );

    expect(response.status).toBe(200);
    expect((await response.json() as any).status).toBe('PENDING');
    expect(db.members).toHaveLength(1);
    expect(db.members[0]).toMatchObject({
      status: 'PENDING',
      request_reason: 'أعيد تقديم الطلب مع توضيح جديد',
    });
  });

  it('allows a rejected applicant to join a public group directly on retry', async () => {
    const db = new GroupJoinDb();
    db.members.push({
      group_id: 'optional-group',
      user_id: student.userId,
      role: 'STUDENT',
      status: 'REJECTED',
      joined_at: 1,
    });

    const response = await handleJoinGroupRequest(
      'optional-group',
      student,
      joinRequest(),
      { DB: db as any } as Env,
    );

    expect(response.status).toBe(200);
    expect((await response.json() as any).status).toBe('ACTIVE');
    expect(db.members[0].status).toBe('ACTIVE');
    expect(db.messages).toHaveLength(1);
  });

  it('never replaces a banned membership when a user requests to join again', async () => {
    const db = new GroupJoinDb();
    db.members.push({
      group_id: 'optional-group',
      user_id: student.userId,
      role: 'STUDENT',
      status: 'BANNED',
      joined_at: 1,
    });

    const response = await handleJoinGroupRequest(
      'optional-group', student, joinRequest(), { DB: db as any } as Env,
    );

    expect(response.status).toBe(403);
    expect((await response.json() as any).error.code).toBe('BANNED');
    expect(db.members).toHaveLength(1);
    expect(db.members[0].status).toBe('BANNED');
  });
});


class GroupMemberVisibilityDb {
  statements: string[] = [];
  readonly rows = [
    { user_id: 'active-1', member_role: 'STUDENT', status: 'ACTIVE', joined_at: 1, request_reason: null, full_name: 'طالب نشط', phone_number: '777000001', global_role: 'STUDENT', grade_id: 10, section_id: 'A' },
    { user_id: 'pending-1', member_role: 'STUDENT', status: 'PENDING', joined_at: 2, request_reason: 'أريد الانضمام', full_name: 'طالب ينتظر', phone_number: '777000002', global_role: 'STUDENT', grade_id: 10, section_id: 'B' },
    { user_id: 'banned-1', member_role: 'STUDENT', status: 'BANNED', joined_at: 3, request_reason: null, full_name: 'طالب محظور', phone_number: '777000003', global_role: 'STUDENT', grade_id: 10, section_id: 'C' },
  ];

  prepare(sql: string) {
    const db = this;
    const normalized = sql.trim().replace(/\\s+/g, ' ');
    const statement = {
      bind() { return statement; },
      async all<T = unknown>(): Promise<{ results: T[] }> {
        db.statements.push(normalized);
        return { results: db.rows as T[] };
      },
      async first<T = unknown>(): Promise<T | null> {
        db.statements.push(normalized);
        return null;
      },
    };
    return statement;
  }
}

describe('Group manager request list visibility', () => {
  it('allows a school teacher with the global group-management permission to review requests without joining first', async () => {
    const db = new GroupMemberVisibilityDb();
    const teacher: UserContext = {
      userId: 'teacher-1',
      phoneNumber: '777000098',
      fullName: 'أستاذ المدرسة',
      gradeId: 0,
      sectionId: 'SYSTEM',
      role: Role.TEACHER,
    };

    const response = await handleGetGroupMembers(
      'private-group',
      teacher,
      { DB: db as any } as Env,
    );

    expect(response.status).toBe(200);
    const body = await response.json() as any;
    expect(body.canManageMembers).toBe(true);
    expect(body.members.some((member: any) => member.status === 'PENDING')).toBe(true);
    expect(db.statements.some(sql => sql.includes('SELECT role FROM group_members'))).toBe(true);
  });

  it('returns active members and pending requests to a global admin without exposing rejected requests as members', async () => {
    const db = new GroupMemberVisibilityDb();
    const admin: UserContext = {
      userId: 'admin-1',
      phoneNumber: '777000099',
      fullName: 'مدير المدرسة',
      gradeId: 0,
      sectionId: 'SYSTEM',
      role: Role.ADMIN,
    };

    const response = await handleGetGroupMembers(
      'private-group',
      admin,
      { DB: db as any } as Env,
    );

    expect(response.status).toBe(200);
    const body = await response.json() as any;
    expect(body.canManageMembers).toBe(true);
    expect(body.members.map((m: any) => m.status)).toEqual(['ACTIVE', 'PENDING', 'BANNED']);
    expect(body.members.find((m: any) => m.status === 'PENDING')?.requestReason).toBe('أريد الانضمام');
    expect(db.statements.some(sql => sql.includes("gm.status IN ('ACTIVE', 'PENDING', 'BANNED')"))).toBe(true);
  });
});
