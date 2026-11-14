# Tanweer — Session Handoff

Checkpoint prepared: 2026-11-14. Repository state and the system-purge reset implementation were re-read from GitHub before writing this file.

## Exact repository state

- Repository: `majd7772233-a11y/Tanweer`.
- Current working branch: `work/release-readiness`.
- Latest source-code commit for the system-purge reset: 6108a4c962814f6cdf288f07fca05af870810bed (fix(android): scope full storage wipe to system purge). Its parent d987344335bb6aff61c3c3c2f78b45dad744b82d added the reset flow, account preference policy, queue synchronization, notification cancellation, and Android regression tests.
- Documentation is being refreshed in a follow-up docs-only commit. Always fetch the live branch SHA before editing.
- Documentation-only commits added for this handoff: architecture `76e7c2ed1868acc74037c8c23bfa13bdd4557506`, initial handoff `c21a47f6fed233f5664b6adc01a14a982d8148dd`, and GPT checkpoint `a2068fd0f050af7d0e64aeb1feb8d787ad2199d5`. They do not intentionally change app/server source. Always fetch the branch's actual latest SHA before editing; this handoff refresh itself will also create a documentation-only commit.
- Production branch `main`: `64143994070bcdc410cf044379fe2801da8f9a3c`. It has not been modified by the work described here.
- Branch enumeration returned exactly two branches: `main` and `work/release-readiness`.
- Do not create any other branch. All further changes go directly to `work/release-readiness`.
- Batch 2 PR #7 is merged into `work/release-readiness` at merge commit `a834068b62094de8247f9b49072090df6d9a8ffb`; its branch was deleted.
- PR #6 remains Open + Draft from `work/release-readiness` to `main`. It is not merged. Do not merge it or update Production.

## System-wide purge reset fix — 2026-11-14

- Root cause: Android previously stored the new data_generation and returned false on every path, so a successful Owner Dashboard purge left Room data, token and local files intact.
- The new client path treats only a changed, nonblank generation marker for the same API origin/account as the explicit purge signal. First/legacy marker initialization is non-destructive; same value, blank value, network failure, and environment changes do not wipe.
- On a mismatch, Android closes the outbox gate, stops/awaits queue work, avoids remote logout because server sessions have already been deleted, clears Room/outbox, account preferences, notebook data, alarms/notifications, all app-private files/caches/downloaded PDFs, and in-memory account UI, then returns to AuthScreen. It advances the local marker after cleanup.
- Global app settings and stable device identity survive. Normal user logout retains shared downloaded PDFs; full private-storage wipe is restricted to system purge.
- Foreground generation check is called from MainActivity.onResume; startup has its own pre-sync verification.
- Main implementation: 6108a4c962814f6cdf288f07fca05af870810bed. CI passed on that source commit: Server workflow 43521664658 completed successfully; Android workflow 43521664707 passed Android unit tests, assembled the Staging Debug APK and uploaded artifact Tanweer-debug-a6c1fc407dcc890cac92d6d10b2a4f4749a8de98. PR validation skipped signed Release assembly/signing. Artifact run: https://github.com/majd7772233-a11y/Tanweer/actions/runs/43521664707.
- No Worker deployment, D1 mutation, Production write, secret change, merge to main, or physical-device test was performed in this phase.

## Last CI and deployment evidence

### Latest CI for current HEAD
- Server workflow `42411918987` on `74a20067`: TypeScript, dependency security baseline, server unit/integration suite and Wrangler bundle validation all passed.
- Android workflow `42411918775` on `74a20067`: Android unit tests and Staging Debug APK build/upload passed. Signed Release steps were skipped because this was a PR validation run.
- This proves CI/build for that source checkpoint, not manual testing on physical devices.

### Staging deployment — important update to older GPT.md notes
- Staging deploy workflow `42411869740` completed successfully from source commit `dca3e13ca21b8ee8ced6e0c89c8903a33c0d5c20`.
- Its D1 migration step applied both:
  - `0031_user_dismissed_school_announcements.sql`
  - `0032_monthly_exam_uniqueness.sql`
- It configured Staging secrets from GitHub Actions secrets, deployed Worker `tanweer-staging` to `https://tanweer-staging.magd.workers.dev`, and passed the health check and Owner Dashboard route smoke test.
- This deployment and migration apply were Staging-only. Production was not deployed and Production D1 was not migrated.
- The workflow file `.github/workflows/deploy-staging.yml` is currently manual-only (`workflow_dispatch`) and guarded to the `work/release-readiness` branch. The temporary one-time trigger file has been removed.

## What is already implemented in the branch

### Batch 1
- Data-loss-safe generation reconciliation, verified server writes, safe group membership snapshot replacement.
- Compact school admin dashboard with server-backed counters, student lookup and limited account/class membership actions.
- Per-account dismissal of official school announcements with a small X.

### Batch 2
- PDF reader gesture ownership: one-finger navigation/pan vs two-finger zoom; page curl aspect ratio preserved.
- PDFBox initialized before text extraction, compact study-tools hub, focus ruler movement/close, offline-generated focus ambience, TTS status/error checks.
- App typography normalized to Settings 12/14/16sp, with modest title emphasis.
- Monthly exam calendar, unique monthly exam per group/subject/month (including legacy QUIZ checks), separate flexible oral tests.
- Direct teacher/admin schedule editing and immediate fresh schedule sync after confirmed mutations.

