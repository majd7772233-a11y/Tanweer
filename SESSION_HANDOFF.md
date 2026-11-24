# Tanweer — Session Handoff

## Current verified state — 2026-11-23 (supersedes older handoff below)

### Repository / PR
- Repository: `majd7772233-a11y/Tanweer`; working branch only: `work/release-readiness`.
- Latest pushed code commit: 3e3e42db82112332cf6c57ff2bbc143e13f370c2 (fix: keep official homework badge visible with long titles) on work/release-readiness; P2-10 through P2-19 are complete with exact-SHA Android + Server CI green; main is untouched.
- PR #6 is Open + Draft + unmerged, base `main`, head `work/release-readiness`. Keep it that way.
- WebGPT task token `d8ff20f1-780c-4e55-acc5-ed2dfe8d71be` must remain RUNNING; do not submit/close it unless the user expressly asks.

### P2-07 completed
- Source fix commit `bbeb3e95130cf099cd4dae34b2ea78c3f71ca745`; documentation closeout commit `995eeaf4bc35dac790a2020c01ca2390fc49d512`.
- Exact-SHA Android run `45041331045` and Server run `45041331041` both passed. Android covered unit tests, Staging Debug assembly and Debug artifact upload; Server covered audit, typecheck, 43 Vitest files / 295 tests and Wrangler Production + Staging dry-runs.
- Phase 5 real-device acceptance remains OPEN; no install/launch was performed.

### P2-08 implementation checkpoint — `cf310f0dae231260e63eb8951db9f81cc76036e3`
- Root cause: issue-card count comes from `cached_issues.commentsCount`; it was refreshed only by a successful details GET. The comment POST ACK marked the row `SYNCED` but did not refresh the parent count if the follow-up GET failed. The P2-07 detail reconciler also skipped the whole parent IssueEntity replacement while local mutation was pending, inadvertently leaving `commentsCount` stale.
- `IssueDao` now counts only `cached_issue_comments` rows with `syncStatus = 'SYNCED'`, and updates only the parent's `commentsCount`. Pending/failed local comments remain visible but are not included in the server-confirmed count.
- The same count reconciliation runs inside the direct add-comment ACK, retry ACK and Outbox ACK Room transactions. The shared detail snapshot reconciler recalculates count even when the issue's other fields are protected by a local-mutation guard.
- Existing list cards observe the Room-backed `IssueItem.commentsCount`; no manual screen reopen is required.
- Regression tests cover authoritative snapshot refresh, empty snapshot clearing confirmed count while preserving pending rows, and immediate ACK count refresh while parent issue state remains locally mutated.
- Local validation: 21 Android unit tests passed (0 failures/errors/skips); `:app:assembleStagingDebug` passed. APK `app/build/outputs/apk/staging/debug/app-staging-debug.apk`, 33,583,629 bytes; built only, not installed/launched.
- Exact-SHA CI: Android run `45045181237` passed unit tests, Staging Debug assembly and Debug APK upload. Artifact ID `14763174129`, name `Tanweer-debug-24cf6f16ebc9f3519c1a92272df6c351fa457706`, size 31,112,584 bytes, ZIP SHA-256 `4282ba97f6758c3706d21a7cc4de75abfac88d15d73459cf9d7543f1e9e8464f`. Server run `45045181236` passed dependency audit, TypeScript, 43 Vitest files / 295 tests and Wrangler Production + Staging dry-runs. An earlier docs-only SHA had transient MavenArtifactFetcher network setup failures, but the P2-08 exact-SHA run passed without source changes or test exclusions.


### P2-09 completed — exact-SHA Android + Server CI passed (07ccb85c55d92e8c3963c5bb523f0f8833fde5ac)
- The homework creation form now displays the study/assignment date separately from the due date. It initializes from the valid selected study date (including an intentional earlier date), and falls back to today only if no valid value exists.
- The date picker updates the form-local study date. “Use today for this homework” also changes only the form-local value; it does not overwrite the app-wide `selected_study_date` preference.
- Both normal homework and official teacher homework flows pass the explicit study date from the dialog to `TanweerViewModel.addHomework()`. Both repository paths (`addHomework` and `addHomeworkWithImages`) receive that value. Existing ViewModel callers remain compatible through a default parameter.
- Arabic and English strings added; resource XML validated and no duplicate keys found.
- Local verification: 22 Android unit tests across 7 test classes passed (0 failures/errors/skips); `:app:assembleStagingDebug` passed. APK `app/build/outputs/apk/staging/debug/app-staging-debug.apk`, 33,590,705 bytes, SHA-256 `4945E734A6C2E67E83A6F5E7ED4DA22CED96B560AD82D5711598B77B2279F8C0`; built only, not installed/launched.
- Exact-SHA Android CI 45048086662 passed unit tests, Staging Debug assembly (BUILD SUCCESSFUL in 2m 44s for tests and 49s for APK assembly), and Debug APK upload. Artifact ID 14763848718, name Tanweer-debug-79d852147c9027ff98be1d9ba2a5eb0da0363bb8, size 31,121,214 bytes, ZIP SHA-256 a45fa419bee786c79430570aad3084846bf5c1a158d934a3a63c64d363c13ac0.
- Exact-SHA Server CI 45048086552 passed dependency audit, TypeScript, 43 Vitest files / 295 tests, and Wrangler Production + Staging dry-runs. No deployment occurred.
- Documentation-only closeout SHA a667deb2976e1fe2070aab9aabd313a90bfbce3f also has green CI after retry: Server run 45050588860 passed; Android run 45050588857 initially hit 13/115 external MavenArtifactFetcher download setup failures, then its failed job was rerun unchanged and the full workflow passed.
- Phase 5 device acceptance remains open: verify the selected date in the form, manual date selection, form-local today reset, normal/official creation with and without images, and persisted Room/API values on real devices. None of those device checks was performed here.
### P2-10 completed — local verification and exact-SHA CI passed
- Root cause: LibraryScreen used book.subjectId.contains(selectedSubjectFilter), so biology also matched biology_act. The parent filter was using partial substring matching against canonical IDs.
- Added LibrarySubjectFilterPolicy with exact known-alias normalization and explicit parent/child relations: biology -> biology_act, chemistry -> chemistry_act, physics -> physics_act, english -> english_work. Exact match is the default; an opt-in Include sub-subjects chip appears only when related child books exist.
- LibraryScreen groups subject chips and computes direct counts using canonical identifiers. The child toggle resets when the grade, selected subject, or All chip changes. Arabic and English label resources were added.
- Five new policy tests cover exact matching, explicit child inclusion only, child-to-parent exclusion, full aliases, and available-child counts. Combined focused Android run: 32/32 across 9 report files, zero failures/errors/skips. Staging Debug build passed.
- Local APK app/build/outputs/apk/staging/debug/app-staging-debug.apk: 33,593,426 bytes; SHA-256 2C24E82251CD84C95EF4C7E8B95589E8DB9567065F2532644B3DCC2F42849E0B. Built only; not installed or launched.
- Exact-SHA CI is green for P2-10 commit 65ee0bf19d6735ca3a76e59cc21df215795ef2e8: Android run 45146557597 passed unit tests, Staging Debug APK assembly and artifact upload (signed Release steps skipped); Server run 45146557390 passed dependency audit, TypeScript, 43 Vitest files / 295 tests, and Wrangler Production + Staging dry-runs. Android artifact 14803404358: 31,125,572 bytes; ZIP SHA-256 507e246e0535011648739eb509ef51068ee1c3048066bfa9bd97312e43cac2f9. No deployment occurred.
- Phase 5 acceptance remains open: visually verify the exact parent filter, opt-in child inclusion, reset behavior, counts and independent search on a real device. No emulator or phone was used.

