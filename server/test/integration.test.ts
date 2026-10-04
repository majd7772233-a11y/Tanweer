import { describe, it, expect, beforeEach } from 'vitest';
import app from '../src/index';
import { Env } from '../src/env';

// In-Memory mock D1 for full integration testing
class MockD1PreparedStatement {
  private db: MockD1Database;
  private sql: string;
  private params: any[];

  constructor(db: MockD1Database, sql: string, params: any[] = []) {
    this.db = db;
    this.sql = sql;
    this.params = params;
  }

  bind(...params: any[]) {
    return new MockD1PreparedStatement(this.db, this.sql, params);
  }

  async first<T = unknown>(): Promise<T | null> {
    const results = this.db.executeSql(this.sql, this.params);
    return (results[0] as T) || null;
  }

  async all<T = unknown>(): Promise<{ results: T[] }> {
    const results = this.db.executeSql(this.sql, this.params);
    return { results: results as T[] };
  }

  async run(): Promise<{ success: boolean }> {
    this.db.executeSql(this.sql, this.params);
    return { success: true };
  }
}

class MockD1Database {
  public tables: Record<string, any[]> = {
    users: [],
    sessions: [],
    devices: [],
    groups: [],
    group_members: [],
    subjects: [],
    schedule_versions: [],
    schedule_slots: [],
    schedule_proposals: [],
    contents: [],
    homeworks: [],
    homework_completions: [],
    exams: [],
    events: [],
    issues: [],
    issue_comments: [],
    group_messages: [],
    media_blobs: [],
  };

  prepare(sql: string) {
    return new MockD1PreparedStatement(this, sql);
  }

