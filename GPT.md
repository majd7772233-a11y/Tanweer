# GPT.md — Tanweer Internal Project Memory

> **Purpose:** This is an internal working notebook for the assistant, not a user manual or a final product specification. Read it before starting substantial Tanweer work.
>
> **Source-of-truth rule:** This document provides context only. The latest code and the exact branch HEAD in GitHub determine what actually exists. Recheck the requested branch, files, tests, and deployment state before making claims or changes.

---

## 0. Project identity

- **Project:** Tanweer.
- **Concept:** A collaborative school platform that replaces scattered WhatsApp class groups with structured academic information: lessons, homework, exams, events, calendars, schedules, Q&A, chat, books/PDFs, notifications, governance, and student contributions.
- **Core philosophy:** Students are contributors, not passive viewers. Student contributions are a first-class part of the product.
- **Problem being solved:** Lessons, homework, and school updates disappear inside chat conversations. Tanweer turns them into searchable, organized, dated, synchronized academic records.
- **Visual direction:** Liquid Glass with real translucency, blur, depth, water-like/3D surfaces, polished cards, and smooth motion. Avoid generic CRUD layouts.
- **Language:** Arabic first, English second. User-facing localization must be complete and natural; do not mix languages carelessly.

## 1. Repository and current project state

### Repository

- GitHub repository: majd7772233-a11y/Tanweer.
- Default branch: main.
- Current development branch for the active release-readiness work: work/release-readiness.
- Do not modify main unless the user explicitly asks.
- Main was last verified at 64143994070bcdc410cf044379fe2801da8f9a3c before the current work.
- The latest previously verified development HEAD before this notebook conversion was 010407e952a03a504c3920960f6673f1499e84a1. This is a historical checkpoint, not a substitute for checking the live branch HEAD.

### Previous foundational product/code changes

The earlier main-line baseline included:
1. Replacing the MORE bottom sheet with an Extra Sections Hub sub-screen and improving back navigation.
2. Improving selected global/group role permission behavior.
3. Cleaning group roles when a user's global role is downgraded.
4. Preventing SYSTEM_OWNER from being assigned as a group role.
5. Restricting direct TEACHER assignment to users whose global role and approval state permit it.
6. Adding pagination to multiple owner dashboard lists.
7. Making governance decision application execute its real database effect rather than only changing its status.
8. Moving quorum/threshold enforcement to the server and snapshotting eligible voters when a decision is created.

The release-readiness branch then accumulated additional Android reliability fixes, including group/date persistence, data-generation safety, transactional Room reconciliation, stricter response validation, and removal of destructive downgrade fallback. Re-read the branch before assuming any older issue remains unresolved.

### CI truth

- A successful Android CI run was previously verified for HEAD 9f32d4cb780f54b28f8ce0ce1365175ef4d6fd7f.
- That run built unit tests and a debug APK. It does not validate later commits.
- Release signing is a separate CI path and must not be inferred from a successful debug build.
- Never claim real-device testing unless the user actually performed it and reported the result.

---

## 2. Non-negotiable product and engineering rules

### 2.1 Role hierarchy

The formal hierarchy is:

STUDENT → MODERATOR → TEACHER → ADMIN → SYSTEM_OWNER

When the user says “priority system”, they mean role hierarchy, not content priority. Do not describe content prioritization as a missing feature because of that phrase.

### 2.2 Students are contributors

The product philosophy allows students to:
- Publish lessons and academic content.
- Upload board photos and files.
- Add homework, exams, and events.
- Ask and answer questions.
- Participate in group chat.
- Propose schedule changes.
- Participate in governance and voting.
- Request role upgrades.

Do not redesign Tanweer as an admin-only content system.

### 2.3 Content ownership

- The original author must be able to edit/delete content they own, subject to the relevant data and policy rules.
- Editing or deleting somebody else's content requires the appropriate permission or community/governance path.
- Preserve user ownership even when the author is a student.

### 2.4 Schedule policy

- Any member may help establish the first schedule.
- Once a stable official schedule exists, unauthorized changes must go through a proposal/vote or an authorized role.
- Never silently mutate the official schedule without an auditable path.

### 2.5 Teacher verification

Teacher promotion is not a simple group-role selector:
1. User requests promotion.
2. A manual review/verification step occurs.
3. The system owner verifies the person through WhatsApp.
4. The owner issues an 8-digit code valid for 24 hours.
5. The user completes verification in the app.

Known WhatsApp contact used by this flow: +967 735465673. Do not expose this unnecessarily in user-facing output.

### 2.6 System owner

- SYSTEM_OWNER is the system owner, level 5.
- It cannot be assigned as a group role.
- The owner dashboard is separate from Android user sessions.
- The expected secret is TANWEER_OWNER_SECRET.
- If this secret is missing, the owner control area must be disabled. Never introduce a default/fallback secret.
- The user has explicitly decided to keep TANWEER_OWNER_SECRET as the owner dashboard secret.

---

## 3. Architecture overview

### 3.1 Android application

- Package: com.magd.tanweer.
- Kotlin + Jetpack Compose.
- Central navigation/state is in TanweerApp.kt and TanweerViewModel.kt.
- Room/DAO provides local storage and cache.
- Repository and UnifiedMutationQueue handle server synchronization and queued mutations.
- PDF subsystem includes PdfBookManager and a substantial PDF reader.
- Key navigation tabs: TODAY, CALENDAR, HOMEWORK, EXAMS, ISSUES, MORE.
- Known sub-screens include: EXTRA_SECTIONS_HUB, GROUPS, LIBRARY, SCHEDULE, TIMELINE, PROFILE, SETTINGS, WHAT_DID_I_MISS, SEARCH, SUBJECT_KNOWLEDGE_BASE, ACADEMIC_HISTORY, TEACHER_DASHBOARD, MODERATOR_DASHBOARD, ADMIN_DASHBOARD, COMMUNITY_DECISIONS, PDF_VIEWER, EVENTS, VERSION_CHECK, STORAGE_MANAGER. Always inspect the current enum before assuming this is exhaustive.

### 3.2 Backend

- Cloudflare Worker + D1 + Durable Objects.
- Intended Worker URL: https://tanweer.magd.workers.dev/
- Known owner dashboard URL: https://tanweer.magd.workers.dev/dashboard
- D1 stores structured application data.
- Durable Objects provide real-time group chat coordination.
- No R2 is required for the current MVP design; media is stored in D1 as BLOBs.
- Android compresses images before upload; a historical target was at most about 1.25 MB per compressed image.
- Worker layout includes router/index, environment types, roles, permissions, auth middleware, rate limits, and services for dashboard, governance, groups, schedule, media, content, homework, exams, events, issues, and voting.
- Do not assume the file tree or endpoint list is complete. Inspect the exact current code.

### 3.3 Data trust model

The server is authoritative for shared application data, authentication, group membership, permissions, and accepted mutations.

The client:
1. Authenticates.
2. Sends eligible pending local mutations only after generation/version safety checks.
3. Requests data using the authenticated account.
4. Receives only records that account is authorized to see.
5. Reconciles those records into Room.
6. Uses Room as the local cache/offline presentation layer.

“Server is the source of truth” does **not** mean downloading private data belonging to every account. The server aggregates contributions from members, then enforces account and group membership access rules before returning records.

---

## 4. Roles and permissions

### 4.1 Role enum

Known server roles:
- STUDENT = 1
- MODERATOR = 2
- TEACHER = 3
- ADMIN = 4
- SYSTEM_OWNER = 5

Role scopes have included GLOBAL, GROUP, and SUBJECT. Role request statuses include PENDING, APPROVED, and REJECTED.

Legacy role normalization has included:
- MEMBER → STUDENT
- VERIFIED_TEACHER → TEACHER
- OWNER → SYSTEM_OWNER
- ADMINISTRATOR → ADMIN

Verify the current implementation before relying on this compatibility list.

