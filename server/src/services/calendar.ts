import { Env, UserContext } from '../env';
import { jsonResponse } from '../lib/response';

export async function handleGetCalendarOverview(groupId: string, yearMonth: string, env: Env): Promise<Response> {
  // yearMonth format: "2026-09"
  const datePrefix = yearMonth ? `${yearMonth}%` : `${new Date().toISOString().slice(0, 7)}%`;

  // Query content counts per day
  const contentDays = await env.DB.prepare(
    `SELECT study_date as date, COUNT(*) as count
     FROM contents
     WHERE group_id = ? AND study_date LIKE ? AND status = 'PUBLISHED'
     GROUP BY study_date`
  ).bind(groupId, datePrefix).all<{ date: string; count: number }>();

  // Query exams
  const exams = await env.DB.prepare(
    `SELECT exam_date as date, id, title, subject_id
     FROM exams
     WHERE group_id = ? AND exam_date LIKE ?`
  ).bind(groupId, datePrefix).all<{ date: string; id: string; title: string; subject_id: string }>();

  // Query events
  const events = await env.DB.prepare(
    `SELECT event_date as date, id, title, category
     FROM events
     WHERE group_id = ? AND event_date LIKE ?`
  ).bind(groupId, datePrefix).all<{ date: string; id: string; title: string; category: string }>();

  // Query homeworks
  const homeworks = await env.DB.prepare(
    `SELECT due_date as date, id, title, subject_id
     FROM homeworks
     WHERE group_id = ? AND due_date LIKE ?`
  ).bind(groupId, datePrefix).all<{ date: string; id: string; title: string; subject_id: string }>();

  return jsonResponse({
    success: true,
    contentsByDate: contentDays.results,
    exams: exams.results,
    events: events.results,
    homeworks: homeworks.results,
  });
}

export async function handleGetDayDetail(groupId: string, date: string, env: Env): Promise<Response> {
  const contents = await env.DB.prepare(
    `SELECT c.id, c.study_date, c.subject_id, c.type, c.title, c.description,
            c.author_name, c.author_grade_section, c.views_count, c.useful_count, c.created_at,
            s.name_ar as subject_name, s.icon as subject_icon, s.color_hex
     FROM contents c
     JOIN subjects s ON c.subject_id = s.id
     WHERE c.group_id = ? AND c.study_date = ? AND c.status = 'PUBLISHED'
     ORDER BY c.created_at ASC`
  ).bind(groupId, date).all();

  // For each content item, fetch media images
  const contentList = [];
  for (const c of (contents.results as any[])) {
    const media = await env.DB.prepare(
      `SELECT id, page_order, url, mime_type, file_size FROM content_media WHERE content_id = ? ORDER BY page_order ASC`
    ).bind(c.id).all();
    contentList.push({
      ...c,
      media: media.results,
    });
  }

  const homeworks = await env.DB.prepare(
    `SELECT h.id, h.study_date, h.due_date, h.subject_id, h.title, h.details, h.page_numbers, h.question_numbers, h.task_type,
            s.name_ar as subject_name, s.icon as subject_icon, s.color_hex
     FROM homeworks h
     JOIN subjects s ON h.subject_id = s.id
     WHERE h.group_id = ? AND (h.study_date = ? OR h.due_date = ?)
     ORDER BY h.created_at ASC`
  ).bind(groupId, date, date).all();

  const exams = await env.DB.prepare(
    `SELECT e.id, e.exam_date, e.subject_id, e.title, e.required_chapters, e.notes,
            s.name_ar as subject_name, s.icon as subject_icon, s.color_hex
     FROM exams e
     JOIN subjects s ON e.subject_id = s.id
     WHERE e.group_id = ? AND e.exam_date = ?`
  ).bind(groupId, date).all();

  const events = await env.DB.prepare(
    `SELECT id, event_date, time_str, title, description, category, location
     FROM events
     WHERE group_id = ? AND event_date = ?`
  ).bind(groupId, date).all();

  return jsonResponse({
    success: true,
    date,
    contents: contentList,
    homeworks: homeworks.results,
    exams: exams.results,
    events: events.results,
  });
}