### Batch 3 code already present on the branch (do not reimplement blindly)
- Startup full reconciliation plus per-tab and per-sub-screen entry refreshes, including destination refresh on return.
- Server snapshot validation for counts/IDs/date/group scope before Room reconciliation; invalid/incomplete snapshots retain cached records.
- Subject Timeline and Academic History lesson/homework/exam cards open their typed detail previews.
- Academic-year options derive from valid stored record dates and the current academic year; an empty `2025-2026` is not fabricated.
- Group visibility mapping is preserved from server to Room/UI and fails closed; private optional groups are hidden from strangers.
- Joined group cards are independently accessible; no one-group activation switch.
- Private/class join requests ask for a required reason; server stores and returns `request_reason` to the authorized manager.
- Lesson and homework publication use atomic parent+media writes and repairable same-ID retries.
- Chat SENT is based on server persistence ACK/echo or confirmed HTTP retry, not local WebSocket enqueue. Conflicting message ID reuse is rejected without duplicate broadcast.
- Knowledge-base entry refreshes discussions too.

These were source-audited and CI-green as of the 2026-11-07 checkpoint. The user's newest real-world test results have not yet been received in this chat; those results are the next input and take priority over this older acceptance record.

## Known acceptance gaps / cautions

- Physical-device/two-account validation was still outstanding in the last written audit. The user now has a newer test report; do not assume the old result remains current.
- Validate cross-device publication, partial upload retry, cached data retention, private group discovery, join-reason visibility, switching between multiple group chats, ACK/FAILED chat UI, and section-entry refresh both online and offline.
- Verify timeline/archive tap behavior on device: code currently opens typed detail previews. Confirm whether this meets the user's expectation of opening the lesson/homework directly before changing navigation.
- Scanned/image-only PDFs still have no OCR. Digital text layer extraction was fixed; do not claim OCR support.
- Staging is deployed with migrations 0031/0032. Production migration state was last read-only checked on 2026-11-07 as 0023; re-check before any Production release.
- No signed Release APK was produced by latest PR CI; Debug Staging APK only.

## Exact next action in the new conversation

1. Download/install the Staging Debug APK from workflow 43521664707 on a test device.
2. Perform physical acceptance: purge while the app is closed then launch; purge while backgrounded then foreground; verify academic cache, outbox, PDFs/notebook files, alarms and auth are gone; verify first-install/missing-marker and failed version requests do not wipe.
3. Confirm app-wide settings and device identity remain; confirm normal logout still retains shared downloaded PDFs.
4. Re-check the branch list (only main and work/release-readiness) and final diff. No Staging deployment, Production merge/deployment, D1 write, or secret change unless explicitly authorized.
5. Keep GPT.md, PROJECT_ARCHITECTURE.md, and this handoff aligned with final test evidence.
## Links

- Repository: https://github.com/majd7772233-a11y/Tanweer
- Working branch: https://github.com/majd7772233-a11y/Tanweer/tree/work/release-readiness
- Instructions: https://github.com/majd7772233-a11y/Tanweer/blob/work/release-readiness/GPT.md
- Architecture: https://github.com/majd7772233-a11y/Tanweer/blob/work/release-readiness/PROJECT_ARCHITECTURE.md
- This handoff: https://github.com/majd7772233-a11y/Tanweer/blob/work/release-readiness/SESSION_HANDOFF.md
- Staging deployment run: https://github.com/majd7772233-a11y/Tanweer/actions/runs/42411869740
- Latest server CI: https://github.com/majd7772233-a11y/Tanweer/actions/runs/42411918987
- Latest Android CI: https://github.com/majd7772233-a11y/Tanweer/actions/runs/42411918775

## Phase 1 implementation — 2026-11-14

### Scope implemented on `work/release-readiness`
- TNR-001: system-version endpoint now requires a valid D1 generation marker before success. D1 read error or absent/blank marker returns 503 and cannot produce a destructive client signal. GitHub release fallback carries the same verified marker.
- TNR-002: public registration no longer attempts to repair any existing account. It cannot update an existing account's academic placement or re-enable a group membership. Existing accounts use login/recovery; legacy partial records require an authorized repair path.
- TNR-003: REST and WebSocket chat messages share a Durable Object quota per user/group (8 per 10 seconds; 30 per minute) and the same 2,000-Unicode-character text validation. REST request bodies and WS frames are limited to 16 KiB; quota-store failure blocks persistence.
- Added `server/test/system-version-generation.test.ts` and `server/test/chat-rate-limit.test.ts`; updated `server/test/auth-hardening.test.ts`, `server/test/group-chat-policy.test.ts` and `server/test/integration.test.ts`.
- The PDF reader was not changed. Page-turn animation, page-curl, zoom, pan, gestures and PDF rendering files were left untouched.

### Verification still required
- Server CI must pass typecheck, full tests, dependency audit and Wrangler production/staging dry runs.
- Verify the updated branch diff contains no PDF reader or animation paths.
- After CI, test on Staging: D1 generation unavailable/missing returns 503 without local purge; duplicate-account registration cannot move or activate a class membership; REST and WebSocket share rate limits and reject overlong messages.
- No Worker deploy, D1 write/migration, Production change, or merge to `main` is included in this implementation.


## Phase 1 chat action quota follow-up — 2026-11-14
- Follow-up checkpoint parent: `3b1f46af53a2be06b30001cd7cb55f839bdc4ecb` (WebSocket handshake IP rate-limit fix; server CI passed at that checkpoint).
- The next patch applies the same persisted Durable Object per-user/per-group quota to supported WS send and delete actions before D1 permission reads, returns early for unsupported control frames, and validates delete IDs.
- Regression coverage adds concurrent quota consumption and fail-closed Durable Object storage read/write tests.
- PDF reader code and animation paths remain untouched. Staging/live two-account WS+REST acceptance is still required; no deployment or Production operation is authorized.
