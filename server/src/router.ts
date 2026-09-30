import { Env } from './env';
import { authenticateRequest } from './middleware/auth';
import { errorResponse, jsonResponse } from './lib/response';
import { handleLogin, handleRegister, handleLoginWithRecoveryCode } from './services/auth';
import { handleGetGroups, handleJoinGroupRequest } from './services/groups';
import {
  handleGetSchedule,
  handleCreateScheduleSlot,
  handleDeleteScheduleSlot,
  handleProposeScheduleChange,
} from './services/schedule';
import { handleGetCalendarOverview, handleGetDayDetail } from './services/calendar';
import { handleCreateContent, handleVoteUseful } from './services/content';
import { handleCreateHomework, handleGetHomeworks, handleToggleHomeworkCompletion } from './services/homework';
import { handleCreateExam, handleGetExams } from './services/exams';
import { handleCreateEvent, handleGetEvents } from './services/events';
import {
  handleAddIssueComment,
  handleCreateIssue,
  handleGetIssueDetails,
  handleGetIssues,
  handleMarkBestAnswer,
} from './services/issues';
import { handleRequestDeletion, handleVote } from './services/voting';
import { handleGetBooks } from './services/books';
import { handleGetGroupMessages, handlePostGroupMessage } from './services/chat';
import { handleGetProfile, handleUpdateProfile } from './services/users';
import { handleGetMedia, handleUploadMedia } from './services/media';

