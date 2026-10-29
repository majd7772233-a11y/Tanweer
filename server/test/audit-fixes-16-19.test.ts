import { describe, it, expect } from 'vitest';
import { handleReissueUserCredentials, handleGetDashboardMedia, handleGetDashboardContentDetail } from '../src/services/dashboard';
import { handleProposeScheduleChange, handleApproveScheduleProposal, applyScheduleProposalToActiveSchedule } from '../src/services/schedule';
import { handleLoginWithRecoveryCode } from '../src/services/auth';
import { Env, UserContext } from '../src/env';
import { hashString } from '../src/lib/crypto';

describe('Audit Fixes 16-19 (TA-16 to TA-19)', () => {
  it('TA-17: handleGetDashboardMedia returns private, no-store Cache-Control header', async () => {
    const mockEnv = {
      DB: {
        prepare: () => ({
          bind: () => ({
            first: async () => ({
              mime_type: 'image/png',
              data: new Uint8Array([137, 80, 78, 71]),
            }),
          }),
        }),
      },
    } as unknown as Env;

    const res = await handleGetDashboardMedia('test-media-123.png', mockEnv);
    expect(res.status).toBe(200);
    expect(res.headers.get('Cache-Control')).toBe('private, no-store, no-cache, must-revalidate');
    expect(res.headers.get('Pragma')).toBe('no-cache');
  });

  it('TA-18: handleGetDashboardContentDetail provides unified DTO and media in both data.media and data.content.media', async () => {
    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          bind: () => ({
            first: async () => {
              if (sql.includes('FROM contents')) {
                return {
                  id: 'cnt_1',
                  group_id: 'grp_1',
                  group_name: 'شعبة أ',
                  study_date: '2026-10-29',
                  subject_id: 'math',
                  subject_name: 'رياضيات',
                  type: 'LESSON',
                  title: 'درس الجبر',
                  description: 'شرح المعادلات',
                  created_by: 'usr_1',
                  author_name: 'أحمد',
                  author_phone: '770000000',
                  status: 'APPROVED',
                  version: 1,
                  views_count: 42,
                  useful_count: 15,
                  is_pinned: 0,
                  created_at: 1700000000,
                  updated_at: 1700000000,
                };
              }
              return null;
            },
            all: async () => {
              if (sql.includes('FROM content_media')) {
                return {
                  results: [
                    {
                      id: 'med_1',
                      page_order: 1,
                      object_key: 'obj_1',
                      url: 'https://example.com/1.jpg',
                      mime_type: 'image/jpeg',
                      file_size: 1024,
                      is_primary: 1,
                    },
                  ],
                };
              }
              return { results: [] };
            },
          }),
        }),
      },
    } as unknown as Env;

    const res = await handleGetDashboardContentDetail('cnt_1', mockEnv);
    expect(res.status).toBe(200);
    const data = await res.json() as any;
    expect(data.success).toBe(true);

    // Verified root media and content.media
    expect(data.media).toBeDefined();
    expect(data.media.length).toBe(1);
    expect(data.content.media).toBeDefined();
    expect(data.content.media.length).toBe(1);

    // Verified unified property names
    expect(data.content.subjectName).toBe('رياضيات');
    expect(data.content.subject_name).toBe('رياضيات');
    expect(data.content.studyDate).toBe('2026-10-29');
    expect(data.content.study_date).toBe('2026-10-29');
    expect(data.content.viewsCount).toBe(42);
    expect(data.content.view_count).toBe(42);
    expect(data.content.usefulCount).toBe(15);
    expect(data.content.likes_count).toBe(15);
  });

  it('TA-16: handleReissueUserCredentials reissues single-use recovery code and revokes existing sessions without revealing old password', async () => {
    let deletedSessionsUserId: string | null = null;
    let insertedTokenRecord: any = null;
    let updatedUserRecoveryHash: string | null = null;

    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          bind: (...params: any[]) => ({
            first: async () => {
              if (sql.includes('FROM users')) {
                return {
                  id: 'usr_target',
                  full_name: 'طالب مجد',
                  phone_number: '771234567',
                  role: 'STUDENT',
                };
              }
              return null;
            },
            run: async () => {
              if (sql.includes('DELETE FROM sessions')) {
                deletedSessionsUserId = params[0];
              }
              if (sql.includes('INSERT INTO user_recovery_tokens')) {
                insertedTokenRecord = params;
              }
              if (sql.includes('UPDATE users SET recovery_code_hash')) {
                updatedUserRecoveryHash = params[0];
              }
              return { success: true };
            },
          }),
          run: async () => ({ success: true }),
        }),
      },
      SESSION_PEPPER: 'test-pepper-123',
    } as unknown as Env;

    const req = new Request('http://localhost/api/dashboard/users/usr_target/reissue-credentials', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        type: 'RECOVERY_CODE',
        expiresInHours: 24,
        revokeExistingSessions: true,
      }),
    });

    const res = await handleReissueUserCredentials('usr_target', 'owner_1', req, mockEnv);
    expect(res.status).toBe(200);
    const data = await res.json() as any;
    expect(data.success).toBe(true);
    expect(data.credentialType).toBe('RECOVERY_CODE');
    expect(data.temporarySecret).toMatch(/^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/);
    expect(data.singleUse).toBe(true);
    expect(data.sessionsRevoked).toBe(true);

    // Old passwords and hashes are NOT exposed
    expect(data.password).toBeUndefined();
    expect(data.passwordHash).toBeUndefined();
    expect(data.oldPassword).toBeUndefined();

    // Verified database side-effects
    expect(deletedSessionsUserId).toBe('usr_target');
    expect(insertedTokenRecord).not.toBeNull();
    expect(updatedUserRecoveryHash).not.toBeNull();
  });

  it('TA-16: handleLoginWithRecoveryCode enforces single-use and burns code', async () => {
    let burnedRecoveryHash: string | null = null;
    let tokenIncremented = false;
    const pepper = 'test-pepper-123';
    const cleanCode = 'ABCDEFGHJKLMNPQR';
    const recoveryHash = await hashString(cleanCode, pepper);

    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          bind: (...params: any[]) => ({
            first: async () => {
              if (sql.includes('FROM users WHERE recovery_code_hash = ?')) {
                if (params[0] === recoveryHash) {
                  return {
                    id: 'usr_target',
                    phone_number: '771234567',
                    full_name: 'طالب مجد',
                    grade_id: 1,
                    section_id: 'A',
                    role: 'STUDENT',
                  };
                }
              }
              if (sql.includes('FROM user_recovery_tokens')) {
                return {
                  id: 'tok_1',
                  max_uses: 1,
                  uses_count: 0,
                  expires_at: Date.now() + 3600000,
                  revoked: 0,
                };
              }
              return null;
            },
            run: async () => {
              if (sql.includes('UPDATE user_recovery_tokens SET uses_count = uses_count + 1')) {
                tokenIncremented = true;
              }
              if (sql.includes('UPDATE users SET recovery_code_hash = ?')) {
                burnedRecoveryHash = params[0];
              }
              return { success: true };
            },
          }),
        }),
      },
      SESSION_PEPPER: pepper,
    } as unknown as Env;

    const req = new Request('http://localhost/api/auth/login/recovery-code', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        recoveryCode: 'ABCD-EFGH-JKLM-NPQR',
      }),
    });

    const res = await handleLoginWithRecoveryCode(req, mockEnv);
    expect(res.status).toBe(200);
    const data = await res.json() as any;
    expect(data.success).toBe(true);
    expect(tokenIncremented).toBe(true);
    expect(burnedRecoveryHash).toMatch(/^USED_\d+_/);
  });

  it('TA-19: handleProposeScheduleChange derives old_subject_id from server truth', async () => {
    let insertedProposal: any = null;
    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          bind: (...params: any[]) => ({
            first: async () => {
              if (sql.includes('FROM group_members')) {
                return { role: 'STUDENT', status: 'ACTIVE' };
              }
              if (sql.includes('FROM schedule_versions')) {
                return { id: 'sch_ver_active' };
              }
              if (sql.includes('FROM schedule_slots')) {
                return { subject_id: 'physics' }; // Server active slot is physics
              }
              return null;
            },
            all: async () => ({ results: [{ role: 'STUDENT' }] }),
            run: async () => {
              if (sql.includes('INSERT INTO schedule_proposals')) {
                insertedProposal = params;
              }
              return { success: true };
            },
          }),
        }),
      },
    } as unknown as Env;

    const user: UserContext = {
      userId: 'usr_student',
      phoneNumber: '770000001',
      fullName: 'طالب مجتهد',
      gradeId: 1,
      sectionId: 'A',
      role: 'STUDENT',
    };

    // Proposing chemistry -> should derive physics as old_subject_id from server truth
    const reqValid = new Request('http://localhost/api/schedule/proposals', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        groupId: 'grp_1',
        dayOfWeek: 1,
        slotOrder: 2,
        oldSubjectId: 'fake_client_old', // client sent fake old subject
        newSubjectId: 'chemistry',
        reason: 'تبديل حصة',
      }),
    });

    const resValid = await handleProposeScheduleChange(user, reqValid, mockEnv);
    expect(resValid.status).toBe(200);
    expect(insertedProposal).not.toBeNull();
    // Index 5 is old_subject_id: should be server truth 'physics', NOT 'fake_client_old'!
    expect(insertedProposal[5]).toBe('physics');
  });

  it('TA-19: applyScheduleProposalToActiveSchedule detects concurrency conflict and marks proposal CONFLICTED', async () => {
    let updatedStatus: string | null = null;
    const mockEnv = {
      DB: {
        prepare: (sql: string) => ({
          bind: (...params: any[]) => ({
            first: async () => {
              if (sql.includes('FROM schedule_proposals')) {
                return { status: 'PENDING', version_number: 1 };
              }
              if (sql.includes('FROM schedule_versions')) {
                return { id: 'sch_ver_curr', version_number: 1 };
              }
              return null;
            },
            all: async () => {
              if (sql.includes('FROM schedule_slots')) {
                // Active slot was changed to 'biology' in the meantime!
                return {
                  results: [
                    {
                      day_of_week: 1,
                      slot_order: 2,
                      subject_id: 'biology', // differs from proposal's expected 'math'
                      start_time: '08:00',
                      end_time: '08:45',
                    },
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

    const proposal = {
      id: 'prop_conflict_1',
      group_id: 'grp_1',
      day_of_week: 1,
      slot_order: 2,
      old_subject_id: 'math', // built upon math
      new_subject_id: 'chemistry',
      status: 'PENDING',
      proposed_by: 'usr_1',
    };

    const result = await applyScheduleProposalToActiveSchedule(proposal, 'admin_1', mockEnv);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.conflict).toBe(true);
      expect(result.currentSubjectId).toBe('biology');
      expect(result.expectedSubjectId).toBe('math');
    }
    expect(updatedStatus).toBe('CONFLICTED');
  });
});