### P2-11 completed — local verification and exact-SHA Android + Server CI passed
- Root cause and scope are recorded in ROADMAP.md under P2-11: structured identifier queries such as `QA_HW_20261119_A` previously matched any substring token, including `QA` embedded inside unrelated identifiers.
- `ArabicSearchEngine.kt` now recognizes compact 3+ segment alphanumeric IDs separated by `_` or `-` when at least one segment contains a digit. Every normalized segment, including one-character tokens, must exist as a complete candidate token; contiguous full ID receives highest ranking. Natural-language queries retain previous partial-token behavior.
- Added `ArabicSearchEngineIdentifierQueryTest`: 5 regression tests for complete IDs, false positives (`QATEST2`, `SYNCQA_ISSUE_20261118_A1`), distributed exact segments, all-single-character IDs (`A_B_C_1`), and natural-language compatibility. Together with `PhaseFourUiPolicyTest`, 29 tests across 2 XML reports passed with zero failures/errors.
- `:app:assembleStagingDebug` passed. APK: 33,593,426 bytes; SHA-256 `0765538BEE3FD3EEE3F957335594384B8E40B977CCC0FD5350EACCEBD25E167C`. It was built only, not installed or launched.
- P2-11 was committed and pushed as `5c661e85cf352eb869eff64e2d27208419e69aa7`. Exact-SHA Android run `45148258115` passed unit tests, Staging Debug assembly and Debug artifact upload (signed Release steps skipped); Server run `45148258094` passed dependency audit, TypeScript, 43 Vitest files / 295 tests and Wrangler Production + Staging dry-runs. Android artifact `14804897617`: 31,126,813 bytes, ZIP SHA-256 `8943bb0e357e72904a6530c42a4e6c2698042c912b75d8d058b0b9add62125d2`. No deployment occurred.
- PR #6 remains Open + Draft + unmerged; `main` is untouched. The WebGPT task token must stay RUNNING. Phase 5 real-device acceptance remains open; no emulator/phone, deployment, migration, D1 write, secret change, or release.
### P2-12 completed — local verification and exact-SHA Android + Server CI passed
- Root cause: `KnowledgeHomeworkCard` stripped all non-digit characters from `pageNumbers`, turning `10-12` into `1012` and selecting PDF page index 1011.
- Added `HomeworkPageReferencePolicy` / `HomeworkPageReference`. Supports single pages, ordered ranges with hyphen/en dash/em dash and whitespace, Arabic-Indic/Persian digits, and explicit Arabic/English labels. Details fallback is allowed only with an explicit page label; malformed, reversed, zero, overflow and ambiguous values produce no page action.
- `KnowledgeHomeworkCard` now displays a range such as `الصفحات: 10–12`; its explicit action opens the first page of the range (10), using zero-based index 9. Single pages remain direct navigation.
- Added `HomeworkPageReferencePolicyTest`: 8/8 passed; combined P2-10/P2-11/P2-12 regression run: 69/69 tests across 12 XML reports, zero failures/errors/skips. `:app:assembleStagingDebug` passed; APK 33,593,388 bytes, SHA-256 `3DAEF25143B1BC0A1415242C666AA0030BD7B7FB8A19F016BB5033DF00143F1B`. Built only; not installed/launched.
- P2-12 was committed and pushed as `881a532902dcd7d89383d453823ecafbd41692b6`. Exact-SHA Android run `45150149560` passed tests, Staging Debug build and artifact upload after two unchanged attempts failed due transient `MavenArtifactFetcher`/Robolectric HTTP download errors in `NotificationToggleBehaviorTest`; the third unchanged retry passed. Server run `45150150093` passed dependency audit, TypeScript, unit/integration tests, Wrangler Production bundle and migration file listing. Artifact `14806154824`: 31,128,739 bytes, ZIP SHA-256 `9ac7749df06d3c1ebf339b5b0ea072a313af01228c3513d316bb425f874e2d42`. No deployment.
- P2-11 exact-SHA CI is green on commit `5c661e85cf352eb869eff64e2d27208419e69aa7` (Android `45148258115`, Server `45148258094`). PR #6 remains Open + Draft + unmerged; `main` is untouched. WebGPT task must remain RUNNING.
- Phase 5 real-device PDF validation remains open. The report's 52.9 MB Quran PDF was not fully downloaded during evidence capture; no claim is made that it was opened. No emulator/phone, deployment, migration, D1 write, secret change or release.
### P2-13 completed — local verification and exact-SHA Android + Server CI passed
- Root cause: `TanweerViewModel.closePdfReader()` always returned to `SubScreen.LIBRARY`, discarding the route from which the PDF was opened.
- Added `PdfReaderReturnDestination` and `PdfReaderNavigationPolicy` to capture/resolve source sub-screen, active tab, and selected Knowledge Base subject. `openBookInPdfReader()` captures only on the first entry; closing restores the exact route and triggers the corresponding tab/sub-screen refresh. User-scope reset discards any pending origin.
- General Library remains the return destination when the reader originated there; Knowledge Base returns to the same subject; Search/other sub-screens and root tabs are also retained. Missing/invalid origin safely falls back to Library.
- Added `PdfReaderNavigationPolicyTest` (5 tests). Combined P2-10/P2-11/P2-12/P2-13 regression run passed 74/74 tests across 13 XML reports, zero failures/errors/skips. `:app:assembleStagingDebug` passed; APK 33,593,388 bytes; SHA-256 `11C04F8396BB14E558B8B4C8515611C356433CA4C0A3B44E0A8A3B9F3AD7B8D8`. Built only; not installed/launched.
- P2-13 was committed and pushed as `f9808b27a42ee46e0f02a916d8486e2e1f06b78b`. Exact-SHA Android run `45152437101` passed unit tests, Staging Debug assembly and artifact upload (signed Release steps skipped); Server run `45152437077` passed dependency audit, TypeScript, tests, Wrangler bundle validation and migration listing. Android artifact `14806578590`: 31,130,609 bytes, ZIP SHA-256 `dc8b7fbfca631e8700e1871368849c26769ce47e43dba0538e7e7957ff50e0c6`. Android first attempt failed on transient Maven/Robolectric HTTP download; unchanged retry passed. No deployment.
- P2-12 exact-SHA CI is green on `881a532902dcd7d89383d453823ecafbd41692b6` (Android `45150149560`, Server `45150150093`). P2-13 exact-SHA CI is green on `f9808b27a42ee46e0f02a916d8486e2e1f06b78b` (Android `45152437101`, Server `45152437077`). The unchanged Android third retry passed after two transient Maven/Robolectric download failures; no tests were skipped.
- PR #6 remains Open + Draft + unmerged, base `main`; `main` is untouched. WebGPT task must remain RUNNING. Phase 5 real-device acceptance remains open; no emulator/phone, deployment, migration, D1 write, secret change, or release.
### P2-14 completed — local verification and exact-SHA Android + Server CI passed
- Root cause: `LaunchedEffect(currentPageIndex, downloadState)` saved the reader state only when the four-second dwell added a new unique page. Revisiting an already-counted page left Room `lastPage` stale; share/external Activity paths lacked an awaited checkpoint.
- `PdfReaderScreen.kt` now captures an immutable `ReadingStateCheckpoint` on each page transition once the PDF is Ready, independently of the unique-page dwell counter. A per-book `Mutex` serializes writes to Room.
- `ON_PAUSE` adds a backup save. Markdown-share and page-snapshot Sharesheet launch only after the Room write completes; system Back and the top-bar Back both save before closing/returning.
- `ReadingStateCheckpointPolicyTest`: 4/4 passed. Combined P2-10 through P2-14 regression run: 78/78 tests across 14 XML reports, zero failures/errors/skips. Staging Debug passed; APK 33,593,388 bytes, SHA-256 `D29B11914470A829DBEAB93E453860F16685F4986936A37DADEF3AB31D17622F`. Built only; not installed or launched.
- P2-14 was committed and pushed in `c82fa87bee2cb796d7221e4701045f4e5df48f01`. Exact-SHA Android run `45155026400` passed unit tests, Staging Debug APK assembly and artifact upload (signed Release steps skipped); Server run `45155027790` passed dependency audit, TypeScript, unit/integration tests, Wrangler Production bundle and migration file listing. Android artifact `14810097213`: 31,138,690 bytes; ZIP SHA-256 `55c477bef8ca9985d450bd50897b980bd86a23e9d3e006fba3d5375bc22fa71d`. Two unchanged Android attempts hit transient Maven/Robolectric HTTP download failures; the third passed. No deployment.
- P2-14 exact-SHA CI is green on `c82fa87bee2cb796d7221e4701045f4e5df48f01` (Android `45155026400`, Server `45155027790`). PR #6 remains Open + Draft + unmerged, base `main`; `main` untouched. WebGPT task must remain RUNNING. Phase 5 device acceptance remains open; no emulator/phone, deployment, migration, D1 write, secret change or release.
### P2-15 completed — local verification and exact-SHA Android + Server CI passed
- Root evidence: the QA report showed duplicate-looking page notes and a newly saved note disappearing after reopening. The report did not isolate the original Room write that disappeared. The current code eliminates the unsafe paths without claiming a single unproven historical cause.
- Old IDs combined book + page + `currentTimeMillis` and the DAO used `REPLACE`, allowing a primary-key collision to silently replace a row. The DAO now orders notes deterministically and uses `ABORT`; a new `getNoteById` lookup supports retry-safe reconciliation.
- Note writes use a stable per-draft UUID. Repository insertion is idempotent for the same ID + book + page + normalized text, checks concurrent insert races, and rejects reuse for a different payload. `BookNoteFlowConfirmation` waits for the exact ID to appear in Room Flow.
- The sheet no longer memoizes filtering against the mutable list identity. It renders one row per ID; save input is cleared only after the exact ID reaches both Room Flow and the UI snapshot list. On error, text and request ID remain for retry. Exam-question tags use the same confirmation path and no longer add optimistic local duplicates.
- Added `BookNoteFlowConfirmationTest`: 6/6 passed. Combined P2-10 through P2-15 regression run passed 84/84 tests across 15 XML reports, zero failures/errors/skips. `:app:assembleStagingDebug` passed; APK 33,593,388 bytes; SHA-256 `DF8F306E17284611DE6A10ADF554A5DDB8273B13091E9ADBB399FB401BC89ECE`. Built only; not installed or launched.
- P2-15 was committed and pushed as `fb94c268f972774c5802e28a47ceb02a838800e9`. Exact-SHA Android run `45169890757` passed unit tests, Staging Debug APK assembly and Debug artifact upload (signed Release steps skipped); Server run `45169891075` passed dependency audit, TypeScript, tests, Wrangler Production bundle validation and migration listing. Android artifact `14816771152`: 31,149,020 bytes; ZIP SHA-256 `c129e6faeafd063c574538193212c07e5558fc8c370485d497663d304d11e18d`. No deployment.
- P2-15 exact-SHA CI is green on `fb94c268f972774c5802e28a47ceb02a838800e9` (Android `45169890757`, Server `45169891075`). PR #6 remains Open + Draft + unmerged, base `main`; `main` untouched. WebGPT task must remain RUNNING. Phase 5 real-device acceptance remains open; no emulator/phone, deployment, migration, D1 write, secret change or release.
### P2-16 completed — local verification and exact-SHA Android + Server CI passed
- Root cause: `.pointerInput(isDrawingMode)` held the initial `isHighlighter`, pen color and width closure while the drawing gesture handler remained alive. Toolbar changes did not recreate the handler, so `onDragEnd` could persist old brush values after the button visibly changed.
- `CanvasPageCurlViewer` now reads the latest tool values through `rememberUpdatedState` without restarting/cancelling the active pointer handler. At each `onDragStart`, `DrawingStrokeStylePolicy` captures one immutable style snapshot; `onDragEnd` saves that stroke with the snapshot, then clears it. Drag cancellation also clears the snapshot.
- Normal pen preserves the selected color/width and `isHighlighter=false`; highlighter preserves its 0.38 alpha, 2.6 width multiplier and `isHighlighter=true`. Existing drawing rows are not rewritten or cleared.
- Added `DrawingStrokeStylePolicyTest` (4 tests). Combined P2-10 through P2-16 regression run passed 88/88 tests across 16 XML reports, zero failures/errors/skips. `:app:assembleStagingDebug` passed; APK 33,593,388 bytes; SHA-256 `74FEE804F28B55BC1752596D4DCD8C635754672BB3E282807A3A1F94F3EB99E6`. Built only; not installed/launched.
- P2-16 was committed and pushed as `4529b6bfa7ddef7a6a175a536db0b57cdda26854`. Exact-SHA Android run `45171486991` passed unit tests, Staging Debug APK assembly and Debug artifact upload (signed Release steps skipped); Server run `45171486969` passed dependency audit, TypeScript, unit/integration tests, Wrangler Production bundle validation and migration listing. The first unchanged Android attempt failed one `NotificationToggleBehaviorTest` Maven/Robolectric HTTP download; the unchanged retry passed, with no skipped tests. Android artifact `14818141413`: 31,151,295 bytes; ZIP SHA-256 `09a74f4162144e309acc0de42acf46104357af7c3095f22f36d119fb79fb4e56`. No deployment.
- P2-16 exact-SHA CI is green on `4529b6bfa7ddef7a6a175a536db0b57cdda26854` (Android `45171486991`, Server `45171486969`). PR #6 remains Open + Draft + unmerged, base `main`; `main` untouched. WebGPT task must remain RUNNING. Phase 5 drawing validation remains open; preserve the page-4 drawing. No emulator/phone, deployment, migration, D1 write, secret change or release.
### P2-17 completed — local verification and exact-SHA Android + Server CI passed
- The old `unique_pages_<book>` set accumulated page indices across sessions/days but was used for the daily goal and velocity over the current session. Automatic scroll also fed that same dwell counter, allowing an autoplay transition to be presented as human reading.
- Kept the legacy lifetime preference key and its contents. New daily human page sets use a separate `daily_human_unique_pages_<book>_<local-date>` key. Session-human, daily-human, lifetime-recorded and auto-scrolled page sets are now independent. The old lifetime count is explicitly labelled as historical record, not today's human progress.
- Manual next/previous, page bar/slider, thumbnails, bookmarks and jump-to-page actions all use a shared navigation source. Human pages count only after the same page remains visible for four seconds. Auto-scroll transitions are recorded only in the separate auto set and do not affect today's goal or human velocity. Manually reselecting the current auto-visited page or stopping auto-scroll switches provenance back to human and starts a fresh dwell.
- The general session stopwatch remains available; a separate human-reading elapsed counter follows the latest visit source without restarting its ticker. Velocity uses session-human unique pages divided by human-reading elapsed seconds. Date-keyed daily statistics refresh when local date rolls over while the reader remains open.
- Added Arabic/English labels for daily goal, session-human pages, auto-scrolled pages, historical pages and human reading speed. Scope is explicit so the legacy 83-page history is not mistaken for today's reading.
- `ReadingPageActivityPolicyTest`: 8/8 passed. Combined P2-10 through P2-17 regression run passed 96/96 tests across 17 XML reports, zero failures/errors/skips. `:app:assembleStagingDebug` passed; APK 33,593,388 bytes; SHA-256 `33FC90625C9F96E6676762F171A9741CD28084CA2C656C7B801BE3D5FFAC82CB`. Built only; not installed or launched.
- P2-17 was committed and pushed as `c5222c697f0792c2350dede92d7fa137be3d892e`. Exact-SHA Android run `45179544460` passed unit tests, Staging Debug APK assembly and Debug artifact upload (signed Release steps skipped); Server run `45179544432` passed dependency audit, TypeScript, unit/integration tests, Wrangler Production bundle validation and migration listing. Android artifact `14821150635`: 31,159,967 bytes; ZIP SHA-256 `eae1c9aef9df3e95545d3ac1981316a62ab7d90c39b1e7f2cf98756727d663f2`. No deployment.
- P2-17 exact-SHA CI is green on `c5222c697f0792c2350dede92d7fa137be3d892e` (Android `45179544460`, Server `45179544432`). PR #6 remains Open + Draft + unmerged; `main` untouched. WebGPT task must remain RUNNING. Phase 5 must verify manual dwell, repeated pages, auto-scroll isolation, stopping/reselecting an auto-visited page, speed denominator and date rollover while preserving the legacy cumulative history. No emulator/phone, history reset, deployment, migration, D1 write, secret change or release.
### P2-18 completed — local verification and exact-SHA Android + Server CI passed
- P2-18 code commit `32a26bc8fdb5438a12b72bb0a431d2ea0d1a6907` is pushed to `work/release-readiness`; working tree clean. `main` is untouched.
- Replaced direct raw display of `event.category`, `exam.examType` and URI-encoded `requiredChapters` with centralized presentation-only formatters. Shared mappings cover Events, Calendar, Exams, Academic History, Class Knowledge Base, Teacher Dashboard, Search and What Did I Miss.
- Event categories supported in display: ACTIVITY, COMPETITION, TRIP, ANNOUNCEMENT, HOLIDAY, and historical CELEBRATION/WORKSHOP. Supported exam labels: MONTHLY, ORAL, MIDTERM and FINAL. Retired QUIZ maps to MONTHLY for historical display only; no Quiz option or label was added.
- Chapter formatter safely decodes encoded delimiters and presents CH1/CHAPTER_2 and Final_Review as localized labels. Unknown chapter names are humanized/preserved; literal plus signs and malformed URI sequences are handled safely.
- Room/API source values and outbox/payload values remain unchanged. No model conversion, schema migration, D1 mutation or server change was made.
- `ApiDisplayValuePolicyTest`: 8/8 passed. Combined Android regression run passed 104/104 tests across 18 XML reports, with zero failures/errors/skips. `:app:assembleStagingDebug` passed; APK 33,593,534 bytes; SHA-256 `1938BE5841C2B44138BC7908742E9F5C469BDD5EFC5866D432AEDC3C604CB4FC`. Built only; not installed or launched.
- Exact-SHA Android run `45197774477` passed unit tests, Staging Debug APK assembly and Debug artifact upload (signed Release steps skipped); Server run `45197774142` passed dependency audit, TypeScript, unit/integration tests, Wrangler Production bundle validation and migration listing. Artifact `14830490875` (`Tanweer-debug-be04c523d584df2edab2f6c347bd754113ab0247`): 31,168,375 bytes; ZIP SHA-256 `d8d9aed320fca892adf2254a7b10ae11e4d114837ef88db4cfc7daaf8be358db`. No deployment.
- P2-18 exact-SHA CI is green on `32a26bc8fdb5438a12b72bb0a431d2ea0d1a6907` (Android `45197774477`, Server `45197774142`). Phase 5 must verify Arabic/English enum labels, encoded chapter sample `CH1%2C_CH2%2C_Final_Review`, unknown/malformed values, legacy QUIZ-to-MONTHLY display, and unchanged Room/API payload values. PR #6 remains Open + Draft + unmerged; `main` untouched. WebGPT task must remain RUNNING. No emulator/phone, deployment, migration, D1 write, secret change or release.
### P2-19 completed — local verification and exact-SHA Android + Server CI passed
- P2-19 code commit `3e3e42db82112332cf6c57ff2bbc143e13f370c2` is pushed to `work/release-readiness`; working tree clean. `main` is untouched.
- Root cause: long subject/title text and both metadata badges shared one horizontal Row without a width constraint. The official badge could be pushed outside the visible card; no evidence showed that `isOfficial` or sync mapping was wrong.
- Extracted `HomeworkCardHeading` into `app/src/main/java/com/magd/tanweer/ui/components/HomeworkCardHeading.kt`. Title has the remaining weighted width and soft-wraps; task type and official badge use independent full-width rows below it. Official badge remains conditional on `homework.isOfficial` only.
- Added localized Arabic/English `homework_official_badge` resources. `HomeworkScreen.kt` no longer hardcodes this label.
- Compose UI regression `HomeworkOfficialBadgeUiTest`: long Arabic title, 220dp width, RTL and 1.3333 font scale; title and official badge both assert displayed. 1/1 passed.
- Combined P2-10 through P2-19 targeted regression run: 105/105 tests across 19 XML reports, zero failures/errors/skips; P2-18 policy test still 8/8. `:app:assembleStagingDebug` passed; APK 33,591,486 bytes; SHA-256 `035641F7CB4755395EE6D534FCBF6BAD3D0B7DDFFF2B7C8A3610CB75DADA121D`. Built only; not installed or launched.
- Exact-SHA Android run `45206925012` passed unit tests, Staging Debug APK assembly and Debug artifact upload (signed Release steps skipped); Server run `45206925082` passed dependency audit, TypeScript, unit/integration tests, Wrangler Production bundle validation and migration listing. Artifact `14835620500` (`Tanweer-debug-8339dc661e6319ff8213b41be0c9c58c54ee7da2`): 31,171,031 bytes; ZIP SHA-256 `24e135a5a0619c7c4a234a00b6b5439e5014f5d8cb7adee2b159f7712e6626b8`. No deployment.
- P2-19 exact-SHA CI is green on `3e3e42db82112332cf6c57ff2bbc143e13f370c2` (Android `45206925012`, Server `45206925082`). Phase 5 must verify the reported Room `isOfficial=1` + `SYNCED` row after refresh/reopen and test long Arabic titles at RTL/large font/narrow width; confirm `isOfficial=0` items do not show the badge. No Room/API mapper, server or database changes; no emulator/phone, deployment, migration, D1 write, secret change or release. PR #6 remains Open + Draft + unmerged. WebGPT task remains RUNNING.
--- Historical handoff snapshot (2026-11-17; superseded by the current section above) ---

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
- **Code commit / push:** P2-05 implementation, tests, ROADMAP and SESSION_HANDOFF were committed together as e217ff83ca8f3bb2c2b6fd5d6b05df891985f149 (fix: use exact calendar ranges for catch-up filters) and pushed to origin/work/release-readiness. Local HEAD and origin matched; the worktree was clean after push. git diff --check passed.
- **Exact-SHA GitHub CI:** Android workflow run 45032029810 succeeded after rerunning its failed job. The first attempt hit transient Robolectric MavenArtifactFetcher IOExceptions while downloading Android artifacts during 12 class initializations; the retry completed testStagingDebugUnitTest successfully (BUILD SUCCESSFUL, 1m 48s), :app:assembleStagingDebug successfully (BUILD SUCCESSFUL, 32s), and Debug APK upload succeeded (artifact ID 14758635756). Signed Release steps were skipped. Server workflow run 45032029839 succeeded: TypeScript typecheck, dependency security baseline, 42 Vitest files / 293 tests, and Wrangler Production + Staging dry-run validations passed. No deployment occurred.
- **Phase 5 remains open:** visually validate range boundaries using real class records and the configured device timezone. No emulator or physical device was used for this implementation.
- **Safety / governance:** no Worker/server changes, Room schema or migration, deploy, secrets change, D1 apply/write, staging/production write, Release signing, app install/launch, main update, PR merge, or new branch. Keep PR #6 Open and Draft. Keep WebGPT task d8ff20f1-780c-4e55-acc5-ed2dfe8d71be RUNNING.
- **Next:** P2-05 implementation and exact-SHA Android/Server CI are verified. Continue with P2-06 on work/release-readiness when requested. Keep visual range acceptance and real-device acceptance in Phase 5.

