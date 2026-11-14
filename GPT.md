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
- The latest verified source-code checkpoint for the system-purge reset fix is 6108a4c962814f6cdf288f07fca05af870810bed. Documentation-only commits may advance the branch after that code checkpoint; always fetch the live HEAD before editing.

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

### Server system-purge reconciliation — implemented 2026-11-14

- The Owner Dashboard's atomic rotation of the public data_generation value is the explicit signal for a system-wide data purge. Android stores the last-seen generation under a key namespaced by normalized API origin and signed-in account ID.
- A missing/blank local marker is a legacy or first-install baseline: initialize it without deleting anything. A blank server value, failed request, same generation, or Staging/Production origin change must never trigger a wipe.
- On a confirmed mismatch, close the outbox gate and stop/await queue work before cleanup. Do not call the remote logout endpoint: the global purge already removed server sessions.
- The forced local reset clears every Room table (including pending outbox rows), account-scoped preferences and in-memory account UI state; cancels account reminder alarms/notifications; clears notebook data; and removes all app-private files/caches and downloaded PDFs. Clear the auth token and return to the sign-in screen.
- Persist the new generation marker only after local cleanup succeeds, so an interrupted cleanup can be retried. Preserve app-wide preferences (theme, typography, notification/haptics settings) and the stable installation device ID.
- Ordinary user logout still isolates Room/account state and notebook data, but preserves shared downloaded PDF books. The all-private-storage wipe is reserved for the explicit system-wide purge.
- Check the generation at startup and when the app returns to the foreground. There is no claim of instantaneous remote push while the app remains suspended.
- Regression coverage: StartupPersistencePolicyTest verifies safe first-baseline behavior, explicit generation mismatch, and account-preference cleanup scope.

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


### 2026-11-02 — Staging Release retry status

- **Build #79:** GitHub Actions run ID `41447916533`, SHA `44e60d633c5a3a466bac0d3b48bc130d2cac85e1`. Staging unit tests passed, the release keystore was prepared and its alias validated, but the signed Release assemble step remained `in_progress` with no artifact available during the latest inspection.
- **Build #80:** Started a separate Staging retry as run ID `41448942136`, SHA `7ac6cafb818d4a32a1a2386d019f796cd0b2f03b`, using a per-commit concurrency group so it would not cancel #79. At the latest inspection its unit-test step was still `in_progress`; no artifact was available.
- **Workflow trigger safety:** The temporary push trigger used to start #80 was removed after it entered the Actions queue. Current Android workflow push trigger is main-only with `workflow_dispatch` retained. The per-commit concurrency grouping remains, so distinct commits do not cancel each other's build runs.
- **Delivery restriction:** Do not hand out the earlier Production-targeted build #76 for this test session. Do not claim a Staging APK exists until a Staging run succeeds through APK verification and artifact upload and its SHA-256 file has been checked.


### 2026-11-02 — Staging APK delivered and backend test environment ready

- **Successful Staging Release:** Android GitHub Actions run #79, ID `41447916533`, commit `44e60d633c5a3a466bac0d3b48bc130d2cac85e1`, completed with `success`. Unit tests, release-keystore preparation, alias validation, signed Staging Release assembly, APK verification, rename, and artifact upload all succeeded.
- **Artifact:** ID `13143271835`, name `Tanweer-Staging-release-44e60d633c5a3a466bac0d3b48bc130d2cac85e1`, ZIP size 22,615,358 bytes. Artifact expiry: 2026-12-02.
- **APK extracted name:** `Tanweer-44e60d6-staging-release.apk`; size 23,181,624 bytes. Its embedded SHA-256 file reports `4846e276420eb09a678e8d895b59db4ecf30026cf841517d54dae3e6fd8a1b67`; the extracted APK was independently hashed and matched exactly. `jarsigner -verify` also reported `jar verified`.
- **Install identity:** Package is configured as `com.magd.tanweer.staging`, label `تنوير (تجريبي)`, version `1.2.0-staging`. It is a separate app from Production `com.magd.tanweer`, so install side-by-side and keep its local storage separate.
- **Backend target:** The Staging flavor uses `https://tanweer-staging.magd.workers.dev/` for REST, derives realtime WebSocket URLs from the same selected API origin, and resolves server-relative media URLs against that origin. Production flavor remains pointed at `https://tanweer.magd.workers.dev/`.
- **Staging Worker current deployment:** Worker `tanweer-staging`, deployment `ce0a7e7a-4244-44f6-8778-1982cfba5add`, active version `dd8c9b67-c63a-4c46-ab64-d5a7e13737ce` at 100%. Cloudflare Worker settings confirm `ENVIRONMENT=staging`.
- **Dashboard smoke tests:** Staging deploy workflow run #7, ID `41447780840`, completed successfully. The workflow verifies API health, Owner Dashboard login HTTP 200 with expected HTML, and unauthenticated `/dashboard` redirect HTTP 302.
- **Staging D1:** Database `tanweer-staging-db` UUID `603edee6-c5f7-4158-8256-24b109110ec0`. Read-only counts: one reserved `SYSTEM_OWNER` user, zero content rows, one seeded global group. No test users or content were seeded automatically.
- **Staging-only secrets:** `JWT_SECRET`, `SESSION_PEPPER`, `PASSWORD_PEPPER`, `TANWEER_OWNER_SECRET` exist on the staging Worker. The Owner Dashboard should be accessed using the separate GitHub Actions secret `TANWEER_OWNER_SECRET_STAGING`; never use the Production Owner secret for Staging testing.
- **Second build attempt:** Run #80, ID `41448942136`, was started as an independent backup using per-commit concurrency after run #79's build step had appeared stalled. At the time of this notebook update, #80 remained in progress at unit tests; run #79 is the confirmed successful artifact and is the one intended for testing.
- **Production boundary:** `main` remains unchanged at `64143994070bcdc410cf044379fe2801da8f9a3c`. No Production Worker deployment, Production D1 migration/write, or Production secret modification was performed.
- **Manual workflow safeguards:** Android push remains main-only with manual `workflow_dispatch`; Staging deployment remains manual-only with `workflow_dispatch`. The temporary branch push triggers used to start CI were removed. Android build concurrency is scoped by both ref and commit SHA.
- **Remaining work:** User must install and test the Staging APK on real devices. Focus on registration/login, profile and role dashboards, offline queue/sync, media uploads/retries/previews, realtime chat, and Owner Dashboard operations. Confirm the Dashboard labels the environment as `staging`; keep all created test records in Staging. No physical-device testing has yet occurred.


### 2026-11-02 — Owner Dashboard JavaScript syntax regression fixed and Staging redeployed

- **User-reported failure:** Owner Dashboard displayed but all click handlers failed. Browser console showed `Uncaught SyntaxError: Unexpected identifier 'data'` and consequent `switchTab is not defined` / `toggleMobileMenu is not defined`.
- **Root cause:** In `server/src/services/dashboard.ts`, the homework-media card HTML was assembled inside a single-quoted JavaScript string. Its inline handler called `this.getAttribute('data-url')`; quote escaping was lost in the generated inline script, terminating the string early and preventing the entire dashboard script from parsing.
- **Code fix:** Replaced that handler with `openMediaLightbox(this.dataset.url)`, avoiding nested quote parsing. The rendered card still uses the same `data-url` attribute and opens its media lightbox with the corresponding URL.
- **Regression protection:** Added D-11 to `server/test/dashboard-fixes.test.ts`. It extracts the rendered dashboard inline script, checks TypeScript parser diagnostics, prints nearby lines on parse failure, and verifies the script can be parsed by `new Function`. This test failed on the original broken HTML with the same `Unexpected identifier 'data'` error, then passed after the fix.
- **Fix commit:** `54b475b05e61ceb6691e42533451731e7c055e19` (`fix(dashboard): preserve JavaScript syntax in homework media cards`).
- **Verification:** Server CI run `41451715769` completed successfully on commit `6f06afa4e13833953eb74e13d89590983c71c8ec`; the run includes typecheck, the full server tests (including D-11), and Wrangler dry-run checks.
- **Staging deployment:** Deploy run `41451897054` completed successfully. It checked out only `work/release-readiness`, passed server tests and Staging Wrangler validation, verified Cloudflare credentials, applied migrations to `tanweer-staging-db` only, configured Staging secrets, and deployed only Worker `tanweer-staging`. New deployed Worker version ID: `6d4e5eb3-42e4-4dd6-aca5-c03b1177d57c`.
- **Post-deployment checks:** `/api/v1/health` succeeded; Owner Dashboard login returned HTTP 200 with the expected Control Center marker; unauthenticated `/dashboard` returned HTTP 302 to login. These route smoke tests passed in run `41451897054`.
- **Workflow safety restored:** The temporary push trigger used to deploy the fix was removed immediately after the successful deployment. `.github/workflows/deploy-staging.yml` is back to `workflow_dispatch` only. Its deployment job still checks that the selected ref is `work/release-readiness`.
- **Branch and Production boundary:** Current `work/release-readiness` head after recording this note is expected to advance with this notebook commit. `main` remains `64143994070bcdc410cf044379fe2801da8f9a3c`. No Production Worker, Production D1, or Production secrets were changed.
- **Outstanding separate console item:** The screenshot also showed `/favicon.ico` returning HTTP 401. That is independent of the syntax failure and does not cause the click handlers to disappear; it was not changed as part of this targeted fix.
- **User retest:** Ask the user to hard-refresh the Staging Dashboard (Ctrl+Shift+R / Ctrl+F5) so the browser requests the new HTML, then test the navigation menu and tabs. No APK rebuild is required for this server-rendered Dashboard fix. Physical browser interaction is not yet independently observed by the assistant.


---

# END OF GPT.md

> **Final reminder to the assistant:** This notebook is not a replacement for reading code. Read it to understand what the project means, then inspect the latest commit to know what is actually implemented.


### 2026-11-02 — Official exam persistence, three-device enforcement, and Staging release deployment

