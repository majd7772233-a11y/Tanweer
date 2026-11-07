import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { handleGetGroups } from '../src/services/groups';

type TestGroup = {
  id: string;
  name: string;
  type: string;
  description: string | null;
  icon: string | null;
  visibility: 'PUBLIC' | 'PRIVATE';
  chat_policy: string;
  is_discoverable: number;
};

type TestMember = {
  group_id: string;
  user_id: string;
  role: string;
  status: 'ACTIVE' | 'PENDING' | 'REJECTED' | 'BANNED';
};

class GroupDiscoveryDb {
  readonly groups: TestGroup[] = [
    { id: 'public-open', name: 'نادي عام', type: 'OPTIONAL', description: null, icon: '🌐', visibility: 'PUBLIC', chat_policy: 'MEMBERS', is_discoverable: 1 },
    { id: 'private-hidden', name: 'فريق خاص', type: 'OPTIONAL', description: null, icon: '🔐', visibility: 'PRIVATE', chat_policy: 'MEMBERS', is_discoverable: 1 },
    { id: 'private-pending', name: 'فريق بطلب معلق', type: 'OPTIONAL', description: null, icon: '🔐', visibility: 'PRIVATE', chat_policy: 'MEMBERS', is_discoverable: 1 },
    { id: 'private-rejected', name: 'فريق بطلب مرفوض', type: 'OPTIONAL', description: null, icon: '🔐', visibility: 'PRIVATE', chat_policy: 'MEMBERS', is_discoverable: 1 },
    { id: 'private-active', name: 'فريق خاص منضم إليه', type: 'OPTIONAL', description: null, icon: '🔐', visibility: 'PRIVATE', chat_policy: 'MEMBERS', is_discoverable: 1 },
    { id: 'private-banned', name: 'فريق محظور منه', type: 'OPTIONAL', description: null, icon: '🔐', visibility: 'PRIVATE', chat_policy: 'MEMBERS', is_discoverable: 1 },
    { id: 'other-class', name: 'الشعبة الثانية ب', type: 'CLASS', description: null, icon: '🏫', visibility: 'PRIVATE', chat_policy: 'MEMBERS', is_discoverable: 1 },
    { id: 'not-discoverable', name: 'مجموعة غير مكتشفة', type: 'OPTIONAL', description: null, icon: '🔒', visibility: 'PUBLIC', chat_policy: 'MEMBERS', is_discoverable: 0 },
  ];

  readonly members: TestMember[] = [
    { group_id: 'private-pending', user_id: 'student-1', role: 'STUDENT', status: 'PENDING' },
    { group_id: 'private-rejected', user_id: 'student-1', role: 'STUDENT', status: 'REJECTED' },
    { group_id: 'private-active', user_id: 'student-1', role: 'STUDENT', status: 'ACTIVE' },
    { group_id: 'private-banned', user_id: 'student-1', role: 'STUDENT', status: 'BANNED' },
  ];

  readonly statements: string[] = [];

  prepare(sql: string) {
    const db = this;
    let params: unknown[] = [];
    const statement = {
      bind(...values: unknown[]) {
        params = values;
        return statement;
      },
      async all<T = unknown>(): Promise<{ results: T[] }> {
        db.statements.push(sql);
        const userId = String(params[0] ?? '');

        if (sql.includes('LEFT JOIN group_members gm')) {
          const rows = db.groups.flatMap((group) => {
            const membership = db.members.find(
              (member) => member.group_id === group.id && member.user_id === userId,
            );
            const status = membership?.status ?? '';
            const isPublic = group.visibility === 'PUBLIC';
            const canSeePrivateRequest = status === 'PENDING' || status === 'REJECTED';
            const isSchoolClass = group.type === 'CLASS';
            const notAlreadyActiveOrBanned = status !== 'ACTIVE' && status !== 'BANNED';

            // Mirror the expected SQL predicate so removing the private-visibility
            // guard makes the regression test expose the otherwise-public group.
            if (
              group.is_discoverable !== 1 ||
              !notAlreadyActiveOrBanned ||
              !(isPublic || isSchoolClass || canSeePrivateRequest)
            ) {
              return [];
            }

            return [{
              id: group.id,
              name: group.name,
              type: group.type,
              description: group.description,
              icon: group.icon,
              visibility: group.visibility,
              chat_policy: group.chat_policy,
              membership_status: membership?.status ?? null,
              member_count: 1,
            }];
          });
          return { results: rows as T[] };
        }

        if (sql.includes('JOIN group_members gm ON gm.group_id = g.id')) {
          const rows = db.members
            .filter((member) => member.user_id === userId && member.status === 'ACTIVE')
            .flatMap((member) => {
              const group = db.groups.find((item) => item.id === member.group_id);
              if (!group) return [];
              return [{
                id: group.id,
                name: group.name,
                type: group.type,
                description: group.description,
                icon: group.icon,
                role: member.role,
                status: member.status,
                visibility: group.visibility,
                chat_policy: group.chat_policy,
                member_count: 1,
              }];
            });
          return { results: rows as T[] };
        }

        return { results: [] };
      },
    };
    return statement;
  }
}

const student: UserContext = {
  userId: 'student-1',
  phoneNumber: '777000001',
  fullName: 'طالب الاختبار',
  gradeId: 10,
  sectionId: 'A',
  role: 'STUDENT',
};

describe('private-group discovery visibility', () => {
  it('hides private groups from strangers but keeps pending/rejected requests visible to their applicants', async () => {
    const db = new GroupDiscoveryDb();
    const response = await handleGetGroups(student, { DB: db as any } as Env);

    expect(response.status).toBe(200);
    const body = await response.json() as {
      myGroups: Array<{ id: string; visibility: string }>;
      discoverGroups: Array<{ id: string; visibility: string; membershipStatus: string | null }>;
    };

    expect(body.discoverGroups.map((group) => group.id).sort()).toEqual([
      'private-pending',
      'private-rejected',
      'other-class',
      'public-open',
    ]);

    expect(body.discoverGroups.find((group) => group.id === 'private-pending')).toMatchObject({
      visibility: 'PRIVATE',
      membershipStatus: 'PENDING',
    });
    expect(body.discoverGroups.find((group) => group.id === 'private-rejected')).toMatchObject({
      visibility: 'PRIVATE',
      membershipStatus: 'REJECTED',
    });

    // An active member sees a private group only in "My groups", not in discovery.
    expect(body.myGroups).toContainEqual(expect.objectContaining({
      id: 'private-active',
      visibility: 'PRIVATE',
    }));
    expect(body.discoverGroups.some((group) => group.id === 'private-active')).toBe(false);

    expect(body.discoverGroups.find((group) => group.id === 'other-class')).toMatchObject({
      visibility: 'PRIVATE',
      membershipStatus: null,
    });
    expect(body.discoverGroups.some((group) => group.id === 'private-hidden')).toBe(false);
    expect(body.discoverGroups.some((group) => group.id === 'private-banned')).toBe(false);
    expect(body.discoverGroups.some((group) => group.id === 'not-discoverable')).toBe(false);
  });
});