## 2026-11-22 - Phase 2 P2-06 implementation checkpoint

- **Base:** e807b494aff7f31dbeee1c37c00da9276d2e270f on work/release-readiness; local branch and origin matched at the base before implementation. PR #6 was rechecked Open, Draft and unmerged; main remains untouched.
- **Scope:** P2-06 only - stop the Calendar day-details panel remaining on the prior month after month navigation, and make the Calendar summary cards reflect the displayed month rather than global group list sizes.
- **Month selection:** Added CalendarMonthPolicy.resolveSelectedDate. The shared previous/next month handler sets day-of-month to 1 before shifting months (avoids end-of-month Calendar rollover), retains the selected day where possible, clamps it to the target month's last day, and updates both Compose selection and the ViewModel selected date. The existing selected-date effect then reloads details for the new month date.
- **Monthly counters:** Exam/event totals are filtered by examDate/eventDate. Homework, project and unknown assignment totals are filtered by studyDate OR dueDate and deduplicated within the month. An assignment whose study date and due date cross months counts in each month where its day marker is visible, but never twice in the same month. Existing authoritative calendar snapshot gating and HomeworkTaskTypePolicy classification remain in force.
- **Policy tests:** Added CalendarMonthPolicyTest 7/7 covering year/month rollover, leap/non-leap February, short-month clamping, invalid selection/month/date strings, cross-month assignment dates and no same-month double count.
- **Focused local verification:** CalendarMonthPolicyTest 7/7, HomeworkTaskTypePolicyTest 4/4, MissedDateRangePolicyTest 8/8, ExamReviewPolicyTest 3/3, ExamVisibilityPolicyTest 5/5; 27/27 tests total, zero failures/errors/skips. git diff --check passed.
- **Local build:** :app:assembleStagingDebug succeeded. APK app/build/outputs/apk/staging/debug/app-staging-debug.apk, 33,583,340 bytes; SHA-256 FAC6B0609DDC8750AEB95D8FC43B47E1C04EC5A03DEB00A865572263D3DF2E32. Build only; not installed or launched.
- **Code commit / push:** Implementation, tests, ROADMAP and SESSION_HANDOFF committed together as a42983cf0c1a1ef8010b323b5f119746623ddfde (fix: keep calendar selection and counts month-scoped), pushed to origin/work/release-readiness. Local and remote branch tips matched; worktree was clean after push.
- **Exact-SHA GitHub CI:** Android run 45034477749 passed on attempt 2. The first attempt had one infrastructure-only Robolectric setup failure in NotificationToggleBehaviorTest (MavenArtifactFetcher.java:129, IOException from HttpURLConnection while downloading an Android test artifact); re-running the failed job succeeded without source changes or skipped tests. Attempt 2 unit tests passed, :app:assembleStagingDebug passed (48s), and Debug APK upload passed: artifact ID 14759446663, name Tanweer-debug-85d45f6e1c04473a700c7fb11389a4298cb76931, size 31,091,107 bytes, ZIP SHA-256 b78e34736549241360a3bc399ac83d9c14d8e238e42ac4b6a0f50ffc1bc6c427. Signed Release steps were skipped.
- **Exact-SHA Server CI:** Run 45034477898 passed: npm audit baseline, TypeScript typecheck, 42 Vitest files passed, Wrangler Production and Staging dry-run validations passed. No Worker deployment or database migration/write occurred.
- **Phase 5 remains open:** Visually validate selected-day details and per-month exam/event/assignment counters using actual November/December 2026 and January 2027 class data and the configured device timezone. No emulator or physical device was used.
- **Safety / governance:** No backend/API contract, Room schema, migration, Worker/server source, deployment, secrets change, D1 apply/write, Staging/Production database write, Release signing, app install/launch, main update, PR merge or new branch. PR #6 remains Open + Draft. WebGPT task d8ff20f1-780c-4e55-acc5-ed2dfe8d71be remains RUNNING until the user explicitly asks to close it.
- **Next:** P2-06 implementation and exact-SHA Android/Server CI are verified. Continue with P2-07 when requested. Keep Phase 5 UI/device acceptance open.
## 2026-11-22 - Phase 2 P2-07 implementation checkpoint

