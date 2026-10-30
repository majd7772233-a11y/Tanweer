import { describe, it, expect } from 'vitest';
import { handleReissueUserCredentials, handlePreviewPurge, handleExecutePurge, handleDashboardCreateHoliday } from '../src/services/dashboard';
import { handleCreateScheduleSlot, applyScheduleProposalToActiveSchedule } from '../src/services/schedule';
import { handleLogin } from '../src/services/auth';
import { handleGetAcademicArchive } from '../src/services/content';
import { handleGetGroupMembers } from '../src/services/groups';
import { mapGroup } from '../src/lib/mappers';
import { Env, UserContext } from '../src/env';
import { hashPassword } from '../src/lib/crypto';
import { Role } from '../src/lib/roles';

describe('Audit Fixes 20-28 (Server verification)', () => {
  const pepper = 'test-secret-pepper-2026';

  it('TA-21: TEMP_PASSWORD allows standard phone+password login and burns token on single use', async () => {
    let updatedPasswordHash: string | null = null;
    let tokenBurned = false;

    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          bind: (...params: any[]) => ({
            first: async () => {
              if (sql.includes('FROM users WHERE id = ?')) {
                return {
                  id: 'usr_student',
                  full_name: 'طالب تنوير',
                  phone_number: '771234567',
                  role: 'STUDENT',
                };
              }
              if (sql.includes('FROM users WHERE phone_number = ?')) {
                return {
                  id: 'usr_student',
                  phone_number: '771234567',
                  full_name: 'طالب تنوير',
                  grade_id: 1,
                  section_id: 'A',
                  role: 'STUDENT',
                  password_hash: updatedPasswordHash,
                };
              }
              if (sql.includes('FROM user_recovery_tokens')) {
                return {
                  id: 'rec_tok_1',
                  expires_at: Date.now() + 3600000,
                  max_uses: 1,
                  uses_count: 0,
                  revoked: 0,
                };
              }
              return null;
            },
            run: async () => {
              if (sql.includes('UPDATE users SET password_hash = ?')) {
                updatedPasswordHash = params[0];
              }
              if (sql.includes('UPDATE user_recovery_tokens SET uses_count = uses_count + 1')) {
                tokenBurned = true;
              }
              return { success: true };
            },
          }),
          run: async () => ({ success: true }),
        }),
      },
      SESSION_PEPPER: pepper,
      PASSWORD_PEPPER: pepper,
    } as unknown as Env;

    // 1. Issue temporary password
    const reissueReq = new Request('http://localhost/api/dashboard/users/usr_student/reissue-credentials', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        type: 'TEMP_PASSWORD',
        expiresInHours: 24,
      }),
    });

    const reissueRes = await handleReissueUserCredentials('usr_student', 'owner_1', reissueReq, mockEnv);
    expect(reissueRes.status).toBe(200);
    const reissueData = await reissueRes.json() as any;
    expect(reissueData.success).toBe(true);
    expect(reissueData.credentialType).toBe('TEMP_PASSWORD');
    expect(reissueData.temporarySecret).toMatch(/^Tmp#[A-Z0-9]+$/);
    expect(updatedPasswordHash).not.toBeNull();
    expect(updatedPasswordHash).toMatch(/^pbkdf2\$/);

    // 2. Perform standard login using phone_number + temporarySecret
    const loginReq = new Request('http://localhost/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        phoneNumber: '771234567',
        password: reissueData.temporarySecret,
      }),
    });

    const loginRes = await handleLogin(loginReq, mockEnv);
    expect(loginRes.status).toBe(200);
    const loginData = await loginRes.json() as any;
    expect(loginData.success).toBe(true);
    expect(loginData.token).toBeDefined();
    expect(tokenBurned).toBe(true);
  });

  it('TA-22: handlePreviewPurge categorizes fully purged, partially purged, and preserved tables accurately', async () => {
    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          first: async () => {
            if (sql.includes("role = 'SYSTEM_OWNER'")) return { c: 1 };
            if (sql.includes("role != 'SYSTEM_OWNER'")) return { c: 10 };
            return { c: 5 };
          },
        }),
      },
    } as unknown as Env;

    const res = await handlePreviewPurge('owner_1', mockEnv);
    expect(res.status).toBe(200);
    const data = await res.json() as any;
    expect(data.success).toBe(true);
    expect(data.fullyPurgedTables).toBeDefined();
    expect(data.partiallyPurgedTables).toBeDefined();
    expect(data.preservedTables).toBeDefined();

    // Verify users & group_members are categorized as partially purged
    const partialNames = data.partiallyPurgedTables.map((t: any) => t.table);
    expect(partialNames).toContain('users');
    expect(partialNames).toContain('group_members');

    // Verify system_settings is categorized as preserved
    const preservedNames = data.preservedTables.map((t: any) => t.table);
    expect(preservedNames).toContain('system_settings');
  });

  it('TA-23: handleSaveSingleSlot strictly rejects invalid dayOfWeek and slotOrder', async () => {
    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          bind: () => ({
            first: async () => {
              if (sql.includes('FROM schedule_versions')) return { id: 'ver_1', version_number: 1 };
              return null;
            },
            run: async () => ({ success: true }),
          }),
          run: async () => ({ success: true }),
        }),
      },
    } as unknown as Env;

    const adminUser: UserContext = {
      userId: 'usr_admin',
      phoneNumber: '770000000',
      fullName: 'مدير',
      gradeId: 1,
      sectionId: 'A',
      role: 'ADMIN',
    };

    // 1. Invalid dayOfWeek = 99
    const reqInvalidDay = new Request('http://localhost/api/v1/schedule/grp_1/slots', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        dayOfWeek: 99,
        slotOrder: 1,
        subjectId: 'sub_math',
      }),
    });
    const resInvalidDay = await handleCreateScheduleSlot('grp_1', adminUser, reqInvalidDay, mockEnv);
    expect(resInvalidDay.status).toBe(400);

    // 2. Invalid slotOrder = -1
    const reqInvalidSlot = new Request('http://localhost/api/v1/schedule/grp_1/slots', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        dayOfWeek: 1,
        slotOrder: -1,
        subjectId: 'sub_math',
      }),
    });
    const resInvalidSlot = await handleCreateScheduleSlot('grp_1', adminUser, reqInvalidSlot, mockEnv);
    expect(resInvalidSlot.status).toBe(400);

    // 3. Valid slot
    const reqValid = new Request('http://localhost/api/v1/schedule/grp_1/slots', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        dayOfWeek: 1,
        slotOrder: 2,
        subjectId: 'sub_math',
      }),
    });
    const resValid = await handleCreateScheduleSlot('grp_1', adminUser, reqValid, mockEnv);
    expect(resValid.status).toBe(200);
  });

  it('TA-24: applyScheduleProposalToActiveSchedule detects conflict when slot was proposed as empty but filled later', async () => {
    let updatedStatus: string | null = null;
    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          bind: (...params: any[]) => ({
            first: async () => {
              if (sql.includes('FROM schedule_proposals')) return { status: 'PENDING', version_number: 1 };
              if (sql.includes('FROM schedule_versions')) return { id: 'ver_active', version_number: 1 };
              return null;
            },
            all: async () => {
              if (sql.includes('FROM schedule_slots')) {
                // Active slot was populated with 'math' in the meantime
                return {
                  results: [
                    { day_of_week: 2, slot_order: 3, subject_id: 'math', start_time: '08:00', end_time: '08:45' },
                  ],
                };
              }
              return { results: [] };
            },
            run: async () => {
              if (sql.includes('UPDATE schedule_proposals SET status =')) {
                updatedStatus = 'CONFLICTED';
              }
              return { success: true };
            },
          }),
        }),
      },
    } as unknown as Env;

    const proposalFromEmpty = {
      id: 'prop_empty_1',
      group_id: 'grp_1',
      day_of_week: 2,
      slot_order: 3,
      old_subject_id: null, // Proposal started from empty slot
      new_subject_id: 'physics',
      status: 'PENDING',
      proposed_by: 'usr_1',
    };

    const result = await applyScheduleProposalToActiveSchedule(proposalFromEmpty, 'admin_1', mockEnv);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.conflict).toBe(true);
      expect(result.currentSubjectId).toBe('math');
      expect(result.expectedSubjectId).toBeNull();
    }
    expect(updatedStatus).toBe('CONFLICTED');
  });

  it('TA-26: handleGetAcademicArchive returns contents created by user across any group', async () => {
    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          bind: () => ({
            all: async () => {
              if (sql.includes('FROM contents')) {
                return {
                  results: [
                    {
                      id: 'cnt_past_1',
                      group_id: 'old_class_9',
                      study_date: '2025-05-10',
                      subject_id: 'science',
                      type: 'LESSON',
                      title: 'درس قديم من الصف التاسع',
                      description: 'شرح سابق',
                      author_name: 'طالب مجد',
                      author_grade_section: '9 - أ',
                      views_count: 50,
                      useful_count: 20,
                      created_at: 1680000000,
                      subject_name: 'علوم',
                      subject_icon: '🔬',
                      color_hex: '#4CAF50',
                      group_name: 'الصف التاسع أ',
                    },
                  ],
                };
              }
              if (sql.includes('FROM content_media')) {
                return {
                  results: [
                    { id: 'med_1', page_order: 1, url: 'https://example.com/p1.jpg', mime_type: 'image/jpeg', file_size: 2048 },
                  ],
                };
              }
              return { results: [] };
            },
          }),
        }),
      },
    } as unknown as Env;

    const user: UserContext = {
      userId: 'usr_student_old',
      phoneNumber: '771112233',
      fullName: 'طالب مجد',
      gradeId: 10,
      sectionId: 'B',
      role: 'STUDENT',
    };

    const res = await handleGetAcademicArchive(user, mockEnv);
    expect(res.status).toBe(200);
    const data = await res.json() as any;
    expect(data.success).toBe(true);
    expect(data.contents).toHaveLength(1);
    expect(data.contents[0].id).toBe('cnt_past_1');
    expect(data.contents[0].title).toBe('درس قديم من الصف التاسع');
    expect(data.contents[0].media).toHaveLength(1);
  });

  it('TA-27: handleDashboardCreateHoliday rejects invalid date format', async () => {
    const mockEnv = {
      DB: {
        prepare: () => ({
          bind: () => ({
            run: async () => ({ success: true }),
          }),
        }),
      },
    } as unknown as Env;

    const reqInvalid = new Request('http://localhost/api/dashboard/holidays', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: 'إجازة تجريبية',
        eventDate: 'not-a-valid-date',
        description: 'مبرر الإجازة',
      }),
    });

    const res = await handleDashboardCreateHoliday(reqInvalid, 'owner_1', mockEnv);
    expect(res.status).toBe(400);
    const data = await res.json() as any;
    expect(data.success).toBe(false);
    expect(data.error.message).toContain('صحيح');
  });

  it('TA-28: sanitizePublicRole conceals SYSTEM_OWNER in handleGetGroupMembers and mapGroup', async () => {
    // 1. Test mapGroup
    const groupWithLegacyOwnerRole = {
      id: 'grp_1',
      name: 'شعبة أ',
      type: 'CLASS',
      role: 'SYSTEM_OWNER',
    };
    const mapped = mapGroup(groupWithLegacyOwnerRole);
    expect(mapped.role).toBe(Role.ADMIN);
    expect(mapped.role).not.toBe('SYSTEM_OWNER');

    // 2. Test handleGetGroupMembers
    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          bind: () => ({
            first: async () => ({ role: 'ADMIN', status: 'ACTIVE' }),
            all: async () => ({
              results: [
                {
                  user_id: 'usr_owner',
                  full_name: 'مالك المنظومة',
                  phone_number: '777777777',
                  member_role: 'SYSTEM_OWNER', // legacy role
                  global_role: 'SYSTEM_OWNER',
                  status: 'ACTIVE',
                  joined_at: 1700000000,
                  grade_id: 1,
                  section_id: 'A',
                },
              ],
            }),
          }),
        }),
      },
    } as unknown as Env;

    const caller: UserContext = {
      userId: 'usr_admin',
      phoneNumber: '770000000',
      fullName: 'مدير',
      gradeId: 1,
      sectionId: 'A',
      role: 'ADMIN',
    };

    const res = await handleGetGroupMembers('grp_1', caller, mockEnv);
    expect(res.status).toBe(200);
    const data = await res.json() as any;
    expect(data.success).toBe(true);
    expect(data.members).toHaveLength(1);
    expect(data.members[0].memberRole).toBe(Role.ADMIN);
    expect(data.members[0].memberRole).not.toBe('SYSTEM_OWNER');
    expect(data.members[0].globalRole).toBe(Role.ADMIN);
    expect(data.members[0].globalRole).not.toBe('SYSTEM_OWNER');
  });
});