### 4.2 Permission categories

Common contribution permissions:
- CONTRIBUTE_CONTENT
- PROPOSE_SCHEDULE
- CREATE_HOMEWORK
- CREATE_EXAM
- CREATE_EVENT
- ASK_QUESTION
- ANSWER_QUESTION
- SEND_CHAT_MESSAGE
- UPLOAD_MEDIA
- REQUEST_ROLE_UPGRADE

Moderation/governance permissions:
- MANAGE_SCHEDULE
- MODERATE_CONTENT
- DELETE_ANY_CONTENT
- PIN_CONTENT
- MODERATE_HOMEWORK
- MODERATE_EXAM
- MODERATE_EVENT
- MODERATE_ISSUES
- VERIFY_BEST_ANSWER
- MODERATE_CHAT
- DELETE_ANY_CHAT_MESSAGE

Group administration:
- MANAGE_GROUP_MEMBERS
- MANAGE_GROUP_SETTINGS

System administration:
- REVIEW_ROLE_REQUESTS
- MANAGE_USERS
- REVOKE_SESSIONS
- VIEW_AUDIT_LOG
- ACCESS_OWNER_DASHBOARD

Some permission names are aliases for the same enum value, not separate persisted permissions. For example, MANAGE_CONTENT may alias MODERATE_CONTENT.

### 4.3 Permission evaluation rules

The intended policy includes:
1. SYSTEM_OWNER has full system authority.
2. ACCESS_OWNER_DASHBOARD is restricted to the system owner.
3. ADMIN has the defined administrative permissions.
4. Student contribution permissions remain available to students.
5. Resource ownership grants the appropriate owner-specific actions.
6. The effective group role is resolved from group membership when required.
7. The resulting role is checked against the specific permission.

Architectural warning: the existence of RoleScope does not prove that every permission path applies scope consistently. Inspect the actual call site and server authorization.

---

## 5. Authentication and accounts

### Registration

- Yemeni phone number + password.
- Email is optional.
- Full name is visible in profile/group contexts.
- Grade and section are part of the student's academic identity.
- New users start as STUDENT.
- Sessions/devices and recovery codes are supported.
- Password hashing has used PBKDF2 and peppering.

### Recovery code

- Server recovery code format: 16 letters/digits, grouped as XXXX-XXXX-XXXX-XXXX.
- A recovery login flow exists.
- Historical Android placeholders did not always match the real format, e.g. shortened or differently prefixed examples.
- Improve the UX with save/copy, confirmation that the user copied it, and clear backup guidance.
- Do not reveal recovery codes without appropriate protections.

### Password validation mismatch

A historical mismatch existed:
- Android validation expected at least 8 characters with letters and digits.
- Server validation had a known minimum of 6 with a different scoring policy.

Treat client/server validation differences as product bugs, not compile issues.

### Registration UX issues to recheck

- AuthScreen previously defaulted to grade 11 / section B, which may mislead users who do not notice the selection.
- A clear confirm-password field was not present in the previously inspected UI.
- Recovery placeholders/input length did not always match the actual code.
- Recheck these against current code before reporting them as current bugs.

---

## 6. Grades and sections

Historically known section structure:
- Grade 7: A
- Grade 8: A / B
- Grade 9: A / B
- Grade 10: A / B / C / D
- Grade 11: A / B / C / D
- Grade 12: A / B / C

Historical total: 18 sections.

Shared groups across sections are a future concept; the model should not assume that every group is exactly one school section.

### Grade/section migration

Changing grade or section may affect:
- Group membership.
- Books.
- Subjects.
- Schedule.
- Academic history.
- Cached records.
- Current group selection.

The correct long-term model is a Section Migration Flow, not a silent profile field change.

---

## 7. Academic content and media

### Lessons / contents

Expected content fields include:
- id
- group_id
- subject_id
- created_by
- title
- description
- type
- views/useful/pin metadata according to schema
- media relationships
- study_date for daily views

Publication principles:
- Make the source clear.
- Identify the author.
- Keep content correctable and auditable.
- Allow official verification by the appropriate teacher/moderator/administrator roles.

### Media

- content_media links content to media.
- Current MVP storage is D1 BLOB.
- Android compresses images before upload.
- Do not redesign around R2 unless the user explicitly changes the product constraint.

---

## 8. Homework, exams, and events

### Homework

Expected behavior:
- Any student may create homework.
- Store study/due dates.
- Completion is personal where appropriate.
- Official editing/review is permission-based.

Historical risk: local completion/status could look successful while the server request failed, causing local/server divergence.

### Exams

Expected behavior:
- Create exams.
- Store exam date.
- Link relevant syllabus/units.
- Provide formal teacher/admin management.

Future direction: build an Exam Center rather than a basic list.

### Events

Events exist in database/API/UI and must be treated as first-class records.

Historical UX gaps included incomplete search integration and calendar markers that did not represent every event type consistently.

---

## 9. Search

### Historical root bug

SearchScreen previously used a daily DAO query with an empty date. ContentDao used an exact studyDate condition, so an empty date did not mean “all content”. Similar query-shape problems affected the knowledge base and academic history.

### Target architecture

Search should become a real search engine:
1. Normalize Arabic text.
2. Tokenize.
3. Search a local index.
4. Query server search where appropriate.
5. Merge results.
6. Rank.
7. Group by record type.
8. Open the exact record through deep links.

Target capabilities:
- Exact and prefix matching.
- Arabic normalization.
- Fuzzy/typo-tolerant matching.
- Stemming only where useful.
- Ranking and recent searches.
- Suggestions and filters.
- Search operators.
- Offline local search plus server search.

Target filters: all, lessons, homework, exams, events, questions, books, messages, schedule, users, and groups.

Example operators:
~~~text
subject:Mathematics
kind:exam
type:homework
date:2026-10-05
from:Name
in:group
~~~

Search results should open the selected item, not merely a generic screen. Link to a PDF page when possible.

---

## 10. Subject Knowledge Base

The SUBJECT_KNOWLEDGE_BASE feature exists as a screen/concept, but historically it was not a real knowledge graph.

Target relationships:
- Subject → lessons
- Subject → homework
- Subject → exams
- Subject → questions
- Subject → verified answers
- Subject → events
- Subject → PDFs/pages
- Subject → timeline

The system should identify what was taught, what remains undocumented, frequent questions, associated sources, and officially verified content.

---

## 11. Timeline, academic history, and catch-up

### Subject Timeline

Historical inconsistency: lesson data could be limited to the current day while homework/exams were read from a wider range.

Target:
- Truly cumulative timeline.
- Unified sorting.
- Month/week filters.
- Direct links for every item.

### Academic History

Do not display academic years that imply a real archive when no backend archive exists. Either implement the archive or make the UI honest about available data.

### What Did I Miss?

A catch-up report should collect:
- Missed lessons.
- Homework.
- Exams.
- Important events.
- Announcements.
- Schedule changes.
- Questions needing attention.

Present them in one coherent report.

---

## 12. Calendar

Calendar reads date-scoped academic records.

Known record types:
- Lessons/day contents.
- Homework by study/due date.
- Exams by exam date.
- Events.

Historical calendar markers sometimes emphasized homework/exams more than other record types. The intended design is an Academic Command Center that presents every relevant daily item.

The Add action should support:
- Lesson.
- Homework.
- Exam.
- Event.
- Announcement.
- Question.
- Potential schedule change.

---

## 13. Home / current class

Historical HomeScreen risks:
- Hardcoded periodTimes rather than actual schedule start/end.
- Displaying the first slot as the current period outside school hours.
- Hardcoded weekday/weekend rules.

Target behavior:
- Determine the current period from the real schedule.
- Make school-day policy configurable.
- Distinguish before school, break, current period, and after school.

---

## 14. Schedule engine

Existing concepts include slots, day of week, start/end time, proposals, versions, direct management, and initial schedule creation.