- **Base:** e80f5e43479d78cb503b2532dbfdb4f4722575d1 on work/release-readiness; the branch was clean and equal to origin at the base. PR #6 remained Open + Draft + unmerged; main was untouched.
- **Scope:** P2-07 only - make issue statuses consistent across the active/resolved filters, issue cards, detail header and search; refresh Room/StateFlow immediately after a confirmed teacher best-answer or comment ACK; retain is_teacher_answer end-to-end.
- **Shared status policy:** Added IssueStatusPolicy and IssueFilter. OPEN and IN_DISCUSSION are both included in Discussing; SOLVED and VERIFIED are included in Resolved; CLOSED is separate; unknown values are visible under All. Filters, list cards, detail header and search all use this mapping and localized Arabic/English labels.
- **State consistency:** Added IssueDataSyncCoordinator, a process-wide issue mutex and one shared issue-details snapshot reconciler. Repository list/details reads and comment/best-answer writes, as well as UnifiedMutationQueue offline comment retries, share the same lock. Confirmed best-answer ACK updates SOLVED and then refreshes authoritative issue/comments. Confirmed comment ACK updates OPEN to IN_DISCUSSION, persists the teacher-answer marker, and refreshes authoritative comments/count. A subsequent details failure is best-effort and does not roll back an acknowledged write.
- **Room reconciliation:** Authoritative snapshots replace synced comments, retain unacknowledged local comments (PENDING, SYNCING, FAILED, LOCAL, DELETE_PENDING), and replace a matching pending ID with the server-confirmed version. The parent IssueEntity.commentsCount is reconciled from the authoritative comments list.
- **Teacher-answer marker:** Server issue-details SELECT includes is_teacher_answer; mapIssueComment emits isTeacherAnswer; GenericResponse and IssueCommentItem carry the POST/detail field; IssueCommentEntity persists it; detail UI displays a separate teacher-answer badge on both accepted and non-accepted answers. The offline queue runs the same ACK confirmation and locked detail reconciler as the online repository path.
- **Room migration:** Room schema version 17; MIGRATION_16_17 adds isTeacherAnswer INTEGER NOT NULL DEFAULT 0. The migration test inserts an existing issue comment in the legacy schema and verifies it survives with the marker defaulted to false.
- **Focused Android verification:** IssueStatusPolicyTest 4/4; IssueCommentReconciliationTest 3/3; DatabaseMigrationTest 1/1; PendingSnapshotRetentionTest 4/4; ExamReviewPolicyTest 3/3; ExamVisibilityPolicyTest 5/5. Total 20/20, zero failures/errors/skips. :app:assembleStagingDebug passed.
- **Server verification:** npm run types passed; npm test -- --reporter=dot passed 43 Vitest files / 295 tests, including mapper and issue-details contract regression for isTeacherAnswer.
- **Local APK (after duplicate R-import correction):** app/build/outputs/apk/staging/debug/app-staging-debug.apk, 33,583,629 bytes, SHA-256 6CEB9E1C08373AF8A6FED6BBA8F4706A8A0908120BBAFB459DAAA77017076E99. Built only; not installed or launched.
- **Initial P2-07 implementation / CI diagnosis:** Initial implementation source commit 50c05318cb3f9248edf73cf41a27f72661a69beb was pushed to work/release-readiness. Android PR run 45040453269 failed during Kotlin compilation because IssuesScreen.kt imported com.magd.tanweer.R twice (lines 3 and 35). This was a source import defect, not an external dependency/network error. Server PR run 45040453287 passed npm audit, TypeScript, 43 Vitest files / 295 tests, and Wrangler Production + Staging dry-runs.
- **Corrective source commit / push:** Removed only the duplicate leading R import while retaining the original app R import; updated ROADMAP and SESSION_HANDOFF; committed as bbeb3e95130cf099cd4dae34b2ea78c3f71ca745 (fix: remove duplicate issue screen resource import) and pushed. Local branch and origin match.
- **Final exact-SHA Android CI:** Run 45041331045 PASS on bbeb3e95130cf099cd4dae34b2ea78c3f71ca745: unit tests passed, Staging Debug assemble passed (48s), and Debug APK upload succeeded. Artifact ID 14760929667, name Tanweer-debug-aefa432b2eeea54a0d3919f9217cab0e33eeaeb7, size 31,110,581 bytes, ZIP SHA-256 1ed639b2f64152398d2689b563d9db99b85c9566ea989354f23d6058756d8c32. Signed Release steps were skipped.
- **Final exact-SHA Server CI:** Run 45041331041 PASS on the same SHA: npm audit baseline, TypeScript typecheck, 43 Vitest files / 295 tests, Wrangler Production and Staging dry-run validations. No Worker deployment or database migration/write occurred.
- **Phase 5 remains open:** Visually validate filters, status labels, teacher-answer badge, accepted-answer detail refresh and offline-comment reconnect on real teacher/student accounts and supported Android versions. No emulator or physical device was used.
- **Safety / governance:** No Worker deployment, Cloudflare secret change, D1 migration apply/write, staging/production write, Release signing, app install/launch, main update, PR merge or temporary branch. Room migration is code-only and has not been applied to any real user database. PR #6 remains Open + Draft. WebGPT task d8ff20f1-780c-4e55-acc5-ed2dfe8d71be remains RUNNING until the user explicitly asks to close it.
- **Next:** P2-07 source implementation and exact-SHA Android/Server CI are verified on bbeb3e95130cf099cd4dae34b2ea78c3f71ca745. Continue with P2-08 when requested; keep the P2-07 filter/status/teacher-answer visual and real-device acceptance open in Phase 5.

