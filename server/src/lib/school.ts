export async function getCurrentAcademicYearId(db: D1Database): Promise<string> {
  try {
    const row = await db.prepare('SELECT id FROM academic_years WHERE is_current = 1 LIMIT 1').first<{ id: string }>();
    if (row?.id) return row.id;
  } catch (_: any) {}

  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1; // 1 to 12
  return month >= 8 ? `${year}-${year + 1}` : `${year - 1}-${year}`;
}