Correct model:
1. Allow initial creation with low friction, including student contribution.
2. Once official/stable, route unauthorized changes through proposal/vote or authorized roles.
3. Never make silent official mutations without an audit trail.

Historical technical debt:
- Accepted proposals could create slots without all expected metadata such as names/icons/colors/times.
- Schedule Proposal and Community Decision are parallel governance paths; clarify their responsibilities or consolidate them.

---

## 15. Governance and community decisions

### Current design context

The governance service has supported content corrections and unified community decisions.

Historical server-side quorum thresholds:
- Deletion/content deletion: 75%.
- Verification/official verification: 65%.
- Schedule/schedule change/pin content: 60%.
- Correction/default: 50%.

Never trust a quorum percentage supplied by the client.

### Voter snapshot

At decision creation:
- Collect active group member IDs.
- Store an eligible voter snapshot.
- Include the creator if eligible and active.
- Historical storage placed JSON metadata inside description, then restored the human-readable text for display.

Example metadata shape:
~~~json
{
  "text": "Human-readable description",
  "voters": ["user1", "user2"]
}
~~~

Long-term improvement: a dedicated metadata column or decision_voter_snapshot table.

### Minimum votes

For a group with more than one eligible member, the historical rule was:

requiredVotes = max(2, ceil(totalEligible * threshold / 100))

A one-member group follows its own special case.

### Decision execution

The actual database effect must be performed by applyDecisionExecution(). Do not merely set status to APPLIED.

### Legacy deletion voting risk

A separate legacy path exists in server/src/services/voting.ts using deletion_requests and different voting rules, including requester auto-vote and historical “NO may immediately reject” behavior.

There have therefore been two deletion governance engines:
1. Legacy deletion_requests.
2. Community decisions.

Treat this as high-priority architectural debt until the old flow is migrated or the two engines have an explicit coherent relationship.

---

## 16. Content corrections

The server-side correction flow has required:
- fieldName
- proposedValue
- reason

It stores originalValue, proposedValue, reason, and PENDING status.

Historically, approval applied TITLE and DESCRIPTION, while rejection set REJECTED.

Known UI/backend mismatch:
- Old UI fields included studyDate/title/order while the inspected backend execution only handled TITLE/DESCRIPTION.
- UI text sometimes described reason as optional while backend required it.

For every future change compare Android request payloads with actual server implementation.

---

## 17. Chat and Durable Objects

- Real-time WebSocket per group.
- GroupChatDO is a Durable Object.
- server/src/index.ts validates token and group membership before forwarding the WebSocket to the DO.
- Server injects trusted identity/group headers such as X-User-Id, X-User-Name, X-User-Grade-Section, X-User-Role, and X-Group-Id.
- Known paths: /ws/groups/:groupId and /wss/groups/:groupId.
- Token may be supplied by Authorization Bearer or a query parameter in the historical implementation.

Historical Android risk: a sent message may remain SENDING when its ACK is lost.

Target:
- Idempotent message ID.
- ACK timeout.
- Retry.
- Reconciliation.
- State transitions SENDING → SENT → FAILED.
- Explicit resend action.

Never trust identity headers from the client. Server must overwrite/inject them after validating the token.

---

## 18. Offline-first and synchronization

Target operation flow:
~~~text
UI mutation
→ local transaction
→ outbox / pending operation
→ network attempt
→ server ACK
→ server ID / version
→ reconciliation
~~~

Historical gaps to recheck:
- Optimistic UI may show success before server confirmation.
- Not every mutation uses the same outbox strategy.
- Delete may affect only local storage.
- Homework completion can diverge from server.
- Schedule deletion could clear local state before server confirmation.
- Empty catch blocks can hide root causes.

Target Sync Center / per-item state:
- synced
- syncing
- pending
- failed
- conflict

### Startup server-authority policy — adopted 2026-11-02

At each app launch and after successful authentication:
1. Restore the local account token and wait for local session loading.
2. Verify server data-generation/version before sending queued writes.
3. If that verification succeeds, drain pending local mutations first.
4. Fetch the latest profile and group memberships from the server.
5. Fetch all server-backed academic data for groups with active membership.
6. Fetch the account-scoped cross-grade archive.
7. Fetch grade books even when a new account has not joined a group.
8. Prefetch the signed-in user's role requests and the selected active group's members, schedule proposals/meta, corrections, and community decisions; fetch only the dashboard matching the user's global role.
8. Keep the splash/loading gate visible until the server reconciliation attempt completes.
9. Validate the saved selected group against the fresh active-membership list.
10. If the server is unavailable, keep safe local cached data visible and preserve pending mutations. Do not pretend that the cache was freshly verified.

This startup fetch is mandatory and must not be disabled by the optional background auto-sync preference.

### Startup data-scope rule

The client must not download private records from every account. It must use authenticated endpoints, and the server must filter records by account identity, active membership, and permission. Contributions from all active group members should appear where policy permits, because the server aggregates them.

### Data that is not yet server-backed

Historical local-only data includes Daily Plan records and book reading state/bookmarks/notes/vocabulary/drawings. Do not claim these are synchronized across devices unless a server API and complete upload/download reconciliation are implemented.

Dashboards, group members, corrections, community decisions, role requests, and real-time chat have had on-demand API paths and are not necessarily part of the Room academic startup snapshot. Inspect current behavior before broad claims of “everything”.

---

## 19. PDF and books

### Separate repository

- Book repository: majd7772233-a11y/tanweer-books.
- Textbooks are maintained separately from the Android app repository.

### Library manager

Known component: PdfBookManager.
Historical characteristics:
- Cache path under filesDir/pdf_books.
- Download on demand.
- Temporary download file, then copy/replace target.
- 60-second read timeout.
- Bitmap LRU cache around 25% of available memory.
- Renderer should be closed when finished.

### PDF Reader

A substantial reader exists and has included bookmarks, notes, drawing, vocabulary, and study tools.

Do not replace it casually. Strengthen it toward a Study OS.

A historical user device folder was /storage/3165-6434/Books and contained roughly 58 PDF files. This device-specific fact must be reverified before relying on it.

---

## 20. Owner dashboard and control center

Known routes include:
- /dashboard/login
- /dashboard
- /api/dashboard/...

Authentication has supported X-Owner-Secret, Bearer token, or tanweer_owner_token cookie.

Historical cookie attributes:
- HttpOnly
- Secure
- SameSite=Lax
- Max-Age=86400

If TANWEER_OWNER_SECRET is absent, disable the dashboard.

Known dashboard domains:
- Stats.
- Users and roles.
- Role changes.
- Disable/enable accounts.
- Session revocation.
- Role requests.
- Groups.
- Audit logs.
- Contents.
- Homework.
- Exams.
- Events.
- Issues.
- Schedules.
- Decisions.
- Chat messages.
- Corrections.
- Sensitive SQL executor.

Pagination was added to multiple owner endpoints historically. Verify current routes.

Do not assume that an endpoint name implies a fully implemented UI. Check read, update, delete, actual side effects, permissions, pagination, loading/error states, and retry.

---

## 21. Admin, teacher, and moderator dashboards

### Teacher dashboard

Expected scope:
- Official homework.
- Exams.
- Content verification.
- Teacher answers.
- Schedule control.
- Student guidance.

### Moderator dashboard

Expected scope:
- Moderation queue.
- Corrections.
- Schedule proposals.
- Inappropriate members/messages.
- Review of community activity.

### Admin dashboard

Must be more than a statistics screen. It should support practical school administration.

Historical UX risk: backend roles may be more complete than the UI workflows. For every role-based feature test visibility, server authorization, and scope independently.

---

## 22. Groups

Group concepts include:
- Class/section groups.
- Members.
- Group roles.
- Join requests.
- Group chat.
- Group settings.
- Future cross-section groups.

