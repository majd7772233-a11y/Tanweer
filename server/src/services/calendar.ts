import { Env, UserContext } from '../env';
import { mapContent, mapExam, mapHomework, mapSchoolEvent } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { jsonResponse } from '../lib/response';

export async function handleGetCalendarOverview(
  groupId: string,
  yearMonth: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  // yearMonth format: "2026-09"
  const datePrefix = yearMonth ? `${yearMonth}%` : `${new Date().toISOString().slice(0, 7)}%`;

  // 1. Content counts per day
  const contentDays = await env.DB.prepare(
    `SELECT study_date as date, COUNT(*) as count
     FROM contents
     WHERE group_id = ? AND study_date LIKE ? AND status = 'PUBLISHED'
     GROUP BY study_date`
  ).bind(groupId, datePrefix).all<{ date: string; count: number }>();

  // 2. Full Exams for the calendar
  const exams = await env.DB.prepare(
    `SELECT e.id, e.group_id, e.exam_date, e.subject_id, e.title, e.required_chapters, e.notes,
            s.name_ar as subject_name, s.icon as subject_icon, s.color_hex
     FROM exams e
     LEFT JOIN subjects s ON e.subject_id = s.id
     WHERE e.group_id = ? AND e.exam_date LIKE ?
     ORDER BY e.exam_date ASC`
  ).bind(groupId, datePrefix).all();

  // 3. Full Events for the calendar
  const events = await env.DB.prepare(
    `SELECT id, group_id, event_date, time_str, title, description, category, location, created_at
     FROM events
     WHERE group_id = ? AND event_date LIKE ?
     ORDER BY event_date ASC`
  ).bind(groupId, datePrefix).all();

  // 4. Full Homeworks for the calendar
  const homeworks = await env.DB.prepare(
    `SELECT h.id, h.group_id, h.study_date, h.due_date, h.subject_id, h.title, h.details,
            h.page_numbers, h.question_numbers, h.task_type, h.media_urls, h.created_at,
            s.name_ar as subject_name, s.icon as subject_icon, s.color_hex,
            (SELECT 1 FROM homework_completions hc WHERE hc.homework_id = h.id AND hc.user_id = ?) as is_completed
     FROM homeworks h
     LEFT JOIN subjects s ON h.subject_id = s.id
     WHERE h.group_id = ? AND h.due_date LIKE ?
     ORDER BY h.due_date ASC`
  ).bind(user.userId, groupId, datePrefix).all();

  return jsonResponse({
    success: true,
    contentsByDate: (contentDays.results || []).map(r => ({ date: r.date, count: Number(r.count) })),
    exams: (exams.results || []).map(mapExam),
    events: (events.results || []).map(mapSchoolEvent),
    homeworks: (homeworks.results || []).map(mapHomework),
  });
}

export async function handleGetDayDetail(
  groupId: string,
  date: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const contents = await env.DB.prepare(
    `SELECT c.id, c.group_id, c.study_date, c.subject_id, c.type, c.title, c.description,
            c.author_name, c.author_grade_section, c.views_count, c.useful_count, c.created_at,
            s.name_ar as subject_name, s.icon as subject_icon, s.color_hex
     FROM contents c
     LEFT JOIN subjects s ON c.subject_id = s.id
     WHERE c.group_id = ? AND c.study_date = ? AND c.status = 'PUBLISHED'
     ORDER BY c.created_at ASC`
  ).bind(groupId, date).all();

  // For each content item, fetch media images and map to camelCase
  const contentList = [];
  for (const c of (contents.results as any[])) {
    const media = await env.DB.prepare(
      `SELECT id, page_order, url, mime_type, file_size FROM content_media WHERE content_id = ? ORDER BY page_order ASC`
    ).bind(c.id).all();
    contentList.push(mapContent(c, media.results || []));
  }

  const homeworks = await env.DB.prepare(
    `SELECT h.id, h.group_id, h.study_date, h.due_date, h.subject_id, h.title, h.details, h.page_numbers, h.question_numbers, h.task_type, h.media_urls, h.created_at,
            s.name_ar as subject_name, s.icon as subject_icon, s.color_hex,
            (SELECT 1 FROM homework_completions hc WHERE hc.homework_id = h.id AND hc.user_id = ?) as is_completed
     FROM homeworks h
     LEFT JOIN subjects s ON h.subject_id = s.id
     WHERE h.group_id = ? AND (h.study_date = ? OR h.due_date = ?)
     ORDER BY h.created_at ASC`
  ).bind(user.userId, groupId, date, date).all();

  const exams = await env.DB.prepare(
    `SELECT e.id, e.group_id, e.exam_date, e.subject_id, e.title, e.required_chapters, e.notes,
            s.name_ar as subject_name, s.icon as subject_icon, s.color_hex
     FROM exams e
     LEFT JOIN subjects s ON e.subject_id = s.id
     WHERE e.group_id = ? AND e.exam_date = ?`
  ).bind(groupId, date).all();

  const events = await env.DB.prepare(
    `SELECT id, group_id, event_date, time_str, title, description, category, location, created_at
     FROM events
     WHERE group_id = ? AND event_date = ?`
  ).bind(groupId, date).all();

  return jsonResponse({
    success: true,
    date,
    contents: contentList,
    homeworks: (homeworks.results || []).map(mapHomework),
    exams: (exams.results || []).map(mapExam),
    events: (events.results || []).map(mapSchoolEvent),
  });
}