  executeSql(sql: string, params: any[]): any[] {
    const trimmed = sql.trim();
    const normalized = trimmed.replace(/\s+/g, ' ');

    // INSERT INTO
    if (/^INSERT INTO/i.test(normalized)) {
      const match = normalized.match(/INSERT (?:OR IGNORE )?INTO (\w+)/i);
      if (match) {
        const table = match[1];
        if (!this.tables[table]) this.tables[table] = [];

        // Parse column names if provided
        const colMatch = normalized.match(/\((.*?)\)\s*VALUES/i);
        const colNames = colMatch
          ? colMatch[1].split(',').map((c) => c.replace(/["`\s]/g, ''))
          : null;

        const row: any = {};
        if (colNames) {
          colNames.forEach((col, idx) => {
            row[col] = params[idx];
          });
        } else {
          params.forEach((val, idx) => {
            row[`col_${idx}`] = val;
          });
        }

        this.tables[table].push(row);
        return [row];
      }
    }

    // UPDATE
    if (/^UPDATE/i.test(normalized)) {
      const match = normalized.match(/UPDATE (\w+)/i);
      if (match) {
        const table = match[1];
        const rows = this.tables[table] || [];

        if (normalized.includes('WHERE id = ?')) {
          const id = params[params.length - 1];
          const found = rows.find((r) => r.id === id);

          if (found && normalized.includes('status = ?')) {
            found.status = params[0];
          }
        }

        return [];
      }
    }

    // DELETE
    if (/^DELETE FROM/i.test(normalized)) {
      const match = normalized.match(/DELETE FROM (\w+)/i);
      if (match) {
        const table = match[1];

        if (this.tables[table]) {
          if (normalized.includes('WHERE version_id = ? AND day_of_week = ? AND slot_order = ?')) {
            const [versionId, day, slot] = params;

            this.tables[table] = this.tables[table].filter(
              (r) =>
                !(
                  r.version_id === versionId &&
                  r.day_of_week === day &&
                  r.slot_order === slot
                )
            );
          } else if (
            normalized.includes('WHERE group_id = ? AND day_of_week = ? AND slot_order = ?')
          ) {
            const [groupId, day, slot] = params;

            this.tables[table] = this.tables[table].filter(
              (r) =>
                !(
                  r.group_id === groupId &&
                  r.day_of_week === day &&
                  r.slot_order === slot
                )
            );
          }
        }

        return [];
      }
    }

    // SELECT
    if (/^SELECT/i.test(normalized)) {
      const fromMatch = normalized.match(/FROM (\w+)/i);
      if (!fromMatch) return [];

      const table = fromMatch[1];
      let rows = [...(this.tables[table] || [])];

      // Handle JOIN query for sessions and users
      if (table.toLowerCase() === 'sessions' && normalized.includes('JOIN users')) {
        const tokenHash = params[0];

        const matchingSessions = (this.tables.sessions || []).filter(
          (s) => s.refresh_token_hash === tokenHash
        );

        return matchingSessions.map((s) => {
          const user = (this.tables.users || []).find((u) => u.id === s.user_id) || {};

          return {
            id: s.id,
            user_id: s.user_id,
            device_id: s.device_id,
            expires_at: s.expires_at,
            phone_number: user.phone_number,
            full_name: user.full_name,
            grade_id: user.grade_id,
            section_id: user.section_id,
            role: user.role || 'MEMBER',
          };
        });
      }

      if (normalized.includes('phone_number = ?')) {
        const phone = params[0];
        rows = rows.filter((r) => r.phone_number === phone);
      }

      if (
        normalized.includes('refresh_token_hash = ?') ||
        normalized.includes('token = ?')
      ) {
        const tokenHash = params[0];

        rows = rows.filter(
          (r) =>
            r.refresh_token_hash === tokenHash ||
            r.id === tokenHash
        );
      }

      if (
        normalized.includes('group_id = ?') &&
        !normalized.includes('user_id = ?')
      ) {
        const groupId = params[0];
        rows = rows.filter((r) => r.group_id === groupId);
      }

      if (/\bid = \?/.test(normalized)) {
        const id = params[0];
        rows = rows.filter((r) => r.id === id);
      }

      if (normalized.includes('checksum = ?')) {
        const checksum = params[0];
        rows = rows.filter((r) => r.checksum === checksum);
      }

      if (table.toLowerCase() === 'group_members') {
        // Find member with group_id and user_id and active status
        const groupId = params[0];
        const userId = params[1];

        const matching = (this.tables.group_members || []).filter(
          (m) =>
            m.group_id === groupId &&
            m.user_id === userId &&
            (m.status === 'ACTIVE' || !m.status)
        );

        return matching;
      }

      if (normalized.includes('user_id = ? AND group_id = ?')) {
        const [userId, groupId] = params;

        rows = rows.filter(
          (r) =>
            r.user_id === userId &&
            r.group_id === groupId
        );
      }

      if (normalized.includes('COUNT(*)') && normalized.includes('schedule_slots')) {
        const count = this.tables.schedule_slots?.length || 0;
        return [{ count }];
      }

      if (normalized.includes('WHERE grade_id = ? AND section_id = ?')) {
        const [gradeId, sectionId] = params;

        rows = rows.filter(
          (r) =>
            r.grade_id === gradeId &&
            r.section_id === sectionId
        );
      }

      return rows;
    }

    return [];
  }
}

describe('End-to-End System Integration Flow', () => {
  let env: Env;
  let mockDb: MockD1Database;
  let token = '';

  beforeEach(() => {
    mockDb = new MockD1Database();

    // Prepopulate system group & subjects for grade 10 A
    mockDb.tables.groups.push({
      id: 'class_10_A',
      name: 'الصف الأول الثانوي (أ)',
      grade_id: 10,
      section_id: 'A',
      group_type: 'CLASS',
      avatar_url: null,
      created_at: Date.now(),
    });

    mockDb.tables.subjects.push({
      id: 'sub_math',
      grade_id: 10,
      name_ar: 'الرياضيات',
      name_en: 'Mathematics',
      icon_name: 'calculate',
      color_hex: '#1E88E5',
    });

    env = {
      DB: mockDb as any,
      CHAT: {
        idFromName: () => 'mock-id',
        get: () => ({
          fetch: async () => new Response('ws'),
        }),
      } as any,
      JWT_SECRET: 'test-jwt-secret-key-12345678901234567890',
      PASSWORD_PEPPER: 'test-pepper-123',
    };
  });

  it('executes the full lifecycle: Register -> Login -> Group -> Schedule -> Media Upload -> Content -> Homework -> Exam -> Issue -> Chat', async () => {
    // 1. Register User
    const regReq = new Request('http://localhost/api/v1/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        fullName: 'عمر خالد المنصوري',
        phoneNumber: '771234567',
        password: 'Password@123',
        gradeId: 10,
        sectionId: 'A',
        role: 'STUDENT',
        deviceId: 'device-test-123',
      }),
    });

    const regRes = await app.fetch(regReq, env, {} as any);
    const regJson = (await regRes.json()) as any;

    expect(regRes.status).toBe(200);
    expect(regJson.token).toBeDefined();
    expect(regJson.user.fullName).toBe('عمر خالد المنصوري');

    token = regJson.token;

    // Automatically ensure group membership is active for the test user
    mockDb.tables.group_members.push({
      id: 'mem_1',
      group_id: 'class_10_A',
      user_id: regJson.user.id,
      role: 'STUDENT',
      status: 'ACTIVE',
      joined_at: Date.now(),
    });

    // 2. Login User
    const loginReq = new Request('http://localhost/api/v1/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        phoneNumber: '771234567',
        password: 'Password@123',
        deviceId: 'device-test-123',
      }),
    });