## 2026-11-23 - Phase 2 P2-20 implementation checkpoint
- **Base / source commit:** started from `9417436` (P2-19 exact-SHA CI documentation) on `work/release-readiness`; P2-20 source and docs committed as `8b425ef589b64bc58e2d2b09f79bd64249f0b5a5` (`fix: clarify academic archive scopes`) and pushed. `git ls-remote` confirmed `origin/work/release-readiness` matches. The worktree is clean. PR #6 remains Open + Draft + Unmerged; `main` is untouched.
- **Scope:** clarify the scope distinction between the personal achievements view and the selected-group annual archive; do not merge counters or modify data.
- **Personal scope:** account-wide historical archive flows across the groups available to the account. Personal lesson cards filter by the current user's creator ID/name. Completed-homework count comes from the current user's completion rows. The annual total still describes all available archive records across those groups and may include records created by other members.
- **Selected-group scope:** only the selected active group's contents, homeworks and exams; counts include records from all group members and exclude other groups.
- **UI:** added `AcademicArchiveScopeInfo` below the mode selector, dynamically displaying Arabic/English explanations for personal vs selected-group mode. Localized the compact summary pill and made the third personal-view metric explicitly say homework totals span groups.
- **Tests:** `AcademicArchiveScopeInfoUiTest` final 4/4: Arabic personal/group tests explicitly inject Arabic configuration + RTL; English personal/group tests explicitly inject `en-US` + LTR. No skipped or failing P2-20 tests.
- **Full local Windows verification:** `:app:testStagingDebugUnitTest` reported 231 tests across 51 XML reports: 230 passed, 1 failed, zero errors/skips. The only failure is the documented Windows/Robolectric `StudentNotebookManagerTest.valid notebook records round trip through atomic storage` (two consecutive saves expected `[lesson-2, lesson-1]`, actual restored `[lesson-1]`). It remains unchanged and was not skipped; prior P1 notes document that the API34 test passes on Ubuntu Android CI.
- **Build:** `:app:assembleStagingDebug` passed after the final resource and test edits. APK `app/build/outputs/apk/staging/debug/app-staging-debug.apk`, 33,591,739 bytes; SHA-256 `205CA5762E913CF10846DCE413AC08BB529EE0D925BD119479C0917BFC63B31F`. Built only; not installed or launched.
- **Data safety:** no Room schema/DAO, repository, API/server/Worker contract, group membership, archive record, completion row or counter source was changed. No DB write or migration.
- **Exact-SHA Android CI:** Run `45220324332` PASS on `8b425ef589b64bc58e2d2b09f79bd64249f0b5a5`: unit tests, Staging Debug assembly and Debug APK upload all passed. Signed Release steps were skipped by workflow conditions.
- **Phase 5:** verify scope labels and counts against a real multi-group student account; switch modes and confirm personal contributions remain distinct from all-member selected-group records. No emulator or phone was used.
- **Exact-SHA Server CI:** Run `45220324386` PASS on the same SHA: dependency security baseline, TypeScript, unit/integration tests and Wrangler Production bundle validation passed. Artifact `14840800635`, 31,175,177 bytes, ZIP SHA-256 `15df8aa2d643a87caa5c56357036c85393d560c9e54a043e6a2a7085db5c0d0ddd`. No Worker deployment or database write occurred.
- **Governance:** no deployment, secret change, D1/staging/production write, Release signing, app install/launch, `main` update or PR merge. Keep PR #6 Open + Draft and WebGPT task `d8ff20f1-780c-4e55-acc5-ed2dfe8d71be` RUNNING.
- **Next:** P2-20 is implemented, committed, pushed and exact-SHA Android/Server CI verified on `8b425ef589b64bc58e2d2b09f79bd64249f0b5a5`. Start P2-21 only on this clean branch; keep P2-20 scope acceptance open for Phase 5.

