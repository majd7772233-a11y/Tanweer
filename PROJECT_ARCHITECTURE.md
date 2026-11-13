# Tanweer — Project Architecture and Engineering Notes

Updated 2026-11-14. Latest verified system-purge reset code checkpoint: 6108a4c962814f6cdf288f07fca05af870810bed on work/release-readiness.

## 1. Repository and branch contract

- Repository: `majd7772233-a11y/Tanweer`.
- The only permitted long-lived branches are `main` and `work/release-readiness`.
- All implementation, tests and documentation for ongoing work go directly to `work/release-readiness`. Do not create per-batch, feature, hotfix, or CI scratch branches.
- `main` is the Production line. At the verification checkpoint it is `64143994070bcdc410cf044379fe2801da8f9a3c`. Do not push to, merge into, or deploy Production unless the user explicitly requests a release.
- `work/release-readiness` is the integration and Staging line. Its verified HEAD is `74a20067ab9edff05a9c82e0dd1958a063d73b6f`.
- PR #7 (Batch 2) is merged into `work/release-readiness` at `a834068b62094de8247f9b49072090df6d9a8ffb`. PR #6 remains an open Draft from `work/release-readiness` to `main`; it is not merged.
- Before every new task, read the latest commit, `GPT.md`, this file, and the latest relevant test output. These notes are a map, not a substitute for checking HEAD.

## 2. Android client

### Toolchain and app identity
- Module: `app`.
- Language: Kotlin; UI is Jetpack Compose / Material 3.
- Android namespace/application ID: `com.magd.tanweer`; staging adds `.staging`.
- Gradle build uses JDK 17 in CI, Java source/target compatibility 11, Room KSP, and the version catalog at `gradle/libs.versions.toml`.
- `compileSdk = 36.1`, `minSdk = 23`, `targetSdk = 36`, version name `1.2.0`, version code `120`.
- Flavors: `production` → `https://tanweer.magd.workers.dev/`; `staging` → `https://tanweer-staging.magd.workers.dev/`.
- Important dependencies: Room, Retrofit, OkHttp, Moshi, Kotlin Coroutines, Coil Compose, PDFBox-Android.

### Source map
- `app/src/main/java/com/magd/tanweer/ui/TanweerApp.kt`: root navigation, tabs and sub-screens.
- `app/src/main/java/com/magd/tanweer/ui/TanweerViewModel.kt`: navigation state, startup reconciliation, section-entry refreshes and UI actions.
- `app/src/main/java/com/magd/tanweer/data/repository/TanweerRepository.kt`: network/data coordination, remote-to-local reconciliation, mutations and media operations.
- `app/src/main/java/com/magd/tanweer/data/remote/TanweerApiService.kt` and `NetworkModule.kt`: Retrofit API and authenticated network setup.
- `app/src/main/java/com/magd/tanweer/data/local/TanweerDatabase.kt`: Room database. Current Room schema version is 16.
- Room entities/DAOs live under `data/local`; models and response contracts are under `data/model` and `data/remote`.
- `app/src/main/java/com/magd/tanweer/data/sync/UnifiedMutationQueue.kt`: offline mutation outbox, retry/backoff and chat reconciliation.
- Main feature screens live under `ui/screens`; reusable Compose components under `ui/components`; shared theme/typography under `ui/theme`.
- Academic-year and date rules live under `util/AcademicYearManager.kt` and related utilities.
- PDF reader: `ui/screens/pdf/PdfReaderScreen.kt`, `CanvasPageCurl.kt`, with PDF extraction in `util/PdfTextExtractor.kt` and focus ambience in `util/FocusNoisePlayer.kt`.