- **Working branch:** `work/release-readiness`. Production `main` was read back at `64143994070bcdc410cf044379fe2801da8f9a3c` and was not modified.
- **Room/KSP repair:** Removed the problematic `@ColumnInfo(defaultValue = "0")` annotation from the local homework entity while keeping the SQL migration default. This resolved the KSP missing-type failure. The Android migration test also accidentally created `cached_exams` twice; the duplicate legacy table creation was removed.
- **Official exam state:** Added `ExamEntity.isOfficial`, retained it through server-to-Room projections and optimistic local writes, and added Room migration `14 -> 15` with `isOfficial INTEGER NOT NULL DEFAULT 0` for legacy cached exams. The migration test verifies that old exam rows and their fields survive and that the new flag defaults to false. Exam cards show the official-publication marker.
- **Three active devices:** Added `MAX_ACTIVE_DEVICES = 3` and `server/src/services/devices.ts`. Session replacement, expired-session cleanup, distinct-device counting, conditional session insertion, and device inventory writes are performed with D1 batch statements. A same-installation login replaces its prior token instead of consuming another slot. Failed recovery-code redemption releases its reserved session; current-device logout also cleans stale inventory.
- **Android identity:** Added `DeviceIdentity.kt`. The app derives a namespace-specific SHA-256 identifier from `Settings.Secure.ANDROID_ID`; it never sends the raw Android ID. A persisted random fallback is used if Android does not provide a usable ID. Registration, password login, and recovery login send this stable identity and app metadata.
- **Account APIs:** Added authenticated `GET /api/v1/auth/devices`, `DELETE /api/v1/auth/devices/{deviceId}`, and `POST /api/v1/auth/logout`. The current device cannot be revoked from the device list; the user logs it out through the logout operation. The app Settings screen displays active devices out of three, marks the current device, allows another device to be removed, and can refresh the list.
- **Device tests:** Added focused device-service tests and extended the integration flow to register three devices, reject a fourth, revoke one old device, then allow a replacement. The integration mock models D1 conditional writes and same-device token rotation. Regex escaping in the mock was corrected after the first run. Final server CI run `41482161120` on commit `9e5d704c3bf708cb1f684c9a21f74c7a929351e0` succeeded: TypeScript typecheck, **27 test files / 172 tests**, and Wrangler Production + Staging dry-run validation.
- **Staging deployment:** Deploy run `41482733467` succeeded on branch `work/release-readiness` (trigger commit `1b8d21a1f437db7ce2db1ede9f8e8298f0b586b7`). It reran typecheck and tests, validated the Staging Worker bundle, applied migrations to `tanweer-staging-db` only, configured staging secrets, and deployed `tanweer-staging` only. Cloudflare reports deployment ID `74e3ea84-2969-44fc-846a-c9d860b2394a`, active version `067f6852-32ee-4864-9db0-f048e669b7cd`, 100% traffic. The deployed Worker settings read back `ENVIRONMENT=staging` and the Staging D1 binding UUID `603edee6-c5f7-4158-8256-24b109110ec0`.
- **Post-deploy route checks:** The workflow received a health response with `status=ok` and `app=Tanweer`; `/dashboard/login` returned HTTP 200 and the expected Control Center marker; unauthenticated `/dashboard` returned HTTP 302. The Dashboard remains served by the Worker.
- **Staging secrets:** Cloudflare lists `JWT_SECRET`, `SESSION_PEPPER`, `PASSWORD_PEPPER`, and `TANWEER_OWNER_SECRET`. The Owner secret was supplied from the separate GitHub Actions secret `TANWEER_OWNER_SECRET_STAGING`. No secret values are recorded here. Production `TANWEER_OWNER_SECRET` was not changed.
- **Staging D1 state at final read-only inspection:** `users=3` (one each with roles `SYSTEM_OWNER`, `ADMIN`, and `STUDENT`), `sessions=2` with two distinct active device IDs, `devices=0`, `contents=1`, and `groups=2`. Migrations are applied through `0026_media_upload_ownership.sql`. This differs from the previous empty-test-data snapshot; do not assume the Staging database is empty. No purge or deletion was performed. Existing sessions without device inventory metadata still appear in the account device list through the sessions table and may show generic metadata until a fresh login writes the devices row.
- **Final Staging APK:** Android run `41484762921`, commit `db0ef140fac67480670f1712cc4c8b69e8bff94b`, succeeded through unit tests, release-keystore preparation and alias validation, signed Release assembly, APK verification, rename, and artifact upload. Artifact ID `13161661715`, name `Tanweer-Staging-release-db0ef140fac67480670f1712cc4c8b69e8bff94b`, ZIP size 22,632,518 bytes. APK entry `Tanweer-db0ef14-staging-release.apk`, 23,198,006 bytes. Verified SHA-256: `a2e79a8df19b56c0931c24488e630660b60879a97048742d350333005dcfe0be`; embedded checksum matched the extracted APK and `jarsigner -verify` reported `jar verified`. This is the APK to distribute for Staging testing.
- **APK install identity:** Package `com.magd.tanweer.staging`, Staging label `تنوير (تجريبي)`, version `1.2.0-staging`. It installs alongside Production package `com.magd.tanweer` and targets `https://tanweer-staging.magd.workers.dev/`.
- **Final Staging rebuild:** Run `41484762921` completed successfully after the earlier inspection. It includes the final Settings timestamp label correction (`آخر تسجيل دخول`); all Android unit tests, signed Release assembly, APK verification, and artifact upload passed.
- **Workflow safety restored:** `.github/workflows/build-android.yml` automatic push is main-only, with `workflow_dispatch` retained. `.github/workflows/deploy-staging.yml` is manual-only and its job requires `refs/heads/work/release-readiness`. Temporary push triggers were removed after each workflow entered the queue.
- **Production boundary:** No Production Worker deployment, Production D1 migration/write, Production secret update, or cleanup was performed. Production `main` remains `64143994070bcdc410cf044379fe2801da8f9a3c`.
- **Physical-device testing:** Not performed by CI. Next: install the verified Staging APK, sign into the existing Staging test accounts, verify Settings > registered devices, test three concurrent installations and revocation/replacement, then exercise normal registration/login, content/media, chat, and Owner Dashboard workflows. Preserve current Staging data unless the owner deliberately decides to purge it.



### 2026-11-02 — Batch 2 implementation checkpoint: permissions, permanent recovery, and school directory

- **Working branch and boundary:** All Batch 2 changes are on `work/release-readiness`. Latest code checkpoint before this notebook update: `3a952b98e9c8c58f3ed047bd075b4c3b507b91c2`. `main` remains at the previously verified production baseline `64143994070bcdc410cf044379fe2801da8f9a3c`; no merge or Production deployment was performed.
- **Best-answer permissions are server-authoritative:** `handleMarkBestAnswer` checks the issue owner by user ID, a globally approved teacher, an active teacher membership in the issue's group, or the school administrator/System Owner. Other students and moderators cannot promote an answer. A selected comment from another issue is rejected with HTTP 404. Regression coverage includes the owner, another student who authored the selected answer, global teacher, group-approved teacher, moderator, administrator, and System Owner.
- **Android approval affordance:** The Issues detail screen only shows the approval action to the issue author or elevated roles. It also fetches the actual group membership for a student-level account so a teacher approved only within that group sees the action. This is strictly a UI hint; the server authorization remains final. Failed/unauthorized API attempts do not leave optimistic solved state behind.
- **Permanent recovery credential:** Recovery codes remain reusable and have no time-based expiration. New records use a very high legacy-compatible use ceiling and `Number.MAX_SAFE_INTEGER` expiry metadata, while authentication intentionally does not consume the code or reject it based on the old use/expiry columns. It becomes invalid only after the user rotates it or an administrator revokes/replaces it. A final credential revalidation after device-session reservation closes the concurrent rotation/revocation window; if the credential changed, the newly reserved session is cleaned up and login fails closed.
- **Protected storage and private delivery:** The verification value is stored as a peppered SHA-256 hash. A separate delivery copy is encrypted with AES-GCM under a domain-separated key derived from the configured server pepper. The new `user_notifications` table (migration `0027_private_user_notifications.sql`) stores this encrypted payload and ties it to the issued recovery token. The authenticated notifications endpoint only returns notifications for the signed-in account and decrypts a recovery code only while its related token is still current and active.
- **Self-service rotation:** The authenticated `POST /api/v1/me/recovery-code` endpoint creates a fresh permanent code, atomically revokes previous recovery/temporary credentials, replaces the hash, and stores the encrypted private notification. The Profile screen can show/hide the code and rotate it. The latest server notification takes precedence over screen-local code state, and the recovery card has a refresh action so a user can load a code reset by an administrator while the profile remains open.
- **Administrative rotation/revocation:** Owner Dashboard reissue now creates a permanent recovery code but never returns the plaintext code to the Dashboard response. It reports private in-app delivery only; the owner cannot read the user's secret through that result. Session revocation is opt-in for recovery-code resets so the user can still reach the account and retrieve the new code; the Dashboard checkbox is unchecked by default. The explicit revoke endpoint invalidates the current recovery hash, revokes old token rows, and removes the pending recovery delivery notification. Temporary passwords remain a separate expiring, one-use credential flow.
- **Unified teacher activation:** Removed the old hard-coded `TNW-8829-2026` pseudo-code from the School Admin screen. The screen now points users to the actual Dashboard role-request approval flow; the real 8-digit Dashboard-issued code is redeemed from the Profile screen via the same server-side one-time verification path. No independent fake/static teacher code remains.
- **School directory and distinct-account statistics:** The Admin Dashboard API builds one row per user, aggregates active group memberships before choosing the user's highest effective role, and returns teacher, moderator, and student lists with account identity, phone, grade/section, and active-group count. Total students/teachers/moderators/admins are computed from unique accounts, not duplicated memberships. The Android School Admin screen now has separate tabs for teachers, moderators, students, operations, and policies, and displays the distinct counts. The “create a new school section” UI action was removed; official school sections remain the predefined hierarchy. The optional-group creation API used elsewhere in the app was not removed.
- **Server CI:** Run `41496656194` completed with `success` on commit `6006647e8f1be8e1f9c5c0f0f21004ba70670a61`: TypeScript typecheck, **29 test files / 182 tests**, and Wrangler dry-run validation for both Production and Staging passed. The only commits after that successful server run (`8b30d2746578d8654c1e69729c2a0167703834a5` and `3a952b98e9c8c58f3ed047bd075b4c3b507b91c2`) changed only the two Android screen files; the server tree is unchanged from the successful CI commit.
- **Android build caveat:** The connected GitHub Actions interface in this session does not expose a workflow-dispatch action. The Android workflow is deliberately still main-push/manual only; it was not changed to create automatic branch builds. Therefore the latest two Android UI changes have not yet been verified by a fresh Gradle CI build or packaged into a new APK. The previously delivered Staging APK predates Batch 2 and must not be treated as containing these changes.
- **Staging migration/deployment:** Migration `0027` is present in the branch but has not yet been applied to `tanweer-staging-db`; no Staging deployment was performed as part of this checkpoint. Do not assume the deployed Staging Worker has the new notification endpoints/table. Deploy Staging only after the remaining planned implementation batches and their final validation, then verify the migration and route smoke tests.
- **Data and production safety:** No user data was purged or deleted from either database. No Worker secrets were displayed or modified. No Production Worker, Production D1, or Production configuration was changed.
- **Remaining validation before the user's requested three-batch manual test:** Run a Staging-flavor Android Gradle build/unit tests for the two latest screen changes, apply migration `0027` to Staging only during the final Staging deploy, verify self-rotation/admin-rotation/revocation on test accounts, verify group-level teacher and school-admin best-answer behavior, and review the directory and unique counts against test accounts. The user explicitly wants to complete all three batches before beginning the comprehensive manual app test; do not ask them to test Batch 2 now.




### 2026-11-03 — Batch 3 implementation and Staging checkpoint