Historical role hardening:
- Prevent SYSTEM_OWNER as a group role.
- Check the target's global role before assigning TEACHER.
- Restrict ADMIN assignment to authorized admins/owners.

Global role and group role can diverge. Example: a global TEACHER may be a STUDENT within a particular group. This may be deliberate or a policy bug; inspect scope before deciding.

---

## 23. Search/content query architecture

A shared historical root cause was using exact-date daily DAO queries to feed screens that need cross-day, monthly, or historical data.

Affected areas included Search, Knowledge Base, Academic History, and Timeline.

Preferred separation:
~~~text
DayContentDao
  → only today's/date-specific records

ContentRepository
  → ranges, subject, group, author, type

SearchIndex
  → full-text and normalized tokens
~~~

Do not force getDayContents(groupId, date) to serve every query shape.

---

## 24. Notifications

Known Android settings include homework, schedule, exam notifications, and auto-sync.

Future improvements:
- Contextual notifications.
- Reminders.
- Digest.
- Deduplication.
- Quiet hours.
- Per-category settings.
- Offline scheduled reminders.

---

## 25. Settings

Known settings:
- Font scale.
- Theme (DARK / AMOLED).
- Haptics.
- Auto-sync.
- Image quality.
- Notifications.
- Cache cleanup.
- Server health check.

Historical gaps: language settings may be incomplete, some Compose strings are hardcoded, and toasts may be overused instead of inline/snackbar/status components.

---

## 26. Localization and Arabic UX

“Arabic interface” does not automatically mean complete localization.

Historical English remnants included labels such as Class Knowledge Base, Scanner, and Recovery Code.

Rules:
- Avoid hardcoded user-facing strings when a string resource is appropriate.
- Test RTL and LTR.
- Respect arrow/icon direction.
- Use natural school terminology.
- Format dates and weekdays appropriately for the selected locale.
- Do not translate technical terms into awkward Arabic.

---

## 27. Historical product/logic issue register

This list contains product, logic, UX, and architecture risks, not merely compile bugs. Recheck every item against current code before calling it unresolved.

1. Search query range bug: empty date supplied to an exact-date DAO.
2. Knowledge Base range bug: daily query used for cumulative content.
3. Academic History could show years without a real server archive.
4. Owner Dashboard decision application previously only changed status; historical fix added applyDecisionExecution.
5. Global role downgrade previously left stale group roles; historical fix cleaned them.
6. Direct TEACHER-from-group escalation was previously possible; historical fix restricted it.
7. A group could previously receive SYSTEM_OWNER; historical fix prevented it.
8. Global/group role divergence remains a policy question.
9. RoleScope is not necessarily applied consistently to every permission.
10. ADMIN group-role semantics require explicit policy.
11. Historical governance voter rules were unclear to users.
12. Voter snapshot metadata was stored in JSON description rather than a dedicated relation.
13. Schedule Proposal and Community Decision are parallel engines.
14. A passed quorum may still require manual approval in some paths; make this explicit.
15. APPROVED/APPLIED need user-friendly labels.
16. Correction details need clearer before/after presentation.
17. Correction UI fields may not match backend-executable fields.
18. Correction reason was optional in some UI text while backend required it.
19. Optimistic offline UI may imply unconfirmed success.
20. Delete can diverge between local and server.
21. Homework completion can diverge between local and server.
22. Schedule create fallback may silently become a proposal after direct-create failure.
23. Schedule delete may clear local state before confirmation.
24. Proposal application needs atomic transaction semantics.
25. New schedule slots may lose metadata after approval.
26. HomeScreen may hardcode period times instead of reading the actual schedule.
27. Home may show the first slot as current outside school hours.
28. Weekend policy may be hardcoded.
29. Calendar may not display all academic entities in one timeline.
30. Calendar Add may not support all entity types.
31. Events may be less integrated than homework/exams.
32. Search is not yet a full search engine.
33. Empty search may lack good recent/trending/suggestion behavior.
34. Search may omit Events.
35. Search may omit Chat.
36. Search may fail to deep-link to the exact item/PDF page.
37. Knowledge Base may not be a true knowledge graph.
38. Subject Timeline ranges may be inconsistent.
39. Timeline sorting may need unified chronology.
40. Academic History may show years not backed by real archives.
41. Personal academic history may rely too heavily on current identity after a section transfer.
42. Recovery-code UX needs save/copy/backup/reveal policy.
43. Recovery input length may mismatch the real code.
44. Sessions/devices UI may be incomplete compared with backend.
45. Grade/section change needs a real migration flow.
46. Group switching may briefly show stale Room/cache data.
47. Sync status may not be unified per mutation.
48. Outbox coverage may not include every mutation.
49. Empty catch blocks in Chat/Schedule/Sync may hide errors.
50. Chat SENDING may remain after a lost ACK.
51. Shared architectural root cause: exact-date DAO queries have been reused for cross-day/historical/search contexts.

Historically, items 4–7 were reported fixed in the then-latest commit. Verify before relying on that status.

---

## 28. Strengthening existing features

### 28.1 Search → real search engine

Pipeline:
~~~text
Input
→ Arabic normalization
→ tokenization
→ local index
→ remote search
→ merge
→ ranking
→ grouped results
→ deep link
~~~

Ranking signals: exact title, exact phrase, prefix, field match, subject/group relevance, recency, verified/official status, engagement/useful score, and fuzzy similarity.

Result UI should show type, title, snippet, author, subject, group, date, verification badge, and quick actions.

### 28.2 Knowledge Base → Academic Knowledge Graph

Connect concepts to source lessons, questions, homework, exams, and books.

### 28.3 Lesson upload → documentation pipeline

Upload flow:
~~~text
photo/file
→ classify
→ optional OCR
→ clean title
→ subject/date
→ attach media
→ suggest duplicates
→ publish
~~~

### 28.4 Homework → task manager

Include due date, personal completion, personal priority (not role priority), reminders, attachments, status, overdue state, and recurrence if needed.

### 28.5 Exams → exam center

Include countdown, scope, study checklist, linked lessons/PDF pages, status, and reminders.

### 28.6 Calendar → command center

Present a unified daily stream, e.g. schedule, lesson, homework, and event in chronological order.

### 28.7 Schedule → versioned engine

Include versions, diffs, reasons, proposals, approvals, and rollback.

### 28.8 Governance → governance center

Each decision should explain who proposed it, what it changes, who can vote, the quorum, votes, outcome, execution result, and audit trail.

### 28.9 Chat → academic chat

Allow a chat message to become a structured Lesson/Homework/Exam/Event/Issue.

### 28.10 Q&A → solved knowledge

A best answer should become searchable knowledge, with verification indication and links to subject/PDF sources.

### 28.11 PDF reader → Study OS

Potential additions: note linking, page references, vocabulary, highlights, questions from pages, homework/exam links, progress, and offline bundles.

### 28.12 Offline-first → offline operating model

The goal is not merely showing cached content. It is read, write, queue, retry, resolve, sync, and recover.

### 28.13 Notifications → contextual intelligence

Prefer digest, due-soon, schedule conflict, missed class, exam tomorrow, and unresolved-question notifications over excessive individual alerts.

### 28.14 Dashboard → control center

Authorized staff should see data health, permissions, governance, users, content, schedule, logs, security, and system flags.

---

## 29. New-feature backlog (70 ideas)

