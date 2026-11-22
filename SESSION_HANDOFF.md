# Tanweer — Session Handoff

**آخر تحديث موثوق:** 2026-11-17  
**مرجع الاستئناف الأساسي:** هذا القسم هو الحالة الحالية المعتمدة. الأقسام التاريخية التالية تسجل ما حدث في تواريخها، وقد تحتوي على رؤوس فروع أو نتائج CI قديمة تم تجاوزها بهذا التحديث.

## نقطة التسليم الحالية — 2026-11-17

### حالة المستودع والفرع
- المستودع: `majd7772233-a11y/Tanweer`.
- فرع العمل الوحيد المسموح: `work/release-readiness`.
- **آخر Commit للكود:** `e81ab420d7599758eb3790e484f8fcf3fd00b680` — `fix(owner-dashboard): require server acknowledgement for mutations`.
- يوجد فوقه تحديث توثيقي فقط على الفرع؛ ملفات المصدر لم تتغير بعد Commit الكود المذكور.
- الأب المباشر لآخر Commit للكود: `9ad066d585c21dc9daca0fa91ec0cd20cec75515`.
- فرع `main` لم يتغير، وآخر SHA موثق له: `64143994070bcdc410cf044379fe2801da8f9a3c`.
- فحص الفروع أعاد فرعين فقط: `main` و`work/release-readiness`. لا تنشئ فروعاً مؤقتة أو إضافية.
- PR #6 ما زال **Open + Draft** من `work/release-readiness` إلى `main`، وغير مدمج. لا تدمجه.