## 2026-11-23 - Phase 2 P2-21 implementation checkpoint
- **Base/source:** started from 704ac55803794fbe3e41d8cf08a1755fb279cb1a; P2-21 source commit 8d004773d07c20f666a86f6622379a37848bc318 is committed and pushed to work/release-readiness. Local and origin heads match; worktree clean. main is untouched. PR #6 remains Open + Draft + Unmerged.
- **Scope:** make Events archive cards open details and expose Edit/Delete only for their actual creator; retain Worker authorization as the final security boundary.
- **Identity contract:** `mapSchoolEvent` maps `created_by` to `createdBy`; Android `SchoolEventItem` + Room `EventEntity` preserve it through daily, group, monthly and local event flows. New events capture the current cached account ID locally; the server still derives `created_by` from authenticated identity.
- **Room:** version 18; `MIGRATION_17_18` adds `cached_events.createdBy TEXT NOT NULL DEFAULT ''`. `@ColumnInfo(defaultValue = "''")` matches the migration. `DatabaseMigrationTest` inserts a legacy event and verifies its title/status survive while unknown creator defaults empty. Migration has not been applied to any real database.
- **Policy/UI:** `EventOwnershipPolicy` requires nonblank matching creator ID + `SYNCED`; hides actions for legacy/pending/failed rows. School-wide announcement actions require ADMIN/SYSTEM_OWNER; holiday edit requires Worker-privileged role. `EventsScreen` cards open localized details; owner-only Edit/Delete actions open a prefilled edit dialog or explicit delete-confirmation dialog. Failures keep data/dialog state; success requires server ACK.
- **Repository/Worker:** PATCH/DELETE re-check local creator ID and `SYNCED`; Room changes only after `success=true` ACK. No offline mutation queue is created for updates/deletes. ViewModel refreshes the event list and affected calendar months after success. Worker PATCH differentiates omitted fields from explicit empty values and uses `NULLIF` to clear optional time/description/location; holiday reason remains mandatory. Added mapper, monthly/day calendar ownership projection, and PATCH clearing tests; existing Worker owner/403 checks remain intact.
- **Android tests/build:** `DatabaseMigrationTest` 1/1 PASS; `EventOwnershipPolicyTest` 5/5 PASS. Full Windows suite: 236 tests across 52 XML reports, 235 passed, 1 failed, zero errors/skips. Sole known failure: `StudentNotebookManagerTest.valid notebook records round trip through atomic storage` expected `[lesson-2, lesson-1]`, restored `[lesson-1]` at line 37 (Windows/Robolectric AtomicFile host issue); it remains unchanged and not skipped. `:app:assembleStagingDebug` PASS. APK `app/build/outputs/apk/staging/debug/app-staging-debug.apk`, 33,612,514 bytes; SHA-256 `E1C86252B5060D92C6B412414DC33A2435179E789712C1F9EBDF8904704219FD`. Not installed/launched.
- **Server tests:** full `npm test -- --reporter=dot` PASS: 44 Vitest files / 299 tests. `npm run lint` PASS (TypeScript noEmit). No Worker deployment or D1 write.
- **Exact-SHA CI:** PASS on 8d004773d07c20f666a86f6622379a37848bc318. Android PR run 45244458212 passed Unit Tests, Staging Debug assembly and Debug artifact upload; Release signing steps were skipped as expected for a PR. Server push run 45244446475 and PR run 45244458257 passed.
- **GitHub artifact:** ID 14852108611, 31,205,658 bytes; ZIP SHA-256 2bcd73edeff2d0c5183172dba53dcba3fae8187c18d9f1c7ff83f412b90ed9c1. Local Staging Debug APK: 33,612,514 bytes; SHA-256 E1C86252B5060D92C6B412414DC33A2435179E789712C1F9EBDF8904704219FD.
- **Phase 5:** real-device visual verification remains open for owner/non-owner, school admin, holiday roles, event editing, confirmed delete and calendar month-crossing refresh. No emulator/physical device used in this session.
- **Governance:** no D1 migration apply, production/staging write, Worker deployment, secret change, Release signing, app install/launch, `main` update or PR merge. Keep PR #6 Open + Draft and WebGPT task `d8ff20f1-780c-4e55-acc5-ed2dfe8d71be` RUNNING.
- **Next:** continue with P2-22 — student-created exam editing — after preserving this P2-21 exact-SHA CI record. Keep PR #6 Open + Draft + Unmerged, do not touch main, and keep WebGPT task RUNNING.


## 2026-11-24 ? Phase 2 P2-22 implementation checkpoint

- **Base:** `9e23cfc28bc05e12195eb0847ade85a14d342219` on `work/release-readiness`; local branch matched `origin/work/release-readiness` and was clean before work. PR #6 remains Open + Draft + Unmerged. `main` untouched.
- **Scope:** TNW-EXAM-EDIT-001 ? allow the creator of a student-created exam to edit it, and allow only group members with the existing exam-moderation permission to edit another member's exam.
- **Identity end-to-end:** server exam mapper emits `createdBy`; Android `ExamItem` and Room `ExamEntity` carry it. All server-fed day, group and monthly `ExamEntity` constructors preserve it, and local creation stores the current account ID. Legacy rows with an empty creator are not treated as owned by the current user.
- **Room:** schema version 19; `MIGRATION_18_19` adds `cached_exams.createdBy TEXT NOT NULL DEFAULT ''`. `@ColumnInfo(defaultValue = "''")` matches the DDL. `DatabaseMigrationTest` verifies a legacy exam survives and its unknown creator defaults to empty. Migration not applied to real data.
- **Android policy/UI:** `ExamOwnershipPolicy` requires `SYNCED` and either an exact nonblank creator match or group-scoped moderator permission. `ExamsScreen` passes the active `GroupItem.role` into `Permission.MODERATE_EXAM`, displays Edit only when authorized, opens the existing form prefilled, and keeps edits in place on failure. Subject, title, chapters, notes, exam type and date are editable.
- **Repository:** `updateExam` is serialized with the exam mutation mutex, rejects non-SYNCED rows and blank required inputs, sends `UpdateExamRequest` with subject ID, and writes the updated Room snapshot only after `isConfirmedServerWrite`. It updates subject name/icon/color locally when the selected subject changes. `CancellationException` is rethrown; HTTP 403 and 409 remain failures without optimistic local mutation.
- **Worker authorization:** `handleUpdateExam` checks current group membership, then allows the authenticated `created_by` owner or `Permission.MANAGE_EXAMS` (alias of `MODERATE_EXAM`) within that group. `PUBLISH_OFFICIAL_EXAM` stays independent. Monthly duplicate detection returns 409; omitted optional fields are preserved while explicitly empty chapters/notes can be cleared.
- **Tests:** Android `ExamOwnershipPolicyTest` 7/7 PASS; `DatabaseMigrationTest` 1/1 PASS; focused total 8/8, zero failures/errors/skips. Server `exam-update-ownership.test.ts` 5/5 PASS, including creator allowed, non-owner student denied, group teacher moderation allowed, moderator denied, and omitted patch fields preserved. Mapper coverage verifies `createdBy`.
- **Android build:** `:app:assembleStagingDebug` PASS. APK `app/build/outputs/apk/staging/debug/app-staging-debug.apk`, 33,618,984 bytes; SHA-256 `6C60E32A32A45FCBBCF7969AFB7DB071851971159B98EC6AE40F0E8C66548937`. Build only; not installed or launched.
- **Full Windows Android suite:** 243 tests across 53 XML reports; 242 passed, one known host-specific failure, zero errors/skips. The sole failure remains `StudentNotebookManagerTest.valid notebook records round trip through atomic storage` (`StudentNotebookManagerTest.kt:37`), the documented Windows/Robolectric `AtomicFile` behavior. The test remains unchanged and was not skipped. The exact-SHA GitHub Ubuntu Android CI passed the complete unit-test task, so the Windows-only failure does not reproduce on the CI host.
- **Server local verification:** `npm audit --audit-level=moderate` found 0 vulnerabilities; `npm run types` PASS; full Vitest suite 45 files / 304 tests PASS. Wrangler production and staging `deploy --dry-run` both passed and showed expected D1/DO bindings. Dry-runs only; no deployment or database write.
- **Diff hygiene:** `git diff --check` PASS. Source and tests were committed as `e29eadcb0ee0616e49035d47cbc8a29e1c63cfd6` and pushed; the CI-result documentation is committed separately. No temporary branch created.
- **Exact-SHA CI:** source commit `e29eadcb0ee0616e49035d47cbc8a29e1c63cfd6` is pushed. Android run `45411666582` passed the full `testStagingDebugUnitTest` task, `assembleStagingDebug`, and Debug artifact upload. Server run `45411666652` passed TypeScript, dependency security baseline, 45 Vitest files / 304 tests, Wrangler production and staging dry-runs. Both workflows are completed with `success` on the same source SHA. Android artifact ID `14927677080`, archive size `31,219,326` bytes, SHA-256 `60443a56300ca463f0e32a767812b597b5f1ed06528801fd5a86d6d07fb13c29`. Release signing/build/upload steps were skipped because this was a pull-request run. The documentation update is a follow-up docs-only commit.
- **Phase 5:** device/account acceptance remains open for owner vs non-owner, teacher moderation, old Room database upgrade, edit after reopening the app, changed subject/date in Calendar, and server 403/409 behavior. No emulator or phone used.
- **Governance:** no Worker deployment, D1 migration apply/write, staging/production write, secret change, Release signing, app install/launch, `main` update, PR merge or new branch. PR #6 remains Open + Draft + Unmerged. WebGPT task `d8ff20f1-780c-4e55-acc5-ed2dfe8d71be` remains RUNNING.
- **Final source commit:** `e29eadcb0ee0616e49035d47cbc8a29e1c63cfd6` (`feat: allow owners to edit synced exams`) is on `origin/work/release-readiness`; Android and Server CI are green. PR #6 remains Open + Draft + Unmerged, based on `main`; head SHA matches the source commit.
- **Artifact link:** `https://github.com/majd7772233-a11y/Tanweer/actions/runs/45411666582/artifacts/14927677080`.
- **Historical next at the P2-22 checkpoint:** proceed to P2-23. P2-23 is completed in the later checkpoint below; the next roadmap item is P2-24. Keep Phase 5 acceptance open.


