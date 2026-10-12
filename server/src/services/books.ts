import { Env, UserContext } from '../env';
import { jsonResponse } from '../lib/response';

export async function handleGetBooks(gradeId: number, env: Env): Promise<Response> {
  const books = await env.DB.prepare(
    `SELECT b.id, b.grade_id, b.subject_id, b.title, b.edition, b.file_size_mb, b.file_url, b.thumbnail_url,
            COALESCE(s.name_ar, b.title) as subject_name, COALESCE(s.icon, '📚') as subject_icon
     FROM books b
     LEFT JOIN subjects s ON b.subject_id = s.id
     WHERE b.grade_id = ?
     ORDER BY b.title ASC`
  ).bind(gradeId).all();

  return jsonResponse({
    success: true,
    books: books.results,
  });
}