- **Working branch and Production boundary:** Batch 3 work remains isolated on `work/release-readiness`. Current branch head before this notebook checkpoint: `4f207f441cdf317c23a1d08bbdb7cbccc9cd3a8b`. Production `main` remains `64143994070bcdc410cf044379fe2801da8f9a3c`; no merge, Production deploy, Production D1 migration, secret update, or account-data cleanup was performed.
- **Public/private groups:** The branch now has separate discoverable PUBLIC and PRIVATE group behavior. Public non-class groups join directly and can be left; private groups create pending join requests, can be re-requested after rejection, and are reviewed through the group-manager queue. Group creation supports ADMIN, TEACHER, and MODERATOR, with name, icon, description, and visibility. Accepted joins post a system chat event; request outcomes are persisted as private in-app notifications.
- **Notification authorization alignment:** Commit `fe04fa79c74b3b111f454344ee48091719f78419` fixed a real regression: globally authorized school ADMIN/MODERATOR/TEACHER/VERIFIED_TEACHER accounts must receive private-join-request notifications even if they have not joined the group, because the same school-level permissions let them review the queue. Active group-level MODERATOR/TEACHER/ADMIN managers are also notified. The query uses DISTINCT, excludes the requester, and does not notify SYSTEM_OWNER as a normal school manager. `server/test/groups-hardening.test.ts` now covers the global-role recipients and duplicate/requester exclusion.
- **School base groups and server-enforced chat:** Migration `0028_group_visibility_chat_policies.sql` adds visibility/chat-policy metadata, keeps `all` PUBLIC with `MEMBERS` chat, and creates `staff_official` PUBLIC/discoverable with `STAFF_ONLY` chat. `getGroupChatSendPermission` re-reads active membership and current role/policy, while Durable Object WebSocket messages revalidate the permission for each send; client-side disabled composer is only a UI hint. Staging read-only inspection confirms `all` has `visibility=PUBLIC, chat_policy=MEMBERS` and `staff_official` has `visibility=PUBLIC, chat_policy=STAFF_ONLY`.
- **Unified schedule decisions:** Schedule proposal creation snapshots eligible voters and creates the linked community decision and initiator vote in one D1 batch. Legacy schedule proposal vote/approve/reject routes are routed into the unified `community_decisions/community_votes` engine; there is no separate manager approval bypass. The decision response emits only the human-readable proposal reason, not the stored JSON voter snapshot. Migration `0029_schedule_proposals_unified_decisions.sql` backfills legacy proposals and votes. The Android schedule flow navigates to the Community Decisions screen after creation.
- **School circulars:** Official school announcements use persisted `events` rows with category `ANNOUNCEMENT`, group `all`, event date, creator/source, and body. Events list and Home show them to users with eligible group membership, rather than leaving them in an administrator-only dashboard record.
- **UI refinements:** `rememberSaveableStateHolder` and per-screen SaveableStateProviders preserve nested screen/list state when navigating back. Profile phone presentation uses `formatYemeniPhoneForDisplay` for local Yemeni mobile format `7XX XXX XXX`; it formats a display string only and leaves the stored phone value unchanged.
- **Server CI:** Run `41627278199` succeeded on the notification fix commit: TypeScript typecheck, **30 test files / 201 tests**, and Wrangler Production + Staging dry-run validation all passed.
- **Staging deployment:** Run `41627713773` succeeded. It reran server typecheck/tests, validated the Staging Worker bundle, applied D1 migrations, configured Staging secrets, deployed the Staging Worker, and passed Staging health and Owner Dashboard smoke checks. D1 UUID `603edee6-c5f7-4158-8256-24b109110ec0` is now migrated through `0029_schedule_proposals_unified_decisions.sql`; `0027_private_user_notifications.sql`, `0028_group_visibility_chat_policies.sql`, and `0029_schedule_proposals_unified_decisions.sql` are applied. No rows were deleted or purged.
- **Android compile fixes:** Initial Staging Android build run `41627713952` exposed four Kotlin compile errors. Commit `b9a8866bf445e56cab1a753ecd1495986345b2a8` fixed them by removing an invalid `GroupMembersResponse.message` access, moving the discover-group pending-request flag into the correct item scope, and moving ProfileScreen imports ahead of declarations. A second run compiled the app source successfully and reached unit tests.
- **Robolectric CI environment:** Android run `41628166634` then failed 5 of 27 tests because Robolectric's `MavenArtifactFetcher` could not download Android SDK test artifacts from its default repository endpoint; the failures were in `ExampleRobolectricTest`, `DatabaseMigrationTest`, `ExamDeleteOutboxTest`, `PendingSnapshotRetentionTest`, and `ScheduleSnapshotReconciliationTest`. The application Kotlin source compiled in that run. Commit `b2047972760c51e9b3843a7eb43b52e37bf649ff` configures Robolectric's supported `robolectric.dependency.repo.url` property to use `https://repo.maven.apache.org/maven2` (rather than the default `repo1`) and sets the repository ID to `mavenCentral`. The next Staging Android run `41628658638` was still in progress at this checkpoint; do not claim an APK or passing Android tests until its final conclusion and artifact have been verified.
- **Workflow safety:** Temporary `work/release-readiness` push triggers were added only long enough to queue the isolated Staging deployment and Android build, then restored. Current `.github/workflows/build-android.yml` is main-push only plus PR/workflow_dispatch; `.github/workflows/deploy-staging.yml` is manual-only and still guards the job to `refs/heads/work/release-readiness`.
- **Manual validation still outstanding:** CI and Staging health checks are not multi-account feature testing. Do not claim manual end-to-end verification. Next test on separate Staging accounts: create public/private groups, direct join/leave and system join event, request/accept/reject and notifications for global teachers/moderators and group managers, students' read-only behavior in `staff_official` with server denial on actual WebSocket sends, schedule decision creation/votes/expiry/application, and persisted announcement visibility/source/date in student views. Also test nested list restoration and phone display while confirming the stored digits are unchanged. Physical-device testing has not been performed.
- **Staging data safety:** Keep existing Staging accounts and rows. Do not purge or reset the D1 database during feature verification. Production remains untouched.


### 2026-11-03 — Batch 3 final status: implementation, Staging deployment, and signed APK

- **Supersedes the earlier Batch 3 in-progress checkpoint above.** Working branch at this checkpoint: `work/release-readiness`; final production `main` remains `64143994070bcdc410cf044379fe2801da8f9a3c`. No Production Worker deployment, Production D1 migration/write, Production secret change, merge to `main`, or user-data purge was performed.
- **Public/private groups:** Public discoverable non-class groups join directly and can be left; private groups create pending requests, can be resubmitted after rejection, and appear in a manager review queue. Group creation is server-authorized for school ADMIN, TEACHER, MODERATOR, and SYSTEM_OWNER and includes name, icon, description, and PUBLIC/PRIVATE visibility. Acceptance posts a system chat event with the member's name and sends the student a private in-app result notification; rejection also sends a private notification.
- **Manager notification correction:** Commit `fe04fa79c74b3b111f454344ee48091719f78419` aligns request notifications with the actual server permission model: school-level ADMIN/MODERATOR/TEACHER/VERIFIED_TEACHER recipients are notified even if not group members, alongside active group-level managers. The query is DISTINCT and excludes the requester.
- **Two school groups and chat security:** Migration `0028_group_visibility_chat_policies.sql` makes `all` PUBLIC / MEMBERS and creates `staff_official` PUBLIC / STAFF_ONLY. Cloudflare Staging inspection confirms both are discoverable SHARED groups. The server checks current active membership and policy for each HTTP send and each Durable Object WebSocket message; students can read the staff group but cannot send, even with a modified client. Leaving either SHARED school group is allowed.
- **Existing public-group requests:** Staging inspection found two legacy PENDING memberships in `all` after the group had become public. Added migration `0030_activate_existing_public_school_group_requests.sql`, restricted to `group_id='all'` and PUBLIC non-class policy. It promotes only those legacy pending requests; it does not modify private groups, classes, or other groups. After applying migration 0030, read-only D1 verification showed both existing `all` memberships ACTIVE.
- **Unified timetable decisions:** Schedule proposal creation inserts the proposal, linked `community_decisions` row, and initial `community_votes` row in one D1 batch. The legacy schedule vote/approve/reject routes enter the same unified decision engine. The moderator dashboard was changed to count/filter/render schedule decisions from `community_decisions` only, removing duplicate proposal cards and double-counting. The shared decision card shows status, vote counts, quorum, and voting controls. The response now joins the linked schedule proposal and subjects to include weekday, slot, current subject, proposed subject, and reason, while never exposing the internal eligible-voter snapshot. Regression test added in `server/test/community-decision-details.test.ts`.
- **School announcements:** Admin-published circulars are stored as `events` rows under group `all` with category ANNOUNCEMENT, date, creator/source, and content. Home and Events surfaces include school-wide events for class members. A repository mapping omission was fixed so `sourceName` and `isOfficial` survive conversion from Room to the UI model. Staging currently has zero announcement rows, so actual admin-post/student-view acceptance should be performed using the test accounts.
- **Navigation and profile:** Existing `rememberSaveableStateHolder` + per-screen SaveableStateProviders preserve nested screen/list state. `formatYemeniPhoneForDisplay` presents local mobile numbers as `7XX XXX XXX`; it returns a display string and does not rewrite the stored phone number.
- **Server CI:** Run `41631114982` succeeded on commit `f740b1fb2c74cfa316b288234225a28b076f4089`; TypeScript, **31 test files / 202 tests**, and Wrangler Production + Staging dry-run validation passed. Run `41631655630` also succeeded after adding migration 0030; its typecheck, tests, and Wrangler validations all passed.
- **Staging deployment:** Run `41631370123` deployed the unified schedule-detail response to `tanweer-staging`, version `48a17b44-8e43-4fd9-a7c6-f9520e67493e`, with health and Owner Dashboard smoke tests passing. Run `41631778981` then applied migration `0030_activate_existing_public_school_group_requests.sql` to Staging D1 and deployed the latest Worker, version `be45baff-80d5-4fd1-9ff8-7b0115335052`; health returned `status=ok, app=Tanweer`, `/dashboard/login` returned 200, and unauthenticated `/dashboard` redirected to login. Staging D1 is migrated through `0030`; no rows were deleted. Aggregate inspection shows `all` remains PUBLIC/MEMBERS with two ACTIVE members and `staff_official` remains PUBLIC/STAFF_ONLY.
- **Android compile/test fixes:** The compile errors in `TanweerRepository.kt`, `GroupsScreen.kt`, and `ProfileScreen.kt` were fixed in commit `b9a8866bf445e56cab1a753ecd1495986345b2a8`. Robolectric's default Maven endpoint caused 5 artifact-download failures; `app/build.gradle.kts` now uses canonical Maven Central. The Room migration test was corrected to include `MIGRATION_15_16`, and the full Kotlin source compiles.
- **Final Staging APK:** Android run `41630247868` succeeded on build trigger commit `b6f7ea551d9ff2c63e60e7630ae8c60136ee422b` (the Android source tree includes moderator-dashboard deduplication from `73fd259d448f99991cfa91af00fd386ec0eb37f9`; the trigger-only workflow change was restored immediately after queueing). Unit tests, keystore preparation, alias validation, signed Release assembly, APK verification, rename, and artifact upload all succeeded. Artifact ID `13227474639`, name `Tanweer-Staging-release-b6f7ea551d9ff2c63e60e7630ae8c60136ee422b`. Extracted APK: `Tanweer-Staging.apk`, **23,214,393 bytes**, SHA-256 `7c384f86d51a716f96965463a90eb1fc4bc54d3e0fde96c4c5593874be3f1965`; artifact checksum matched and `jarsigner -verify` reported `jar verified`. Gradle config sets Staging package suffix `.staging`, app id `com.magd.tanweer.staging`, and base URL `https://tanweer-staging.magd.workers.dev/`.
- **Workflow safety restored:** `.github/workflows/build-android.yml` is again main-push only (plus PR/manual trigger); `.github/workflows/deploy-staging.yml` is manual-only and retains the `work/release-readiness` branch guard. Final branch head before this GPT.md update: `8b0846f43729b4ddcdce174dfac2c185e4dbab73`. Production main remains unchanged.
- **Manual acceptance testing remains pending:** CI and Worker smoke tests do not simulate independent signed-in users or physical devices. Next manual validation on Staging accounts/devices: create public and private groups as teacher/moderator/admin; direct join/leave and system join event; private request accept/reject and manager/student notifications; read-only student WebSocket send denial in `staff_official`; schedule proposal details/vote/expiry/application; admin circular publishing and student Home/Events visibility; list position restoration; phone display with stored digits unchanged. Do not claim these account/device tests have already been performed.
- **Staging data safety:** The existing test accounts and rows remain. Migration 0030 only activates pending rows in the newly public `all` group; no account or content data was purged. Production remains untouched.