## 2026-11-24 — Phase 2 P2-23 final implementation checkpoint

- **Source baseline:** 40c1ade732d33119a3fb305f9982c200de1f5cc3 on work/release-readiness. The implementation commits were pushed as 2a361eda (initial P2-23), 01af068 (mapper syntax correction), 540082c (Android creator mapping/date-picker corrections), and 1620eba (separate edit/delete permissions). The final source SHA is 1620eba5b3607deb3529ac63b900c5b03dd00413.
- **PR:** #6 remains Open + Draft + Unmerged, base main, head work/release-readiness. No changes to main.
- **Room identity:** server mapHomework now emits createdBy; HomeworkItem and HomeworkEntity carry it. All three server snapshot constructors and both local creation paths retain it. Database version 20 adds cached_homeworks.createdBy TEXT NOT NULL DEFAULT ''. Legacy rows with unknown owners remain non-editable/non-deletable.
- **Edit:** owner-only student path supports title, details, page numbers, question numbers and due date. The form is prefilled. Explicitly clearing details/pages/questions is supported; omitted fields are preserved by the Worker PATCH.
- **Delete:** a separate confirmation is required. Room is changed only after server acknowledgement. The local transaction removes the homework, its cached completion rows and active-account daily-plan reference; the screen cancels its reminder after success.
- **Separate authorization:** MODERATE_HOMEWORK allows group-teacher editing/moderation; DELETE_HOMEWORK is a distinct permission. The creator may edit and delete their own record. A group teacher can edit another member's homework but cannot delete it solely via moderation permission; a group administrator or system administrator may delete. Android policy and menu model canEdit and canDelete independently.
- **Tests:** HomeworkOwnershipPolicyTest has seven cases including independence of edit/delete grants and fail-closed legacy/unsynced rows. DatabaseMigrationTest covers v19 to v20. Server homework-ownership.test.ts verifies owner update/delete, optional clear/preserve, rejection of other students, teacher edit/no-delete and group-admin delete.
- **Exact-SHA Server CI:** run 45419856761 PASS; TypeScript PASS; npm audit 0 vulnerabilities; 46 Vitest files / 313 tests PASS; Wrangler production and staging dry-runs PASS. No deployment or D1 write.
- **Exact-SHA Android CI:** run 45419856793 PASS; full testStagingDebugUnitTest PASS; assembleStagingDebug PASS; Debug APK artifact upload PASS. PR release signing/build steps were skipped as configured. Artifact ID 14933139019, archive size 31,253,863 bytes, SHA-256 d7e540cd74532453cc895a92c5d57364c9268dcbc4890bfca2cbe6d06fc83bf7.
- **Artifact:** https://github.com/majd7772233-a11y/Tanweer/actions/runs/45419856793/artifacts/14933139019
- **Local verification limitation:** WebGPT terminal was unavailable during this continuation, so no local Gradle/npm commands were run in this session. Exact-SHA GitHub Actions provides the successful build/test evidence.
- **Evidence limitation:** %TEMP%\tanweer-homework-round-20261119\evidence was absent in the active workstation session. Historical screenshots were not claimed as reviewed.
- **Phase 5:** physical-device/account checks remain open for owner vs non-owner, teacher edit vs no-delete, group-admin delete, existing Room database migration, restart persistence, reminder cleanup and UI visual acceptance. No emulator or physical device was used.
- **Governance:** no Worker deployment, D1 migration apply/write, production/staging data write, secret change, Release signing, app install/launch, main update, PR merge or new branch.
- **Next roadmap item:** P2-24 — Android UI access to deleting a user's own group-chat message. P2-23 implementation and CI are complete; preserve Phase 5 acceptance as open.


## 2026-11-24 — Phase 2 P2-24 group chat message deletion checkpoint

- **Source baseline:** P2-23 docs head `d5359239ddf150c4e4db811351e4b98440727f32` on `work/release-readiness`. P2-24 source edits were pushed as incremental commits beginning with `e745877` (server owner authorization + scoped deletion), `e95d240` (server policy tests), `87a1af8` (Android DELETE API + ACK DTO), `8b9da3f` (Room DAO), `efb4caf` (repository ACK/reconciliation + live deletion event), `68bbd3f` (ViewModel), `abd45ba` (long-press UI/confirmation), `1cb6572`/`c4bbb8b` (Arabic/English resources), `f105329` (UI policy), and Android tests. Final source SHA: `c599a66c265d901f897fc2cd09b3b4658bc44974`.
- **PR:** #6 remains Open + Draft + Unmerged, base `main`, head `work/release-readiness`. The branch head was verified as the final source SHA before this docs-only checkpoint; no changes to `main`.
- **UI and authorization hints:** long-press on a `SENT` message opens a Delete action only for its exact sender or a user granted chat moderation at global/group level. The confirmation dialog is localized in Arabic and English. Pending/failed messages and messages from another group fail closed.
- **REST / ACK:** added Retrofit `@DELETE` for the existing Worker route. The app requires HTTP success and exact `groupId` + `messageId` in the application response. Repository retains the local Room row on error, denial, transport failure or mismatched ACK; after a valid ACK it removes the exact scoped row and attempts history reconciliation.
- **Realtime:** active WebSocket peers consume `message_deleted` and delete the matching scoped Room row. This is a server-originated event after D1 deletion, not optimistic hiding.
- **Worker correction:** the old REST handler checked send permission before checking message ownership, which prevented a student from deleting their own persisted message in a `STAFF_ONLY` group. Ownership is now checked independently of send policy; non-owner deletion still uses `DELETE_ANY_CHAT_MESSAGE`/`MODERATE_CHAT`. The DELETE statement includes both IDs and the response carries both IDs.
- **Tests:** Server CI covers owner delete in staff-only group, non-owner student denial, active group moderator delete, and missing-message rejection. Android adds nine group-message policy cases and two exact-ACK contract cases.
- **Exact-SHA Server CI:** run `45429340619` PASS on `c599a66c265d901f897fc2cd09b3b4658bc44974`; TypeScript PASS; `npm audit` reports 0 vulnerabilities; 46 Vitest files / 316 tests PASS; Wrangler production/staging dry-runs PASS. No Worker deployment or D1 write.
- **Exact-SHA Android CI:** run `45429341310` PASS on the same source SHA; complete `testStagingDebugUnitTest`, `assembleStagingDebug`, and Debug APK upload PASS. PR Release signing/build/upload skipped as configured. Artifact ID `14938551917`, size `31,268,918` bytes, SHA-256 `cf951b2159bd7fead3b7635a824e5d546f35eec0f37f8dd11b2fa4e5bad3a4a0`.
- **Artifact:** https://github.com/majd7772233-a11y/Tanweer/actions/runs/45429341310/artifacts/14938551917
- **CI correction:** Android run `45428849017` initially failed at AAPT string parsing because the English text used an apostrophe; corrected the resource wording. Final exact-SHA run `45429341310` passed.
- **Local verification limitation:** WebGPT terminal access is unavailable in this continuation; no local Gradle/npm commands or device tests are claimed. Exact-SHA GitHub Actions provides the build/test evidence.
- **Offline cache limitation:** live-connected peers receive the deletion event. History REST currently returns only the latest 100 rows and local sync preserves cached older messages, so invalidating stale deleted messages on devices that were offline is not yet guaranteed. This is explicitly retained as a Phase 5/future tombstone reconciliation check, not claimed as complete.
- **Phase 5:** real-device Arabic/English long-press and confirmation UX; owner vs non-owner; group moderator/teacher/admin; 403/404 behavior; active peer broadcast; app restart; offline-device cache handling. No emulator or physical phone used.
- **Governance:** no Worker deployment, D1 migration/apply/write, staging/production data write, secret change, Release signing, app install/launch, main update, PR merge or new branch. PR #6 stays Open + Draft + Unmerged.
- **Next roadmap item:** P2-25 — group chat auto-scroll/unread indicator. Keep Phase 5 acceptance open.


