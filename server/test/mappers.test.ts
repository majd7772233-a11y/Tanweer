import { describe, it, expect } from 'vitest';
import {
  mapGroup,
  mapScheduleSlot,
  mapHomework,
  mapExam,
  mapSchoolEvent,
  mapContent,
  mapIssue,
  mapIssueComment,
  mapChatMessage,
} from '../src/lib/mappers';

describe('Server-to-Android Mappers (snake_case -> camelCase Contract)', () => {
  it('maps GroupEntity to GroupItem', () => {
    const raw = {
      id: 'class_10_A',
      name: 'الصف العاشر أ',
      type: 'CLASS',
      description: 'شعبة أ',
      icon: '🏫',
      role: 'ADMIN',
      member_count: 32,
    };
    const mapped = mapGroup(raw);
    expect(mapped.id).toBe('class_10_A');
    expect(mapped.memberCount).toBe(32);
    expect(mapped.role).toBe('ADMIN');
    expect((mapped as any).member_count).toBeUndefined();
  });

  it('maps ScheduleSlot correctly', () => {
    const raw = {
      id: 'slot_1',
      day_of_week: 0,
      slot_order: 1,
      subject_id: 'math',
      subject_name: 'الرياضيات',
      subject_icon: '📐',
      color_hex: '#00E5FF',
      start_time: '08:00',
      end_time: '08:45',
    };
    const mapped = mapScheduleSlot(raw);
    expect(mapped.dayOfWeek).toBe(0);
    expect(mapped.slotOrder).toBe(1);
    expect(mapped.subjectId).toBe('math');
    expect(mapped.subjectName).toBe('الرياضيات');
    expect(mapped.startTime).toBe('08:00');
    expect((mapped as any).day_of_week).toBeUndefined();
  });

  it('maps Homework correctly', () => {
    const raw = {
      id: 'hw_123',
      group_id: 'grp_1',
      study_date: '2026-09-30',
      due_date: '2026-10-01',
      subject_id: 'physics',
      title: 'واجب الكهرباء',
      details: 'حل ص 45',
      page_numbers: '45',
      question_numbers: '1-4',
      task_type: 'HOMEWORK',
      subject_name: 'الفيزياء',
      subject_icon: '⚡',
      color_hex: '#7C4DFF',
      is_completed: 1,
      created_at: 1700000000000,
    };
    const mapped = mapHomework(raw);
    expect(mapped.groupId).toBe('grp_1');
    expect(mapped.studyDate).toBe('2026-09-30');
    expect(mapped.dueDate).toBe('2026-10-01');
    expect(mapped.pageNumbers).toBe('45');
    expect(mapped.isCompleted).toBe(true);
    expect((mapped as any).group_id).toBeUndefined();
  });

  it('maps Content & Media items correctly', () => {
    const rawContent = {
      id: 'cnt_123',
      group_id: 'grp_1',
      study_date: '2026-09-30',
      subject_id: 'chemistry',
      type: 'LESSON',
      title: 'درس الكيمياء العضوية',
      description: 'شرح السبورة',
      author_name: 'أحمد علي',
      author_grade_section: 'أول ثانوي — أ',
      views_count: 15,
      useful_count: 8,
      created_at: 1700000000000,
      subject_name: 'الكيمياء',
      subject_icon: '🧪',
      color_hex: '#00E676',
    };
    const rawMedia = [
      {
        id: 'med_1',
        page_order: 1,
        url: 'https://tanweer.magd.workers.dev/api/v1/media/med_1',
        mime_type: 'image/jpeg',
        file_size: 450000,
      },
    ];
    const mapped = mapContent(rawContent, rawMedia);
    expect(mapped.groupId).toBe('grp_1');
    expect(mapped.authorGradeSection).toBe('أول ثانوي — أ');
    expect(mapped.viewsCount).toBe(15);
    expect(mapped.usefulCount).toBe(8);
    expect(mapped.media.length).toBe(1);
    expect(mapped.media[0].pageOrder).toBe(1);
    expect(mapped.media[0].fileSize).toBe(450000);
    expect((mapped as any).author_grade_section).toBeUndefined();
  });

  it('maps Chat message correctly', () => {
    const raw = {
      id: 'msg_1',
      group_id: 'grp_1',
      sender_id: 'usr_1',
      sender_name: 'محمد',
      sender_grade_section: 'تاسع — ب',
      text: 'السلام عليكم',
      timestamp: 1700000000000,
    };
    const mapped = mapChatMessage(raw);
    expect(mapped.senderId).toBe('usr_1');
    expect(mapped.senderName).toBe('محمد');
    expect(mapped.senderGradeSection).toBe('تاسع — ب');
    expect(mapped.status).toBe('SENT');
  });
});