### آخر تحقق CI على HEAD الحالي
- Android workflow: [Run 44098040628](https://github.com/majd7772233-a11y/Tanweer/actions/runs/44098040628) — **Success** على SHA `e81ab42`.
- Server workflow: [Run 44098040625](https://github.com/majd7772233-a11y/Tanweer/actions/runs/44098040625) — **Success** على SHA `e81ab42`.
- Server workflow إضافي: [Run 44098028792](https://github.com/majd7772233-a11y/Tanweer/actions/runs/44098028792) — **Success** على SHA `e81ab42`.
- نتيجة Android تعني نجاح سير عمل البناء/الاختبارات الآلية في GitHub؛ لا تعني اختباراً على هاتف فعلي. ونتيجة Server تتضمن TypeScript typecheck والاختبارات والتحقق من حزمة Wrangler وفق اسم خطوة الـCI. لا تعتبر أيّاً منها دليلاً على نشر Worker.

### ما أُنجز في امتداد المرحلة الرابعة
- **TNR-005 — SQL Studio:** الإصلاح موثق في commit `515ddc023cb29e99a206be9c0ae7a7021487914a`: توحيد استهلاك عقد `SELECT` / `MUTATION` وربط صفوف SELECT و`meta.changes` بنتائج الواجهة، مع اختبارات استجابة الخادم وDOM.
- **TNR-011 — Search History:** الإصلاح موثق في commit `c543fd8e08dccdee04d972ed2ee780d4769a37a8`: عدم حفظ كل بادئة أثناء الكتابة؛ يسجل البحث عند تأكيده أو اختيار اقتراح/عنصر من السجل.
- **حقيقة حالات المزامنة واللوحات:** commits `83786b82740e5c0bc44425e64c4471f37975fd37` و`637d911a0595c06ba0cb4b4ce83086f205df58dd` وما تلاهما أضافت حالات مزامنة مرتبطة بالمسار/النطاق، وإجراءات إعادة المحاولة، وحماية حالات الفراغ غير المؤكدة.
- **عزل طلبات لوحات الأدوار:** commits `d55aa79a1d4957934c95c602af516d932abd7bfd` و`01c057936289bae4f8f19fd551113a79e47bf804` وما تلاهما تربط النتائج بالحساب/الدور/جيل الطلب، وتمنع استجابات مجموعة قديمة أو حساب سابق من الكتابة فوق الحالة الحالية.
- **TNR-010 — قناة التحديث:** حسب قرار المستخدم المؤكد بتاريخ 2026-11-17، التطبيق لم يُنشر بعد وما زال قيد التطوير، ولن ينشر الآن. لذلك لا يُطلب تنفيذ إصلاح TNR-010 الآن، ولا يُعد عائقاً لإغلاق المرحلة الرابعة في هذه الدورة. لا تغيّر قناة التحديث لمجرد وجود metadata إصدار عام غير منشور؛ أعد تقييمها عند اقتراب المستخدم من أول نشر فقط.
- **لوحة System Owner:** commit `9ad066d585c21dc9daca0fa91ec0cd20cec75515` حسّن التمييز بين الاستجابة الفاشلة والقائمة الفارغة، والتحقق من إحصاءات Overview، والحفاظ على آخر قيم مؤكدة، وإظهار retry. commit `e81ab420d7599758eb3790e484f8fcf3fd00b680` جعل إجراءات تجميد/تفعيل الحسابات، إبطال الجلسات، رفض/اعتماد الطلبات، تعديل الدور وإنشاء/حذف الإجازات لا تعلن النجاح إلا بعد HTTP success وAPI success صريحين. أضيفت اختبارات انحدار لهذه الحواجز.
- **ملفات التوثيق:** كل متابعة برمجية لاحقة يجب أن تحدّث `SESSION_HANDOFF.md` في نفس commit الذي يحتوي تعديل الكود. تقرير التدقيق هو `DEEP_CODE_AUDIT_REPORT_2026-11-14.md`.

### حالة المرحلة الرابعة الآن
- المرحلة الرابعة **ما زالت قيد الاستكمال، ولم نعلن إغلاقها**.
- TNR-005 وTNR-011 لديهما إصلاحات موثقة؛ CI الحالي ناجح على HEAD الذي يحتوي التغييرات.
- TNR-010 مستثنى حالياً من عوائق المرحلة الرابعة بقرار المستخدم، ويعاد النظر فيه قبل أول نشر فقط.
- نقطة الاستئناف ليست إعادة تنفيذ الإصلاحات السابقة: أعد قراءة الكود الفعلي على HEAD، تحقق من عقد SQL Studio واختبارات TNR-005 ومن مسار سجل البحث واختبارات TNR-011، ثم واصل تدقيق بقية مسارات الواجهات ولوحة المالك بحثاً عن حالات Loading / Empty / Error / Offline / Cached / Success غير الصادقة أو إجراءات لا تتحقق من ACK الخادم.
- لا تعتبر وجود اختبارات CI بديلاً عن مراجعة المتبقي من المسارات أو اختبارات المتصفح وقارئ الشاشة. واختبارات Staging والجهاز الفعلي جزء من القبول اللاحق، لا تدّع تنفيذها.

### آخر commits المهمة في هذه الجولة
- `38e8ab9ac5a7049bc5411c9edd9b46e24f15c9bc` — حماية مزامنة ملف المستخدم والتحقق من الحساب/الجلسة والاستجابة.
- `f8b44c9164cc55273075a7a18bf030acc742a500` — عرض إحصاءات الملف الشخصي بعد مزامنة مؤكدة فقط.
- `fb7aeee4c0db716fff0eef1725600e85a1a3779a` — نطاق لقطات أعضاء المجموعة وإحصاءات المشرف.
- `29f0832e0ea360fae06487d6fe738629f303a1d2` — إصلاح Kotlin smart-cast في شاشة المشرف.
- `e155b5c04d141a5db429ddf550246ea2f516dd26` — منع إظهار أصفار افتراضية لإحصاءات المعلم غير المتحققة.
- `9ad066d585c21dc9daca0fa91ec0cd20cec75515` — تحسين صدق بيانات وقوائم وإجراءات لوحة المالك.
- `e81ab420d7599758eb3790e484f8fcf3fd00b680` — اشتراط إقرار الخادم قبل إعلان نجاح إجراءات لوحة المالك.

### حدود العمل والصلاحيات
- لا تلمس `main`، ولا تدمج PR #6، ولا تنشئ فرعاً جديداً.
- لا تنشر Worker إلى Production، ولا تطبق D1 migrations على Production، ولا تغير الأسرار أو المفاتيح أو بيانات Production.
- لم يتم في نقطة التسليم هذه إجراء قبول على جهاز فعلي. لا تذكر ذلك كأنه تم.
- لا تبدأ أعمال TNR-010 ولا تنشئ APK للإصدار العام الآن؛ التطبيق ما زال تطويراً داخلياً وغير منشور.
- المرحلة الخامسة مخصصة لاختبارات القبول الشاملة: أدوار متعددة، حسابان/أجهزة، الانقطاعات، Staging، PDF والملفات والدفتر والإشعارات والأمن والجهاز الفعلي.

### خطوات الاستئناف الدقيقة
1. اقرأ `GPT.md` وأحدث `SESSION_HANDOFF.md` وتقرير التدقيق من الفرع الحالي، ثم تحقق من HEAD والفروع وPR قبل أي تعديل.
2. اقرأ تنفيذ TNR-005 وTNR-011 واختباراتهما على HEAD الحالي، ولا تعِد كتابة ما هو موجود.
3. أكمل تدقيق المرحلة الرابعة من حيث توقفت: بقية مسارات الشاشات، لوحات الأدوار، ولوحة System Owner والإجراءات الحساسة.
4. عند اكتشاف عيب، أصلحه بالكامل وأضف اختبارات وتحديثاً للتوثيق في نفس commit، ثم شغّل CI وتحقق من نتيجته على SHA الجديد.
5. لا تعلن إغلاق المرحلة الرابعة إلا بعد اكتمال نطاق مراجعتها وتوثيق المتبقي بوضوح. أبقِ PR #6 مفتوحاً وDraft، ولا تنشر أو تدمج.

**لا تعتمد على SHA أو حالة CI قديمة واردة في السجل التاريخي أدناه عندما تتعارض مع نقطة التسليم الحالية أعلاه.**

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

## Phase 1 — REST stream and WebSocket frame byte bounds

- Base checkpoint: `1064e404a38a36a82a7d725b586b462b6964a364` (`fix(chat): rate limit websocket delete actions`). It already included the pre-upgrade IP limiter, delete/send shared WS quota, invalid delete-ID rejection, concurrent quota tests and fail-closed storage tests.
- The remaining transport gap was that REST body size was checked only after `request.text()`, and binary WebSocket size was calculated after decoding. The follow-up now reads REST streams incrementally to 16 KiB and cancels on the first over-limit chunk; WS binary byte length is checked before decoding.
- Regression coverage verifies stream cancellation/no second read and raw ArrayBuffer length.
- Re-run the full server suite/typecheck/Wrangler validation and Android unit/build workflow on the resulting commit. CI from the parent commit does not validate these changes.
- Do not deploy Staging or Production, change secrets, mutate D1, or merge to `main` without explicit authorization. PDF reader files/animation/zoom/gesture paths remain out of scope and untouched.

## CI correction — TextDecoder options

- TypeScript on `af67b696e390e9d00c9f0aacfbe8446c464708c1` failed at `new TextDecoder('utf-8', { fatal: true })` because the installed Workers Types declaration requires `ignoreBOM`.
- The follow-up sets `ignoreBOM: false`, preserving the standard BOM-stripping behavior. The earlier run stopped before the test step; it is not test evidence.
- Re-run the full server workflow and confirm the Android workflow result before further phase closure.

## Phase 2 implementation checkpoint — 2026-11-15

- Latest code checkpoint before this documentation commit: `dc76de52e81f9739b07beb5be7644673702233c0` on `work/release-readiness`; parent `d3437db98fcf8c7ca217188a73c4dc26f8c8f581`.
- PR #6 remains open Draft with base `main`. The only repository branches remain `main` and `work/release-readiness`.
- `main` remains `64143994070bcdc410cf044379fe2801da8f9a3c`.
- Phase 2 code implemented: strict schedule/proposal/vote validation; D1 migration 0033 committed but not applied; WebSocket socket/session generations and bounded reconnect policy; repository chat-history generation gate; logout invalidates chat before queue wait; Outbox failed state requires explicit retry, sequence ordering and UUID IDs; snapshot pending-row retention has direct regression coverage; the Settings retry button is wired to the explicit retry operation.
- Server CI for the parent commit `d3437db98fcf8c7ca217188a73c4dc26f8c8f581` passed typecheck, dependency audit, full tests and Wrangler dry-run. Server CI is rerunning against `dc76de52e81f9739b07beb5be7644673702233c0` because the logout ordering changed.
- Android unit tests and Staging Debug APK assembly/upload passed on `40cadcd391f9c3df679671dd63c9820dee7063c2`. Android CI for `d3437db98fcf8c7ca217188a73c4dc26f8c8f581` was running at the time of the last checkpoint; the new `dc76...` run also needs confirmation before claiming the final Android result.
- No PDF reader/page-curl/zoom/gesture paths were changed.
- Migration 0033 has not been applied. No Worker deploy, D1 write, secret update, Production change or merge to main has occurred.
- Remaining release acceptance: live Staging test using two accounts and REST+WS; switch groups during slow history load; disconnect/reconnect, normal close and logout races; Outbox pending/failure/backoff/manual retry/idempotency across network loss; schedule/vote tests against a Staging DB after authorized migration; physical Android acceptance.


## Phase 3 — TNR-006 notebook checkpoint — 2026-11-15

- Corrupt/empty notebook JSON is backed up and is never overwritten as an empty collection.
- AtomicFile persistence and a Boolean save result are wired through notebook state and the transcription UI.
- Regression tests cover valid round trips, corrupt data preservation and prevention of overwrite.
- Remaining Stage 3 work: PDF render/download storage and notification lifecycle. Real acceptance remains deferred until all roadmap implementation phases are complete.


## Phase 3 — TNR-009 reminder checkpoint — 2026-11-15

- Summary reminders are updated or cancelled from current Room snapshots. Empty local data cancels an alert only when all four sync methods report SyncResult.Success (or auto-sync is intentionally off); network/offline/error and read exceptions retain prior alerts.
- Individual alarms now verify the current task/exam, group membership, completion, date and preference before display.
- PendingIntent alarm cancellation includes its original action. Added AcademicReminderPolicyTest.
- Phase 3 scope TNR-006, TNR-007 and TNR-009 is implemented with regression tests. CI passed on the later code checkpoint df1cca02c0e0fda33e58f20dae0960c9bdf2f0f9: Server run 43734220778 and Android run 43734220722. Physical device/Staging acceptance remains deferred to Phase 5.
- Roadmap total: five phases. Two remain after Phase 3: Phase 4 and Phase 5.

- Follow-up regression covers the text-only lesson summary checkbox with zero image pages and validates completion page indices.

- PDF follow-up: download cancellation is propagated and preserves the resumable partial file; the bitmap cache cap is one eighth of app heap, with 64 MiB maximum.

- Notebook cache retry hardened: pre-existing board files are reconciled into the notebook record; batch and single-page cache upserts merge the latest record and fail closed on corruption.

- Notebook read path shares the same synchronized manager lock as writes/upserts to prevent in-process read/write overlap during AtomicFile replacement.


## Latest handoff — Phase 3 code implementation complete (2026-11-15)

- Verified working branch: `work/release-readiness`.
- Latest code commit: `df1cca02c0e0fda33e58f20dae0960c9bdf2f0f9` ([commit](https://github.com/majd7772233-a11y/Tanweer/commit/df1cca02c0e0fda33e58f20dae0960c9bdf2f0f9)).
- Phase 3 implementation is complete and automated validation passed:
  - Server CI [43734220778](https://github.com/majd7772233-a11y/Tanweer/actions/runs/43734220778): security baseline, typecheck, tests and Wrangler validation passed.
  - Android CI [43734220722](https://github.com/majd7772233-a11y/Tanweer/actions/runs/43734220722): unit tests, Debug APK assembly and artifact upload passed. Signed Release steps were skipped.
  - Debug APK artifact name: `Tanweer-debug-fe05758342c6bf86a5c9df4148b2c092978e374e` (30,812,924 bytes).
- Phase 3 includes TNR-006 AtomicFile notebook persistence/corruption guard/atomic cache-record merges/text-only checkbox; TNR-007 target-bounded rendering, capped bitmap cache, validator-backed resume and verified PDF promotion; TNR-009 summary/individual notification state validation.
- Do not start manual device or Staging acceptance yet. The user explicitly deferred all real acceptance until every implementation phase is complete.
- Phase 4 remains: SQL Studio response contract, update channel truth, search history, screen state/data truth, Owner Dashboard operations and responsive/accessibility audit.
- Phase 5 remains: cross-role, multi-device, network-failure, file, PDF, notebook, notification, security and physical-device/Staging acceptance.
- Roadmap has five phases; exactly two remain. No Worker deploy, D1 migration apply (0033 remains unapplied), secret change, Production mutation or merge to main was performed.
- Preserve the PDF reader/page curl/zoom/gesture files; Phase 3 did not modify them.


## Continuation — Phase 4 hardening (2026-11-16)
- Latest reviewed HEAD before this continuation: b04693d1d5923f2f6fdc7f76609188a96ff144b2. The working branch remains work/release-readiness; main is not to be modified.
- Fixes included in the next commit: independent teacher/moderator/admin loading states; explicit exception handling with cancellation propagation for dashboard and moderator-list repositories; blank active-group errors; visible loading/retry feedback for moderator corrections/decisions/members; assertFalse import for the confirmed-empty policy tests.
- CI on b04693d1d5923f2f6fdc7f76609188a96ff144b2: Server passed; Android test compilation failed because assertFalse was missing from the test imports. This patch adds it and also fixes the request-state race found in review.
- Same patch updates GPT.md and DEEP_CODE_AUDIT_REPORT_2026-11-14.md. Wait for CI on the new exact HEAD before declaring Phase 4 closed.
- No Worker deployment, D1 migration apply, secret rotation, Production write/deploy, merge to main, or physical-device acceptance.

- Group-scoped startup and screen requests now use per-source request generations. A slower response from an old group cannot overwrite the newer group's corrections, decisions or membership snapshot; switching groups clears only the old group's data, while same-group refresh failures preserve the last successful snapshot.

## 2026-11-16 — Phase 4 checkpoint after dashboard request isolation

- Current source HEAD before this documentation checkpoint: `01c057936289bae4f8f19fd551113a79e47bf804`; branch remains `work/release-readiness`. `main` remains `64143994070bcdc410cf044379fe2801da8f9a3c`.
- SQL Studio TNR-005: commit `515ddc023cb29e99a206be9c0ae7a7021487914a` aligns the input/output IDs with the actual HTML and consumes the server's `SELECT` / `MUTATION` response contract. Server tests cover returned rows, empty SELECT, `meta.changes`, invalid SQL and DOM/script wiring.
- Search history TNR-011: commit `c543fd8e08dccdee04d972ed2ee780d4769a37a8` removes persistence from each keystroke; history is stored when Search is committed with the keyboard or a suggestion/history item is selected.
- Sync and screen truth: commits `83786b82740e5c0bc44425e64c4471f37975fd37` and `637d911a0595c06ba0cb4b4ce83086f205df58dd` expose route-scoped sync status and refresh actions, report the oldest included source checkpoint, add responsive mobile navigation state and accessible names for owner-dashboard controls.
- Owner Dashboard: the responsive breakpoints at 900px and 500px are preserved. The mobile menu communicates its expanded state. The principal operational search/filter/confirmation controls have programmatic names and associated modal labels. These are source/DOM assertions; they do not replace keyboard/screen-reader testing in a browser.
- Role-dashboard truthfulness: `b04693d1d5923f2f6fdc7f76609188a96ff144b2` adds explicit failure/empty-state policy; `030340224845cd1657991cb126b0fb7913b9bef1` isolates teacher, moderator and admin loading/error states and preserves previous teacher/moderator snapshots on refresh failure; `0660bfaf63094a4adb8045f87549459f8272a349` adds per-source request generations for group membership, corrections and decisions to prevent old-group responses from overwriting the active group.
- Dashboard account isolation: `d55aa79a1d4957934c95c602af516d932abd7bfd` binds teacher/moderator/admin dashboard requests—including startup fetches—to the initiating account, role and request generation. A user/session reset invalidates outstanding generations. Unit tests cover matching account/role/generation and rejection of stale account, role or generation results. `01c057936289bae4f8f19fd551113a79e47bf804` ensures only the still-active generation can clear its loading flag.
- CI evidence: Server CI passed on `01c0579` (run `43929928009`). Android CI passed on `d55aa79` (run `43929317637`); the Android run for the current source HEAD `01c0579` was still running at the time this note was prepared (run `43929927910`). Do not treat that pending run as a pass. The earlier duplicate `CancellationException` import that caused the `0660bfaf` build failure was removed in `ec27b82033728ea3f52a406ac6a350f8c220824c`; CI passed on that commit (Android run `43928080752`).
- TNR-010 remains deferred until immediately before the first public release, as the user confirmed the app is still unpublished and under development. No update-channel runtime behavior was changed.
- Phase 4 is not closed by this checkpoint. Close it only after Android CI succeeds on the latest code/documentation HEAD and the remaining route/dashboard checks are reviewed. Phase 5 physical-device/Staging acceptance remains deferred until implementation phases are complete.
- No Worker deployment, D1 migration apply, secret rotation, Production write/deploy, merge to `main`, or physical-device acceptance was performed.


## 2026-11-16 — Phase 4 follow-up: scoped empty states and first-schedule safety
- Rechecked the live development branch before this patch: `work/release-readiness` at `bb61dd7a158eeb5a492cc07b057dab3583dc3c6e`; the only other branch remains `main` at `64143994070bcdc410cf044379fe2801da8f9a3c`.
- Previous source checkpoint `bb61dd7` had successful Android and Server CI runs (`43934387283` and `43934387390`). Those results validate that checkpoint only; this patch requires its own CI run.
- Fixed the remaining missing empty-state guard in the Teacher Dashboard's separate Official Homeworks tab. It now claims an empty list only after a successful dashboard snapshot with no active load/error.
- Schedule bootstrap safety: the initial empty Room Flow no longer grants all users editing rights by itself. Empty schedule slots are editable by staff, when the server explicitly marks initial setup, or after a schedule snapshot is known. The weekly grid and compact day list share the same rule; unknown state shows a pending-sync message and does not expose direct-edit actions.
- Library grade scope: successful book sync records the grade ID it belongs to, and a request generation prevents a slower previous-grade result from replacing the status of the currently requested grade. Selecting another grade starts its own sync; an empty catalogue is displayed only after a successful sync for that exact grade. An unverified grade uses its pending/offline/error state.
- Academic History: empty archive claims, personal empty lists and per-subject empty labels require a successful archive snapshot. When no records exist before a confirmed snapshot, the screen shows the archive sync state and avoids presenting zero as a confirmed result.
- Added pure-policy tests for initial schedule editing and grade-scoped empty catalogues. No backend schema, migration, secret, deployment, main-branch, or production change was made.
- Phase 4 remains open until Android and Server CI pass on this patch and the remaining UI routes are rechecked. Browser/assistive-technology acceptance and physical-device testing remain separate acceptance work.


## 2026-11-16 — Android CI follow-up for archive-state import
- Android CI on `bd6938825ce41fc7d54bf17e4a35b1da9c9576e1` compiled the source until two unresolved symbols in `AcademicHistoryScreen.kt`: missing imports for `hasAuthoritativeSnapshot` and `PendingSyncEmptyState`. The implementation itself was not reported to have additional Kotlin errors in that compile attempt.
- Added the two explicit component imports. This follow-up requires a fresh Android CI run; the failed run is not considered a validation result.


## 2026-11-16 — Group catalogue empty-state gate
- `syncGroups()` is the single repository operation that retrieves and reconciles both `myGroups` and `discoverGroups`. The Groups screen previously rendered empty copy against Room flows whose initial value is `emptyList()`.
- Both shared-group and discovery empty states now require `groupsSyncState == Success` through `hasAuthoritativeSnapshot`. Until then, the screen presents the actual idle/loading/offline/error sync state instead of claiming no groups exist.
- This reuses the existing policy regression test that only `SyncState.Success` may confirm an empty list. No new endpoint or database behavior was introduced.
- Android and Server CI results for the current head are still being rechecked; this UI follow-up requires its own Android CI validation.



## 2026-11-17 — Phase 4 continuation: group chat history truth

- Verified current HEAD before this change: `d35cbd81c67d7f5b43e61dfbfbcb7c97efd135b9`. Android CI run `44075715683` and Server CI run `44075715750` both passed on that exact HEAD.
- Continued the screen-state audit after the Groups catalogue empty-state gate. Found that the chat screen displayed its “start discussion” empty state as soon as the local Room flow was empty, even while the REST history request was still running or had failed.
- Group chat connection now returns the actual history sync result after opening the WebSocket immediately. The ViewModel exposes a request-generation- and group-scoped chat history state; stale responses after switching/leaving chats cannot update the visible group's state. HTTP errors, thrown exceptions and cancellation are handled distinctly. History payloads are validated against the requested group before persistence.
- The chat UI shows a pending/error/offline sync state and a retry action until a successful snapshot for the exact group is received. Only a successful empty server snapshot can show the genuine no-messages state. Existing local messages remain visible while sync status is surfaced.
- Added Arabic and English strings and a pure regression test for exact-group success gating. This patch requires fresh Android and Server CI; the prior successful runs do not validate it.
- No staging/production deployment, D1 migration, secret change, merge, or physical-device acceptance was performed.



## 2026-11-17 — Phase 4 follow-up: schedule proposal empty-state gate

- Continued the route-state audit and found that the schedule proposal review dialog received a group-scoped proposal list but showed “no pending proposals” when the current group had not yet produced a successful proposal snapshot.
- The dialog now receives the scoped proposal sync state and snapshot validity. It displays a pending/error/offline state with a refresh action until the exact active group has a successful snapshot; only then can an empty list be described as having no pending proposals.
- Added a policy regression to ensure an unverified group scope cannot authorize the empty state. Requires a new Android CI run after the current chat-history patch.



- Schedule proposal dialog follow-up: when a refresh fails after a previous successful snapshot, the dialog keeps the last confirmed proposals visible but now displays the sync error/offline status and labels the count as belonging to the last confirmed snapshot. A first-load empty result still cannot claim there are no proposals.


## 2026-11-17 — Phase 4 Owner Dashboard data-state follow-up

- Based on the verified source HEAD e155b5c04d141a5db429ddf550246ea2f516dd26, hardened System Owner overview statistics and list loaders.
- Overview statistics now validate HTTP success, API success, required nested fields and non-negative counts before replacing displayed values. Failed refreshes preserve the last confirmed values and announce that they are stale.
- User directories, role requests, groups, lessons, homework, exams, events, holidays, issues, schedules, decisions, corrections, chat and audit lists distinguish failed/malformed responses from successful empty arrays. Failed table states expose an explicit retry action.
- Owner group selectors for schedules, chat and holiday creation report group-list failures instead of swallowing them. Missing schedule-slot and decision vote counts render an unknown marker rather than invented zero.
- Added render-level regression assertions for failed versus empty user lists, stale overview status and visible group-selector failures.
- This patch does not change owner authentication/authorization, role visibility, schema/migrations, Worker deployment, secrets, Production data or main. Validation must run on the exact new HEAD; previous CI on e155b5c does not validate this patch.


## 2026-11-17 — Phase 4 Owner Dashboard mutation acknowledgement

- Owner account freeze/enable, single/all session revocation, role request rejection, role change/approval and holiday creation/deletion now require both HTTP success and an explicit API success flag before showing success or refreshing dependent data.
- Failed actions surface the server error message through the dashboard error helper and stop the success path. This is a client-side confirmation safeguard; server-side authorization remains authoritative and unchanged.
- Regression assertions verify the account/session action handlers contain the explicit acknowledgement guard. CI validation is pending on the new commit.


## 2026-11-17 — Phase 4 Owner Dashboard operation and schedule-grid hardening

- **Base branch / commit:** work/release-readiness / 4502fb01a120e3ab8eb45c0170891173126004bd. Changes below are prepared in the local worktree and are not committed yet.
- **Owner mutation acknowledgement:** Added one shared client-side policy requiring response.ok === true and data.success === true. Applied it to account/session actions, credential issue/revocation, role change/approval/rejection, content pinning, homework/exam deletion, holiday create/delete, schedule reset, decision apply/reject, chat deletion, purge preview and purge execution. Error messages now prefer server details and do not reuse an HTTP-success body as proof of an HTTP-confirmed operation.
- **Destructive purge uncertainty:** Final purge now requires a non-empty newGeneration and a non-empty tablesPurged list before displaying success. HTTP 5xx, connection loss, or an incomplete success payload clear the in-memory secret and leave the destructive action disabled with explicit guidance to refresh and inspect the generation before any retry.
- **Owner schedule grid:** The weekly schedule detail view now validates HTTP status, API success and the slots array. Failure or malformed responses render an accessible error with a retry action scoped to the same group rather than a fabricated empty grid.
- **Related read/login checks:** User/content detail reads now reject failed HTTP responses; session refresh after credential reissue requires a successful HTTP response and a valid sessions array; owner login navigation requires HTTP success plus API success.
- **Regression tests:** Expanded server/test/dashboard-fixes.test.ts to cover the shared mutation guard, every remaining sensitive owner action, non-retryable ambiguous purge outcomes, weekly-grid error/retry behavior and HTTP-confirmed owner login.
- **TNR-005 / TNR-011:** Re-read the current implementation and tests. The SQL Studio contract and committed-search history behavior remain correct; neither was reimplemented.
- **Validation:** No local build/test was run, per the remote-CI workflow. Android and Server CI must be checked on the exact pushed SHA. The existing runs 44098040628 / 44098040625 validate the earlier e81ab42 code checkpoint only.
- **Scope/safety:** No Android source, schema, migration, secret, Worker deployment, Staging/Production data, main, branch creation or PR merge was changed. PR #6 must remain open and Draft. TNR-010 remains deferred until before the first public release.
- **Phase 4 status:** Still open. Do not close until the new SHA passes Android and Server CI and the remaining route/dashboard review is complete. Browser keyboard/screen-reader and real-device acceptance are not claimed.
- **Next:** Inspect the final diff and regression test source, commit the code and this handoff update together on work/release-readiness, push, then verify both CI workflows on the resulting SHA.

## 2026-11-17 — Phase 4 Search and verified-summary consistency

- **Base:** work/release-readiness at c238557939a49cc611ecbb0a17bdb10b3bb1c99f. This follow-up is saved locally as one combined code/test/docs commit on work/release-readiness, based on this SHA. Push and fresh CI are pending.
- **Search freshness:** SearchScreen now surfaces the freshness state for the selected search domain. The ALL view aggregates the seven sources: lessons, homework, exams, events, issues, chat history and grade books. Group sources are accepted only for the exact active group; books are accepted only for the currently selected grade.
- **Partial offline handling:** Added aggregateRequiredSyncStates so one OfflineCached source cannot make the whole Search index appear available when another required source remains Idle/unverified. Errors and in-flight states remain visible.
- **Chat history:** Added a Search-only, request-generation-guarded REST sync state in TanweerViewModel. It uses the repository's group-message snapshot endpoint without opening or replacing the Groups screen's WebSocket connection. The state is reset on account/session reset and scoped to the exact group. The endpoint retrieves the latest 100 server messages; older locally cached messages are retained by the repository.
- **Search refresh:** Search status bar's retry action refreshes the selected source or all seven sources. Every group refresh passes the screen's explicit activeGroupId, avoiding reliance on a possibly blank internal selection when the screen falls back to the user's default group.
- **Search entry:** SubScreen.SEARCH now refreshes grade books as well as the previously refreshed group sources. Changing the selected group re-runs the current destination's sync through the existing ViewModel navigation path.
- **Timeline summary:** SubjectTimelineScreen now shows — for lesson/homework/exam counts until all three authoritative snapshots are confirmed, instead of presenting initial Room zeros as real counts.
- **Localization:** The local result count and index/chat-window note use matching Arabic and English string resources; the chat note discloses the latest-100 server window and retained older local history.
- **Regression tests:** Expanded PhaseFourUiPolicyTest for verified timeline counts and Search aggregation across complete, partially offline, idle and error states.
- **Validation:** git diff --check passes. No local Android build/test was run; require fresh Android CI on the exact pushed SHA. Server source did not change in this follow-up; the previous Server CI runs on c2385579 passed.
- **Phase status:** Phase 4 remains open until this new SHA passes Android CI and the final source review is reconciled. This does not claim physical-device, screen-reader, keyboard or Staging acceptance.
- **Scope:** No database schema/migration, API/server source, Worker deployment, secrets, Staging/Production data, main, branch creation or PR merge changed. Keep PR #6 open and Draft. TNR-010 remains deferred until before first public release.
- **Next:** Inspect this final diff and documentation, commit on work/release-readiness, push, then verify Android and Server CI on the exact new SHA and refresh this handoff.

## 2026-11-17 — Phase 4 final Search empty-state guard and current CI security blocker

- **Base:** work/release-readiness at 75b3c5c1dbf40e6692aa701e4352e90360097715. The follow-up code, tests and these notes are being committed together.
- **Search no-match truth:** SearchScreen no longer says that a query found no results merely because the current Room count is zero. It confirms no-match only after SyncState.Success for the selected source scope. Loading/Refreshing, Idle and Error show distinct unverified states; OfflineCached says that no match exists in saved local data but more server results may appear after reconnect.
- **Localization:** Added matching Arabic and English resources for the verified no-match, offline-local, pending, loading, error and enter-query messages.
- **Regression:** Added shouldShowConfirmedNoSearchResults and coverage for Idle, Loading, Refreshing, OfflineCached, Error, blank queries, nonzero counts and confirmed successful empty results.
- **Latest Android CI before this follow-up:** Run 44140654341 succeeded on 75b3c5c1: Android unit tests, Debug APK assembly and Debug APK upload succeeded; Release signing tasks were skipped by the PR workflow.
- **Latest Server CI before this follow-up:** Run 44140654335 failed at npm audit --audit-level=moderate; TypeScript, server tests and Wrangler validation were skipped by the workflow. This is a dependency-security blocker, not a failure in changed Server code (no Server source or dependency files were changed in 75b3c5c1).
- **Security finding:** npm audit reports GHSA-fc5q-39xc-vjpf / CVE-2026-112366 in transitive picomatch (<=4.0.7), through fdir 6.5.0 and tinyglobby 0.2.17 into vite 8.3.5 / vitest 5.0.5. npm registry and upstream releases currently show 4.0.7 as the latest published picomatch. Upstream PR 194 is still open/unmerged and its own description says it does not remediate the general repeated-extglob ReDoS. npm's suggested audit fix --force would downgrade Vitest to 3.1.4 / Vite to 6.2.7; that is a breaking toolchain change and is not accepted without a verified, fully patched dependency path.
- **Security decision:** Do not weaken the audit threshold, suppress the advisory, force a test-tool downgrade, or vendor an incomplete upstream patch. Keep Server CI blocked until a complete upstream fix is published or a reviewed mitigation is agreed and tested. Server code/lockfile were left unchanged by this follow-up.
- **Phase 4 status:** Implementation review is substantially advanced, but do not close this phase while the required Server dependency security gate is red. Fresh Android and Server CI on the new combined commit are still required. Screen-reader, keyboard, physical-device and Staging acceptance are not claimed.
- **Scope:** No schema/migration, backend code, Worker deploy, secrets, Production/Staging data, main, branch creation or PR merge. Keep PR #6 open and Draft. TNR-010 remains deferred by user decision until before the first public release.
- **Next:** Commit/push this follow-up, verify Android CI, then keep the task open while tracking the upstream picomatch security fix and rerun Server CI when a complete safe fix is available.

### 2026-11-17 — picomatch ReDoS remediation

- Resolved the current Server npm-audit blocker GHSA-fc5q-39xc-vjpf / CVE-2026-112366 by vendoring a reviewed backport of upstream picomatch PR #237 (source commit 1bf0d04563cb9ce568cf36efc5f6b7187d9911d5).
- The local package is explicitly versioned 4.0.8+tanweer.1; this is a downstream build, not an official npm release. Server package.json pins picomatch as a local development tarball dependency and sets overrides.picomatch to $picomatch; the lockfile records its local archive integrity. No Vite/Vitest downgrade, advisory suppression, audit-level change, or external runtime dependency was introduced.
- The backport collapses the redundant outer repetition for eligible single-branch extglobs containing ordinary star wildcards. It excludes globstars, path separators and complex syntax. In capture mode it uses the conservative literal fallback to preserve capture behavior.
- Added server/test/picomatch-security.test.mjs for regex-shape regression, expected matching behavior, capture fallback, and a child-process timeout probe against crafted non-matching strings. Source and attribution are documented in server/vendor/picomatch/UPSTREAM_SECURITY_PATCH.md.
- The npm local-file override and clean-install behavior were verified in an isolated npm fixture. A project lockfile regeneration, project npm audit, full Server CI, and fresh Android CI remain to be completed/verified before this checkpoint can be called green. Do not close Phase 4 yet.
- Keep the existing boundaries: branch work/release-readiness only; main untouched; PR #6 open and Draft; no merge, Worker deployment, D1 migration, secrets, or Production/Staging writes. TNR-010 remains deferred until before first release.

### 2026-11-17 - post-backport verification

- The checked-in Server package manifest and package-lock.json now use the local picomatch 4.0.8+tanweer.1 archive via a direct devDependency plus the $picomatch npm override. Lockfile integrity pins the archive; Vite/Vitest remain at their existing major versions.
- The exact Server CI audit command, npm audit --audit-level=moderate, now passes on the updated project lock with zero reported vulnerabilities at every severity. The previous five high records were five affected packages reporting the same underlying advisory.
- A clean copy of the entire Server package (excluding existing node_modules) completed lock generation and npm ci --ignore-scripts; npm ls confirmed Vite, Vitest, tinyglobby and fdir all resolve to the same local patched version. npm audit then returned zero vulnerabilities. The local clean-install probe used Node 26/npm 12 and disabled lifecycle scripts; the repository CI on Node 24 remains the authoritative install/typecheck/test/bundle validation.
- No local TypeScript, Vitest, Android build, or Wrangler bundle command was run. Fresh Server and Android GitHub Actions runs on the committed SHA are required. Phase 4 remains open until those checks and the already documented manual acceptance work are complete.

## 2026-11-21 — Phase 1 local implementation checkpoint

- **Base checkpoint:** branch work/release-readiness, HEAD c7e23899139f9427a1eb50ad3907f9ac384af150 (docs: add prioritized roadmap from visual QA report). This checkpoint is still an uncommitted local worktree; no new commit or push was made in this session.
- **P1-01 draft retention:** foreground onAppResumed() no longer toggles the root isCheckingAuth splash flag during background consistency verification, so the current Compose tree is retained during Photo Picker/Camera returns. Lesson and homework form fields use rememberSaveable; scan pages use ScanPageDraftSaver and app-private persistent source files, then resume processing. Explicit lesson cancel now cleans unsent source/processed temp files. Successful/offline publication keeps processed files when Room/outbox may still reference them.
- **P1-02 RenderThread:** user-provided power-save/long-uptime context was preserved in ROADMAP. No blur, shadows, gradients, rounded clipping, motion, shader or other visual effect was reduced. Status remains deferred pending Phase 5 controlled normal-power testing.
- **P1-03 PDF/TTS:** added PdfSpeechTextQuality guard and focused unit tests. It blocks clearly corrupt extraction before speech; it does not repair the underlying PDF font/GlyphMap/RTL extraction. Do not label root extraction repair complete.
- **P1-04/P1-05 chat:** added object-shaped HTTP ACK DTO with exact message identity confirmation; added reconnect history reconciliation, request/socket generation guards and a dedicated WebSocket OkHttp client with protocol Ping/Pong.
- **P1-06 ADMIN identity:** added D1 migration 0034_detach_global_school_admins.sql with restore-point tables, preservation/removal of CLASS-only memberships, and non-class membership retention. Login/profile omit grade and section for ADMIN. Role change can restore saved scope or accept an explicit validated replacement class; pure policy regression coverage is in server/test/admin-class-assignment-policy.test.ts (6 tests).
- **P1-07 admin boundary:** system governance permissions remain SYSTEM_OWNER-only and /api/dashboard/* remains behind verifyOwnerAuth. The school Admin dashboard now includes read-only staff lists for teachers, moderators and administrators; it does not expose Owner audit/session/global-role/purge surfaces.
- **Additional draft cleanup:** fixed the explicit bottom Cancel button in UploadLessonDialog to follow the same safe cleanup path as the dialog close/back controls.
- **Server validation:** npm test passed on the current worktree: 42 files / 293 tests. npm run lint passed (tsc --noEmit). Expected stderr from simulated failure-path tests did not represent failed test cases.
- **Android build validation:** :app:assembleStagingDebug completed successfully. Kotlin/Java compilation succeeded.
- **Android unit-test blocker (refreshed 2026-11-21):** The latest full :app:testStagingDebugUnitTest run completed 124 tests: 123 passed and one failed, the same unmodified StudentNotebookManagerTest.valid notebook records round trip through atomic storage assertion (expected [lesson-2, lesson-1], actual [lesson-1]). StudentNotebookManager.kt and its test are unchanged in this worktree; source last changed in df1cca0. A previous checkpoint reported seeing the second JSON write in student_notebook_records.json.new, but this latest run did not independently inspect the Robolectric sandbox path, so the Windows/Robolectric rename explanation remains unconfirmed. Do not suppress the test, weaken its assertion, or change notebook logic without establishing the cause. A non-Windows CI/Robolectric comparison is still required.
- **Device acceptance boundary:** per user direction, no real app interaction was performed for acceptance in this checkpoint. The temporary .staging.qa clone installed during the preceding signature workaround was removed; original com.magd.tanweer and com.magd.tanweer.staging packages remained installed. Do not launch or test the app on emulator/phone again before Phase 5.
- **Database/deployment boundary:** migration 0034 was added as source only; it was not applied to Staging or Production. No Worker deploy, secret change, production/staging write, main change, branch creation or PR merge occurred. Keep PR #6 open and Draft. Keep this WebGPT task running until the user explicitly asks to finish it.
- **Next:** reconcile Android AtomicFile test under Linux CI (or another supported Robolectric environment), inspect the complete P1 diff and migration SQL once more, ensure all changed UI strings are localized, then commit code + docs together on work/release-readiness. All emulator/phone manual acceptance, including P1-02 controlled power/soak testing, remains in Phase 5.



## 2026-11-21 - Phase 1 verification refresh

- Exact code checkpoint: branch work/release-readiness at c7e23899139f9427a1eb50ad3907f9ac384af150; origin/work/release-readiness is the same SHA. All Phase 1 implementation changes remain local, unstaged and uncommitted. No commit or push was made.
- Server: npm test -- --reporter=dot passed 42 test files / 293 tests; npm run lint (tsc --noEmit) passed after the latest chat role-normalization edit.
- Android targeted regressions: 5 test classes / 16 tests passed: PostGroupMessageResponseTest (2), RealtimeChatManagerPolicyTest (3), ScanPageDraftSaverTest (3), ImageProcessingDraftCleanupTest (2), PdfSpeechTextQualityTest (6).
- Android full suite: 124 tests completed, 123 passed and one failed in the existing StudentNotebookManager AtomicFile round-trip test. The failure is left open; it was not skipped or masked.
- Staging Debug packaging: :app:assembleStagingDebug passed. Local artifact: app/build/outputs/apk/staging/debug/app-staging-debug.apk (33,566,213 bytes; SHA-256 F003A8A73319F5787410E998F92376A0E54D4EBD4B1F4FD4B669B502329C9731). The APK was built only; it was not installed or launched.
- Migration safety check: Migration 0034 was applied only to an isolated in-memory SQLite database seeded with representative ADMIN, student, CLASS and school-wide memberships. Restore points, retained non-class memberships and PRAGMA foreign_key_check all passed. No D1/Staging/Production database was read or changed.
- Small review fixes in this refresh: reconnect retry state is cleared when switching chat sessions/disconnecting; the Worker ADMIN sender label now uses the canonical role normalizer. Server tests/lint and targeted Android tests passed after these changes.
- Acceptance boundary: no emulator, physical phone, UI walkthrough, or live Staging acceptance was performed. All such tests remain in Phase 5. P1-02 remains environment-confounded/deferred; visual effects were not reduced or changed.
- Governance: no deployment, secret change, D1 migration application, Production/Staging write, merge, main-branch change, or temporary branch creation occurred. PR #6 remains open and Draft. Keep this WebGPT task running until explicitly told to close it.

## 2026-11-21 — Phase 1 closeout continuation

- **Branch checkpoint:** work/release-readiness. Last committed HEAD and origin/work/release-readiness remain c7e23899139f9427a1eb50ad3907f9ac384af150 at the start of this continuation. Phase 1 source changes are still local pending final review/commit.
- **AtomicFile root-cause investigation:** Reproduced the Windows/Robolectric API 34 failure with direct file inspection. After the first save the base JSON contains lesson-1. After the second save, student_notebook_records.json.new contains both lesson-2 and lesson-1, but the base file still contains lesson-1; the subsequent read therefore returns only lesson-1. Inspected Robolectric's Android 14 instrumented AtomicFile bytecode: finishWrite closes/syncs and calls File.renameTo(newName, baseName). Windows File.renameTo does not provide Android/Linux replace-existing semantics, which explains the host-only failure. This is a confirmed Windows/Robolectric filesystem behavior, not a demonstrated Android production failure.
- **Test integrity:** Restored StudentNotebookManagerTest to its original @Config(sdk = [34]). No assertion was weakened, no test was skipped, and no production StudentNotebookManager behavior was changed. A temporary API 23 comparison was attempted but Robolectric could not download android-all-instrumented:6.0.1_r3-robolectric-r1-i7; API 23 was reverted. The existing Android GitHub Actions workflow already runs the full Staging Android unit suite on ubuntu-latest before packaging; this exact-SHA Linux run is now the required authoritative check for the AtomicFile test.
- **Build host resource exhaustion:** One local Gradle attempt failed before test execution because the JVM could not allocate native memory (only ~250 MB physical RAM free; daemon configured with -Xmx4g, four workers). Re-running the same test with process-local JAVA_TOOL_OPTIONS, --max-workers=1, no parallel/configuration cache, and -Xmx1024m allowed the task to execute. No persistent project memory settings were changed. The JVM crash logs were removed and must not be committed.
- **Server validation refreshed:** npm test -- --reporter=dot passed 42 files / 293 tests; npm run lint (tsc --noEmit) passed. Expected stderr from fail-closed/error-path tests did not indicate test failures.
- **P1-07 scope decision:** The school ADMIN remains a school administrator, not the system Owner. /api/dashboard/* is behind verifyOwnerAuth; global role mutation, global audit review, other-users' session revocation and system-wide governance remain Owner-only. The school app has separate school-admin dashboard data and student-scoped status/class-membership endpoints; account owners manage only their own devices. The dashboard provides staff directories as read-only. Do not grant Owner-wide permissions to ADMIN to make the dashboard look more complete.
- **P1-03 limit:** PdfSpeechTextQuality prevents clearly corrupted extracted text from reaching TTS; it does not repair missing PDF ToUnicode/Glyph maps, encoding, or RTL extraction. Keep that limitation explicit and use the user's real books in Phase 5. Do not report root PDF extraction repair as done.
- **P1-02:** Remains deferred to Phase 5 per user direction about Ultra Power Saving and extremely long test sessions. No blur, shadows, gradients, clipping, animation, shaders, or other visual quality were reduced.
- **Phase 5 carryovers:** camera/Photo Picker and Activity restoration acceptance; real-device two-client HTTP/WebSocket reconnect and D1 persistence; real PDF sample/text/TTS verification; QA-account verification before applying migration 0034; controlled RenderThread normal-power/soak testing. No emulator/device acceptance was performed here.
- **Scope guard:** Migration 0034 remains source-only and was verified against isolated in-memory SQLite only. No D1/Staging/Production database change, Worker deployment, secret change, production data write, main-branch change, temporary branch, PR merge or app installation/launch.
- **Pull request / task:** PR #6 must remain open and Draft. Keep WebGPT task d8ff20f1-780c-4e55-acc5-ed2dfe8d71be RUNNING until the user explicitly asks to close it.
- **Completion of source delivery:** reviewed the Phase 1 diff and localization, passed git diff --check, committed code/tests/migration/docs as f98371e10aa3ad303f28815e73ad90416f50c799, pushed to work/release-readiness, and verified Android + Server CI green on that exact SHA (see Exact-SHA CI closeout below). Physical-device acceptance remains Phase 5.

- **School tenancy boundary:** Reviewed GPT.md, migrations and current schema. This codebase currently models one school per Worker/D1: all is the school-wide group and schema has no school_id/district_id tenant key. P1-07 admin scope is therefore the single-school database boundary; do not present this as multiple schools isolated inside one shared D1. A shared-database multi-school/district tenancy model is a separate design/migration task before deploying one Worker/D1 for several schools.

## 2026-11-21 — Exact-SHA CI closeout

- Final implementation commit: f98371e10aa3ad303f28815e73ad90416f50c799 (fix: complete Phase 1 release-readiness remediation), pushed to origin/work/release-readiness. At push time HEAD and origin were identical and worktree clean. Commit includes 39 files: Android/Worker changes, tests, migration 0034, ROADMAP and SESSION_HANDOFF.
- Android CI: GitHub Actions run 44936152421 — Build Android APK — completed success. On Ubuntu, Run unit tests executed testStagingDebugUnitTest and ended BUILD SUCCESSFUL in 3m 3s; Assemble Debug APK ended BUILD SUCCESSFUL in 1m 42s. Debug APK artifact uploaded. Signed Release steps were intentionally skipped. The unchanged API 34 StudentNotebookManagerTest.valid notebook records round trip through atomic storage passed in this Linux run; local Windows/Robolectric failure was due to the host File.renameTo replace-existing semantic mismatch.
- Server CI: GitHub Actions run 44936152423 — Typecheck + Tests + Wrangler Validation — completed success. Typecheck passed; dependency security baseline passed; Vitest completed 42 test files / 293 tests passed; Wrangler production bundle validation passed. This was validation only, not deployment.
- Local Android caveat: the full unit suite on the Windows laptop had 123/124 passing due to the host-specific Robolectric rename behavior. The exact same unmodified test and production code passed on Ubuntu CI, resolving the CI acceptance gate. Focused local Android regressions passed 16/16 and local Staging Debug assemble had passed.
- Phase 1 disposition: implementation and automated CI are complete. Phase 5 retains physical camera/Photo Picker and lifecycle acceptance, two-client live HTTP/WebSocket reconnect and D1 persistence, real book PDF extraction/RTL/TTS verification and any required root extraction correction, controlled RenderThread soak testing under normal-power conditions, and explicit QA review before applying migration 0034. P1-02 remains deferred as requested; no visual effects were reduced. P1-03 is a guard against speaking obviously garbled text, not a claim that custom PDF font encodings/ToUnicode/RTL extraction have been repaired.
- Architecture caveat: one school per Worker/D1 in current schema. Multi-school/district tenancy inside one shared D1 remains a separate architecture/migration task.
- Safety / PR: migration 0034 was not applied to D1; no staging/production writes, Worker deploy, secret change, release signing, device install/launch, main change or merge. PR #6 remains open and Draft. WebGPT task d8ff20f1-780c-4e55-acc5-ed2dfe8d71be remains RUNNING.
- Next: Phase 1 implementation can hand off to Phase 2. Phase 5 acceptance items remain explicit and must not be marked passed until performed.
## 2026-11-21 — Phase 2 P2-01 implementation checkpoint

- **Base commit:** d1e65a0cf69cad042f9ce62cd95f67c3d69b7c7b on work/release-readiness; code/docs for P2-01 were local and uncommitted when this checkpoint was written. main remains untouched.
- **Scope:** P2-01 only — local FAILED exam rows could appear in approved schedules, counters, history, calendar and catch-up screens even though the server had not accepted them.
- **Confirmed streams:** Exam list, daily list and history DAO streams now return SYNCED only. The shared repository stream is used by Exams, Calendar, Class Knowledge Base, Academic History, Home, Search, Subject Timeline, WhatDidIMiss and exam notification lookup, so their totals are derived only from confirmed rows. Local PENDING/SYNCING/LOCAL and FAILED have dedicated Room streams.
- **Local resolution UI:** ExamsScreen now shows a pending-sync section and a failed-local section with retry and an explicitly confirmed local discard. It does not send a server delete when discarding an unaccepted local draft and does not display the generic empty-schedule card while local drafts are visible. Arabic and English labels are localized.
- **Durable failure/retry:** Permanent direct create rejection (including HTTP 409) saves the error and create payload in a FAILED Outbox row in the same transaction as the local exam status. Manual retry requeues only failed EXAM_CREATE (including the legacy EXAM/CREATE representation), resets its retry budget and transitions the exam to PENDING atomically. It refuses duplicate active creates and does not requeue exam toggles, deletes, homework or other failed mutations. Discard only deletes failed create operations and the associated local failed exam, and refuses to act while the create is active.
- **Snapshot reconciliation:** A failed row missing from a trusted server snapshot remains on-device for recovery. A same-ID row confirmed by the server supersedes the failed local row. It is not allowed to win the local pending merge merely because it is FAILED.
- **Automated tests:** ExamVisibilityPolicyTest 5/5; PendingSnapshotRetentionTest 4/4; ExamDeleteOutboxTest 8/8 — 17/17 focused tests pass, with no skips. Full local Windows suite: 129 tests, 128 pass and one unchanged StudentNotebookManagerTest.valid notebook records round trip through atomic storage fails because Windows/Robolectric File.renameTo does not replace an existing base file with Android/Linux semantics. The test/assertion and notebook implementation were not modified. The same API34 test passed in the earlier Ubuntu Android CI run 44936152421; retain the caveat and verify fresh CI on the P2 commit.
- **Build artifact:** :app:assembleStagingDebug passed. app/build/outputs/apk/staging/debug/app-staging-debug.apk is 33,554,805 bytes; SHA-256 81D6FDCC6876A26DC082F0A9AB04ACEBDD07EE3FA1F0D434BF0B7454DAD64D17. Built only; not installed or launched.
- **Acceptance still open:** no real Worker 409/retry-success round trip, process-restart check, two-device comparison, or UI acceptance on Exams/Calendar/Archive/WhatDidIMiss was performed here. Keep all physical-device and live environment acceptance in Phase 5, per user direction.
- **Safety / release boundaries:** no emulator or phone app interaction; no Worker deployment, secret change, D1 migration apply, Staging/Production database write, release signing, main change, PR merge or branch creation. PR #6 remains open and Draft. Keep WebGPT task d8ff20f1-780c-4e55-acc5-ed2dfe8d71be RUNNING until explicitly asked to close.
- **Exact-SHA delivery / CI closeout:** P2-01 code, tests and its implementation notes were committed as 412bba30859426a891ba860a92aa201446007537 (fix: keep failed local exams out of confirmed counts) and pushed to origin/work/release-readiness. Local HEAD and origin matched; the worktree was clean after delivery. Android GitHub Actions run 44948329141 completed success: unit tests passed on Ubuntu, Staging Debug APK assembly passed and Debug APK artifact upload passed. Signed Release/keystore steps were skipped for this PR validation. Server GitHub Actions run 44948329112 completed success: TypeScript typecheck and dependency security baseline passed, 42 Vitest files / 293 tests passed, and Wrangler Production + Staging dry-run validation passed. These were validation-only runs, not deployments.
- **Next:** continue with P2-02 on work/release-readiness. P2-01 implementation and automated CI are verified, but keep its two-device/live HTTP 409 retry-success/re-entry and UI acceptance items open for Phase 5. Do not start device acceptance early.


## 2026-11-22 — Phase 2 P2-02 implementation checkpoint

- **Base:** `a158ece6870c6658bea126ddc8dccfb14c46b93a` on `work/release-readiness`; PR #6 was verified open, Draft and unmerged before this change. `main` remains untouched.
- **Scope:** P2-02 only — consistent separation of `HOMEWORK` and `TASK` (project) in homework list counts/filters, home summary, calendar month/day details, WhatDidIMiss, and notifications.
- **Shared policy:** `HomeworkTaskTypePolicy` maps `HOMEWORK` and legacy null/blank values to homework, `TASK` to project, and unknown future values to UNKNOWN. Unknown values stay visible in the unfiltered list but are not counted as homework or projects.
- **Android changes:** HomeworkScreen now has separate type and due-today counts and normalized filters; cards carry an explicit type badge. HomeScreen separates pending homework, projects and unknown assignments. Calendar separates totals and per-day markers/cards, including a type-aware detail modal. WhatDidIMiss presents a broad assignments category and per-item type badges. The homework digest notification ID 2001 filters to HOMEWORK only; individually scheduled projects keep working but receive project-specific copy.
- **Localization / tests:** Added paired Arabic/English resources and `HomeworkTaskTypePolicyTest` for legacy, normalized, TASK and unknown values.
- **Validation boundary:** The WebGPT terminal bridge was unavailable during this continuation, so no local Git/Gradle command, local build, local test, or local diff check is claimed. Exact-SHA GitHub CI completed successfully for implementation commit `89e16d4ab99b7068ce13a4b7704acd196e501172`; Android and Server evidence is recorded below.
- **Phase 5 remains open:** visual UI acceptance for Homework filters, Calendar day markers/details, Today summary, WhatDidIMiss, and real notification/reminder display on Android 10/15. No emulator or physical phone was used.
- **Safety / governance:** no Worker deploy, secrets change, D1 migration apply, Staging/Production DB write, Release signing, app installation/launch, `main` change, PR merge or new branch. PR #6 remains open and Draft. Keep WebGPT task `d8ff20f1-780c-4e55-acc5-ed2dfe8d71be` RUNNING until the user explicitly asks to close it.
- **Exact-SHA CI closeout:** Android workflow run `45013081671` completed success on `89e16d4ab99b7068ce13a4b7704acd196e501172`. `testStagingDebugUnitTest` ended `BUILD SUCCESSFUL` (1m 53s); `assembleStagingDebug` ended `BUILD SUCCESSFUL` (40s); Debug APK artifact upload passed. Signed Release preparation/build/verification/upload steps were skipped. Server workflow run `45013081388` completed success: TypeScript typecheck, dependency security baseline, 42 Vitest files / 293 tests, and Wrangler Production + Staging dry-run validations passed. No deployment occurred.
- **Next:** continue with P2-03 on `work/release-readiness` when the user asks to proceed. Keep UI, notification, and physical-device acceptance for Phase 5.

## 2026-11-22 ? Phase 2 P2-03 implementation checkpoint

- **Base:** 3b70f7cdcbbe6d3ce45ecdd4a0a408cb8a74e294 on work/release-readiness. Branch was clean and synchronized before edits; PR #6 remained open, Draft, and unmerged.
- **Scope:** P2-03 only ? turning off homework/exam/schedule notification settings must remove active notifications immediately and prevent in-flight/daily checks or scheduled alarms from restoring them.
- **Implementation:** TanweerViewModel now delegates the three setting transitions to category-specific TanweerNotificationManager methods. Summary IDs 2001/2002/2003 are cancelled synchronously for only the selected category.
- **Persistent individual-reminder tracking:** Homework and exam AlarmManager reminder IDs are stored in separate SharedPreferences string-set registries when their PendingIntents are scheduled. Category cancellation removes both the alarm PendingIntent and its active notification, and then clears only that category registry. It uses the Room IDs as a second recovery path for alarms created before the registry existed or for entries no longer registered. Account purge clears the registries.
- **Race / restart safety:** Each category has a generation counter. checkAndSendReminders now captures settings and generations atomically under the same lock used by setting updates. Summary and individual notification posting, as well as creation of new individual alarms, check both the enabled preference and generation before performing the operation. The daily check also reconciles persisted OFF preferences after process recreation. A scheduled reminder cannot be created when its global notification type is OFF. Alarm broadcasts capture the category generation before asynchronous Room reads and pass that captured token to the final notification publisher, preventing an old broadcast from posting after a rapid OFF/ON transition.
- **Local automated verification:** NotificationToggleBehaviorTest 10/10 on Robolectric API 28 (including persisted OFF preference/process-recreation reconciliation and stale individual-broadcast OFF/ON race); NotificationDeliveryPolicyTest 2/2; existing AcademicReminderPolicyTest 3/3. Combined 15/15, with zero failures, errors, or skips. git diff --check passed.
- **Local build:** :app:assembleStagingDebug succeeded. Artifact app/build/outputs/apk/staging/debug/app-staging-debug.apk, 33,573,932 bytes, SHA-256 2FE0E0C405BF84C21F890C34884BBB1E93BCDA7D66244796BE58AB9E55DC0CB7. Build only; no app install or launch. Temporary Gradle memory/worker changes were restored and gradle.properties is unchanged.
- **Validation limits:** Robolectric used API 28 for the local run. Physical Android 10 and Android 15 validation, restart verification on devices, and QA settings-baseline restoration remain Phase 5. Do not claim actual device tests.
- **Exact-SHA GitHub CI:** commit `35763ee8b6e8faa217df51798fcb2ec7274841b9` ? Android workflow run `45021785196` completed success: unit tests, Staging Debug APK assembly, and Debug artifact upload passed. Signed Release steps were skipped. Server workflow run `45021785246` completed success: TypeScript typecheck, dependency security baseline, 42 Vitest files / 293 tests, and Wrangler Production + Staging dry-run validation passed. No deployment occurred.
- **Safety / governance:** no Worker deploy, secret changes, D1 migration apply, staging/production writes, Release signing, device install/launch, main change, PR merge, or new branch. Keep PR #6 open and Draft. Keep WebGPT task d8ff20f1-780c-4e55-acc5-ed2dfe8d71be RUNNING.
- **Next:** P2-03 implementation and exact-SHA CI are verified. Continue with P2-04 on work/release-readiness when asked. Keep actual Android 10/15 device and process-restart acceptance for Phase 5.


## 2026-11-22 - Phase 2 P2-04 implementation checkpoint

- **Base:** e9931427047e9a69e7dcf667b0412232f451bec2 on work/release-readiness; branch was synchronized and clean before this work. PR #6 was verified Open, Draft and unmerged; main remains untouched.
- **Scope:** P2-04 only - separate an exam's scheduled date from the student's personal review state, and stop counting elapsed dates as completed reviews.
- **Shared policy:** added ExamReviewPolicy. isReviewed requires both syncStatus == SYNCED and isCompleted == true. Monthly summary numerator counts confirmed completed rows only; denominator includes confirmed rows only. This also excludes FAILED, PENDING, SYNCING, LOCAL, and DELETE_PENDING defensively, preserving the P2-01 trust boundary.
- **UI:** monthly summary no longer uses isExamDatePassed to infer personal review. ExamDetailModal shows independent date status (passed/today/upcoming) and review status (reviewed/not reviewed). The explicit review action still toggles isCompleted.
- **Localization:** new schedule/review status labels, progress label, and month count format are available in Arabic and English.
- **Focused local verification:** ExamReviewPolicyTest 3/3 and ExamVisibilityPolicyTest 5/5 passed (8 tests total; zero failures, errors, or skips). Arabic and English string XML parsed successfully with no duplicate string keys. The old isCompleted || isExamDatePassed expression is absent from ExamsScreen.kt.
- **Local build:** :app:assembleStagingDebug passed. Artifact app/build/outputs/apk/staging/debug/app-staging-debug.apk, 33,576,366 bytes, SHA-256 53636AECC1F93AF92487618B2852FA22CD7AE9D90E402B3FCCCB3405DC074EDF. Build only; it was not installed or launched.
- **Code commit / push:** P2-04 implementation, tests, ROADMAP and SESSION_HANDOFF were committed together as 9e090054dfd2d0f9277a4ea9e4d6a208d40eb968 (fix: separate exam date from personal review status) and pushed to origin/work/release-readiness. Local HEAD and origin matched; the worktree was clean after push. git diff --check passed before commit.
- **Exact-SHA GitHub CI:** Android workflow run 45028829069 completed success on 9e090054dfd2d0f9277a4ea9e4d6a208d40eb968: testStagingDebugUnitTest passed (BUILD SUCCESSFUL, 1m 50s), :app:assembleStagingDebug passed (BUILD SUCCESSFUL, 35s), and Debug APK artifact upload passed. Release keystore/signing/build steps were skipped. Server workflow run 45028829155 completed success: TypeScript typecheck, dependency security baseline, 42 Vitest files / 293 tests, and Wrangler Production + Staging dry-run validations passed. No deployment occurred.
- **Phase 5 remains open:** reproduce QAORAL1 review cancellation on the actual Galaxy S9 Android 10 and Android 15 devices; validate post-ACK update, detail reopen, and process recreation. No physical device or emulator was used for this implementation.
- **Safety / governance:** no server code, Room schema, migration, Worker deploy, secrets change, D1 apply/write, staging/production write, release signing, app install/launch, main change, PR merge, or temporary branch. Keep PR #6 Open and Draft. Keep WebGPT task d8ff20f1-780c-4e55-acc5-ed2dfe8d71be RUNNING.
- **Next:** P2-04 code and exact-SHA Android/Server CI are verified. P2-05 implementation and local validation are recorded below; its exact-SHA CI is pending. Continue with P2-06 after P2-05 CI closeout. Keep QAORAL1 review-toggle, P2-05 visual range review, and Android 10/15 device/process-restart acceptance in Phase 5.


## 2026-11-22 - Phase 2 P2-05 implementation checkpoint

- **Base:** e866151cf60b6f34cddf571486369a0d051c90a4 on work/release-readiness; branch was synchronized and clean before edits. PR #6 was verified Open, Draft and unmerged; main remains untouched.
- **Scope:** P2-05 only - correct catch-up period date boundaries in WhatDidIMissScreen.
- **Policy:** MissedDateRangePolicy now resolves local calendar dates using Calendar and the active TimeZone. Yesterday means the previous local date only. Last 3 days means today plus two prior local dates. This week means Sunday through today. Last 2 weeks means 14 local dates including today. Since last visit still starts at the stored previous-visit date and ends today. Invalid or future stored visit dates fall back safely to today.
- **Timezone / DST:** the policy adds calendar days rather than subtracting fixed 24-hour milliseconds. Strict yyyy-MM-dd parsing and formatter round-tripping reject malformed values. The Compose screen resolves the range again on recomposition rather than pinning it with stale remember keys.
- **Localization:** range labels are now Android string resources in Arabic and English.
- **Focused local verification:** MissedDateRangePolicyTest 8/8, ExamReviewPolicyTest 3/3, and ExamVisibilityPolicyTest 5/5 passed (16/16 total; zero failures, errors, or skips). Coverage includes leap day, month/year rollover, week start, exact 14-day count, saved/future/invalid visit dates, one UTC instant across Los Angeles and Kiritimati, and the New York DST transition. Both string XML files parsed and contain no duplicate resource keys. git diff --check passed.
- **Local build:** :app:assembleStagingDebug passed. Artifact app/build/outputs/apk/staging/debug/app-staging-debug.apk, 33,578,884 bytes, SHA-256 FC1BB359FF6687094468CC0C7D50C0673C9DA992AD90F832CD28BF9967EE3B6C. Build only; not installed or launched.
- **Delivery / CI:** implementation, tests, ROADMAP and SESSION_HANDOFF are being committed together on work/release-readiness. Exact-SHA Android/Server CI is pending until push and must be verified before marking P2-05 automation closed.
- **Phase 5 remains open:** visually validate range boundaries using real class records and the configured device timezone. No emulator or physical device was used for this implementation.
- **Safety / governance:** no Worker/server changes, Room schema or migration, deploy, secrets change, D1 apply/write, staging/production write, Release signing, app install/launch, main update, PR merge, or new branch. Keep PR #6 Open and Draft. Keep WebGPT task d8ff20f1-780c-4e55-acc5-ed2dfe8d71be RUNNING.
- **Next:** finish P2-05 commit/push and exact-SHA Android/Server CI closeout, then continue with P2-06 on work/release-readiness. Keep real-device acceptance in Phase 5.
