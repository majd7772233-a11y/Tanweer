import { Env } from './env';
import { authenticateRequest } from './middleware/auth';
import { errorResponse, jsonResponse } from './lib/response';
import { handleLogin, handleRegister, handleLoginWithRecoveryCode } from './services/auth';
import {
  handleGetGroups,
  handleJoinGroupRequest,
  handleGetGroupMembers,
  handleUpdateGroupMemberRole,
  handleRemoveGroupMember,
  handleApproveGroupJoinRequest,
} from './services/groups';
import {
  handleGetSchedule,
  handleCreateScheduleSlot,
  handleSaveScheduleBatch,
  handleDeleteScheduleSlot,
  handleGetScheduleProposals,
  handleProposeScheduleChange,
  handleVoteScheduleProposal,
  handleApproveScheduleProposal,
  handleRejectScheduleProposal,
  handleGetScheduleVersions,
} from './services/schedule';
import { handleGetCalendarOverview, handleGetDayDetail } from './services/calendar';
import { handleCreateContent, handleVoteUseful, handleUpdateContent, handleDeleteContent, handleGetContents, handleGetAcademicArchive } from './services/content';
import { handleCreateHomework, handleGetHomeworks, handleToggleHomeworkCompletion, handleUpdateHomework, handleDeleteHomework } from './services/homework';
import { handleCreateExam, handleGetExams, handleUpdateExam, handleDeleteExam, handleToggleExamCompletion } from './services/exams';
import { handleCreateEvent, handleGetEvents, handleUpdateEvent, handleDeleteEvent } from './services/events';
import {
  handleAddIssueComment,
  handleCreateIssue,
  handleGetIssueDetails,
  handleGetIssues,
  handleMarkBestAnswer,
  handleDeleteIssue,
  handleDeleteIssueComment,
} from './services/issues';
import { handleRequestDeletion, handleVote } from './services/voting';
import { handleGetBooks } from './services/books';
import {
  handleCreateContentCorrection,
  handleGetGroupCorrections,
  handleApproveCorrection,
  handleRejectCorrection,
  handleGetCommunityDecisions,
  handleCreateCommunityDecision,
  handleVoteCommunityDecision,
  handleGetTeacherDashboardData,
  handleGetModeratorDashboardData,
  handleGetAdminDashboardData,
} from './services/governance';
import { handleGetGroupMessages, handlePostGroupMessage, handleDeleteGroupMessage } from './services/chat';
import { handleGetProfile, handleUpdateProfile } from './services/users';
import { handleGetMedia, handleUploadMedia } from './services/media';
import { handleCreateRoleRequest, handleGetMyRoleRequests, handleGetRoleMetadata, handleRedeemRoleCode } from './services/roleRequests';

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

  // Media Serving with Authentication & Membership Scoping
  if (path.startsWith('/api/v1/media/') && method === 'GET') {
    const mediaId = path.split('/')[4];
    return handleGetMedia(mediaId, request, env);
  }

  // Health check
  if (path === '/api/v1/health' || path === '/health') {
    return jsonResponse({ status: 'ok', app: 'Tanweer', timestamp: Date.now() });
  }

  // System Version & Release Check (Dynamic GitHub Release fetcher with fallback)
  if (path === '/api/v1/system/version' && method === 'GET') {
    try {
      const ghRes = await fetch('https://api.github.com/repos/majd7772233-a11y/Tanweer/releases/latest', {
        headers: {
          'User-Agent': 'Tanweer-Server/1.0',
          'Accept': 'application/vnd.github.v3+json'
        }
      });
      if (ghRes.ok) {
        const rel = await ghRes.json() as any;
        const tag = rel.tag_name || 'v1.2.0';
        const verName = tag.replace(/^v/i, '');
        const apkAsset = Array.isArray(rel.assets) ? rel.assets.find((a: any) => a.name?.endsWith('.apk')) : null;
        const parts = verName.split('.').map((p: string) => parseInt(p, 10) || 0);
        const calcCode = parts.length === 3 ? parts[0] * 100 + parts[1] * 10 + parts[2] : (parts.length === 2 ? parts[0] * 100 + parts[1] : 120);

        let sha256Hex: string | null = null;
        const shaAsset = Array.isArray(rel.assets)
          ? rel.assets.find((a: any) => a.name?.toLowerCase().includes('sha256') || a.name?.toLowerCase().includes('checksum'))
          : null;
        if (shaAsset && shaAsset.browser_download_url) {
          try {
            const shaRes = await fetch(shaAsset.browser_download_url);
            if (shaRes.ok) {
              const text = await shaRes.text();
              const m = text.match(/\b([a-fA-F0-9]{64})\b/);
              if (m) sha256Hex = m[1].toLowerCase();
            }
          } catch {}
        }
        if (!sha256Hex && rel.body) {
          const bodyMatch = rel.body.match(/\b([a-fA-F0-9]{64})\b/);
          if (bodyMatch) sha256Hex = bodyMatch[1].toLowerCase();
        }

        let dataGeneration = 'gen_initial_1';
        try {
          const genRow = await env.DB.prepare('SELECT value FROM system_settings WHERE key = ?').bind('data_generation').first<{ value: string }>();
          if (genRow?.value) dataGeneration = genRow.value;
        } catch {}

        return jsonResponse({
          currentVersion: verName,
          versionCode: calcCode,
          dataGeneration,
          minSupportedVersion: '1.0.0',
          minSupportedVersionCode: 1,
          isUpdateRequired: false,
          releaseDate: rel.published_at ? rel.published_at.slice(0, 10) : new Date().toISOString().slice(0, 10),
          changelog: rel.body ? rel.body.split('\n').map((l: string) => l.trim().replace(/^[-*•]\s*/, '')).filter((l: string) => l.length > 0) : [],
          downloadUrl: apkAsset ? apkAsset.browser_download_url : (rel.html_url || 'https://github.com/majd7772233-a11y/Tanweer/releases/latest'),
          sha256: sha256Hex,
        });
      }
    } catch (_: any) {}

    let fallbackDataGeneration = 'gen_initial_1';
    try {
      const genRow = await env.DB.prepare('SELECT value FROM system_settings WHERE key = ?').bind('data_generation').first<{ value: string }>();
      if (genRow?.value) fallbackDataGeneration = genRow.value;
    } catch {}

    return jsonResponse({
      currentVersion: '1.2.0',
      versionCode: 120,
      dataGeneration: fallbackDataGeneration,
      minSupportedVersion: '1.0.0',
      minSupportedVersionCode: 1,
      isUpdateRequired: false,
      releaseDate: '2026-10-06',
      changelog: [
        '✨ نظام الحوكمة الموحد والتصويت الشامل على القرارات المدرسية',
        '📷 ماسح السبورة الذكي عالي الدقة ومعالجة التباين التلقائي',
        '📚 مكتبة المناهج الذكية مع إدارة متقدمة لتخزين الكتب وحجم الذاكرة',
        '🛡️ محرك الصلاحيات الهرمي وتأمين الرتب المدرسية',
        '🚀 توافق متقدم مع أجهزة Android بدءاً من Android 6.0 (API 23+) وما فوق',
        '⚡ بنية مزامنة متكاملة Offline-First تضمن موثوقية العمل دون انقطاع'
      ],
      downloadUrl: 'https://github.com/majd7772233-a11y/Tanweer/releases/latest',
      sha256: '9f8337ec80c50d3663a8e7e174b5c777a834241e3d366a6a70a0006ee7885b00'
    });
  }

  // Unified Role Metadata (public)
  if (path === '/api/v1/roles/metadata' && method === 'GET') {
    return handleGetRoleMetadata();
  }

  // Authenticated routes
  const { user, error } = await authenticateRequest(request, env);
  if (error || !user) return error!;

  // Role Upgrade Requests
  if (path === '/api/v1/role-requests' && method === 'POST') {
    return handleCreateRoleRequest(user, request, env);
  }
  if (path === '/api/v1/role-requests/my' && method === 'GET') {
    return handleGetMyRoleRequests(user, env);
  }
  if ((path === '/api/v1/role-requests/redeem' || path === '/api/v1/roles/redeem') && method === 'POST') {
    return handleRedeemRoleCode(user, request, env);
  }

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
  if (path.startsWith('/api/v1/groups/') && path.endsWith('/members') && method === 'GET') {
    const groupId = path.split('/')[4];
    return handleGetGroupMembers(groupId, user, env);
  }
  if (path.startsWith('/api/v1/groups/') && path.includes('/members/') && path.endsWith('/role') && method === 'POST') {
    const parts = path.split('/');
    const groupId = parts[4];
    const targetUserId = parts[6];
    return handleUpdateGroupMemberRole(groupId, targetUserId, user, request, env);
  }
  if (path.startsWith('/api/v1/groups/') && path.includes('/members/') && (path.endsWith('/remove') || method === 'DELETE')) {
    const parts = path.split('/');
    const groupId = parts[4];
    const targetUserId = parts[6];
    return handleRemoveGroupMember(groupId, targetUserId, false, user, env);
  }
  if (path.startsWith('/api/v1/groups/') && path.includes('/members/') && path.endsWith('/ban') && method === 'POST') {
    const parts = path.split('/');
    const groupId = parts[4];
    const targetUserId = parts[6];
    return handleRemoveGroupMember(groupId, targetUserId, true, user, env);
  }
  if (path.startsWith('/api/v1/groups/') && path.includes('/requests/') && path.endsWith('/approve') && method === 'POST') {
    const parts = path.split('/');
    const groupId = parts[4];
    const targetUserId = parts[6];
    return handleApproveGroupJoinRequest(groupId, targetUserId, user, env);
  }

  // Group Chat Messages
  if (path.startsWith('/api/v1/groups/') && path.includes('/messages/')) {
    const parts = path.split('/');
    const groupId = parts[4];
    const messageId = parts[6];
    if (method === 'DELETE') {
      return handleDeleteGroupMessage(groupId, messageId, user, env);
    }
  }
  if (path.startsWith('/api/v1/groups/') && path.endsWith('/messages')) {
    const groupId = path.split('/')[4];
    if (method === 'GET') {
      return handleGetGroupMessages(groupId, user, env);
    }
    if (method === 'POST') {
      return handlePostGroupMessage(groupId, user, request, env);
    }
  }

  // Schedule CRUD & Lifecycle
  if (path.startsWith('/api/v1/schedule/') && path.endsWith('/batch') && method === 'POST') {
    const groupId = path.split('/')[4];
    return handleSaveScheduleBatch(groupId, user, request, env);
  }
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
  if (path.startsWith('/api/v1/schedule/') && path.endsWith('/proposals') && method === 'GET') {
    const groupId = path.split('/')[4];
    return handleGetScheduleProposals(groupId, user, env);
  }
  if (path.startsWith('/api/v1/schedule/') && path.endsWith('/versions') && method === 'GET') {
    const groupId = path.split('/')[4];
    return handleGetScheduleVersions(groupId, user, env);
  }
  if (path.startsWith('/api/v1/schedule/proposals/') && path.endsWith('/vote') && method === 'POST') {
    const proposalId = path.split('/')[5];
    return handleVoteScheduleProposal(proposalId, user, request, env);
  }
  if (path.startsWith('/api/v1/schedule/proposals/') && path.endsWith('/approve') && method === 'POST') {
    const proposalId = path.split('/')[5];
    return handleApproveScheduleProposal(proposalId, user, env);
  }
  if (path.startsWith('/api/v1/schedule/proposals/') && path.endsWith('/reject') && method === 'POST') {
    const proposalId = path.split('/')[5];
    return handleRejectScheduleProposal(proposalId, user, request, env);
  }
  if ((path === '/api/v1/schedule/proposals' || (path.startsWith('/api/v1/schedule/') && path.endsWith('/proposals'))) && method === 'POST') {
    return handleProposeScheduleChange(user, request, env);
  }
  if (path.startsWith('/api/v1/schedule/') && method === 'GET') {
    const groupId = path.split('/')[4];
    return handleGetSchedule(groupId, user, env);
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
  if ((path === '/api/v1/contents/archive' || path === '/api/v1/content/archive') && method === 'GET') {
    return handleGetAcademicArchive(user, env);
  }
  if (path === '/api/v1/contents' && method === 'GET') {
    const groupId = url.searchParams.get('groupId') || `class_${user.gradeId}_${user.sectionId}`;
    return handleGetContents(groupId, user, env);
  }
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
  if (path.startsWith('/api/v1/content/') && path.endsWith('/corrections') && method === 'POST') {
    const contentId = path.split('/')[4];
    return handleCreateContentCorrection(contentId, user, request, env);
  }
  const contentParts = path.split('/');
  if (path.startsWith('/api/v1/content/') && contentParts.length === 5) {
    const contentId = contentParts[4];
    if (method === 'PUT' || method === 'PATCH' || method === 'POST') {
      return handleUpdateContent(contentId, user, request, env);
    }
    if (method === 'DELETE') {
      return handleDeleteContent(contentId, user, env);
    }
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
    return handleToggleHomeworkCompletion(hwId, user, request, env);
  }
  if (path.startsWith('/api/v1/homeworks/') && !path.endsWith('/toggle')) {
    const hwId = path.split('/')[4];
    if (method === 'PUT' || method === 'PATCH' || method === 'POST') {
      return handleUpdateHomework(hwId, user, request, env);
    }
    if (method === 'DELETE') {
      return handleDeleteHomework(hwId, user, env);
    }
  }

  // Exams
  if (path === '/api/v1/exams' && method === 'GET') {
    const groupId = url.searchParams.get('groupId') || `class_${user.gradeId}_${user.sectionId}`;
    return handleGetExams(groupId, user, env);
  }
  if (path === '/api/v1/exams' && method === 'POST') {
    return handleCreateExam(user, request, env);
  }
  if (path.startsWith('/api/v1/exams/')) {
    const parts = path.split('/');
    const examId = parts[4];
    if (parts[5] === 'toggle-completion' && method === 'POST') {
      return handleToggleExamCompletion(examId, user, request, env);
    }
    if (method === 'PUT' || method === 'PATCH') {
      return handleUpdateExam(examId, user, request, env);
    }
    if (method === 'DELETE') {
      return handleDeleteExam(examId, user, env);
    }
    if (method === 'POST') {
      return handleUpdateExam(examId, user, request, env);
    }
  }

  // Events
  if (path === '/api/v1/events' && method === 'GET') {
    const groupId = url.searchParams.get('groupId') || `class_${user.gradeId}_${user.sectionId}`;
    return handleGetEvents(groupId, user, env);
  }
  if (path === '/api/v1/events' && method === 'POST') {
    return handleCreateEvent(user, request, env);
  }
  if (path.startsWith('/api/v1/events/')) {
    const eventId = path.split('/')[4];
    if (method === 'PUT' || method === 'PATCH' || method === 'POST') {
      return handleUpdateEvent(eventId, user, request, env);
    }
    if (method === 'DELETE') {
      return handleDeleteEvent(eventId, user, env);
    }
  }

  // Issues
  if (path === '/api/v1/issues' && method === 'GET') {
    const groupId = url.searchParams.get('groupId') || `class_${user.gradeId}_${user.sectionId}`;
    return handleGetIssues(groupId, user, env);
  }
  if (path === '/api/v1/issues' && method === 'POST') {
    return handleCreateIssue(user, request, env);
  }
  if (path.startsWith('/api/v1/issues/') && path.includes('/comments/') && method === 'DELETE') {
    const parts = path.split('/');
    const issueId = parts[4];
    const commentId = parts[6];
    return handleDeleteIssueComment(issueId, commentId, user, env);
  }
  if (path.startsWith('/api/v1/issues/') && path.endsWith('/comments') && method === 'POST') {
    const issueId = path.split('/')[4];
    return handleAddIssueComment(issueId, user, request, env);
  }
  if (path.startsWith('/api/v1/issues/') && method === 'GET') {
    const issueId = path.split('/')[4];
    return handleGetIssueDetails(issueId, user, env);
  }
  if (path.startsWith('/api/v1/issues/') && method === 'DELETE') {
    const issueId = path.split('/')[4];
    return handleDeleteIssue(issueId, user, env);
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

  // Content Corrections
  if (path.startsWith('/api/v1/groups/') && path.endsWith('/corrections') && method === 'GET') {
    const groupId = path.split('/')[4];
    return handleGetGroupCorrections(groupId, user, env);
  }
  if (path.startsWith('/api/v1/corrections/') && path.endsWith('/approve') && method === 'POST') {
    const corrId = path.split('/')[4];
    return handleApproveCorrection(corrId, user, env);
  }
  if (path.startsWith('/api/v1/corrections/') && path.endsWith('/reject') && method === 'POST') {
    const corrId = path.split('/')[4];
    return handleRejectCorrection(corrId, user, env);
  }

  // Community Decisions & Voting Engine
  if (path.startsWith('/api/v1/groups/') && path.endsWith('/decisions')) {
    const groupId = path.split('/')[4];
    if (method === 'GET') {
      return handleGetCommunityDecisions(groupId, user, env);
    }
    if (method === 'POST') {
      return handleCreateCommunityDecision(groupId, user, request, env);
    }
  }
  if (path.startsWith('/api/v1/decisions/') && path.endsWith('/vote') && method === 'POST') {
    const decisionId = path.split('/')[4];
    return handleVoteCommunityDecision(decisionId, user, request, env);
  }

  // In-App Role Dashboards
  if (path === '/api/v1/dashboards/teacher' && method === 'GET') {
    return handleGetTeacherDashboardData(user, env);
  }
  if (path === '/api/v1/dashboards/moderator' && method === 'GET') {
    return handleGetModeratorDashboardData(user, env);
  }
  if (path === '/api/v1/dashboards/admin' && method === 'GET') {
    return handleGetAdminDashboardData(user, env);
  }

  return errorResponse('NOT_FOUND', 'المسار المطلوب غير موجود', 404);
}