1. Smart Daily Brief.
2. Smart What Did I Miss summary.
3. Personal Study Planner.
4. Smart Revision Map.
5. Class Knowledge Graph.
6. Contribution Reputation.
7. Content Quality Score.
8. Duplicate Detection.
9. Smart Merge.
10. Verified Content badge.
11. Teacher Official Channel.
12. School Announcements stream.
13. Cross-section Groups.
14. Subject Communities.
15. Shared Exam Room.
16. Exam Countdown Center.
17. Smart contextual reminders.
18. Message-to-Academic-Item converter.
19. Notification Digest.
20. Personal Home Dashboard.
21. Academic Streak.
22. Study Statistics.
23. Personal Bookmarks.
24. Universal Favorites.
25. Recently Viewed.
26. Offline Downloads Center.
27. Storage Manager.
28. Conflict Center.
29. Content audit trail.
30. Content History.
31. Restore Content.
32. Smart Moderation Queue.
33. Abuse/Spam Detection.
34. Report System.
35. Safety Mode.
36. Group Rules.
37. Group Welcome Board.
38. Section Migration Assistant.
39. Academic Year Closure.
40. School Calendar Layer.
41. Current School Time.
42. Quick Actions.
43. Share as Tanweer Card.
44. Deep Links.
45. Smart QR Codes.
46. Group QR Join.
47. Teacher Assignment System.
48. Teacher Availability.
49. Smart FAQ from solved questions.
50. Related Questions / Suggested Answers.
51. Knowledge Gap Detection.
52. Most-Needs-Documentation detector.
53. Contribution Suggestions.
54. Daily Summary.
55. Weekly Academic Digest.
56. Teacher/Admin Broadcast.
57. Expiring Content.
58. Pinned Daily Content.
59. Important Academic Alerts.
60. Owner System Health.
61. Data Integrity Checker.
62. Permission Simulator.
63. Governance Simulator.
64. Feature Flags.
65. Maintenance Mode.
66. Emergency Lock.
67. Backup/Export.
68. Import/Recovery.
69. Student Data Export.
70. Privacy Center.

---

## 30. Important future design pillars

### Contribution reputation

Use it to highlight good work, detect spam, encourage quality, and assist moderation. Never use reputation to bypass security permissions or create a new role hierarchy.

### Content verification

Possible states: COMMUNITY, VERIFIED, OFFICIAL, ARCHIVED, DISPUTED. Do not confuse verification status with global user role.

### Versioning

Important entities should eventually support version, updated_by, updated_at, change_reason, and previous_version.

### Audit

Important administrative/governance mutations must be traceable.

### Deep links

Conceptual routes:
~~~text
tanweer://content/<id>
tanweer://homework/<id>
tanweer://exam/<id>
tanweer://issue/<id>
tanweer://book/<id>/page/42
~~~

---

## 31. Data/database mental model

Likely entity families:
~~~text
users
sessions
role_requests
role_audit_log

groups
group_members

contents
content_media
content_corrections

homeworks
exams
events

issues
issue_comments

schedule_slots
schedule_proposals
schedule_versions

community_decisions
community_decision_votes

legacy deletion/voting tables
chat/realtime persistence-related tables
~~~

Exact names may differ. Never create migrations or queries from this mental model without inspecting current migration/schema files.

---

## 32. Security rules

1. The server is authoritative for permissions.
2. UI permission checks are not a security boundary.
3. Never trust a client-supplied role.
4. Never trust groupId without a membership check.
5. Owner dashboard access requires the correct TANWEER_OWNER_SECRET flow.
6. Never add a default/fallback owner secret.
7. Never store owner secrets in Android.
8. WebSocket access must validate token and membership.
9. Sensitive operations need audit logs.
10. Role downgrade must keep global/group state consistent.
11. SYSTEM_OWNER is not a group role.
12. Teacher verification must not allow self-service privilege escalation.
13. Voter snapshots must prevent late joiners from changing the electorate.
14. Prevent duplicate voting.
15. Prevent repeated or invalid decision execution.
16. SQL executor must never be exposed to non-owner roles.

---

## 33. Product quality definition

A successful build does not mean the product is ready.

A feature is only good when:
- UI is clear.
- Text is localized.
- Loading state exists.
- Empty state is meaningful.
- Error state is understandable.
- Retry exists where needed.
- Offline behavior is known.
- Server confirms success.
- Local cache does not lie.
- Deep links work.
- Server permissions are correct.
- Role scope is correct.
- Ownership/audit behavior is clear.
- Duplicate engines do not implement the same task with conflicting rules.

---

## 34. GitHub audit protocol

When asked to inspect the latest commit:

### Phase 1 — Establish reality

1. Verify main or the requested branch.
2. Get the latest SHA.
3. Read commit metadata and diff.
4. Do not rely on remembered older commits.

### Phase 2 — Read relevant architecture

At minimum inspect:
- Android entry/navigation.
- ViewModel/state.
- Repository/DAO.
- Auth.
- Sync/offline.
- Relevant screens.
- Worker router.
- Permissions/roles.
- Relevant backend services.
- Migrations/schema.
- CI/deploy when relevant.

### Phase 3 — Search for product defects

Do not stop at syntax, compile, imports, or Gradle.

Look for:
- False success.
- Stale state.
- Missing data.
- Wrong scope.
- UI/backend mismatch.
- Duplicate systems.
- Broken navigation.
- Hidden untranslated text.
- Empty catches.
- Missing permissions.
- Bad defaults.
- Incorrect date/time logic.
- Orphan records.
- Race conditions.
- Offline divergence.
- Security bypasses.

### Phase 4 — Report

Prefer ordering:
1. P0 critical issues.
2. P1 important issues.
3. P2 UX/product issues.
4. P3 polish.
5. What the latest commit already fixed.
6. What remains.
7. Ways to strengthen existing features.
8. New feature ideas.

---

## 35. Code-editing preferences

- New file: provide the full file.
- Small existing-file change: provide the full function and exact placement.
- Small file with many changes: provide the full file.
- Large file with many changes: provide every complete replacement function and exact placement.
- Do not merely say “test and tell me”; provide several practical steps together.
- Compare every patch with the latest commit.
- Provide a Git commit message when relevant.

Technical preferences:
- User often prefers Java/XML in other projects, but Tanweer itself is Kotlin/Compose.
- The user expects polished UI, not basic CRUD.
- Prioritize user-facing logic and product correctness over compile-only checks.

---

## 36. Avoid suggestions without a reason

- Do not suggest running Wrangler CLI on the user's Android device. The user previously stated that npx wrangler is not suitable for that environment.
- Wrangler/Cloudflare may be used in CI/server development when appropriate.
- Do not assume R2 is available for the MVP; the current design intentionally avoids it because of payment-card constraints.
- Do not revert to an admin-centric system that suppresses student contributions.

---

## 37. Suggested priorities

### P0 — Trust and correctness

- Unify governance engines, especially legacy voting vs community decisions.
- Align client/server validation.
- Fix offline false-success.
- Unify delete/update semantics.
- Prevent role/scope inconsistencies.
- Fix Search data sources.
- Expose errors instead of empty catches.
- Make WebSocket delivery reliable.
- Audit owner/SQL/dashboard security.

### P1 — Core product strength

- Real search engine.
- Calendar Command Center.
- Real Knowledge Base.
- Schedule versioning.
- Clear Governance Center.
- Sessions/devices UI.
- Real academic history.
- Group migration flow.

### P2 — Differentiation

- Study OS.
- Contribution reputation.
- Duplicate detection.
- Smart reminders.
- Related questions.
- Knowledge gap detection.
- Cross-section communities.

### P3 — Polish

- Animation.
- Haptics.
- Rich glass effects.
- Customizable home.
- Share cards.
- Advanced personalization.

---

## 38. Practical roadmap

### Phase A — Trust layer
Auth consistency, permissions, role scope, audit, governance unification, and sync correctness.

### Phase B — Academic core
Search, Knowledge Base, Timeline, Calendar, Schedule, Homework, and Exam Center.

### Phase C — Collaboration
Chat, Q&A, groups, contributions, and moderation.

### Phase D — Intelligence
Summaries, suggestions, duplicate detection, knowledge graph, and smart reminders.

### Phase E — Control and scale
Owner Control Center, monitoring, data integrity, backups, maintenance mode, and feature flags.

---

## 39. What makes Tanweer different from WhatsApp