## Batch 4 — D1 lesson media reliability and School Admin dashboard (2026-11-04)

### Storage decision (R2 is intentionally not used)
- R2 is not enabled for this Cloudflare account and no R2 bucket or binding was added.
- The existing media path continues to store small image BLOBs in D1. The upload handler caps a single image at 1,800,000 bytes (under D1's 2,000,000-byte BLOB/row limit).
- Read-only Staging inspection before this deployment showed 6 media blobs / 107,789 bytes total / 34,303-byte largest blob; database storage was about 675,840 bytes. No media rows or user data were removed.
- The Worker bundle reports only `DB`, `CHAT`, and `ENVIRONMENT=staging`; it does not bind R2.

### Lesson media defects fixed
- `ContentMediaJson` is now the single Room codec for lesson media. It reads both historical JSON arrays of URL strings and current JSON arrays of media objects, and retains id, page order, URL, MIME type, size, and checksum.
- The group-content, cross-group academic archive, and daily-detail sync paths now persist complete media objects instead of converting them into URL-only strings. Added Android tests for full metadata round-trip, legacy cache compatibility, invalid entries, and empty/malformed input.
- The lesson notebook no longer asks Coil to fetch a protected media URL without authentication. It downloads only the visible page through the authenticated media endpoint, stores it in app-private cache, and then renders the local file. The existing download-all/offline cache action remains.
- The media HTTP helper attaches bearer and group headers only when the destination origin (scheme, host, and effective port) matches the configured Tanweer API origin; external image hosts do not receive Tanweer credentials.
- Authenticated media responses now use `Cache-Control: private, no-store` and `X-Content-Type-Options: nosniff`. The day-detail media query includes checksum metadata as well.

### School Admin dashboard improvements
- The dashboard API now returns a separate effective-admin directory, account status and activity metadata, active membership totals, disabled account totals, event/announcement/issue counts, and D1 media count/bytes.
- The Android model now includes the new admin directory and statistics.
- The screen has horizontally scrollable tabs suitable for narrow phones, a preserved tab/search/list state, searchable teacher/moderator/student/admin directories, visible active/disabled account status, an analytics section, and an expanded school summary.
- The Owner-only system dashboard was not changed.

### Commits and current refs
- Media metadata codec + Android regression tests: `4cb070959a49ad9b7a5eb9be2edd0afa3dea00f3`.
- Authenticated page download + protected-media headers + daily-detail checksum: `ba580d5474c921c3d46ad7b6d1a685e29de79c06`.
- Admin dashboard server statistics and model: `bf1e470a9b2a1b9ec978c33e16c6087911cbf3ef`.
- Admin dashboard searchable/mobile UI: `269874d914a5904d107422a4c58750a69cffcf83`.
- Temporary branch-only CI triggers were committed to start validation, then immediately restored. The current working branch head after restoration is `a6e03282655735770f64af5b136f1fd876844d8a`.
- Production `main` remains `64143994070bcdc410cf044379fe2801da8f9a3c`; it was not changed, merged, or deployed.
- `.github/workflows/build-android.yml` is restored to build on `main` (plus PR/manual); `.github/workflows/deploy-staging.yml` is restored to manual-only with a strict work-branch guard.

### Validation and Staging deployment
- Server checks: TypeScript passed; 31 Vitest files / 202 tests passed; Wrangler Staging dry-run passed.
- Staging deploy run: `41798829651` succeeded. Worker `tanweer-staging` version: `502c7c1c-6f3f-46a7-a21e-0567ff4398f8`. Health endpoint returned `{"status":"ok","app":"Tanweer"}`; dashboard login route and unauthenticated redirect smoke tests passed.
- Android run: `41798829494` succeeded. Android unit tests passed; release keystore preparation and validation passed; signed Staging APK assembly, APK verification, rename, and artifact upload all passed.
- Artifact: `13306328561`; archive digest: `sha256:b90643a1f08ad54a41ebc3c600533ced5ac652538b49c5255db53867f132dccd`.
- Extracted APK: `Tanweer-Staging.apk`, size 23,247,178 bytes; SHA-256: `d2a96af58cd32bec89d2262e8e37fef0c90ce9a26a111c1e106784d56ae29a0d`. Gradle flavor config makes this the separate Staging application id `com.magd.tanweer.staging`, version `1.2.0-staging`. Local `jarsigner -verify` reports `jar verified`.

### Remaining acceptance test
- Automated tests and Staging smoke checks do not simulate two independent signed-in student accounts on physical devices. After installing this Staging APK, still test a teacher publishing a multi-page lesson and a second student account in the same group opening it and downloading pages; also verify a non-member remains denied. Do not claim the physical multi-account test is complete.
- No Production D1 writes, secrets changes, Worker deployment, branch merge, or user data purge were performed.


## Batch 5 — Root-cause fix for lesson media HTTP 401 (2026-11-04)

### Root cause
- Both lesson pages and homework attachments already upload through the same authenticated multipart API: `POST /api/v1/media/upload`, with the image in the `file` form part. The server stores the bytes in `media_blobs`.
- The records then have different, correct relationship tables: lesson pages use `content_media`; homework attachments use `homework_media` plus the legacy `homeworks.media_urls` JSON field. These relationships are used by the server to authorize group members and should not be collapsed into one table.
- The 401 was in the Android download path, not the upload path. `StudentNotebookManager` opened a raw `HttpURLConnection` and looked up `tanweer_prefs/auth_token`. Login/session restore never wrote that preference: the actual session token is stored in Room (`UserEntity.token`) and mirrored into `NetworkModule` for Retrofit's auth interceptor. Consequently, page download requests could be sent without `Authorization`; `handleGetMedia` correctly returned HTTP 401 before checking media linkage or group membership.

### Fix
- `NetworkModule` now exposes a read-only `currentAuthToken()` accessor over the same volatile in-memory token used by Retrofit. `setAuthToken` normalizes blank/whitespace values to null.
- All protected media download paths in `StudentNotebookManager` now use that live session token: visible-page caching, download-all/offline caching, and saving a page to the Gallery. The unused/non-populated SharedPreferences token key is no longer read.
- Added a shared media-header builder that attaches `Authorization: Bearer …` and the group header only when scheme, host, and effective port match the configured Tanweer API origin. Internal protected downloads do not follow redirects; external image hosts receive no Tanweer token. A missing internal session token now fails fast with a clear session message rather than making an inevitably unauthenticated request.
- No R2 bucket/binding was introduced; the existing D1 BLOB media pipeline and the lesson/homework relation model remain unchanged.

### Regression tests and build
- Added `MediaRequestAuthTest` for correct bearer/group headers on the API origin, no credential leakage to external hosts, missing-token rejection, and default-port origin matching.
- Android CI run `41856466678` succeeded on trigger commit `6cc86fc10f00e8509d65224e274234a22168a042`: `testStagingDebugUnitTest` reported `BUILD SUCCESSFUL`; signed Staging Release assembly also reported `BUILD SUCCESSFUL`; release keystore validation, APK verification, artifact rename, and upload all passed.
- The temporary branch-only push trigger used to run CI was restored immediately afterward in commit `4e1ab6a98a4266b4679a208769752d112b09d227`. The final workflow configuration is back to main-push plus PR/manual, and Staging deployment remains manual-only.
- Staging artifact ID: `13337921608`; APK: `Tanweer-6cc86fc-staging-release.apk`; size 23,247,158 bytes; SHA-256 `d0056bb6b996b1307d2266d55f105d21c7b8f61887ce4c277346c3618b108e42`. Extracted artifact checksum matched and `jarsigner -verify` reported `jar verified` (the signing certificate is self-signed, as expected for sideload distribution).
- Android app source fix commit: `9005023cf3b06202129dd9c3d8a5fdc44c183a2e`. This notebook update follows the workflow-restoration commit.

### Remaining real-device acceptance
- The CI run validates Kotlin unit tests and the signed APK, but it does not emulate two separate authenticated student accounts. Still verify on two Staging devices/accounts: publish a multi-page lesson from account A, open it from account B while both are active members of the same group, and open/download pages. Also verify a non-member remains denied (expected 403, not 401). Do not describe this physical cross-account test as complete until performed.
- Production `main` remains `64143994070bcdc410cf044379fe2801da8f9a3c`; no merge, Production deployment, Production D1 write, secret change, or data purge occurred.


## Batch 6 — Protect local data, truthful publishing, compact School Admin, and per-user announcements (2026-11-05)

### Data loss root cause and fix
- Found a destructive Android startup path: any mismatch between the single global SharedPreferences data_generation marker and the Worker value called clearAllTables(), cleared the auth token, and triggered ViewModel logout. This could erase Room cache/outbox when switching API environment or account.
- Generation markers were namespaced by normalized API origin and signed-in account id, and ordinary mismatches were initially treated as refresh-only. This was the policy at the time; the 2026-11-14 system-purge work in Section 18 supersedes it for the Owner Dashboard's explicit global purge rotation while preserving non-destructive behavior for missing markers, failed requests, and environment/account scope changes.
- Added Android regression tests for missing and changed generation markers. Profile endpoint now returns defaultGroupId, which Android sync previously expected but the response omitted.

### Publishing confirmation fix
- Lesson, homework (with or without pages), exam, and event create/retry paths now require HTTP success plus explicit response success=true before marking rows SYNCED or showing a published confirmation.
- All four media upload/retry locations require upload response success=true and a returned remote URL; local file paths are never treated as remote uploads.
- Unified outbox replay now applies the same confirmation to queued exams, events, issues, comments, corrections, schedule slot creates, and chat. Server error/message content is shown on rejected writes; false success flags are not acknowledged.

### Compact school-admin dashboard
- Replaced the broad multi-tab screen with a focused dashboard: actual server statistics, searchable school-wide student directory, school announcement publishing, account status actions, and class membership removal.
- Dashboard model success now defaults to false; repository and ViewModel require explicit success=true. Errors clear stale dashboard data and are shown as errors.
- New admin-only handlers disable/reactivate student accounts (disabling revokes that student's sessions) and remove only the student's current class membership. They refuse to modify staff/admin/owner roles and preserve the account and academic contributions.
- The screen explicitly shows returned students versus total students when the API list is capped.

### Per-account school-announcement dismissal
- Added migration 0031 user_dismissed_school_announcements and POST /api/v1/events/{eventId}/dismiss.
- Dismissal is stored by user_id + event_id. The shared school event is not edited or deleted; only the dismissing account is excluded from subsequent event snapshots.
- Home screen has a small X per announcement; its local card disappears only after the server confirms the dismissal. Other students retain the card.

### Main commits in this batch
- 80cfed7b5db9ac0143272cce09ae24e2a6917ac1, f2b109ab242458e22a571f22e2af600cc73c47c4, 35e009754ddb955c568a88650be45b2ae4146cb1 — non-destructive generation policy, account/origin scoping, Android tests.
- 268ac5d93747952de6f03e744a7557b440d20d1e, f549f1bc8b4c0ae329ff92e561b6e4de16373233, a17a104801f3936300eeee8e36050479895a4773 — per-user dismissal migration, event service, router.
- 543fb65a931f661239a70c4dc99c43d62ca2ccbb, ad9df4d6a7ea75dbe1d87c4d15853070112409cd, 5953286851b40f335050f323c00a018eb00845a8, 4b9e087c8c9c1c14b05778e71ab9c4f4acded71e — Android dismissal API, repository, ViewModel, Home UI.
- 5e35c4321e6a1dbb733f96bd991442cac2eceb71, f18d7f87a44618553178783bacee1d7a2da33be6, 1e0f9ff8e48b1e141f7ae3020356c0c99c438592, 41e4c64a6808fa90ea52963892ba0a26371e801f, 01c0b824b4e6731e0e690df267e78e1ed8d705c9, 8e9e880ff8e554d15187ce4c281bbaa423636aaf — safe server/API/Android student-admin actions.
- 277c01e3a0a84ca9efed4b762bfa6ea18ba20a5d, cfe0e5481484bb165b2f01bebe5f68334b8e0376, 2abef4b2dfdc869bbc149f243ac783a840390f54, f8addcc777c18009d9dddad91ee479dca929b115, 6de9f2198f71a3dcb69c9b824e79abe5859c33b5 — truthful dashboard state and compact UI.
- f4ce0680cee3184065121cb786c44f21defd81a1, 1957697a9aa53373268ae2c41efb57cad3b1d4e1, 5222bea62ab2a5d4e9e0723f18527fa23615eeaa, 28807457f64473e7e127f2112999b7aca10680bf, 360ff6b52d1a606879f1ad72b55fceb0f30674be — direct and queued publish confirmation fixes.
- 16c3969beb44ea6ae8f998b13e92e8bb3442dbc3, b2949e64aa4485668645c82be8a576ee2b829a4d, 279a2c93c287eb08254c27b4efb64f4fdfb64c35 — new server regression tests and test mock correction.

### CI / current state
- The initial CI run identified a stale test double for the new per-account role lookup; the mock was corrected to include group roles and model the effective role query. The latest server workflow run 42069054233 then passed: TypeScript, 34/34 Vitest files, dependency audit, Wrangler Production dry-run, and Wrangler Staging dry-run. No Worker was deployed.
- Latest Android workflow run 42070665717 passed Android unit tests and Staging Debug APK assembly. The PR workflow intentionally skips release keystore preparation and signed Release assembly; this artifact is a test/debug build, not a production or signed release APK.
- Draft PR #6: https://github.com/majd7772233-a11y/Tanweer/pull/6. It remains a draft, points to main, and has not been merged.
- Debug artifact ID 13436236607, source commit dfc4335190182a71b9422e8531742e272f11c019; archive SHA-256 374a4d092414a2aa596e46d98eb8e7c86c240ba67b6eab564152565e7b2c2b88. Extracted APK `app-staging-debug.apk` SHA-256 858de8bd87f9a22d75a032e8e802f18c80da87b6b661368252b151f1be0205e5.

### Remaining work / safety
- Batch 1 CI passed. Physical two-account/two-device acceptance remains pending: test per-user announcement dismissal, student disable/reactivate and class removal, then repeat the original affected-device sync/publish test. Do not claim physical testing complete.
- Batch 2: comprehensive PDF viewer and study-tools reengineering; correct system bars/navigation insets; pinch zoom distinct from single-finger page navigation; preserve good page-turn behavior; typography follows app font-size preference with modest main-title emphasis.
- Batch 3: monthly exam calendar/history and one exam per subject per month; oral tests remain random; midterm/final stay as-is but browsable; direct schedule editing for authorized staff with immediate sync; fresh sync at app start and section entry; open lessons/homework/exams from subject timeline; academic archive and year rollover; groups/cards, always-active groups, visibility and join-reason fixes.
- R2 is not enabled and is not required. Continue the existing D1 media BLOB design. Production main remains 64143994070bcdc410cf044379fe2801da8f9a3c; no Production D1 writes, Production deployment, secret changes, merge, or data purge.


## Batch 1 — final implementation checkpoint (2026-11-05)

### Data-loss / sync safety
- Confirmed read-only aggregate counts still exist in Production D1: users 12, group_members 12, contents 9, homeworks 3, exams 5, events 2, issues 2, media_blobs 11. No records were fetched by name, and no D1 rows were written.
- At this 2026-11-05 checkpoint, server data-generation metadata was treated as a refresh signal only. This historical rule was superseded on 2026-11-14 by the scoped, explicit Owner Dashboard purge signal documented in Section 18; normal snapshot reconciliation remains non-destructive.
- A valid authenticated group-membership snapshot is authoritative, so removing a student from a class is reflected in the group list. The reconciliation only replaces group metadata; lesson, homework, exam, event, notebook and archive records live in separate Room tables and are not cleared by this operation. Blank or duplicate group IDs still cause the snapshot to be rejected.
- Lesson/homework/exam/event create and retry paths, and all lesson/homework media upload/retry paths, now require HTTP success plus response `success: true` before marking an item `SYNCED`. Android regression tests cover generation markers, application-level write confirmation, and retention of cached academic records when group membership metadata changes.

### School Admin
- Replaced the overloaded dashboard with one compact screen: D1-backed school counters, searchable student directory, explicit loading/error states, official school announcement composer, and two verified student actions.
- A manager can disable/reactivate an actual student account; disabling revokes active sessions. Reactivation requires the student to sign in again.
- A manager can remove the student from their current class membership. The account, authored lessons and academic history remain intact.
- Server-side checks calculate each target's highest role across active group memberships. Accounts with teacher, moderator, or admin roles in any group cannot be treated as ordinary students by these actions. Only the global ADMIN role can invoke this limited management API; SYSTEM_OWNER's infrastructure powers remain separate.

### School announcements
- Added migration `0031_user_dismissed_school_announcements.sql` and an account/event dismissal relation.
- A small X on each official announcement calls the authenticated dismissal endpoint. The server suppresses that announcement only for the requesting account; other students continue to receive the shared event. The local Room row is removed only after the server confirms success.
- Migration is committed but has not been applied to Production or Staging because no Worker/D1 deployment was performed.

### Tests / deliverables
- Added server tests for per-account announcement dismissal and school-admin actions, including elevated group-role protection, session revocation, and preserving student content.
- Latest server CI: run `42070665376`, passed TypeScript, 34 Vitest files / 219 tests, security audit and both Wrangler dry-runs.
- Android CI: run `42070665717`, Android unit tests and Staging Debug APK assembly passed. This PR run did not perform signed Release assembly. The APK targets `https://tanweer-staging.magd.workers.dev/`; do not use it as end-to-end evidence until the Staging Worker and migration 0031 are deployed.
- Staging Debug artifact: `13436236607`; archive SHA-256 `374a4d092414a2aa596e46d98eb8e7c86c240ba67b6eab564152565e7b2c2b88`; extracted APK SHA-256 `858de8bd87f9a22d75a032e8e802f18c80da87b6b661368252b151f1be0205e5`. 
- PR #6 remains a draft. Current branch head at this checkpoint: `67f6dfb0b98c61a03573e26b9716147cedaa39d6`. Production main remains `64143994070bcdc410cf044379fe2801da8f9a3c`.

### Acceptance still pending
- Physical two-account/two-device validation remains necessary: publish and dismiss an official school announcement on account A, confirm it stays visible to B; disable/reactivate a student and remove a test student from a class; repeat the original affected-device content refresh/publish scenario. The test APK is a debug staging build; do not claim real-device acceptance or production behavior verified until these checks are performed.
- No staging or production D1 migration was applied, no Worker was deployed, no secrets changed, no merge occurred, and no user data was intentionally deleted.

### Remaining batches
- Batch 2: re-engineer the PDF reader and study-tools hub, retain the acceptable page-turn animation, correct excess page height, isolate two-finger pinch zoom from one-finger page navigation, fix reader text extraction/rendering, ruler overlay and close control, focus audio, responsive tool cards, system-bar and bottom-navigation insets.
- Batch 3: unify typography around the app's 12/14/16 settings with modest title emphasis; monthly exam calendars and unique subject/month exams while leaving midterm/final and random oral behavior intact; direct schedule editing plus immediate server sync; sync on startup and entering each section; clickable timeline/archive lesson/homework navigation; dynamic academic year history; and groups/clubs card, messaging, visibility and join-reason fixes.


### Membership-snapshot design review correction (2026-11-05)
- Removed the default-class gate from `syncGroups()`: it could have blocked a legitimate school-admin class removal and preserved a stale active-group entry. A successful authenticated group list is now the source of truth for current memberships.
- The data-loss protection remains at the correct boundary: `syncGroups()` only replaces `cached_groups`; it does not clear the independent lesson/homework/exam/event tables or notebook files. Added a Robolectric Room regression test proving cached lesson, homework and exam rows survive membership metadata refresh.
- Retained malformed snapshot protection for blank or duplicate group IDs.
- Correction commits: `fdbcd1d450dd55e7c06b72de6f03cc0a234c609d`, `02b7be6a90c07223b4c4af3af34903d392d43559`, `753639831de2436daab1f98821f3a9a4c02dd90f`, `dfc4335190182a71b9422e8531742e272f11c019`.


### Batch 1 — Cloudflare read-only verification follow-up (2026-11-05)
- Rechecked the current branch and PR after the previous checkpoint: `work/release-readiness` is at `032cef01b22807582eee5aea439a4ee0e54ce559`; draft PR #6 remains open against `main`, not merged. Production `main` is still `64143994070bcdc410cf044379fe2801da8f9a3c`.
- Read-only Cloudflare inspection confirmed the configured D1 databases: Production `tanweer-db` (`c937c1d8-77dd-4292-a27c-43cf4bd78eae`) and Staging `tanweer-staging-db` (`603edee6-c5f7-4158-8256-24b109110ec0`).
- Production aggregate counts remain: users 12, group_members 12, contents 9, homeworks 3, exams 5, events 2, issues 2, media_blobs 11. Each query was SELECT-only; Cloudflare returned `rows_written: 0` and `changed_db: false`. No user-level rows or personal details were fetched.
- Staging's latest tracked migration is `0030_activate_existing_public_school_group_requests.sql`; migration `0031_user_dismissed_school_announcements.sql` is not applied. Production also does not have `user_dismissed_announcements`. Do not deploy the updated Worker before applying migration 0031 to the chosen target D1.
- Verified CI run metadata: server run `42070665376` and Android run `42070665717` both completed successfully on source commit `dfc4335190182a71b9422e8531742e272f11c019`; the two commits after that source build only correct documentation/checksum metadata.
- No Worker deployment, D1 migration, D1 write, secret change, merge, or Production change was made during this follow-up. Physical two-account/two-device acceptance remains outstanding.


## Batch 2 — Study reader, typography, exam calendar and schedule editing (2026-11-05)

### Branch / safety (historical correction, updated 2026-11-07)
- Repository: `majd7772233-a11y/Tanweer`.
- User requires exactly two branches, with all batch work committed directly to `work/release-readiness`: `main` and `work/release-readiness`.
- Batch 2 was developed temporarily on `work/batch-2-study-experience`, then PR #7 was merged into `work/release-readiness` (merge commit `a834068b62094de8247f9b49072090df6d9a8ffb`). The temporary branch and four old `ci/android-validation-base-*` branches were removed; the temporary cleanup workflow self-deleted.
- At the latest Batch 3 checkpoint, the only branches are `main` at `64143994070bcdc410cf044379fe2801da8f9a3c` and `work/release-readiness` at `9b524c3a965f6aa362904d783488768bf1b8dff2`.
- PR #6 remains an open draft targeting `main`; it has not been merged. Batch 3 changes are direct commits on `work/release-readiness`, not a new branch or PR.
- No Worker deployment, D1 migration apply, secret change, production write, production merge, or user-data purge was performed.

### PDF reader and study tools
- Replaced overlapping page/tap/pinch gesture handlers in `CanvasPageCurl.kt` with one gesture owner: one finger handles page turn/tap or pan while zoomed; two or more fingers handle pinch zoom. Double-tap toggles zoom. Preserve the existing page-curl animation.
- Corrected the page-curl bitmap destination to aspect-fit the PDF page instead of stretching it to the entire reader viewport during animation.
- Kept Android system bars visible and reserved safe insets; made the study-tools sheet compact, vertically scrollable, bounded by the available height and navigation-bar insets. Its header no longer uses an oversized title or claims a fixed number of connected tools.
- Focus ruler now has a visible translucent reading band, a drag handle path to reposition it, and an explicit X close control.
- Added `FocusNoisePlayer`: generated, low-volume offline focus ambience through `AudioTrack`, with start/stop/recovery and disposal cleanup. UI only reports active playback after the player starts.
- TTS now validates the selected language and the return status from `TextToSpeech.speak`, tracks multi-chunk utterances until the final chunk completes, stops prior reading when switching pages, and does not invent speech when a scanned/image page has no text. Such a page still needs OCR, which is not implemented.
- Fixed PDFBox text extraction initialization: `PdfTextExtractor.initialize(applicationContext)` invokes `PDFBoxResourceLoader.init` once before loading a PDF. Repository and reader pass application context; digital page text is cached only when extraction returns content.
- Added Robolectric PDF extraction tests that generate a text PDF and verify extraction, plus out-of-range page handling.

### Shared typography
- Converted screen and shared-component numeric font-size/line-height declarations to the shared Tanweer typography helpers across the app UI.
- Settings now exposes the three requested presets: 12sp, 14sp, and 16sp. Restored legacy values are normalized to the nearest supported scale; invalid values fall back to 12sp.
- Main title role is capped at a modest 18sp; regular content uses the selected shared 12/14/16sp scale rather than per-screen oversized values.
- Added `TypographyScaleTest` for legacy, invalid and supported scale normalization.

### Exams
- Monthly exams are constrained per group + subject + calendar month. API preflight returns a 409 conflict; D1 insert/update triggers enforce the same rule under concurrent requests.
- Historical `QUIZ` entries are included in the uniqueness guard because older Android snapshots displayed them as monthly exams. No existing exam rows are deleted or rewritten.
- Same subject can have another monthly exam in a later month; different subjects can have tests in the same month; oral exams remain unrestricted.
- Added `0032_monthly_exam_uniqueness.sql` and server regression coverage for duplicate, next-month, different-subject, oral, legacy-QUIZ and concurrent conflict cases.
- Added month selection through a calendar picker and empty-month history navigation. Oral exams are presented in their own flexible list, outside the monthly calendar, with detail opening and authorized delete.

### Schedule
- Teacher/admin direct editing no longer requires a separate activation button. Authorized users can tap an occupied slot and edit directly; students retain the existing proposal path for occupied slots.
- Schedule create/delete operations require both HTTP success and API `success: true`; after confirmation the ViewModel requests a fresh schedule snapshot and reports online/offline/error state.

### Validation checkpoint
- Server workflow run `42108844583` passed TypeScript, the full server unit/integration suite, dependency security baseline, and Wrangler production bundle validation. This was validation only; no deploy was performed.
- Latest Android workflow run at this checkpoint: `42108844559`, source `28cef2aff446f5f0bb1ab89dcfda815ec4479e4d`. Android unit tests passed, including the new PDF extraction test. Staging Debug APK assembly was still in progress when recorded; do not claim APK assembly complete until a later workflow result confirms it.
- The earlier Android failures on `7a189b9`, `623b0c4`, `ec488d3` and `2be91db` were stale runs that exposed missing LazyColumn `items` import or local `stopAudioPlayback` declaration order. Those source issues were corrected in later commits; current source is at `28cef2a`.
- Migration `0032` is committed but has not been applied to Staging or Production. A Staging APK must not be used for end-to-end testing against an un-migrated Staging Worker.
- Physical-device acceptance (pinch/page gesture separation, focus audio/TTS, bottom safe area, schedule sync, month history and two-account interactions) remains outstanding and must be performed on devices; do not claim it has been completed here.

### Still open / explicit limitations
- Digital text layer extraction is fixed; scanned/image-only PDFs still have no OCR, so their text cannot yet be read aloud.
- Device-specific TTS language packs and AudioTrack behavior require real-device verification.
- No signed Release assembly is part of PR validation. Production and Staging D1 remain unchanged.
- Batch 3 remains separate: full synchronization at startup and section entry; direct navigation from subject timeline/archive; academic-year rollover; groups/clubs UX, visibility, messaging and join-request reason; do not pull those tasks into Batch 2.


## Batch 3 — synchronization integrity, group membership and publication recovery (2026-11-07)

### Branch policy and current refs
- Continue directly on `work/release-readiness`; do not create batch-specific branches.
- Exactly two refs remain: `main` = `64143994070bcdc410cf044379fe2801da8f9a3c`; `work/release-readiness` = `9b524c3a965f6aa362904d783488768bf1b8dff2`.
- Batch 2 PR #7 is merged into `work/release-readiness` at `a834068b62094de8247f9b49072090df6d9a8ffb`. PR #6 remains an open draft targeting `main`, unmerged.

### Section-entry and return synchronization
- `TanweerViewModel` now routes tab/subscreen entry through `syncTabOnEntry`, `syncSubScreenOnEntry`, and `syncCurrentDestinationOnEntry`.
- Entry triggers forced section-specific refreshes even when optional background auto-sync is disabled. The destination is also refreshed after returning from a nested screen; closing PDF returns to and refreshes Library.
- TODAY refreshes home, schedule, homework, exams and events; Calendar refreshes calendar/events; remaining tabs refresh their own datasets.
- Schedule, groups, library, timeline/knowledge-base, archive, search, catch-up, events, community decisions, role dashboards, profile/settings and version screen entry all request their relevant server snapshots.
- Startup full account reconciliation already existed from Batch 1 and remains the baseline; Batch 3 adds fresh section-entry snapshots rather than replacing it.

### Safe server snapshot reconciliation
- Added `ServerSnapshotValidation` and unit tests for declared-count equality and unique/nonblank record IDs.
- Lesson-list, account archive and day-detail refresh paths reject incomplete, duplicated, malformed or mismatched server snapshots before Room deletion/reconciliation. Invalid day details must match the requested date and group; valid shared events may use the global group ID. On invalid responses, cached academic rows are retained.
- This blocks a truncated/partial server response from being misread as an authoritative empty snapshot and clearing existing local records.

### Groups, clubs and privacy
- Root cause of private groups appearing public in Android: Room `GroupEntity` -> `GroupItem` mapping omitted `visibility` and `chatPolicy`, so model defaults silently changed PRIVATE/STAFF_ONLY to PUBLIC/MEMBERS. Added `GroupItemMapper.toGroupItem()` and use it for all, My Groups and Discovery flows; tests preserve both fields.
- Server mapper now fails closed: missing/unknown group visibility is PRIVATE, not PUBLIC.
- Group cards no longer show a single-group activation action/state. Groups remain independently accessible; each joined shared-group card provides its own discussion entry.
- Private or approval-required join flow now retains the server privacy marker so Android shows the join-reason dialog and sends the reason. Server already requires a nonblank reason for PENDING requests and records it in `group_members.request_reason`; API test coverage remains in the groups hardening suite.
- Fixed group creation SQL placeholder count so visibility/chat policy bindings align with SQL placeholders. Group row + creator's active moderator membership remain one atomic D1 batch.
- After successful create, Android switches to My Groups and clears Discovery search so the new group is visible.
- Join operation now treats success as confirmed only when both HTTP and JSON `success=true`.

### Lesson publication and retry repair
- Server previously inserted `contents` first, then `content_media` rows sequentially; a mid-list failure could leave a partially published lesson. Retrying the same content ID returned success as soon as the parent row existed, without repairing missing pages.
- Reworked first publication to a single transactional D1 `batch()` for parent content + all media. The route confirms every statement and returns an error instead of claiming partial success.
- Normalize and validate positive, unique page-order values before writing.
- Idempotent retry now verifies ownership/group scope, compares stored media metadata, rejects conflicting or duplicated page rows, and adds only missing page rows transactionally. A previously partial lesson can complete on retry without duplicating existing media.
- Duplicate-checksum logic excludes the same content ID so a retry of that exact lesson reaches the repair path rather than being mistaken for a duplicate lesson.
- Added regressions for a second media-row failure with full rollback and successful retry, and for repairing a legacy partially written lesson while keeping existing page rows single.

### Chat publication confirmation and idempotency
- Android no longer considers `WebSocket.send()` queue acceptance to be successful publication. `sendChatMessage` waits for server ACK or its own persisted-message echo; after an 8-second timeout it uses HTTP with the same message ID. It sets SENT only after confirmed ACK or HTTP `success=true`; otherwise FAILED.
- Retry follows the same confirmation contract. A rapid duplicate debounce no longer reports success for a message that was not sent.
- Both REST and GroupChatDO now inspect D1 `INSERT OR IGNORE` changes. If the row already exists, they verify exact group, sender and text identity before treating it as an idempotent retry. Conflicting ID reuse returns 409/explicit WebSocket error and does not broadcast or ACK.
- Exact persisted retry can be ACKed but is not rebroadcast as a second message.
- Added server regression tests for exact retry and conflicting message IDs. Updated the integration-test MockD1 parser to model `INSERT OR IGNORE`; it previously recognized only `INSERT INTO`, creating a false 409 in the end-to-end test.

### Archive and timeline behavior checked
- The existing lesson/homework detail preview dialogs in Subject Timeline and Academic History already open on card tap; do not add a second overlapping preview flow.
- Academic-year selector derives available years from actual stored lesson/homework/exam dates plus the current academic year. `AcademicYearManager.resolveAvailableAcademicYears` no longer invents an empty prior year. Regression test confirms `2025-2026` is absent when there are no records in that year and `2026-2027` is the current year.
- Continue preserving real date-derived history; don't hardcode year rollover based on a device clock alone.

### Validation checkpoint (latest)
- Current branch HEAD: `9b524c3a965f6aa362904d783488768bf1b8dff2`.
- Server workflow run `42299705972` passed: TypeScript, dependency-security baseline, all 34 test files / 236 tests, and Wrangler production-bundle validation. No deployment was performed.
- Android workflow run `42299378382` passed on commit `02bb4e44698a610350e9714150e3639048be7df5`: Android unit tests, Staging Debug APK assembly and artifact upload succeeded. The only commit between that Android-validated source and current HEAD `9b524c3` is `server/test/integration.test.ts` (the MockD1 `INSERT OR IGNORE` matcher correction); no Android source changed after the successful build. Signed Release assembly was skipped because release-signing material was not available in this CI run.
- A further Android run `42299713915` on current HEAD was still in progress at this checkpoint; the preceding successful Android run covers the unchanged Android source tree.
- Read-only Cloudflare D1 inspection on 2026-11-07 confirmed Staging's latest migration is `0030_activate_existing_public_school_group_requests.sql`, while Production's latest migration is `0023_group_request_reason.sql`. Both SELECT checks returned `rows_written: 0` and `changed_db: false`.
- Migrations `0031_user_dismissed_school_announcements.sql` and `0032_monthly_exam_uniqueness.sql` remain unapplied in both environments; Production also lacks the later Staging schema changes from migrations 0024–0030. Therefore new school-announcement dismissal, private-group, chat-policy and monthly-exam database behavior is **not live in either environment** until each target database applies its migrations sequentially and the matching Worker is deployed.
- No Staging or Production D1 migration, write, Worker deployment, secret change, branch creation, PR merge into main, or data purge occurred in Batch 3.
- Physical-device / two-account acceptance remains outstanding: publish the same lesson with multiple pages and verify all pages appear on a second account; interrupt/retry publication; verify private group visibility, reason submission and manager request review; send the same chat message from one device and confirm one copy on another; verify ACK/failed-message UI and section-entry refresh while online/offline.

### Batch 3 remaining / next actions
- Wait for the current-head Android workflow `42299713915` to finish; the verified Android source has already passed on `02bb4e4`, but latest HEAD should be tracked until its run concludes.
- Continue auditing homework media upload/retry for the same multi-write partial-publication risk, and check every UI mutation for false success when server body `success` is false.
- Review D1 migration rollout ordering and Production schema drift before any deployment. Do not apply migrations or deploy Workers as part of code-only work without a separate explicit release instruction.
- Do not add another branch. All further changes belong on `work/release-readiness`.


## Batch 3 — synchronization, academic navigation and groups (2026-11-07)

### Branch policy and base
- All Batch 3 work is committed directly to `work/release-readiness`. No new working branch was created.
- The repository branch list at the implementation checkpoint contains exactly `main` and `work/release-readiness`.
- Production `main` remains `64143994070bcdc410cf044379fe2801da8f9a3c`; no commit was pushed to it.
- Batch 2 PR #7 was merged into `work/release-readiness` earlier in this session. Its temporary branch and the four historical CI scratch branches were removed. The temporary cleanup workflow was also deleted after use.

### Server-authoritative synchronization audit
- Reviewed the authenticated startup path in `TanweerViewModel` and `TanweerRepository.syncInitial`: generation verification gates outbox draining; profile and active memberships refresh first; then current-day data, the account-wide cross-group academic archive, every active group's lessons/homework/exams/events/schedule/issues/calendar, and the grade's books are pulled. Startup auxiliary data also refreshes chat history, role requests, group members, proposals, corrections, community decisions and role-specific dashboard data.
- Retained the existing tab and sub-screen entry refresh routing rather than duplicating it. Each destination has a targeted refresh; navigation back refreshes the destination again. Calendar month/day selection already has its own refresh effects.
- Closed one gap in the unified subject workspace: entering `SUBJECT_KNOWLEDGE_BASE` now refreshes group issues/discussions in addition to lessons, homework and exams (`a9fb2129`).
- No Room wipe, table-wide destructive refresh, generation-marker reset, or outbox deletion was added. The snapshot count/ID/group validation and pending-row preservation protections from the preceding commits remain in place.

### Timeline and academic archive
- Re-audited `SubjectTimelineScreen` and `AcademicHistoryScreen`: tapping a lesson, homework or exam already opens the corresponding detail preview; the timeline and archive reuse the same typed preview dialogs. No duplicate navigation or second preview flow was introduced.
- `AcademicYearManager.resolveAvailableAcademicYears` includes the current academic year and years inferred from valid recorded lesson/homework/exam dates only. It no longer fabricates an empty prior year. Its regression test explicitly verifies that `2025-2026` is absent when it has no records and `2026-2027` remains available as the current year.
- Invalid calendar dates are rejected with strict Gregorian validation, so malformed dates cannot create phantom archive years.

### Groups, clubs and join requests
- Fixed the server discovery query in `server/src/services/groups.ts`: user-created PRIVATE optional/shared groups are no longer exposed to strangers as if they were public. The visibility filter still returns a PRIVATE group to its own pending/rejected applicant (to inspect status or retry), and keeps other school CLASS sections discoverable as PRIVATE approval-required sections. Active and banned memberships remain excluded from discovery.
- Added `server/test/groups-visibility.test.ts` to cover public groups, hidden private groups, pending/rejected applicants, active/banned users, other school CLASS sections and non-discoverable records. The test also asserts that the SQL visibility guard is present, not only that the fake fixture returns the expected rows.
- Tightened the shared-group card actions: the leave action is now a small secondary text action instead of a second full-width button. Joined official/shared cards display their active-membership state; no manual group activation switch is part of the current group screen.
- Confirmed the join-reason path is wired end to end: private/class requests open a required 500-character reason field; the API rejects missing/blank/oversized reasons and stores `request_reason`; the members endpoint returns it; the manager dialog displays the actual reason before approve/reject. The “no reason provided” text is only the fallback for a genuinely empty stored value.
- Existing per-group chat entry allows opening one joined class/club chat, returning to the group list, then opening another. Server membership/chat-policy checks remain authoritative.

### Validation checkpoint — source commit `a9fb2129dda4efb2f551d3bd53b804dcc494b7fd`
- Server workflow `42362616683` passed: TypeScript, dependency security baseline, all 36 Vitest files / 242 tests, and Wrangler production-bundle validation.
- Android workflow `42362616605` passed: Android unit tests, Staging Debug APK assembly and Debug artifact upload.
- Debug artifact: `13569774791`; SHA-256 `e78ee37dede23311439ed13b2a5e8d37d06e496988580dbbc54c53f0b9c5fb40`; expiry `2026-11-14`.
- Signed Release assembly was skipped by this workflow; do not describe this artifact as a signed Release APK.
- This validation is code/CI validation only. No Worker deployment, D1 migration apply/write, production change, secret change, data purge or merge into `main` was performed.
- The join-reason, timeline/archive preview and existing startup/entry synchronization paths were audited from the current source; these are not claimed as new implementations in this batch. Real-device and two-account acceptance remains necessary for cross-device group privacy/chat and online/offline refresh behavior.

### Remaining acceptance
- Perform physical-device/two-account checks for private group discovery versus class-section discovery; submit a non-empty join reason and verify the manager sees it; open/switch between class and club chats; refresh each section after a second device publishes an update; tap lesson/homework/exam entries in both timeline and archive.
- Before any Staging or Production rollout, review and apply only the migrations appropriate to that environment in order, then deploy the matching Worker. This batch introduced no new D1 migration.
- Continue keeping all future work on `work/release-readiness`; maintain exactly the two branches `main` and `work/release-readiness`.


## 2026-11-07 — Batch 3 post-implementation source audit (superseding checkpoint)

### Exact repository state
- Current working branch at this audit: `work/release-readiness`, HEAD `d387c7dbb9d7bf0b2209267af7f174d3c65e3103`.
- Production `main` remains `64143994070bcdc410cf044379fe2801da8f9a3c`.
- Branch enumeration returned exactly two branches: `main` and `work/release-readiness`. The Batch 2 branch and four old CI scratch branches are deleted; the one-time cleanup workflow is absent.
- Batch 2 PR #7 is merged into `work/release-readiness` at `a834068b62094de8247f9b49072090df6d9a8ffb`. Batch 1 PR #6 remains an open draft from `work/release-readiness` to `main`; it has not been merged into Production.
- This entry supersedes earlier Batch 3 references to HEAD `9b524c3a965f6aa362904d783488768bf1b8dff2` and checkpoint source `a9fb2129dda4efb2f551d3bd53b804dcc494b7fd`.

### Final code audit — requirements already implemented
- **Startup and section-entry synchronization:** Startup verifies the server data generation before releasing outbox mutations, refreshes the profile and memberships, reconciles the active group and account-wide archive, then refreshes datasets for all confirmed active groups. Auxiliary startup fetches recent chat history, role requests, group members, schedule proposals, corrections, community decisions and role-specific dashboard data. Each root tab and sub-screen has a targeted fresh entry refresh; navigation back refreshes the destination. The unified subject workspace also refreshes discussions. These operations use forced entry refreshes even when optional background auto-sync is disabled.
- **No destructive empty-snapshot assumption:** Server snapshot endpoints return authoritative counts. Android validates optional counts, unique/nonblank IDs, group/date scope and snapshot structure before Room reconciliation. Pending local rows are retained. Incomplete, truncated or malformed responses are rejected without clearing cached academic records.
- **Timeline and archive navigation:** Subject Timeline opens the typed detail dialog for lesson, homework and exam cards. Academic History does the same from the subject archive lists. The dialogs are shared rather than creating duplicate navigation flows.
- **Academic-year selection:** Available years derive from the current academic year plus actual valid dates present in lesson/homework/exam records. Strict Gregorian date validation rejects impossible dates. The empty `2025-2026` year is absent when it has no recorded content; `2026-2027` is selected dynamically as the current year.
- **Private-group discovery and membership metadata:** Group discovery hides private optional/shared groups from strangers, while preserving visibility to the user's own pending/rejected request and keeping other school CLASS sections discoverable as approval-required. The server mapper fails closed for unknown visibility; Room-to-UI mapping preserves both `visibility` and `chatPolicy`.
- **Group-card behavior:** Joined official/shared-group cards show active membership and their own discussion/manage actions. The member-facing Group screen contains no per-group activation button; each joined group can be opened independently. The leave action is a small secondary action rather than a second oversized card button. Search and responsive card layout remain.
- **Join reasons:** Private/class approval requests show a required reason field (maximum 500 characters). The server rejects absent, blank or oversized reasons, stores `request_reason`, and returns the saved reason to the manager review UI. The manager sees the actual reason before approval/rejection. A fallback “no reason” label applies only to a genuinely empty historical value.
- **Lesson/homework publication recovery:** Lesson parent + media rows are committed atomically; same-ID retry checks ownership/group and media identity and can repair missing pages without duplicating existing ones. Homework parent + attachments use the same atomic/repairable pattern; attachment rows are scoped to the validated parent ID and page order. Conflicting same-ID metadata is rejected. Android UI mutations require server `success=true` rather than HTTP success alone.
- **Cross-device chat confirmation:** Android waits for a persisted server ACK or persisted-message echo before showing SENT, then falls back to HTTP with the same idempotency ID after timeout. REST and Durable Object reject conflicting ID reuse and do not rebroadcast exact persisted retries as duplicates.
- **Latest source additions after the earlier audit:** commits `8140204e1949c4e640400a2bcad3220106d9de21`, `31dfcbfb811b036c03b2da1c92c008c11791ac9e`, `7c772e75187bd62b135ea95187216bdbb2527ce0`, `2dd4f1ffdea0bcad02f50cf2726e0f51e8f44660`, `36e20c28b40068fcd4a6b8ed09aaea7c66e539c3`, `b755f007d71be55deebef2ef8ab4b53975accb3f`, `3a00449fbe9e251810672062d00f36ecfd4bda6d` and `a9fb2129dda4efb2f551d3bd53b804dcc494b7fd` add homework atomic publication/retry, server write confirmation, authoritative snapshot counts, private-group visibility and its regression coverage, card cleanup/membership state, and knowledge-base discussion refresh.

### Latest CI on the current source HEAD
- Server workflow run `42362851971` completed successfully on `d387c7dbb9d7bf0b2209267af7f174d3c65e3103`: TypeScript typecheck, dependency security baseline, **36 Vitest files / 242 tests**, and Wrangler Production-bundle dry-run validation all passed. Expected fault-injection tests log simulated D1 failures while asserting rollback/retry behavior; these are not workflow failures.
- Android workflow run `42362851953` completed successfully on the same HEAD: unit-test task and Staging Debug APK assembly succeeded, and Debug APK artifact upload succeeded.
- Debug artifact ID: `13570497342`; artifact archive SHA-256: `3855c3296e11a1f3bd951e1f72e50c0b90e501651171cca0384df283b169659d`; size 30,776,068 bytes; expiry `2026-11-14T10:14:19Z`.
- Signed Release assembly was skipped in this workflow because release-signing material was not available. Do not describe the artifact as a signed Release APK.
- These checks are source/CI validation only. No Worker deployment or D1 migration apply was performed in this post-audit session.

### Remaining acceptance and release boundary
- Code implementation for the listed Batch 3 items is present and CI-green. **Manual acceptance is still outstanding**: use two separate Staging accounts/devices to verify a second device sees a published lesson/homework without stale data, same-ID retry does not duplicate, a private group is hidden to a stranger, a nonblank reason is visible to the manager, switching between joined class/club chats works, chat ACK/FAILED state is correct, and section-entry refresh behaves online/offline.
- The timeline/archive entries open the current typed detail preview dialogs; confirm on-device whether this level of detail meets the expected “open directly” behavior.
- No Staging or Production D1 migration, D1 write, Worker deployment, secret change, Production merge, or account-data purge is included here. Batch 2 migrations `0031` and `0032` remain subject to environment-specific sequential rollout review before using the corresponding features on a deployed environment.
- Continue all future edits on `work/release-readiness`; never create another batch-specific branch. Keep only `main` and `work/release-readiness`.




## 2026-11-12 — Current continuation checkpoint (supersedes earlier environment and HEAD notes)

### Exact Git state re-verified on 2026-11-12
- Repository: `majd7772233-a11y/Tanweer`.
- Working branch: `work/release-readiness`.
- Latest app/backend logic commit before handoff documentation changes: `a9fb2129dda4efb2f551d3bd53b804dcc494b7fd` (`fix(sync): refresh subject discussions when opening the knowledge base`, 2026-11-07 10:07 UTC).
- Latest pre-handoff branch commit before documentation-only changes: `74a20067ab9edff05a9c82e0dd1958a063d73b6f` (`ci(staging): remove one-time deploy trigger file`, 2026-11-07 20:37 UTC). This is an infrastructure-trigger cleanup only and does not change app/server source.
- Production `main`: `64143994070bcdc410cf044379fe2801da8f9a3c`. No Production commit was made.
- Branch enumeration returned exactly two branches: `main` and `work/release-readiness`. No batch/feature/CI scratch branches remain. Do not create another branch.
- Batch 2 PR #7 is merged into `work/release-readiness` at `a834068b62094de8247f9b49072090df6d9a8ffb`; PR #6 remains Open + Draft to `main`, unmerged.

### Staging deployment correction — supersedes older “0031/0032 unapplied” statements
- A successful manual Staging deployment occurred in workflow run `42411869740`, using source commit `dca3e13ca21b8ee8ced6e0c89c8903a33c0d5c20`.
- The run explicitly applied Staging D1 migrations `0031_user_dismissed_school_announcements.sql` and `0032_monthly_exam_uniqueness.sql`; both are marked successful in the deployment log.
- The same run configured Staging app secrets from the corresponding GitHub Actions secrets, deployed Worker `tanweer-staging` to `https://tanweer-staging.magd.workers.dev`, and passed health plus Owner Dashboard route smoke tests.
- This was Staging-only. No Production D1 migration or Production Worker deployment occurred. Do not confuse the Staging secret `TANWEER_OWNER_SECRET_STAGING` with the Production Owner secret.
- `.github/workflows/deploy-staging.yml` is manual-only (`workflow_dispatch`) and restricted to `work/release-readiness`. The one-time trigger file has been removed. A future Staging deploy must be explicitly requested by the user.
- Production migration state was last read-only checked on 2026-11-07 as `0023_group_request_reason.sql`; re-check before any Production rollout. Do not infer Production has Staging's 0031/0032 migrations.

### CI on last code HEAD
- Server workflow `42411918987` completed successfully on code HEAD `74a20067`: typecheck, dependency security baseline, server tests and Wrangler bundle validation passed.
- Android workflow `42411918775` completed successfully on the same HEAD: Android unit tests, Staging Debug APK assembly and artifact upload passed. Signed Release steps were skipped because this was PR validation.
- These are CI/build results; they do not replace the user's new real-device/test report or two-account acceptance.

### Batch implementation state
- Batch 1, Batch 2 and the Batch 3 code described in the 2026-11-07 post-implementation audit are already present on `work/release-readiness` and have passed CI checkpoints. Read the Batch 3 post-implementation audit above before changing those features; avoid reimplementing completed paths.
- New real-world test results are expected from the user in the next conversation. They have not been received or analyzed in this checkpoint. The next task is to inspect those exact results, reproduce/trace each failure against current HEAD, identify root causes, fix them on `work/release-readiness`, and add regressions.

### New durable documentation
- `PROJECT_ARCHITECTURE.md`: Android/backend stack, source map, sync/data-loss invariants, roles/groups semantics, deployment safety and workflow expectations.
- `SESSION_HANDOFF.md`: exact branch/commit/CI/Staging state, implemented batch status, known acceptance gaps and the next action for the new conversation.
- Always re-fetch latest HEAD before editing; the documentation commits that follow this checkpoint will advance the branch without intentionally changing app/server behavior.

### Continuation constraints
- Work only on `work/release-readiness`; keep exactly `main` and `work/release-readiness`.
- Never push to or merge into `main`; PR #6 remains a Draft until the user explicitly asks for a Production release.
- No Production migration, Worker deploy, secret update or data purge without explicit authorization.
- Do not claim physical-device acceptance from CI. Keep user-provided test observations distinct from automated CI evidence.

## Phase 1 — critical data, registration and chat guardrails (2026-11-14)

- The `/api/v1/system/version` route requires a nonblank, successfully-read D1 `data_generation` before returning success. D1 read failure or a missing marker returns 503; never synthesize a generation value on this path. GitHub release metadata may fall back only after the real D1 marker is verified.
- Public registration is creation-only. Existing phone numbers return `PHONE_EXISTS` (unless the existing stored class membership is banned, which returns `GROUP_BANNED`). A missing session is not evidence of an incomplete registration and must never authorize profile/class mutation or membership reactivation. Legacy partial accounts need an explicit, authorized support-repair design rather than public registration repair.
- Group chat validates message text as a non-empty string of at most 2,000 Unicode code points. One per-user/per-group Durable Object quota is shared by REST and WebSocket: up to 8 messages per 10 seconds and 30 per minute. Quota storage failure must reject the send, not bypass the guard. REST bodies and WS frames are limited to 16 KiB.
- Phase 1 leaves the PDF reader untouched. Preserve `PdfReaderScreen.kt`, `CanvasPageCurl.kt`, gesture routing, zoom/pan, page-turn animation, and page-curl geometry unless a later task explicitly targets PDF behavior.
- Implementation and regression tests are recorded in the deep audit report and current session handoff. Verify the live branch HEAD and CI before claiming completion; do not deploy or merge to Production without explicit release authorization.


## 2026-11-14 — Phase 1 WebSocket action quota
- Consume the same Durable Object per-user/per-group quota for every supported WebSocket chat action, including delete-message, before performing D1 authorization reads.
- Ignore unsupported control frames before D1 work. Keep per-action live membership and chat-policy verification for supported actions.
- Validate WebSocket delete IDs as nonblank strings up to 128 characters.
- Test concurrent quota calls and both storage get/put failure paths; fail closed on either failure.
