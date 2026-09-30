export function mapGroup(r: any, defaultRole = 'MEMBER') {
  return {
    id: r.id,
    name: r.name,
    type: r.type,
    description: r.description ?? null,
    icon: r.icon ?? null,
    role: r.role ?? defaultRole,
    memberCount: Number(r.member_count ?? r.memberCount ?? 1),
  };
}

export function mapScheduleSlot(r: any) {
  return {
    id: r.id,
    dayOfWeek: Number(r.day_of_week ?? r.dayOfWeek),
    slotOrder: Number(r.slot_order ?? r.slotOrder),
    subjectId: r.subject_id ?? r.subjectId,
    subjectName: r.subject_name ?? r.subjectName ?? '',
    subjectIcon: r.subject_icon ?? r.subjectIcon ?? '📚',
    colorHex: r.color_hex ?? r.colorHex ?? null,
    startTime: r.start_time ?? r.startTime ?? null,
    endTime: r.end_time ?? r.endTime ?? null,
  };
}

export function mapHomework(r: any) {
  return {
    id: r.id,
    groupId: r.group_id ?? r.groupId,
    studyDate: r.study_date ?? r.studyDate,
    dueDate: r.due_date ?? r.dueDate,
    subjectId: r.subject_id ?? r.subjectId,
    title: r.title,
    details: r.details ?? null,
    pageNumbers: r.page_numbers ?? r.pageNumbers ?? null,
    questionNumbers: r.question_numbers ?? r.questionNumbers ?? null,
    taskType: r.task_type ?? r.taskType ?? 'HOMEWORK',
    subjectName: r.subject_name ?? r.subjectName ?? '',
    subjectIcon: r.subject_icon ?? r.subjectIcon ?? '📝',
    colorHex: r.color_hex ?? r.colorHex ?? '#00E5FF',
    isCompleted: Boolean(r.is_completed ?? r.isCompleted ?? false),
    createdAt: Number(r.created_at ?? r.createdAt ?? Date.now()),
  };
}

export function mapExam(r: any) {
  return {
    id: r.id,
    groupId: r.group_id ?? r.groupId,
    examDate: r.exam_date ?? r.examDate,
    subjectId: r.subject_id ?? r.subjectId,
    title: r.title,
    requiredChapters: r.required_chapters ?? r.requiredChapters ?? null,
    notes: r.notes ?? null,
    subjectName: r.subject_name ?? r.subjectName ?? '',
    subjectIcon: r.subject_icon ?? r.subjectIcon ?? '🔴',
    colorHex: r.color_hex ?? r.colorHex ?? '#FF3366',
  };
}

export function mapSchoolEvent(r: any) {
  return {
    id: r.id,
    groupId: r.group_id ?? r.groupId,
    eventDate: r.event_date ?? r.eventDate,
    timeStr: r.time_str ?? r.timeStr ?? null,
    title: r.title,
    description: r.description ?? null,
    category: r.category ?? 'ACTIVITY',
    location: r.location ?? null,
    createdAt: Number(r.created_at ?? r.createdAt ?? Date.now()),
  };
}

export function mapContent(r: any, media: any[] = []) {
  return {
    id: r.id,
    groupId: r.group_id ?? r.groupId,
    studyDate: r.study_date ?? r.studyDate,
    subjectId: r.subject_id ?? r.subjectId,
    type: r.type ?? 'LESSON',
    title: r.title,
    description: r.description ?? null,
    authorName: r.author_name ?? r.authorName ?? '',
    authorGradeSection: r.author_grade_section ?? r.authorGradeSection ?? '',
    viewsCount: Number(r.views_count ?? r.viewsCount ?? 0),
    usefulCount: Number(r.useful_count ?? r.usefulCount ?? 0),
    createdAt: Number(r.created_at ?? r.createdAt ?? Date.now()),
    subjectName: r.subject_name ?? r.subjectName ?? '',
    subjectIcon: r.subject_icon ?? r.subjectIcon ?? '📚',
    colorHex: r.color_hex ?? r.colorHex ?? '#00E5FF',
    media: media.map(m => ({
      id: m.id,
      pageOrder: Number(m.page_order ?? m.pageOrder ?? 1),
      url: m.url,
      mimeType: m.mime_type ?? m.mimeType ?? 'image/jpeg',
      fileSize: Number(m.file_size ?? m.fileSize ?? 0),
    })),
  };
}

export function mapIssue(r: any) {
  return {
    id: r.id,
    groupId: r.group_id ?? r.groupId,
    subjectId: r.subject_id ?? r.subjectId ?? null,
    homeworkId: r.homework_id ?? r.homeworkId ?? null,
    examId: r.exam_id ?? r.examId ?? null,
    title: r.title,
    description: r.description ?? null,
    status: r.status ?? 'OPEN',
    bestCommentId: r.best_comment_id ?? r.bestCommentId ?? null,
    authorName: r.author_name ?? r.authorName ?? '',
    subjectName: r.subject_name ?? r.subjectName ?? null,
    subjectIcon: r.subject_icon ?? r.subjectIcon ?? null,
    commentsCount: Number(r.comments_count ?? r.commentsCount ?? 0),
    createdAt: Number(r.created_at ?? r.createdAt ?? Date.now()),
  };
}

export function mapIssueComment(r: any) {
  return {
    id: r.id,
    issueId: r.issue_id ?? r.issueId,
    userId: r.user_id ?? r.userId,
    authorName: r.author_name ?? r.authorName ?? '',
    comment: r.comment,
    isBestAnswer: Boolean(r.is_best_answer ?? r.isBestAnswer ?? false),
    createdAt: Number(r.created_at ?? r.createdAt ?? Date.now()),
  };
}

export function mapChatMessage(r: any) {
  return {
    id: r.id,
    groupId: r.group_id ?? r.groupId,
    senderId: r.sender_id ?? r.senderId,
    senderName: r.sender_name ?? r.senderName,
    senderGradeSection: r.sender_grade_section ?? r.senderGradeSection ?? '',
    text: r.text,
    timestamp: Number(r.timestamp ?? Date.now()),
    status: 'SENT',
  };
}