Tanweer is not “WhatsApp for studying”. It should become an **Academic Operating System for the class and school**.

Its value must come from structured academic memory, search, history, schedules, homework/exam context, verified knowledge, offline reliability, governance, and student contributions.

---

## 40. Launch-readiness checklist

### Product
- [ ] Every screen has a clear purpose.
- [ ] No screen implies data that does not exist.
- [ ] Empty states are accurate.
- [ ] Errors are understandable.
- [ ] Navigation has no unnecessary steps.

### Data
- [ ] No silent local/server divergence.
- [ ] Migration paths are clear.
- [ ] Delete cascades are deliberate.
- [ ] Important records have version/audit coverage.

### Security
- [ ] Owner authentication is secure.
- [ ] Permissions are enforced server-side.
- [ ] Role upgrades are verified.
- [ ] No scope bypass.
- [ ] No leaked secrets.

### Governance
- [ ] One clear voting engine.
- [ ] Stable voter snapshot.
- [ ] Clear thresholds.
- [ ] Actual decision execution.
- [ ] No double application.

### Offline
- [ ] Outbox.
- [ ] Retry.
- [ ] Reconciliation.
- [ ] Conflict handling.
- [ ] Per-item status.

### Localization
- [ ] Arabic first.
- [ ] No unexplained English remnants.
- [ ] RTL checked.
- [ ] Date/time formatting checked.

---

## 41. How to update this notebook after important work

This file is a living project memory. After each meaningful implementation step, record exactly what changed.

Meaningful steps include:
- A substantial feature added/modified.
- An important logic bug fixed.
- Schema/API change.
- Role/permission change.
- Architecture/navigation change.
- Final design decision.
- Feature removal.
- Roadmap/priority change.
- Serious new bug discovered.
- Important CI/deployment result.
- A new verified commit.

Each log entry should state:
- What was inspected.
- What was changed.
- Exact file/function/endpoint impact.
- Tests/CI performed and result.
- Branch and commit SHA.
- Remaining risks and next step.

Do not invent a commit SHA. Before commit, write “not yet committed” and update the entry when the commit exists. Keep entries concise but specific; do not duplicate the entire project memory.

---

## 42. Initial notebook history

### 2026-10-05 — Notebook created

- Created GPT.md as internal assistant project memory.
- Recorded the then-known repository baseline and architecture.
- Documented Android + Worker/D1/DO, roles, governance, offline sync, search, PDF, dashboards, and product principles.
- Separated known bugs from historical fixes.
- Added the 70-item feature backlog and update protocol.

The original baseline SHA and main-branch state above are historical only. Always inspect the live branch before acting.

---

## 43. Design decisions to preserve

1. Official project name: Tanweer.
2. Students are contributors, not passive viewers.
3. Content ownership must be preserved.
4. Role hierarchy: Student → Moderator → Teacher → Admin → System Owner.
5. “Priority system” means role hierarchy, not content priority.
6. Teacher verification is formal, not self-service.
7. SYSTEM_OWNER cannot be a group role.
8. Backend is authoritative for permissions.
9. Cloudflare Workers powers the backend; no R2 in the current MVP.
10. Books live in a separate repository.
11. Search must become a real search engine.
12. Knowledge Base must become an academic knowledge graph.
13. Offline behavior must be mutation-safe, not cache-only.
14. Governance must not have two conflicting engines for the same decision.
15. Owner Dashboard should be a real control center, not merely statistics.
16. Arabic-first + Liquid Glass is the UX direction.
17. At startup, the authenticated server is authoritative for shared records and current membership.
18. Never expose private records across accounts; server authorization defines the visible group data.
19. Keep a local cache for safe offline reading, but do not describe it as freshly synchronized when the server was unavailable.

---

## 44. Facts that must always be rechecked

The following may change in any commit:
- Exact filenames and paths.
- DAO/function names.
- API routes.
- Migration numbers.
- CI state.
- Server schema.
- Screen names.
- Quorum thresholds.
- Owner secret variable.
- PDF implementation details.
- Test count.
- APK size.
- Book count.
- Status of each item in the historical 51-issue list.

When giving the user a statement about the current state, re-read GitHub if it materially affects the answer.

---

## 45. Handling a new task in a new conversation

1. Read GPT.md fully or read the relevant sections.
2. Determine whether the user wants analysis, modification, or a new feature.
3. Inspect the latest commit before editing code.
4. Compare actual code with GPT.md.
5. Do not treat an old bug as confirmed without rechecking.
6. Follow the user's code-editing preferences.
7. After each important step, append an exact work-log entry to GPT.md.

---

## 46. Template for each future work-log entry

~~~md
### YYYY-MM-DD — Short step title

- **Branch / commit:** <branch> / <sha or not yet committed>
- **Inspected:** <files, routes, schema, or workflows>
- **Changed:** <exact implementation and behavior>
- **Tests:** <test names and result>
- **CI:** <run link/result, or not run>
- **Risks / limitations:** <what remains>
- **Next step:** <specific next action>
~~~

---

## 47. Recent work log

### 2026-11-02 — Investigation of missing startup academic data (previous step)

- **Branch / commit:** work/release-readiness / 9f32d4cb780f54b28f8ce0ce1365175ef4d6fd7f.
- **Inspected:** MainActivity, TanweerViewModel, TanweerRepository, Room DAOs/entities, startup generation handling, server D1 aggregate counts, and backend membership behavior.
- **Found:** Selected group/date were transient in memory; a missing local data-generation marker could trigger destructive clearing; multiple cache reconciliations were DELETE+INSERT without transactions; some 2xx responses were accepted without checking success/scope; Room downgrade fallback could destroy data.
- **Changed:** Persisted group/date per user; adopted missing generation marker without purge; added transactional reconciliation and response scope validation; removed destructive downgrade fallback.
- **Tests/CI:** Added startup selection policy tests and schedule snapshot Room tests. CI run 41429456447 succeeded for the then-current HEAD and produced a debug APK artifact. No real-device test was performed.
- **Cloudflare:** Read-only verification only. No production database writes, migration, secret change, or Worker deployment.
- **Next step:** Make startup synchronization mandatory and authoritative across all active memberships.

### 2026-11-02 — Server-authoritative startup reconciliation (current implementation step)

- **Branch:** work/release-readiness. Main remains untouched.
- **Inspected:** TanweerViewModel startup/auth collectors; TanweerRepository initial sync, archive sync, profile/groups/day/books/issues APIs; TanweerApiService endpoints; GroupDao active-group query; UnifiedMutationQueue; server group service and router.
- **Security/data-scope finding:** The server's groups endpoint distinguishes the authenticated user's active memberships from discoverable groups. Startup must sync only active memberships and must never download another account's private data.
- **Repository change, commit a1a617873a07fe4304771aaaa3f3a92493455809:** Reworked syncInitial so profile and groups refresh first; selected/default/first authorized group is used for the daily view; all active memberships are reconciled; books are fetched independently of group membership.
- **Archive change, included in the same repository commit a1a617873a07fe4304771aaaa3f3a92493455809:** Cross-group sync now includes issues and the selected calendar month, validates the archive success flag, and reports archive request failures instead of silently ignoring them.
- **Outbox queue change, commit e2e15827ea9b24e88167205f0306d3d787465472:** Added an explicit force-after-generation-check path to drainQueue; ordinary background drains remain blocked by the generation gate.
- **Repository outbox change, commit 1fd14777a9fb58135dcd27f9da9428d7d86b0b1e:** processOutboxQueue uses the explicit path and documents the caller's responsibility to verify server generation first.
- **Membership policy helper, commit 327d4a7a0373b5e8071f8f7b43687895d15a3b08:** Added resolveAuthorizedGroupId to keep saved selections only when the server confirms active membership; otherwise choose the authorized default, first membership, or empty selection.
- **Tests, commit 17ab0e6d0cc553c6e8be40481badd5d3779ce021:** Added tests for retained membership, removed membership, unavailable default fallback, and no active membership.
- **ViewModel startup/auth change, commit 80603f3235bb44737e9ce5f54a9a5854144c4ff9:** Added a local-session readiness gate; kept the splash/loading state until startup reconciliation finishes; made startup pull mandatory regardless of the background auto-sync preference; removed duplicate sync calls from register/login/recovery success paths; revalidates the selected group after fetching server memberships; updates sync states from the startup result.
- **Generation safety follow-up, commit 010407e952a03a504c3920960f6673f1499e84a1:** Outbox push is skipped unless the server's non-empty data_generation was successfully verified. Server pull still runs; pending local records are preserved for a later safe retry.
- **Cloudflare:** Reviewed D1 binding/batch documentation and worker route structure. No production D1 writes, schema migration, Worker deployment, or secret changes were made.
- **Tests/CI:** Not yet run for these new commits at the time this entry was written. The earlier successful run does not validate this HEAD.
- **Important limitation:** Daily Plan and book annotations/reading data are local-only in the inspected architecture. Several privileged details (dashboards, members, corrections, decisions, role requests) and chat still use on-demand/realtime flows rather than one complete Room startup snapshot. Do not claim every app feature is cross-device synchronized yet.
- **Next step:** Review the final diffs, add/fix tests, run Android unit tests and debug APK CI on the current HEAD, then update this log with the verified result.