### Data ownership and sync invariants
- The server is authoritative for shared school records. Room is the local cache/UI source and must remain useful offline.
- Never treat a temporary empty response, partial/malformed response, or network failure as permission to wipe Room. Ordinary server snapshot reconciliation is non-destructive.
- The Owner Dashboard's explicit global purge rotates data_generation. A changed, previously recorded marker for the same normalized API origin and account ID is the only automatic full-wipe signal. A missing marker initializes a baseline; same/blank values, failed checks, or environment changes do not wipe.
- On a confirmed global-purge mismatch, close and drain the outbox gate, wait for in-flight queue work, skip remote logout (the server already removed sessions), clear Room including outbox, account-scoped preferences, notebook data, reminders, app-private files/caches/downloaded PDFs, auth token and in-memory account UI, then return to sign-in. Commit the new marker only after cleanup succeeds.
- App-wide preferences and the stable device-installation identifier survive a global purge. Ordinary user logout clears account Room/session/notebook state but preserves shared downloaded PDF books; only the explicit system purge wipes all app-private storage.
- Verify the marker at startup and on Activity foreground resume. The app does not claim instantaneous push-based logout while suspended.
- Server snapshots must be validated before reconciliation: check declared counts when present, unique/nonblank IDs, requested group/date scope and required shape. Retain cached rows on invalid/incomplete snapshots.
- A local write is not confirmed by HTTP 2xx alone. Check the response body's `success == true` and any required returned entity/ID before marking data SENT/SYNCED or showing success.
- For multi-row lesson/homework publications, use the server's atomic parent-plus-media batch and same-ID repairable idempotent retry. Do not re-create duplicate pages on retry.
- The offline outbox has atomic item reservation, exponential backoff and startup recovery for stuck SYNCING rows. Do not bypass or delete pending mutations casually.
- Chat SENT requires a persisted server ACK/echo or confirmed HTTP success using the same idempotency ID; WebSocket local send acceptance is not delivery confirmation.
- Startup and destination-entry refreshes already exist. Inspect `TanweerViewModel`'s `syncInitialData`, `syncAllComprehensive`, `syncTabOnEntry`, `syncSubScreenOnEntry` and `syncCurrentDestinationOnEntry` before adding overlapping calls.

### Global purge reset implementation (2026-11-14)

- Android implementation lives in TanweerRepository.checkAndApplyDataGeneration, TanweerRepository.logout, TanweerViewModel.verifyServerDataGeneration/onAppResumed, and UnifiedMutationQueue.stopAndAwait.
- TanweerNotificationManager.cancelAccountReminders cancels account-sourced alarms before Room is erased; DAO ID queries supply the request-code identities.
- LocalAccountDataPolicy is the allowlist boundary for deleting account preferences while preserving app settings. Do not broaden it to include device identity or UI preferences without a product decision.
- StartupPersistencePolicyTest covers marker baselines/mismatches and account-preference cleanup scope.

## 3. Cloudflare backend

### Stack
- Worker entry point: `server/src/index.ts`; route dispatch is in `server/src/router.ts`.
- Runtime and APIs: TypeScript on Cloudflare Workers.
- Database: D1 / SQLite through the `DB` binding.
- Realtime group chat: Durable Object class `GroupChatDO` under `server/src/realtime/GroupChatDO.ts`.
- Business logic is organized under `server/src/services`; shared auth, permission, response and ID helpers under `server/src/lib` and `server/src/middleware`.
- D1 migrations: `server/src/db/migrations/*.sql`.
- Backend tests: `server/test/*.test.ts`, using Vitest. `server/package.json` provides `npm test`, `npm run types`, and Wrangler commands.
- Worker/D1 bindings and environments: `server/wrangler.jsonc`.

### Security and data invariants
- Server-side permission checks are authoritative. Android visibility, role labels or hidden buttons are not authorization.
- The system owner (infrastructure-level authority) is distinct from school ADMIN and from group moderators/teachers. Preserve that boundary; do not expose the Owner Dashboard secret or replace it with ordinary role checks.
- Check group membership and `chatPolicy` on the server, including existing WebSocket sessions/messages.
- Group visibility must fail closed: unknown/missing visibility must not silently become PUBLIC. Preserve `visibility` and `chatPolicy` through D1 → API mapper → Room entity → UI model.
- Join requests for private/approval-required groups require a nonblank reason (max 500 characters); persist `request_reason` and return it to authorized managers. Do not fabricate a reason.
- Idempotency must verify identity/scope/content, not merely accept a duplicate ID. Exact retries can be treated as already persisted; conflicting ID reuse must return a conflict and must not rebroadcast.
- D1 multi-statement invariants should use `DB.batch()` where atomicity is required, and check each statement's `meta.changes`. Never report success after partial publication.

## 4. Main academic/domain concepts

