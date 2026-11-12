# Tanweer — Session Handoff

Checkpoint prepared: 2026-11-12. All Git facts below were re-read from GitHub before writing this file.

## Exact repository state

- Repository: `majd7772233-a11y/Tanweer`.
- Current working branch: `work/release-readiness`.
- Current working HEAD: `74a20067ab9edff05a9c82e0dd1958a063d73b6f`.
- Latest commit: `ci(staging): remove one-time deploy trigger file` (2026-11-07 20:37 UTC). This removes the one-time staging dispatch trigger file; it does not change application source.
- Production branch `main`: `64143994070bcdc410cf044379fe2801da8f9a3c`. It has not been modified by the work described here.
- Branch enumeration returned exactly two branches: `main` and `work/release-readiness`.
- Do not create any other branch. All further changes go directly to `work/release-readiness`.
- Batch 2 PR #7 is merged into `work/release-readiness` at merge commit `a834068b62094de8247f9b49072090df6d9a8ffb`; its branch was deleted.
- PR #6 remains Open + Draft from `work/release-readiness` to `main`. It is not merged. Do not merge it or update Production.

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

1. Read latest `work/release-readiness` HEAD from GitHub again. Do not assume it is still `74a20067`.
2. Read `GPT.md`, `PROJECT_ARCHITECTURE.md` and this file.
3. Receive and inspect the user's new test report/logs/screenshots. Do not ask them to repeat anything already provided in that new conversation.
4. Map each observation to the exact affected feature, reproduce or trace its path in the current code, identify the root cause, then implement the smallest complete fix on `work/release-readiness`.
5. Add regression tests, run targeted and full CI checks, update `GPT.md` and this handoff, and verify there are still exactly two branches.
6. No new branch, no Production merge, no Production D1 migration, no Production Worker deployment, and no secret changes unless the user explicitly authorizes them.

## Links

- Repository: https://github.com/majd7772233-a11y/Tanweer
- Working branch: https://github.com/majd7772233-a11y/Tanweer/tree/work/release-readiness
- Instructions: https://github.com/majd7772233-a11y/Tanweer/blob/work/release-readiness/GPT.md
- Architecture: https://github.com/majd7772233-a11y/Tanweer/blob/work/release-readiness/PROJECT_ARCHITECTURE.md
- This handoff: https://github.com/majd7772233-a11y/Tanweer/blob/work/release-readiness/SESSION_HANDOFF.md
- Staging deployment run: https://github.com/majd7772233-a11y/Tanweer/actions/runs/42411869740
- Latest server CI: https://github.com/majd7772233-a11y/Tanweer/actions/runs/42411918987
- Latest Android CI: https://github.com/majd7772233-a11y/Tanweer/actions/runs/42411918775