### 2026-11-02 — Pending-mutation-safe reconciliation and strict outbox gate

- **Branch:** work/release-readiness. Main remains untouched.
- **Inspected:** Content, homework, exam, event, issue DAOs; the full/date/month Room reconciliation code; outbox queue callers; server data-generation guard.
- **Found:** A server row with the same ID as an unacknowledged local row could overwrite it through Room's REPLACE behavior. DELETE_PENDING rows also needed explicit protection in date/month cleanup. In addition, a force-drain API could bypass the generation gate from the manual sync path, and startup could reopen the queue after an unverified version check.
- **DAO changes, commit ea31047389de1670e09d6e25a45209723be8e2fa:** Added pending-row lookups for contents, homework, exams, events, and issues; retained DELETE_PENDING rows during full snapshot cleanup.
- **Generic merge helper, commit 085b5999843201bb1ec2641f44c908ccc3a2e830:** Added ServerSnapshotReconciliation.preservePending so an unacknowledged local row wins an ID collision and survives if absent from the server response.
- **Helper tests, commit b9e07ef463304c317e57c59675b8ca392b5eb993:** Tested collision precedence, retention of local-only pending rows, and server replacement of non-pending rows.
- **Archive query, commit ddb7f7729783740cc4f00005dbf3b87b58ad6178:** Added all-groups pending contents lookup for the account-scoped archive merge.
- **Snapshot integrations, commit fbbd9167784d2c1eac66e2ad3e27918cb525cefe:** Applied pending-row preservation to group contents, archive contents, homework, exams, events, issues, daily snapshots, and monthly calendar snapshots.
- **Atomicity follow-up, commit 5428a531384980cf0bf23dfe70c3226064e3c430:** Moved full-snapshot pending reads inside the corresponding Room transactions, so pending read + server replacement is atomic.
- **Queue gate correction, commits 80a1533484af99b10880a74e7e43f9edc629a717, 141cb14b8bf599fe53921a1306920ce310ab18a7, and f0ec3da5f45d2d4a670897c018bcbe9d7a820357:** Removed the force bypass from drainQueue, made repository outbox processing respect the shared gate, and kept the gate closed unless data_generation was verified.
- **Date/month deletion fix, commit ea168eb9e803503f2bad7dbc1cdd6e8feb2b193c:** Extended DELETE_PENDING retention to contents, homework, and event date/month cleanup queries.
- **Database integration tests, commit 9faa3fbd3302b2457c19c560222a751cc297dcd7:** Added in-memory Room tests for pending state lookups and DELETE_PENDING retention across full/date/month cleanup.
- **Login-time verification retry, commit 4846400c5f659a3ab5eb0fb2f8edba57c07d99e3:** Retries a previously unavailable data-generation check after authentication, before any queued local write can be submitted.
- **Closed-by-default queue initialization, commit 08291af89c496893d24d245b06096081d79fd7df:** UnifiedMutationQueue now starts with `isDrainAllowed = false`, preventing the periodic worker from racing ahead of ViewModel's startup generation check.
- **Authorized secondary-data prefetch, commits 05c0dda490c9ee7a8fe79a28874fb3e5dc153765 and 32496f7a9f6ddfcbaf009960ac98c59cab0cb585:** Startup now concurrently preloads the current active group's members, schedule metadata/proposals, corrections, community decisions, the user's role requests, and only the global-role dashboard corresponding to the signed-in account.
- **Tests/CI:** The current branch has not yet passed a CI run for these newest commits. The earlier successful CI run is not evidence for this code. The temporary validation PR is PR #4 and must be closed without merging after the final result.
- **Cloudflare:** No D1 writes, migrations, secrets changes, or Worker deployment.
- **Next step:** Verify the Android build and unit tests plus server tests on the latest HEAD; inspect all current-head logs and close the validation-only PR unmerged.


### 2026-11-02 — Startup chat history and latest validation cycle

- **Branch / commit:** work/release-readiness / 99a88d8102a20202d54124ef550efa1b47fb34cf.
- **Inspected:** Android chat REST endpoint and response model, ChatDao, Repository WebSocket lifecycle, ViewModel startup gate, Worker chat service, and Cloudflare D1 transaction documentation.
- **Server contract verified:** The authenticated chat endpoint checks active group membership and returns the latest 100 messages in chronological order. The client must not retrieve group chat for accounts that are not active members.
- **Repository change, commit d3433e2bb711e744bd3b360f9356297985212302:** Added syncGroupChatHistory(groupId), validates success and group scope, inserts server-confirmed messages as SENT, and does not clear older local history.
- **ViewModel change, commit 99a88d8102a20202d54124ef550efa1b47fb34cf:** Startup now fetches recent chat history for every server-confirmed active group. It does not open WebSockets in the background; the live socket remains tied to entering the chat screen.
- **Room test, commit aa4ef3c99dee63ac16a0cb87bb414c884d350315:** Added an in-memory database test proving that inserting the latest server chat snapshot retains older cached messages and confirms a matching locally SENDING message as SENT.
- **CI, final HEAD f96172e9f33ba2179d3e4119098ea9cedf58937d:** Android run 41437771455 succeeded. `testDebugUnitTest` passed; `assembleDebug` passed; the Debug APK artifact uploaded successfully. Server run 41437771436 succeeded: TypeScript typecheck, 23 test files / 160 tests, and Wrangler production + staging dry-run bundle validation all passed. Release signing steps were correctly skipped for the validation PR.
- **Cloudflare:** Read current D1 Worker API documentation. D1 batch operations execute sequentially inside a SQL transaction; no D1 writes, migrations, secrets changes, or Worker deployment were performed.
- **Remaining scope:** The Android app still has user-specific local-only study data such as Daily Plan entries and PDF annotations/reading state that do not yet have complete server APIs. Startup cannot synchronize data that has no server persistence contract. Chat prefetch is the latest 100 server messages per active group, matching the current endpoint limit; older local history is retained.
- **Debug APK artifact:** `Tanweer-debug-823660d24355a83334b0e5ab4a82a4fa6b5237bd`, artifact ID `13136773993`, 30,649,285 bytes, SHA-256 `5e910cbe05d9390392954b85254c519c076646c2413f14b0c039fff17c1e54a9`. The artifact is available in Actions run 41437771455 and expires 2026-11-09.
- **Cloudflare safety:** Only read-only D1/Worker inspection and CI `wrangler deploy --dry-run` checks were performed. No production Worker deployment, D1 write/migration, secret modification, or production data change occurred.
- **Device testing:** No physical-device test was performed in this session; CI validates build and automated tests only.
- **Notebook language cleanup:** Removed the remaining Arabic-script brand label and Arabic text from search-operator examples. Verified the entire `GPT.md` file contains no Arabic-script characters.
- **Validation PRs:** PR #4 and PR #5 were both closed without merging. Each targeted a disposable branch copied from main; neither targeted `main` directly.