    const loginRes = await app.fetch(loginReq, env, {} as any);
    const loginJson = (await loginRes.json()) as any;

    expect(loginRes.status).toBe(200);
    expect(loginJson.token).toBeDefined();

    const authHeaders = {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    };

    // 3. Get User Profile & Groups
    const profileReq = new Request('http://localhost/api/v1/me', {
      method: 'GET',
      headers: authHeaders,
    });

    const profileRes = await app.fetch(profileReq, env, {} as any);
    expect(profileRes.status).toBe(200);

    const groupsReq = new Request('http://localhost/api/v1/groups', {
      method: 'GET',
      headers: authHeaders,
    });

    const groupsRes = await app.fetch(groupsReq, env, {} as any);
    expect(groupsRes.status).toBe(200);

    // 4. Schedule permissions & CRUD

    // 4A. Initial schedule setup: Any student can populate slots when the schedule is empty.
    const initialScheduleReq = new Request(
      'http://localhost/api/v1/schedule/class_10_A/slots',
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify({
          dayOfWeek: 1,
          slotOrder: 1,
          subjectId: 'sub_math',
          startTime: '08:00',
          endTime: '08:45',
        }),
      }
    );

    const initialScheduleRes = await app.fetch(
      initialScheduleReq,
      env,
      {} as any
    );
    expect(initialScheduleRes.status).toBe(200);

    // 4B. Once the schedule exists, normal students CANNOT edit official slots directly.
    const studentScheduleReq = new Request(
      'http://localhost/api/v1/schedule/class_10_A/slots',
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify({
          dayOfWeek: 2,
          slotOrder: 2,
          subjectId: 'sub_math',
          startTime: '09:00',
          endTime: '09:45',
        }),
      }
    );

    const studentScheduleRes = await app.fetch(
      studentScheduleReq,
      env,
      {} as any
    );

    expect(studentScheduleRes.status).toBe(403);

    const studentScheduleJson = (await studentScheduleRes.json()) as any;
    expect(studentScheduleJson.success).not.toBe(true);

    // 4C. The student can submit a schedule-change proposal instead.
    const proposalReq = new Request(
      'http://localhost/api/v1/schedule/proposals',
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify({
          groupId: 'class_10_A',
          dayOfWeek: 1,
          slotOrder: 1,
          oldSubjectId: 'sub_science',
          newSubjectId: 'sub_math',
          reason: 'يوجد تعارض في الحصة الأولى ونحتاج نقل الرياضيات إلى هذا الموعد.',
        }),
      }
    );

    const proposalRes = await app.fetch(
      proposalReq,
      env,
      {} as any
    );

    const proposalJson = (await proposalRes.json()) as any;

    expect(proposalRes.status).toBe(200);
    expect(proposalJson.success).toBe(true);
    expect(proposalJson.proposalId).toBeDefined();

    // Verify that the proposal was actually stored.
    expect(mockDb.tables.schedule_proposals).toHaveLength(1);
    expect(mockDb.tables.schedule_proposals[0]).toMatchObject({
      group_id: 'class_10_A',
      proposed_by: regJson.user.id,
      day_of_week: 1,
      slot_order: 1,
      new_subject_id: 'sub_math',
      status: 'PENDING',
    });

    // 4C. A schedule manager/moderator MAY modify the official schedule.
    const member = mockDb.tables.group_members.find(
      (m) =>
        m.id === 'mem_1' &&
        m.user_id === regJson.user.id
    );

    expect(member).toBeDefined();

    // Promote only for the schedule-manager portion of this integration test.
    member.role = 'MODERATOR';

    const managerAddSlotReq = new Request(
      'http://localhost/api/v1/schedule/class_10_A/slots',
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify({
          dayOfWeek: 1,
          slotOrder: 1,
          subjectId: 'sub_math',
          startTime: '08:00',
          endTime: '08:45',
        }),
      }
    );

    const managerAddSlotRes = await app.fetch(
      managerAddSlotReq,
      env,
      {} as any
    );

    const managerAddSlotJson = (await managerAddSlotRes.json()) as any;

    expect(managerAddSlotRes.status).toBe(200);
    expect(managerAddSlotJson.success).toBe(true);
    expect(managerAddSlotJson.slotId).toBeDefined();

    expect(mockDb.tables.schedule_versions).toHaveLength(1);
    expect(mockDb.tables.schedule_slots).toHaveLength(1);

    // 4D. Schedule manager can delete the official slot.
    const deleteSlotReq = new Request(
      'http://localhost/api/v1/schedule/class_10_A/slots/1/1',
      {
        method: 'DELETE',
        headers: authHeaders,
      }
    );

    const deleteSlotRes = await app.fetch(
      deleteSlotReq,
      env,
      {} as any
    );

    const deleteSlotJson = (await deleteSlotRes.json()) as any;

    expect(deleteSlotRes.status).toBe(200);
    expect(deleteSlotJson.success).toBe(true);
    expect(mockDb.tables.schedule_slots).toHaveLength(0);

    // Return the member to the real student's role for the remainder
    // of the end-to-end lifecycle.
    member.role = 'STUDENT';

    // 4E. Group members, including students, can still read the schedule.
    const getScheduleReq = new Request(
      'http://localhost/api/v1/schedule/class_10_A',
      {
        method: 'GET',
        headers: authHeaders,
      }
    );

    const getScheduleRes = await app.fetch(
      getScheduleReq,
      env,
      {} as any
    );

    expect(getScheduleRes.status).toBe(200);

    // 5. Upload Media with Valid JPEG Magic Numbers (FF D8 FF ...)
    const validJpegBytes = new Uint8Array([
      0xff,
      0xd8,
      0xff,
      0xe0,
      0x00,
      0x10,
      0x4a,
      0x46,
      0x49,
      0x46,
      0x00,
      0x01,
      0x01,
      0x01,
      0x00,
      0x60,
    ]);

    const uploadReq = new Request(
      'http://localhost/api/v1/media/upload',
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'image/jpeg',
        },
        body: validJpegBytes.buffer,
      }
    );

    const uploadRes = await app.fetch(
      uploadReq,
      env,
      {} as any
    );

    const uploadJson = (await uploadRes.json()) as any;

    expect(uploadRes.status).toBe(200);
    expect(uploadJson.success).toBe(true);
    expect(uploadJson.url).toContain('/api/v1/media/');

    // 6. Create Content / Lesson with uploaded Media
    const contentReq = new Request(
      'http://localhost/api/v1/content',
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify({
          groupId: 'class_10_A',
          subjectId: 'sub_math',
          date: '2026-10-01',
          title: 'درس المعادلات التربيعية',
          description: 'شرح طريقة إكمال المربع والحل بالدستور',
          mediaUrls: [uploadJson.url],
        }),
      }
    );

    const contentRes = await app.fetch(
      contentReq,
      env,
      {} as any
    );

    expect(contentRes.status).toBe(200);

    // 7. Create Homework
    const hwReq = new Request(
      'http://localhost/api/v1/homeworks',
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify({
          groupId: 'class_10_A',
          subjectId: 'sub_math',
          dueDate: '2026-10-03',
          title: 'واجب ص 45 تدريب 1 و 2',
          description: 'حل المسائل في دفتر الواجبات',
        }),
      }
    );

    const hwRes = await app.fetch(
      hwReq,
      env,
      {} as any
    );

    expect(hwRes.status).toBe(200);

    const getHwReq = new Request(
      'http://localhost/api/v1/homeworks?groupId=class_10_A',
      {
        method: 'GET',
        headers: authHeaders,
      }
    );

    const getHwRes = await app.fetch(
      getHwReq,
      env,
      {} as any
    );

    expect(getHwRes.status).toBe(200);

    // 8. Create Exam
    const examReq = new Request(
      'http://localhost/api/v1/exams',
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify({
          groupId: 'class_10_A',
          subjectId: 'sub_math',
          date: '2026-10-15',
          title: 'اختبار شهري أول',
          topics: 'الوحدة الأولى كاملة',
        }),
      }
    );

    const examRes = await app.fetch(
      examReq,
      env,
      {} as any
    );

    expect(examRes.status).toBe(200);

    // 9. Create Issue & Add Comment
    const issueReq = new Request(
      'http://localhost/api/v1/issues',
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify({
          groupId: 'class_10_A',
          subjectId: 'sub_math',
          title: 'استفسار حول قانون المميز',
          description: 'متى يكون الجذران متساويين؟',
        }),
      }
    );

    const issueRes = await app.fetch(
      issueReq,
      env,
      {} as any
    );

    const issueJson = (await issueRes.json()) as any;

    expect(issueRes.status).toBe(200);

    const commentReq = new Request(
      `http://localhost/api/v1/issues/${issueJson.issueId || issueJson.id || 'iss_1'}/comments`,
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify({
          comment: 'عندما يكون المميز b^2 - 4ac مساوياً للصفر.',
        }),
      }
    );

    const commentRes = await app.fetch(
      commentReq,
      env,
      {} as any
    );

    expect(commentRes.status).toBe(200);

    // 10. Post Group Chat Message & Retrieve
    const chatMsgReq = new Request(
      'http://localhost/api/v1/groups/class_10_A/messages',
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify({
          messageText: 'السلام عليكم يا شباب، متى موعد تسليم الواجب؟',
        }),
      }
    );

    const chatMsgRes = await app.fetch(
      chatMsgReq,
      env,
      {} as any
    );

    expect(chatMsgRes.status).toBe(200);

    const getChatReq = new Request(
      'http://localhost/api/v1/groups/class_10_A/messages',
      {
        method: 'GET',
        headers: authHeaders,
      }
    );

    const getChatRes = await app.fetch(
      getChatReq,
      env,
      {} as any
    );

    expect(getChatRes.status).toBe(200);
  });
});