## 2026-11-24 — P2-25 group chat auto-scroll/unread implementation handoff

- **Current branch:** `work/release-readiness`.
- **Current source HEAD:** `839089b52ebaba0cf17a6ac30866a72ad1ae2f9b`.
- **Source commit sequence:** `06ef124` (auto-follow/unread UI, helper, tests, resources), `c46cc2e` (no-anchor safety and null-safe latest visibility), `839089b` (correct expected unread accumulation set).
- **P2-25 status:** implementation and exact-SHA Android/Server CI complete.
- **Android CI:** run `45434428809` PASS: unit tests, Staging Debug APK build, Debug artifact upload. Artifact ID `14941895686`; size `31,277,983` bytes; SHA-256 `f243c3a42e316175b439ae8d111f4d64670da818e8e910dab4284eec459808df`. [Artifact](https://github.com/majd7772233-a11y/Tanweer/actions/runs/45434428809/artifacts/14941895686).
- **Server CI:** run `45434428853` PASS on the same SHA: 46 files / 316 tests, TypeScript, audit 0 vulnerabilities, Wrangler dry-runs. Server code was not changed by P2-25.
- **First test attempt:** Android run `45433814960` failed one new assertion because an existing unread ID was correctly retained by the helper but absent from the expected set. Fixed expectation in `839089b`; final Android run passed.
- **Implementation details:** `GroupChatAutoScrollPolicy` calculates ID additions/removals and the follow threshold using the previous last message anchor. `GroupsScreen` scopes Room messages to the active group, initializes to latest once, preserves Search deep-link position, follows smoothly only when previously near the end, exposes Arabic/English unread plurals, and clears unread IDs on delete or when the user reaches latest. The current activity's `adjustResize` behavior is respected through the live LazyList viewport.
- **No local/device claims:** WebGPT terminal was unavailable; no local Gradle/npm commands were run. No emulator/phone acceptance performed. Verify real keyboard behavior and gesture feel in Phase 5.
- **PR/governance:** PR #6 remains Open + Draft + Unmerged. `main` unchanged; no Worker/D1 deployment or production data write.
- **Next step:** P2-26 — add `is_teacher_answer` to Android DTO/domain mapping and distinguish teacher-answer source from accepted/best-answer state. Read current API DTO, mapper, `IssueCommentItem`, Room policy and UI before edits.


## 2026-11-24 — P2-26 teacher-answer marker verification handoff

- **Branch:** `work/release-readiness`.
- **Source code HEAD:** `3dc3a1f51841f9acd27598a23a11f588d0eba53c`.
- **Docs commit:** follows after the code checkpoint; this changes only `ROADMAP.md` and `SESSION_HANDOFF.md`.
- **P2-26 status:** implementation verified; regression test added and exact-SHA CI green. The issue description was stale: `isTeacherAnswer` already existed throughout the complete Android/server path at the starting HEAD.
- **End-to-end path:** D1 `is_teacher_answer` → Worker `mapIssueComment` / issue details response → Moshi `IssueDetailResponse.comments: List<IssueCommentItem>` → `IssueDataSyncCoordinator` → Room `IssueCommentEntity.isTeacherAnswer` → repository `getIssueComments` → Compose badge in both best-answer and ordinary-comment cards. POST acknowledgement also carries `isTeacherAnswer`.
- **Schema:** migration `16 → 17` already adds the Room column with default 0. No schema change required.
- **Regression test:** `IssueCommentReconciliationTest.teacherAnswerAndBestAnswerMarkersRemainIndependent` proves a teacher comment can be both teacher-authored and best answer, and that changing the best answer to a student comment does not clear the teacher-origin flag.
- **Android CI:** run `45436415549` PASS; unit tests, Staging Debug build and Debug artifact upload passed. Artifact `14942116975`, size `31,277,983`, SHA-256 `bc6876e4da1cefc14c98b6700c77f67d40c80ffd4bc9d17e3f659e9095a728c0`. [Artifact](https://github.com/majd7772233-a11y/Tanweer/actions/runs/45436415549/artifacts/14942116975).
- **Server CI:** run `45436415405` PASS: TypeScript; 46 files / 316 tests; npm audit 0 vulnerabilities; production and staging Wrangler dry-runs. No deployment.
- **No local/device claims:** no local Gradle/npm commands or device acceptance performed. Retain Phase 5 checks.
- **PR/governance:** PR #6 Open + Draft + Unmerged; no main update, deployment, D1 write or secret mutation.
- **Next step:** P2-28 — verify the subject-book action from Exam details and distinguish a true subject-book deep link from the explicitly labeled general-library destination. Keep Phase 5 acceptance open.


## 2026-11-24 — Phase 2 P2-27 navigation-context checkpoint

- **Branch/source:** `work/release-readiness`; implementation commit `01aeb35e5daf8752392e074fb37851815b7ddfb5`. Documentation follow-up is committed separately.
- **PR:** #6 remains Open + Draft + Unmerged, base `main`; no changes to `main`.
- **Confirmed moderator navigation repair:** the Moderator Dashboard member-management shortcut now records its source route and current root tab before opening `GROUPS`. Back resolves to `MODERATOR_DASHBOARD`; direct Groups entry and non-moderator navigation retain the prior root-tab fallback. A typed policy rejects invalid return sources. Source tokens are cleared on ordinary navigation, tab selection and user-state reset.
- **Saved nested UI state:** Moderator dashboard section/filter and Knowledge Base subject tab now use `rememberSaveable` inside the existing `SaveableStateHolder` providers. The PDF origin policy already existed and preserved the Knowledge Base subject; the subject tab is now preserved too. The reader continues receiving the requested initial page.
- **Tests:** new `GroupsNavigationPolicyTest` covers valid Moderator Dashboard return, default Groups fallback and invalid-origin rejection. Existing `PdfReaderNavigationPolicyTest` covers Knowledge Base subject, general library, Search route policy, and root/sub-screen returns. Search result/filter restoration remains an unconfirmed UX requirement and was not changed.
- **Exact-SHA Android CI:** run `45440017812` PASS on source SHA `01aeb35e5daf8752392e074fb37851815b7ddfb5`: `testStagingDebugUnitTest`, `assembleStagingDebug`, Debug artifact upload. Release signing/build steps were skipped as expected for a PR.
- **Android artifact:** ID `14944025108`; archive size `31,279,203` bytes; SHA-256 `929673ee99dc0112ab514ef27f16c6235b17d590d162079595945790000514a4`. [Artifact](https://github.com/majd7772233-a11y/Tanweer/actions/runs/45440017812/artifacts/14944025108).
- **Exact-SHA Server CI:** run `45440018343` PASS: TypeScript, npm audit (0 vulnerabilities), 46 Vitest files / 316 tests, Wrangler production/staging dry-runs. No Server code changed; no deploy or D1 write.
- **No local/device claims:** no local Gradle/npm execution, emulator or phone acceptance was performed in this continuation. Phase 5 device acceptance remains open.
- **Governance:** no Worker deployment, migration apply, D1 write, secret mutation, Release signing, `main` update or PR merge.
- **Next roadmap item:** P2-28 — verify the subject-book action from Exam details, including whether a subject/book association is available or the action must explicitly open the general Library.