### 2026-11-02 — Isolated Staging deployment and CI authentication repair

- **Working branch:** `work/release-readiness`. Production `main` remains unchanged at `64143994070bcdc410cf044379fe2801da8f9a3c`.
- **Purpose:** Prepare an isolated backend environment for owner-led Android and Dashboard testing. Production Worker `tanweer` and production D1 database `tanweer-db` were explicitly excluded from every deployment and migration command.
- **Android Release run:** GitHub Actions run #76, ID `41442432262`, triggered on commit `65632a65765439b1ce5ceb5a31ddb3149ca8707e`. Android unit tests passed; the Release keystore decoded successfully and the requested alias was validated. At the time of this entry, `assembleRelease` was still running; APK verification, SHA-256, and artifact upload were not yet confirmed.
- **Staging deployment attempts:** Run #1 (`41442432388`) successfully completed server typecheck/tests, Wrangler dry-run, Cloudflare credential validation, and D1 migrations, but failed because the direct Wrangler secret-configuration shell step lacked `CLOUDFLARE_API_TOKEN`. Run #2 (`41442817064`) proved the secret step fixed and uploaded the staging secrets, but then exposed the same missing environment variable in the direct Worker-deploy shell step.
- **Workflow fix:** Added `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` mappings from GitHub Actions Secrets to both direct Wrangler shell steps in `.github/workflows/deploy-staging.yml`. No secret values were copied into the repository or logs by this change.
- **Successful Staging run:** Run #3, ID `41443013168`, completed successfully. TypeScript checks, all 160 server tests, staging Wrangler dry-run, Cloudflare authentication, migration step, secret configuration, Worker deployment, and the Staging health check all passed.
- **Staging Worker:** Created and deployed `tanweer-staging` at `https://tanweer-staging.magd.workers.dev`. Cloudflare deployment ID: `5feac8a8-9ada-4def-9298-19dd7885ba0c`; active version ID: `8741c85f-6de3-4b35-8575-13e5c7394997` at 100% traffic. The GitHub health check for `/api/v1/health` returned success.
- **Staging D1:** `tanweer-staging-db` (UUID `603edee6-c5f7-4158-8256-24b109110ec0`) now has the project's complete migration schema through `0026_media_upload_ownership.sql`. The only row in `users` is the seeded non-login `SYSTEM_OWNER` principal. No student, teacher, or test-account records were created by deployment.
- **Staging secrets:** The workflow successfully configured `JWT_SECRET`, `SESSION_PEPPER`, `PASSWORD_PEPPER`, and the separate staging `TANWEER_OWNER_SECRET` sourced from `TANWEER_OWNER_SECRET_STAGING`. Secret values must never be written into this notebook. This is separate from the production Owner secret.
- **Manual-only safeguards:** Temporary push triggers on `work/release-readiness` were used only to start the Release and Staging workflows because the connected GitHub actions interface did not expose a workflow-dispatch action. The original trigger configuration was restored immediately after each run was queued. Current `build-android.yml` and `deploy-staging.yml` are back to `workflow_dispatch`-only for these manual paths; no automatic future Staging deployment was left enabled.
- **Cloudflare production safety:** No deployment, migration, secret update, or D1 write was performed on production Worker `tanweer` or D1 `tanweer-db`.
- **Device testing:** No physical Android device test has been performed yet. CI and the Staging health endpoint passing do not replace testing login, sync, chat, and Dashboard behavior from the actual devices.
- **Next steps:** Confirm the signed Release APK artifact and SHA-256 from run #76; then install it and test against the Staging API. Verify the Owner Dashboard login separately using the staging-only owner secret and test the application with clean staging data before considering any production rollout.


### 2026-11-02 — Android Staging flavor and isolated test routing

- **Production boundary:** GitHub `main` remains unchanged at `64143994070bcdc410cf044379fe2801da8f9a3c`. All Android flavor and staging routing changes remain on `work/release-readiness`.
- **Android build flavors:** Added explicit `production` and `staging` product flavors. `production` keeps package ID `com.magd.tanweer` and API origin `https://tanweer.magd.workers.dev/`. `staging` uses package ID `com.magd.tanweer.staging`, app label `تنوير (تجريبي)`, and API origin `https://tanweer-staging.magd.workers.dev/`. Staging installs side-by-side and does not replace the user's Production app or its local data.
- **Network isolation:** `NetworkModule` reads the selected build flavor's `BuildConfig.API_BASE_URL`; relative media URLs resolve through that selected base; realtime WebSocket URLs derive their `wss://`/ `ws://` origin from the same base. The previous hard-coded production URLs in media fallback and realtime chat were removed from the Android request paths.
- **Environment tests:** Added `NetworkEnvironmentTest` for API origin, relative URL resolution, WebSocket origin, and preserving absolute external URLs. Updated `ExampleRobolectricTest` so it expects the correct localized app name for Production versus Staging.
- **Release workflow:** Build workflow selects Production only on `main` and Staging on other branches. It invokes flavor-specific test and release tasks and uploads a flavor-named APK with SHA-256. The permanent workflow configuration retains push only on `main` plus manual `workflow_dispatch`; temporary push triggering of `work/release-readiness` was removed after the validation build was queued.
- **Dashboard origin label:** Changed the Owner Dashboard's canonical URL label/comment from a Production-specific host to a relative `/dashboard` path, so the same dashboard HTML labels the current Worker origin correctly in Staging.
- **Staging identity:** `server/wrangler.jsonc` now sets `ENVIRONMENT = "staging"` in the staging environment. Cloudflare Worker settings were read back and confirmed that this value is active alongside the four required staging secret bindings. No secret values are recorded here.
- **Latest Staging deploy:** Run #7, ID `41447780840`, succeeded. It passed server typecheck/tests, Wrangler dry-run, credential verification, staging migration step, staging secret configuration, Worker deployment, API health check, and Owner Dashboard route smoke test (login page HTTP 200 with the expected control-center markup; unauthenticated dashboard request HTTP 302).
- **Staging database state:** `tanweer-staging-db` remains isolated. Read-only verification after deployment showed one user (the reserved non-login `SYSTEM_OWNER` principal), zero contents, and one seeded global group. No student/teacher test accounts or test content were generated by the deployment.
- **Android build history:** Run #77 exposed the disabled Android Gradle `resValue` feature; moved the staging app label to `app/src/staging/res/values/strings.xml`. Run #78 then exposed the old Robolectric test's fixed expectation of the Production app label; changed the test to assert the correct name for the selected flavor. The corrected Staging Release run #79 is currently in progress: unit tests, release keystore preparation, and keystore alias validation succeeded; signed Release assembly is the active step. No Staging APK artifact or checksum is confirmed yet.
- **Earlier Production-targeted artifact:** Android run #76 succeeded, but it predates the environment split and targets Production. Do not use it for this testing session. The intended test artifact is only the Staging-flavor Release from run #79 after successful verification and upload.
- **Manual test plan:** Install the Staging APK alongside the existing Production app. Use only the Staging backend and the separately configured `TANWEER_OWNER_SECRET_STAGING` for the Owner Dashboard. Test registration/login, offline sync/retry, media uploads and preview, realtime group chat, role-specific dashboards, and owner operations. Verify Dashboard reports `staging`. Keep Production unused during test-data creation.

---

# END OF GPT.md

> **Final reminder to the assistant:** This notebook is not a replacement for reading code. Read it to understand what the project means, then inspect the latest commit to know what is actually implemented.