export async function handleApiRoute(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const path = url.pathname;
  const method = request.method;

  // Public Endpoints
  if (path === '/api/v1/auth/register' && method === 'POST') {
    return handleRegister(request, env);
  }
  if (path === '/api/v1/auth/login' && method === 'POST') {
    return handleLogin(request, env);
  }
  if (path === '/api/v1/auth/recovery-login' && method === 'POST') {
    return handleLoginWithRecoveryCode(request, env);
  }

  // Public Media Serving (for images loaded by Coil / browsers)
  if (path.startsWith('/api/v1/media/') && method === 'GET') {
    const mediaId = path.split('/')[4];
    return handleGetMedia(mediaId, env);
  }

  // Health check
  if (path === '/api/v1/health' || path === '/health') {
    return jsonResponse({ status: 'ok', app: 'Tanweer', timestamp: Date.now() });
  }

  // Authenticated routes
  const { user, error } = await authenticateRequest(request, env);
  if (error || !user) return error!;

  // User Profile
  if ((path === '/api/v1/me' || path === '/api/v1/profile') && method === 'GET') {
    return handleGetProfile(user, env);
  }
  if ((path === '/api/v1/me' || path === '/api/v1/profile') && (method === 'PATCH' || method === 'PUT' || method === 'POST')) {
    return handleUpdateProfile(user, request, env);
  }

  // Groups
  if (path === '/api/v1/groups' && method === 'GET') {
    return handleGetGroups(user, env);
  }
  if (path.startsWith('/api/v1/groups/') && path.endsWith('/join-request') && method === 'POST') {
    const groupId = path.split('/')[4];
    return handleJoinGroupRequest(groupId, user, env);
  }

  // Group Chat Messages
  if (path.startsWith('/api/v1/groups/') && path.endsWith('/messages')) {
    const groupId = path.split('/')[4];
    if (method === 'GET') {
      return handleGetGroupMessages(groupId, user, env);
    }
    if (method === 'POST') {
      return handlePostGroupMessage(groupId, user, request, env);
    }
  }

  // Schedule CRUD
  if (path.startsWith('/api/v1/schedule/') && path.endsWith('/slots') && method === 'POST') {
    const groupId = path.split('/')[4];
    return handleCreateScheduleSlot(groupId, user, request, env);
  }
  if (path.startsWith('/api/v1/schedule/') && path.includes('/slots/') && method === 'DELETE') {
    // /api/v1/schedule/{groupId}/slots/{dayOfWeek}/{slotOrder}
    const parts = path.split('/');
    const groupId = parts[4];
    const dayOfWeek = parseInt(parts[6]);
    const slotOrder = parseInt(parts[7]);
    return handleDeleteScheduleSlot(groupId, dayOfWeek, slotOrder, user, env);
  }
  if (path.startsWith('/api/v1/schedule/') && method === 'GET') {
    const groupId = path.split('/')[4];
    return handleGetSchedule(groupId, user, env);
  }
  if (path === '/api/v1/schedule/proposals' && method === 'POST') {
    return handleProposeScheduleChange(user, request, env);
  }

  // Calendar & Day
  if (path === '/api/v1/calendar' && method === 'GET') {
    const groupId = url.searchParams.get('groupId') || `class_${user.gradeId}_${user.sectionId}`;
    const month = url.searchParams.get('month') || '';
    return handleGetCalendarOverview(groupId, month, user, env);
  }
  if (path.startsWith('/api/v1/day/') && method === 'GET') {
    const date = path.split('/')[4];
    const groupId = url.searchParams.get('groupId') || `class_${user.gradeId}_${user.sectionId}`;
    return handleGetDayDetail(groupId, date, user, env);
  }

  // Content / Lessons & Media Upload
  if (path === '/api/v1/media/upload' && method === 'POST') {
    return handleUploadMedia(user, request, env);
  }
  if (path === '/api/v1/content' && method === 'POST') {
    return handleCreateContent(user, request, env);
  }
  if (path.startsWith('/api/v1/content/') && path.endsWith('/useful') && method === 'POST') {
    const contentId = path.split('/')[4];
    return handleVoteUseful(contentId, user, env);
  }

  // Homeworks
  if (path === '/api/v1/homeworks' && method === 'GET') {
    const groupId = url.searchParams.get('groupId') || `class_${user.gradeId}_${user.sectionId}`;
    return handleGetHomeworks(groupId, user, env);
  }
  if (path === '/api/v1/homeworks' && method === 'POST') {
    return handleCreateHomework(user, request, env);
  }
  if (path.startsWith('/api/v1/homeworks/') && path.endsWith('/toggle') && method === 'POST') {
    const hwId = path.split('/')[4];
    return handleToggleHomeworkCompletion(hwId, user, env);
  }

  // Exams
  if (path === '/api/v1/exams' && method === 'GET') {
    const groupId = url.searchParams.get('groupId') || `class_${user.gradeId}_${user.sectionId}`;
    return handleGetExams(groupId, user, env);
  }
  if (path === '/api/v1/exams' && method === 'POST') {
    return handleCreateExam(user, request, env);
  }

  // Events
  if (path === '/api/v1/events' && method === 'GET') {
    const groupId = url.searchParams.get('groupId') || `class_${user.gradeId}_${user.sectionId}`;
    return handleGetEvents(groupId, user, env);
  }
  if (path === '/api/v1/events' && method === 'POST') {
    return handleCreateEvent(user, request, env);
  }

  // Issues
  if (path === '/api/v1/issues' && method === 'GET') {
    const groupId = url.searchParams.get('groupId') || `class_${user.gradeId}_${user.sectionId}`;
    return handleGetIssues(groupId, user, env);
  }
  if (path === '/api/v1/issues' && method === 'POST') {
    return handleCreateIssue(user, request, env);
  }
  if (path.startsWith('/api/v1/issues/') && path.endsWith('/comments') && method === 'POST') {
    const issueId = path.split('/')[4];
    return handleAddIssueComment(issueId, user, request, env);
  }
  if (path.startsWith('/api/v1/issues/') && method === 'GET') {
    const issueId = path.split('/')[4];
    return handleGetIssueDetails(issueId, user, env);
  }
  if (path.startsWith('/api/v1/issues/') && path.includes('/best-answer/') && method === 'POST') {
    const issueId = path.split('/')[4];
    const commentId = path.split('/')[6];
    return handleMarkBestAnswer(issueId, commentId, user, env);
  }

  // Voting & Deletions
  if (path === '/api/v1/voting/deletion-request' && method === 'POST') {
    return handleRequestDeletion(user, request, env);
  }
  if (path.startsWith('/api/v1/voting/') && path.endsWith('/vote') && method === 'POST') {
    const reqId = path.split('/')[4];
    return handleVote(reqId, user, request, env);
  }

  // Books
  if (path === '/api/v1/books' && method === 'GET') {
    const gradeId = parseInt(url.searchParams.get('gradeId') || user.gradeId.toString());
    return handleGetBooks(gradeId, env);
  }

  return errorResponse('NOT_FOUND', 'المسار المطلوب غير موجود', 404);
}