- **Official class section:** a school's main class group, typically `class_<grade>_<section>`. It is distinct from optional/shared groups and clubs.
- **Optional/shared group or club:** group membership is per-group. A user can enter one joined group, return to the list, then enter another; there is no single global “activate one group” switch.
- **Visibility:** PUBLIC groups are discoverable; PRIVATE optional groups are not exposed to strangers. A user's own pending/rejected request may still see the group to inspect/retry. Other school CLASS sections may remain discoverable as approval-required.
- **Membership statuses:** ACTIVE, PENDING, REJECTED and BANNED are materially different. Do not conflate them with the global user role.
- **Group roles:** STUDENT/MEMBER, MODERATOR, TEACHER and related normalized variants are scoped to the group; ADMIN/SYSTEM_OWNER are governed separately.
- **Academic year:** derive from the configured school-year boundary and valid stored record dates. Do not hardcode a rollover or manufacture empty years based solely on the device clock. `AcademicYearManager` uses date-derived records plus the current academic period.
- **Timeline/archive detail:** Subject Timeline and Academic History already use typed detail previews for lesson/homework/exam items. Verify actual on-device interaction before adding a second overlapping navigation flow.
- **Exams:** monthly exams are unique per group + subject + calendar month; oral exams are flexible and not constrained by the monthly calendar; legacy QUIZ rows must be considered in uniqueness checks.
- **Schedule:** teacher/admin direct edits are immediate; student proposal flow remains separate. Successful writes should trigger fresh schedule reconciliation.

## 5. Branches, environments and deployment

- Android CI builds Staging for non-main branches and Production for `main`. PR builds create a Staging Debug APK; signed Release is not produced by normal PR validation.
- The isolated Staging deploy workflow is `.github/workflows/deploy-staging.yml`, manually dispatched only (`workflow_dispatch`) and guarded to `work/release-readiness`.
- It runs typecheck/tests, validates the Staging Worker bundle, applies Staging D1 migrations only, configures Staging application secrets, deploys the Staging Worker, then checks health and Owner Dashboard routes.
- Do not run `npm run db:migrate:prod`, apply Production D1 migrations, deploy Production, change secrets, or merge to `main` without the user's explicit release instruction.
- Apply D1 migrations sequentially to the intended environment. Never infer Production schema from Staging or vice versa.

### Last verified environment checkpoint
- Successful Staging deployment: workflow run `42411869740`, deployed source commit `dca3e13ca21b8ee8ced6e0c89c8903a33c0d5c20`.
- That run applied Staging migrations `0031_user_dismissed_school_announcements.sql` and `0032_monthly_exam_uniqueness.sql`, configured Staging secrets from GitHub Actions secrets, deployed `tanweer-staging`, and passed `/api/v1/health` plus Owner Dashboard route smoke tests.
- The run was explicitly Staging-only. It did not apply migrations or deploy to Production.
- Latest production migration state had last been read-only checked on 2026-11-07 as `0023_group_request_reason.sql`. Re-check Production read-only before any future release; do not assume Staging migration state matches Production.

## 6. Workflow expectations for code changes

1. Read latest commit and the exact relevant code before editing. Do not rely on chat memory or an earlier summary when repository state is available.
2. Read/update `GPT.md` for every substantial phase and keep this architecture document aligned when architecture or deployment facts change.
3. Reproduce the reported failure from the new test/log evidence. Identify the first causal failure, distinguish it from downstream symptoms, then fix the root cause.
4. Add regression coverage for the failure and adjacent edge cases. Do not weaken/remove existing tests to make CI green.
5. Run targeted tests, then full server typecheck/tests/Wrangler validation and Android unit tests/build as applicable.
6. Inspect final diff, current branch list and exact HEAD. Ensure only `main` and `work/release-readiness` exist.
7. Report PASS/FAIL/NOT RUN truthfully. CI success is not real-device acceptance.
8. Do not merge PR #6 into `main` or deploy Production unless explicitly instructed.

## 7. Relevant documentation

- `GPT.md`: project engineering instructions, phase history, current checkpoint and known limitations.
- `PROJECT_ARCHITECTURE.md`: architecture and durable invariants (this file).
- `SESSION_HANDOFF.md`: exact current session continuation state and next action.
- `server/README.md`: backend development and local commands.
