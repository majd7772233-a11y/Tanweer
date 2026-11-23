# Tanweer — ROADMAP إصلاح أخطاء الفحص الشامل

> **نوع الوثيقة:** خطة إصلاح مبنية على تقرير الفحص البصري والوظيفي؛ ليست إعلاناً عن تنفيذ الإصلاحات أو اجتياز التطبيق.
> **المصدر:** TanweerReport.md بعد قراءة التقرير كاملاً واعتماد نتائج الجولات الأحدث حتى 20 نوفمبر 2026.
> **المشروع:** D:\MyApps\tanweer
> **المستودع:** majd7772233-a11y/Tanweer
> **فرع العمل:** work/release-readiness
> **Commit الأساس:** 32de537c98b17954393b1c681b2c66a19d2b36b3 — Add TanweerReport.md
> **بيئة التقرير:** Staging، Android 15 Emulator وGalaxy S9 Android 10 في الجولات التي كان فيها الجهازان متصلين.

---

## 1. منهجية تنظيم النتائج

هذه الخارطة تجمع الأخطاء ذات الأثر الفعلي، وتحافظ على معرّفات التقرير وخطوات إعادة الإنتاج والملفات والأدلة. لا تكرر السبب الجذري نفسه لمجرد ظهوره في عدة شاشات.

| الأولوية | المعنى |
|---|---|
| **P1** | فقد بيانات/مسودات، انهيار، تضليل جوهري في الحفظ أو الإرسال أو القراءة التعليمية، فشل وظيفة أساسية، أو تناقض مرتفع الأثر في هوية/صلاحيات الإدارة |
| **P2** | بيانات أو حالات مضللة، مزامنة أو نطاق/تاريخ غير صحيح، CRUD ناقص، أو خلل وظيفي/تنقل مؤثر |
| **P3** | وضوح الواجهة والاكتشاف، قص النصوص والأزرار، الحالات الفارغة، قابلية الوصول، أو طلب شبكة زائد لا يثبت وحده فساد البيانات |

لم يسجل التقرير عيباً مؤكداً بدرجة P0. وأي أولوية لم يحددها التقرير صراحةً هي تقدير ترتيب مبدئي وليست تصنيفاً من صاحب التقرير.

### قواعد ترجيح نتائج التقرير

1. المصفوفة الأولية كانت مؤقتة أثناء الفحص؛ تعتمد نتيجة الجولة الأحدث عند التعارض. فتح تفاصيل الاختبار الذي ورد أولاً NOT VERIFIED تم تأكيده لاحقاً في ملحق الأستاذ على Galaxy S9، فلا يسجل كعطل.
2. السجل المحلي FAILED الذي يظهر في الاختبارات والتقويم والأرشيف و«ماذا فاتني» سبب واحد ذو آثار متعددة، وليس أربعة أخطاء مستقلة.
3. عقد إرسال المحادثة نفسه أعيد رصده تحت أكثر من ID؛ جُمعت المعرفات في بند واحد مع ذكر جميع المراجع.
4. اسم TNW-DATA-002 استُخدم في موضعين مختلفين: اختلاف نطاق الأرشيف الشخصي/أرشيف الشعبة، وفقد ملاحظة PDF المؤقتة بعد إعادة فتح اللوحة. هما مشكلتان مختلفتان وتم فصلهما.
5. لا تُسجل نتائج PASS أو البيانات الفارغة الصحيحة أو العمليات التي لم تُنفذ عمداً على أنها أعطال.
6. NOT RUN وUNVERIFIED وINCONCLUSIVE تبقى في قائمة التحقق اللاحق، ولا تتحول إلى FAIL دون إعادة إنتاج واضحة.

### حالات مستبعدة من قائمة الأخطاء

- تفاصيل الاختبار التي كانت غير مؤكدة أولاً أصبحت PASS بعد إعادة الفحص؛ لا تعاد كعيب.
- Bookmarks والتنقل الأساسي بالمصغرات وتدوير PDF ومشاركة لقطة الصفحة إلى Android Chooser دون إرسال خارجي واستعادة موضع القراءة في مسار الرجوع الطبيعي اجتازت المسارات التي اختُبرت.
- حدث التقويم الذي تعذر تحديد حدوده بسبب null accessibility root غير محسوم.
- زر إضافة الواجب على الهاتف عبر ADB غير حاسم؛ يحتاج لمساً يدوياً/تحكماً موثوقاً قبل الحكم.
- exact alarm على Android 15 لم يكتمل لأن إذن Alarms & Reminders بقي غير ممنوح؛ مسار الرفض/الإعدادات نجح.
- pinch بإصبعين لم يُختبر بواسطة ADB.
- اختلاف الأرشيف الشخصي عن أرشيف الشعبة لا يثبت فساد قاعدة بيانات؛ قد يكون اختلاف نطاق.
- تاريخ الدراسة السابق في نموذج الواجب قد يكون مقصوداً؛ المطلوب إظهاره لا فرض تاريخ اليوم.
- TNR-010 ليس عيباً حالياً: أكد المستخدم أن التطبيق لم يُنشر بعد وما زال قيد التطوير. يعاد تقييمه قبل أول نشر عام فقط.

## 2. خريطة المراحل

| المرحلة | النطاق | الأولوية الغالبة |
|---|---|---|
| 1 | سلامة المسودات، الاستقرار، الرسائل، WebSocket، هوية المدير وصلاحياته | P1 |
| 2 | صحة البيانات والحالات والتواريخ والإحصاءات والإشعارات | P2 |
| 3 | اكتمال العمليات الإدارية والتعاون والتنقل | P2 |
| 4 | UX وRTL وإمكانية الوصول والاستجابة البصرية | P2/P3 |
| 5 | إعادة اختبار القبول وإغلاق المتبقي | تحقق/قبول |

---

# المرحلة 1 — P1: سلامة البيانات والثقة والاستقرار

> لا تُغلق المرحلة حتى تثبت سلامة المسودات، وحالة الرسائل التي حفظها الخادم، واستقرار الاتصال، وهوية مدير المدرسة، وتوافق صلاحيات API مع الواجهة.

### سجل التنفيذ المحلي حتى 21 نوفمبر 2026

هذه حالة تنفيذ مصدرية واختبارات آلية فقط؛ لا تعني اجتياز القبول النهائي. لم تُجرَ اختبارات تشغيل حقيقية على المحاكي أو الهاتف في هذه الجولة، بناءً على قرار تأجيل جميعها إلى المرحلة الخامسة.

| البند | ما نُفذ في الشيفرة المحلية | الحالة وحدود الإثبات |
|---|---|---|
| P1-01 — المسودات | فحص foreground لم يعد يرفع isCheckingAuth ولا يستبدل شجرة Compose بـSplash؛ حقول الدرس والواجب تستخدم rememberSaveable، ومرفقات المسح تُنسخ إلى مساحة التطبيق الخاصة ويعاد بناء بيانات الصفحات من Saver. زر إلغاء الدرس ينظف ملفات المسودة غير المرسلة. | IMPLEMENTED — يحتاج اختبار Photo Picker/Camera وعودة Activity/تدوير/إعادة إنشاء فعلية في Phase 5. لا تُحذف ملفات المعالجة التي يشير إليها outbox أو الرفع المؤجل. |
| P1-02 — RenderThread | لا تغيير رسومي أو بصري. أُضيف توضيح ظروف Ultra Power Saving وطول جلسة التشغيل وقرار عدم تقليل المؤثرات. | DEFERRED / ENVIRONMENT-CONFOUNDED — لا يغلق قبل اختبار مضبوط في Phase 5. |
| P1-03 — PDF/TTS | أضيف PdfSpeechTextQuality ليوقف النطق للنص الواضح التلف/المحارف التحكم/أنماط التشويه المعروفة، مع السماح بنصوص عربية/إنجليزية وصفحات معادلات. | PARTIAL MITIGATION — يمنع نطق استخراج تالف بوضوح، لكنه لا يصلح Glyph maps أو Encoding أو RTL في ملفات PDF. اختبار عينات الكتب الفعلية في Phase 5. |
| P1-04 — HTTP chat ACK | أضيف DTO يطابق message ككائن، وتحقق ACK من id/groupId/text/status، مع تحديث تاريخ المجموعة بعد إعادة الاتصال. | IMPLEMENTED IN SOURCE — اختبارات DTO ضمن Android مرت؛ اختبار جهازين وتأكيد حفظ D1 والـcomposer في Phase 5. |
| P1-05 — WebSocket | أضيف Ping/Pong بروتوكولي عبر OkHttp client مخصص للمحادثات، وHistory reconciliation بعد reconnect مع حواجز generation/Mutex. | IMPLEMENTED IN SOURCE — لا يوجد إثبات بعد لوصول ثنائي الاتجاه تحت انقطاع/عودة الشبكة؛ Phase 5. |
| P1-06 — فصل ADMIN | Migration 0034 تحفظ الصف والعضويات الصفية القديمة في restore points، وتزيل عضوية CLASS فقط وتبقي المجموعة العامة. login/profile يعيدان ADMIN بلا صف/شعبة؛ تغيير الدور يدعم الاستعادة أو إسناد صف/شعبة بديلين صالحين. | IMPLEMENTED IN SOURCE — سياسة الاستعادة 6/6 اختبارات خادمية؛ لم تُطبق migration على أي قاعدة Staging/Production. التحقق من حسابات QA وتسجيل الدخول في Phase 5. |
| P1-07 — صلاحيات/لوحة ADMIN | حُصرت صلاحيات التدقيق وتغيير الأدوار العامة وإلغاء الجلسات ومراجعة الطلبات والعمليات الشاملة على SYSTEM_OWNER، وجميع /api/dashboard/* خلف verifyOwnerAuth. لوحة المدرسة تعرض مؤشرات المدرسة وقوائم قراءة للمعلمين والمشرفين والمديرين، وتتيح إجراءات الطلاب المحددة فقط عبر endpoints مدرسية مستقلة؛ إدارة الأجهزة الشخصية تبقى في مسارات الحساب الذاتية. | IMPLEMENTED — حُسم الحد الفاصل: ADMIN مدير مدرسة وليس Owner؛ لا تُمنح له أدوات الحوكمة العامة أو سجلات Owner أو إبطال جلسات الآخرين. اختبارات الصلاحيات والخادم تؤكد الفصل. |
ملاحظة النطاق المعماري الحالي: كل قاعدة D1/Worker في هذا الإصدار تمثل مدرسة واحدة؛ المجموعة all هي المجموعة المدرسية المشتركة. لا يحتوي المخطط الحالي على school_id أو district_id أو عزل tenants داخل قاعدة مشتركة. دعم عدة مدارس/مديريات في D1 واحدة يحتاج مرحلة تصميم وعزل مستقلة، ولا يُدّعى ضمن Phase 1.

**نتائج التحقق المحلي الحالية (21 نوفمبر 2026):**
- Server: npm test نجح — 42 ملفاً / 293 اختباراً؛ npm run lint نجح (tsc --noEmit).
- Android: :app:assembleStagingDebug نجح، والترجمة Kotlin/Java ناجحة. مجموعة Unit Test المحلية نفذت 124 اختباراً؛ 123 نجح وواحد تعثر على Windows/Robolectric في StudentNotebookManagerTest.valid notebook records round trip through atomic storage. نتيجة Linux CI على SHA النهائي مطلوبة للتحقق الكامل.
- تشخيص AtomicFile: أُعيد إنتاج الحالة مع فحص فعلي للملفات؛ بعد الحفظ الثاني يحتوي student_notebook_records.json.new على السجلين بينما يظل الملف الأساسي على السجل الأول. فحص تنفيذ Robolectric لـAndroid 14 أكد أن finishWrite يعتمد على File.renameTo لاستبدال الملف الأساسي، وهو سلوك لا يطابق دلالات rename على Android/Linux عند وجود الوجهة. أُعيد إعداد الاختبار إلى API 34 الأصلي؛ لم يُخفَ الاختبار ولم يتغير كود الإنتاج أو توقع الاختبار. اختبار API 23 لم يصل إلى التنفيذ بسبب تعذر تنزيل android-all-instrumented 6.0.1 من Maven. سير عمل Android الحالي ينفذ Unit Test على ubuntu-latest قبل بناء APK؛ نجاح الاختبار على Linux CI للـSHA النهائي هو شرط الإغلاق. لا نسجل الاختبار PASS قبل ظهور نتيجة CI.
- لم يُثبت أي APK ولم يُفتح أي تطبيق على الأجهزة. لا توجد نتيجة قبول حقيقية للمحاكي/الهاتف في هذه الجولة.
### إغلاق نطاق التنفيذ للمرحلة الأولى (21 نوفمبر 2026)
- فحص CI على SHA f98371e10aa3ad303f28815e73ad90416f50c799 (21 نوفمبر 2026): Android run 44936152421 نجح في testStagingDebugUnitTest ثم assembleStagingDebug على Ubuntu؛ Server run 44936152423 نجح في TypeScript وdependency security baseline و42 ملف اختبار / 293 اختباراً وWrangler Production bundle validation. تم بذلك حسم اختلاف AtomicFile الخاص بويندوز دون تعديل الاختبار أو كود الإنتاج.

- اكتمل تنفيذ الإصلاحات المصدرية P1-01 وP1-04 وP1-05 وP1-06، وحُسم نطاق P1-07 لصالح إبقاء صلاحيات Owner العامة منفصلة عن إدارة المدرسة. P1-03 لديه حاجز جودة يمنع نطق النص الواضح التلف فقط؛ لا ندّعي إصلاح Glyph maps/Encoding/RTL من دون عينات PDF فعلية.
- P1-02 يبقى مؤجلاً بقرار المستخدم إلى Phase 5 بسبب ظروف Ultra Power Saving والجلسات الطويلة؛ لم نخفض Blur أو الظلال أو التدرجات أو الحركة أو أي جودة بصرية.
- تُعد المرحلة مكتملة من ناحية تنفيذ المصدر بعد نجاح فحوص CI على SHA النهائي. قبول الكاميرا/Photo Picker، اختبار جهازين للمحادثة، عينات PDF، حسابات QA/ترحيل D1، واختبار RenderThread المضبوط على المحاكي والهاتف تبقى أعمال قبول Phase 5 صريحة وليست ادعاء نجاح في Phase 1.
- حدود النشر: Migration 0034 مصدر فقط ولم تُطبّق على D1. لا Worker deploy أو secret change أو Staging/Production write. PR #6 يبقى مفتوحاً وDraft، ولا merge إلى main.

## P1-01 — فقد نموذج الدرس/الواجب عند العودة من منتقي الصور أو الكاميرا

**مراجع التقرير:** TNW-UI-001، TNW-UI-001B، TNW-UI-001C، TNW-HW-ATTACHMENT-LOSS-001.
**الأولوية:** P1 — فقد مدخلات لم يرسلها المستخدم بعد.

**إعادة الإنتاج والنتيجة:**
1. فتح «ماذا فاتني» ثم «توثيق درس جديد»، وإدخال عنوان ووصف مثل QA_LESSON_IMAGE_20261117، ثم فتح ألبوم الصور واختيار صورة، مع إبقاء منتقي الصور أكثر من 12 ثانية قبل تأكيد «إضافة (1)».
2. في الكاميرا، إدخال QA_LESSON_CAMERA_20261117، وفتح «تصوير سبورة»، والتقاط صورة ثم تأكيد «تم».
3. في Build 120، فتح نموذج مشروع/واجب ثم اختيار صورة من Photo Picker أو العودة من CaptureActivity.

في جولات الدرس عاد التطبيق إلى MainActivity ثم Splash، وبعد نحو 8 ثوانٍ عاد النموذج فارغاً بلا عنوان أو وصف أو صورة. بقي PID نفسه ولم يثبت قتل العملية أو انهيارها. في جولة الواجب عاد التطبيق إلى قائمة الواجبات وأُغلقت نافذة الإضافة؛ لم يُنشأ مشروع ولم تُرفع صورة.

**السبب المرجح المسجل:** MainActivity.onResume يستدعي ViewModel.onAppResumed؛ وبعد مرور 10 ثوانٍ أو أكثر يرفع isCheckingAuth أثناء verifyServerDataGeneration. في TanweerApp يُستبدل محتوى Scaffold مؤقتاً بـSplash، فيُزال UploadLessonDialog من شجرة Compose. الحقول والصور محفوظة باستخدام remember عادي، فتفقد عند إزالة المحتوى. نافذة الواجب تتأثر كذلك بفقد حالة isLocalAddModalOpen وحقولها المحلية.

**الأثر:** ضياع مسودة ومرفقات المستخدم دون أن يطلب الإلغاء أو الإرسال.

**الإصلاح المطلوب:** لا تستبدل شجرة التطبيق كاملة بـSplash عند فحص foreground؛ أبقِ الوجهة والمحتوى مع مؤشر تحقق غير هدّام. احفظ المسودة في ViewModel/SavedStateHandle واستعدها عند Activity recreation/عودة picker. احتفظ بملفات الصور عبر URI/ملف مستمر بدلاً من Bitmap عابر. لا تمسح المسودة إلا عند إلغاء المستخدم أو نجاح النشر.

**اختبارات القبول:** اختيار صورة، إلغاء المنتقي، تأكيد صورة، كاميرا، إعادة إنشاء Activity، foreground بعد أكثر من 10 ثوانٍ، وفشل/نجاح generation check. يجب أن تبقى كل الحقول والمرفقات حتى يقرر المستخدم.

**الملفات:** UploadLessonDialog.kt، HomeworkScreen.kt، TanweerApp.kt، MainActivity، onAppResumed() وverifyServerDataGeneration().
**الأدلة:** %TEMP%\tanweer-homework-round-20261119، %TEMP%\tanweer-core-round-20261118، ومقاطع TNW-UI-001/TNW-HW-ATTACHMENT-LOSS-001.

## P1-02 — انهيار Native في RenderThread بسبب std::bad_alloc

**مرجع التقرير:** TNW-RENDER-001.
**الأولوية:** P1 — انهيار قسري للتطبيق.

**الواقعة:** 17 نوفمبر 2026، الساعة 18:42:04؛ العملية com.magd.tanweer.staging، PID 13491، TID 13509 على RenderThread. سجل ExitInfo reason=5 (APP CRASH NATIVE)، status 6، وFatal signal 6 (SIGABRT) مع std::bad_alloc.

**Backtrace المسجل:** libGLESv2_enc.so / GL2Encoder::updateProgramInfoAfterLink، ثم libhwui.so / GrGLProgramBuilder::CreateProgram وGrGLGpu::ProgramCache::findOrCreateProgram وGrOpsRenderPass::bindPipeline وCircularRRectOp::onExecute، وصولاً إلى SkiaOpenGLPipeline::draw وRenderThread::threadLoop.

**سياق الواقعة:** العملية كانت تعمل نحو 6,530 ثانية (نحو 109 دقائق)، وRSS قرابة 219MB. كانت الواجهة تعرض Hub/الأرشيف/الحوكمة؛ لا يثبت التقرير أن فتح بطاقة واحدة سبب الانهيار.

**توضيح المستخدم بعد مراجعة ظروف الاختبار (21 نوفمبر 2026):** أفاد المستخدم بأن المحاكي/الجهاز كان يعمل في وضع توفير الطاقة الفائق أثناء عدد كبير من الاختبارات، وأن ذلك كان يسبب بطئاً عاماً شديداً. كما كان الجهاز يعمل أحياناً من 08:00 صباحاً حتى 23:00 مساءً دون توقف، وكانت جولات الاختبار المتأخرة تتم بعد هذا الحمل الطويل. هذه عوامل بيئية مربكة ومعقولة قد تفسر ضغط renderer أو تضخيمه، لكنها لا تثبت وحدها سبب `std::bad_alloc`.

**قرار التصميم:** لا تخفّض جودة المؤثرات، ولا تزل Blur أو الظلال أو Gradients أو Rounded clipping أو الحركة، ولا تستبدلها بتصميم أبسط استناداً إلى هذه الواقعة وحدها. لا يوجد حالياً دليل يبرر تشويه الواجهة لحل مشكلة محتملة في وضع المحاكي/الطاقة.

**خطة التحقق قبل أي تعديل بصري/رسومي:** أعد الاختبار بعد تشغيل بارد وترك الجهاز يستقر، مع تعطيل Ultra Power Saving واستخدام وضع أداء/طاقة عادي، ثم قارن جلسة مماثلة على المحاكي وجهاز فعلي. سجل مدة التشغيل وRSS وGPU/renderer وApplicationExitInfo/tombstone. لا يُفتح تعديل موارد الرسم إلا إذا تكرر الانهيار في ظروف طبيعية قابلة للتكرار وأثبت profiler نمو موارد مرتبطاً بالتطبيق.

**الأثر:** التطبيق أغلق وعاد إلى Launcher؛ لم يسجل التقرير فقد بيانات من الحادث.

**الحالة الحالية:** DEFERRED — مرشح سبب بيئي؛ لم يُغلق العيب بعد. لا تغيير بصري مخطط قبل إعادة الاختبار المضبوط. إذا عاد الانهيار في ظروف طبيعية، افحص دورة حياة موارد renderer وإعادة استخدامها بقياسات profiler، مع الحفاظ على الشكل والمؤثرات الأصلية.

**اختبارات القبول:** جلسة تنقل طويلة تكرر فتح/إغلاق Hub والحوكمة والأرشيف، قياس الذاكرة وGPU، تشغيل على محاكي وجهاز فعلي، وجمع tombstone عند تكرار الحادث.

**الدليل:** C:\Users\Magd\AppData\Local\Temp\tanweer-tombstone-16.txt، الحجم المسجل 540,147 بايت، ومجلد %TEMP%\tanweer-core-round-20261118.

## P1-03 — القارئ الصوتي ينطق نص PDF عربياً مشوهاً وغير متعلق بالصفحة

**مرجع التقرير:** TNW-TTS-001، وإعادة الاختبار بتاريخ 18 نوفمبر 2026.
**الأولوية:** P1 — قد يقدّم التطبيق كلاماً غير متعلق بالدرس على أنه محتوى الكتاب.

**ما سمعه المستخدم:** صوت شبيه بـS2 جذر تربيعي 24 م3، ثم عبارات مثل «حقوق الطبع محفوظة» وكلام لا علاقة له بما يظهر على الصفحة.

**الدليل الفني:** في كتاب الأحياء book_10_biology، الصفحات 55 و57–61 تحمل extractionType=PDFBOX، لكن extractedText يبدأ بمقاطع مثل ÁU? ?O? وتتخلله علامات استفهام ومحارف تحكم وترتيب عربي/لاتيني مشوه. الصفحة المرئية تحتوي مادة عربية واضحة.

**مسار المصدر:** PdfReaderScreen.kt يطلب repository.getOrExtractPageText(...) ثم يمرر pageText مباشرة إلى enqueueSpeech(...). PdfTextExtractor.kt يستخدم PDFBox PDFTextStripper مع sortByPosition=true. لا توجد طبقة تحقق من جودة النص قبل النطق.

**دليل TTS:** ظهر GoogleTTSServiceImpl: Skipping Synthesiscallback.error() call for TextToSpeech.ERROR. في إعادة الاختبار ظهر مسار صوت نشط في AudioFlinger، ثم عادت الواجهة إلى وضع قارئ النص بعد الإيقاف/فشل التوليف.

**الاستنتاج:** فساد استخراج طبقة النص الرقمية مثبت؛ كما ظهر خطأ توليف إضافي، لكن لا تكفي الأدلة لعزوه إلى إعداد الصوت وحده. لا يُفترض أن OCR هو الحل الوحيد؛ افحص Encoding/Glyph maps والخطوط واتجاه RTL في PDF.

**الإصلاح المطلوب:** تحقق من جودة النص المستخرج قبل speak()؛ ارفض النص الفارغ/المشوه برسالة مفهومة؛ عالج اتجاه RTL والترميز والخطوط؛ وافصل خطأ الاستخراج عن خطأ محرك TTS. لا تعلن بدء قراءة الكتاب إذا كان النص غير صالح.

**اختبارات القبول:** بداية الكتاب وصفحة وسطية وصفحة معادلات وصفحة حقوق نشر؛ قارن النص الخام بالصورة المرئية قبل النطق، وسجل speak() وUtterance callbacks.

**الأدلة:** %TEMP%\tanweer-pdf-round-20261118، reader-page55-main.png، inspect_pdf_text.py/نسخة Room، وtts-retest.log.

## P1-04 — عقد HTTP لإرسال رسائل المحادثة لا يطابق نموذج Android

**مراجع التقرير:** TNW-SYNC-CHAT-001، TNW-GROUP-CHAT-HTTP-ACK-SHAPE-001/002، TNW-GROUP-CHAT-STALE-COMPOSER-001، TNW-GROUP-CHAT-RETRY-IDEMPOTENCY-002.
**الأولوية:** P1 — حالة إرسال الرسالة لا تعكس ما حدث فعلياً في الخادم.

**النتيجة المتكررة:** POST إلى /api/v1/groups/{groupId}/messages أعاد HTTP 200 وحُفظت الرسالة في D1، لكن Android فشل في تحليل الرد.

**سبب اختلاف العقد:** Android يعرّف GenericResponse.message على أنه String? في TanweerApiService.kt، بينما server/src/services/chat.ts يعيد message: messagePayload ككائن يحتوي id وgroupId وsender وtext وtimestamp وstatus. Moshi يسجل Expected a string but was BEGIN_OBJECT at path $.message.

**الأثر:** sendChatMessageOverHttp يعيد false رغم حفظ الرسالة؛ التطبيق يعرض FAILED ويعيد النص إلى حقل الكتابة. تظهر الرسالة لاحقاً من GET التاريخ وتتحول إلى SENT. قد يظن المستخدم أنها لم تُحفظ ويعيد إرسال النص نفسه.

**إعادة المحاولة:** retryChatMessage يعيد استخدام المعرّف نفسه؛ INSERT OR IGNORE مع تحقق هوية الصف يمنع إدراج نسخة جديدة، لكن الواجهة قد تبقى FAILED حتى جلب التاريخ.

**الإصلاح المطلوب:** استخدم DTO خاصاً برد الرسالة الذي يحتوي message object، أو غيّر عقد الخادم والعميل معاً. ميّز بين «حُفظت الرسالة لكن تعذرت قراءة ACK» و«لم تُحفظ»، وطابق الرسالة بالـID، وحدّث الحالة فوراً عند reconciliation، ولا تترك النص نفسه في محرر رسالة جديدة إذا ثبت حفظه.

**اختبارات القبول:** object response، فشل parser، history reconciliation، retry بنفس ID، composer restoration، جهازان وحسابان، وعدم إنشاء رسالة مكررة.

**الملفات:** TanweerApiService.kt، TanweerRepository، server/src/services/chat.ts.
**الأدلة:** %TEMP%\tanweer-sync-round-20261118، %TEMP%\tanweer-groups-round-20261119، %TEMP%\tanweer-live-chat-20261119؛ رسائل QA SYNCQA_CHAT_20261118_A1/B1 وQAPHONE177_1719 وQAEMU177_1740.

## P1-05 — وصول المحادثات اللحظي غير موثوق بسبب إعادة اتصال WebSocket

**مراجع التقرير:** TNW-SYNC-CHAT-002، TNW-GROUP-CHAT-WS-RECONNECT-001، TNW-GROUP-CHAT-LIVE-BIDIRECTIONAL-002، TNW-GROUP-CHAT-WS-STABILITY-002.
**الأولوية:** P1 — الرسائل قد لا تصل مباشرة إلى الطرف الآخر رغم إمكان جلبها لاحقاً.

**الدليل:** أثناء إبقاء شاشتي المحادثة مفتوحتين، أرسل المحاكي ثم الهاتف؛ لم تظهر بعض الرسائل عند الطرف المستقبل مباشرة، ووصلت بعد الخروج وإعادة فتح المحادثة وتنفيذ GET للتاريخ. ظهرت حالات HTTP 200 وحفظ D1 دون إثبات بث حي مكافئ.

**استقرار الاتصال:** على Galaxy S9 ظهر GET /ws/groups/class_10_C كل نحو 30 ثانية يتبعه WS failed ... HTTP=null؛ وفي جولة لاحقة ظهر javax.net.ssl.SSLException: Read error ... Connection reset by peer ثم reconnect و101 Switching Protocols. تكررت إخفاقات على المحاكي أيضاً.

**السبب/النطاق:** RealtimeChatManager.kt يعالج onFailure ويعيد الاتصال بمهلة متزايدة، لكن لم يظهر heartbeat/ping أو فحص صحة صريح للجلسة. يوجد مسار broadcast في server/src/services/chat.ts وGroupChatDO.broadcast يرسل إلى sessions؛ وجود المسار لا يثبت وصول الحدث إلى الجلسة.

**الإصلاح المطلوب:** heartbeat/health check، توثيق عمر الجلسة، رصد أسباب HTTP=null وSSL reset، والتحقق من بقاء session صالحة. لا تعرض «متصل» اعتماداً على آخر onOpen فقط. احتفظ بمصالحة history بالـID كاحتياط لا كبديل عن إثبات live delivery.

**اختبارات القبول:** رسائل في الاتجاهين بين حسابين مع بقاء الشاشتين مفتوحتين، قطع/عودة الشبكة، sleep/resume، reconnect، ومنع الفقد والتكرار.

**الأدلة:** %TEMP%\tanweer-groups-round-20261119\evidence و%TEMP%\tanweer-live-chat-20261119.

## P1-06 — حساب ADMIN ما زال مرتبطاً بصف وشعبة

**مرجع التقرير:** TNW-ADMIN-ROUND-20261120، القسم 1.
**الأولوية:** P1 — هوية مدير المدرسة لا تطابق شرط الاستقلال عن أي صف/شعبة.

**الدليل المحلي:** نسخة Room للقراءة فقط أظهرت cached_user.role=ADMIN مع gradeId=10 وsectionId=C وdefaultGroupId=class_10_C، وعضوية class_10_C بدور ADMIN، إضافة إلى مجموعة all بدور ADMIN.

**التناقض المرئي:** الملف الشخصي يعرض «الإدارة المدرسية العامة»، لكن أسفله يظهر «مساهماتي الدراسية في الشعبة»، وشاشة «اليوم» تبقي سياق الصف والشعبة.

**السبب حسب مراجعة المصدر:** server/src/services/dashboard.ts في handleChangeUserRole يغير الدور العام ويحدث أدوار العضويات، لكنه لا يزيل عضوية class_* ولا يمسح grade_id/section_id. server/src/services/auth.ts يمنع defaultGroupId التلقائي عند دخول ADMIN لكنه يستمر في إرجاع gradeId وsectionId.

**غياب مسار آمن:** مغادرة مجموعة CLASS محظورة، ومسار إزالة عضو المجموعة يمنع إزالة الذات، ومسار فصل طالب المدرسة يمنع فصل حساب المدير، وhandleUpdateProfile لا يقبل gradeId/sectionId كـnull. لم يتم تجاوز هذه القيود أو تعديل DB يدوياً.

**الإصلاح المطلوب:** مسار ترحيل/تغيير دور آمن يفصل ADMIN عن الشعبة والصف، يمسح grade_id وsection_id وdefaultGroupId وعضوية CLASS فقط، ولا ينشئ عضوية طالب بديلاً، ويحافظ على عضوية المدرسة العامة وسجل الحساب.

**اختبارات القبول:** حساب ADMIN جديد وحساب تمت ترقيته، تسجيل خروج/دخول، الملف الشخصي واليوم والبوابة ومجموعة all، مع التأكد من عدم بقاء class group.

**الأدلة:** %TEMP%\tanweer-admin-round-20261120\evidence ونسخة Room للقراءة فقط.

## P1-07 — لوحة ADMIN لا تغطي النطاق الإداري وتوجد فجوة بين صلاحية التدقيق وحماية API

**مرجع التقرير:** TNW-ADMIN-ROUND-20261120، القسم 2.
**الأولوية:** P1 — إدارة المدرسة والمحاسبة الإدارية غير متسقة.

**الموجود في Android:** أربع مؤشرات أساسية فقط (الطلاب، الموقوفون، الحسابات، المجموعات)، قائمة الطلاب، البحث، إيقاف/فصل الطالب، ونموذج تعميم مدرسي.

**المفقود من الواجهة:** إدارة المعلمين والمشرفين والمديرين، سجلات التدقيق، إدارة الجلسات/الأجهزة، ومراجعة طلبات الأدوار؛ نموذج بيانات الخادم يتضمن مؤشرات الكادر والعضويات والمحتوى والوسائط والإحصاءات الأكاديمية التي لا تظهر في الواجهة.

**تعارض الصلاحية:** server/src/lib/permissions.ts يمنح ADMIN VIEW_AUDIT_LOG وMANAGE_USERS وREVOKE_SESSIONS وREVIEW_ROLE_REQUESTS، لكن /api/dashboard/audit-logs في server/src/index.ts محمي داخل verifyOwnerAuth ويشترط مالك المنظومة، ولا يوجد له سطح في تطبيق المدير. مسار تغيير الدور العام /api/dashboard/users/:id/role محمي كذلك بمصادقة مالك المنظومة.

**الإصلاح المطلوب:** حسم عقد الصلاحيات: إما تعديل مصفوفة الصلاحيات أو route guards بحيث تتطابق، ثم بناء سطح Android للعمليات المصرح بها للمدير. أبقِ SYSTEM_OWNER منفصلاً ولا توسع صلاحيات ADMIN ضمنياً.

**اختبارات القبول:** حساب ADMIN وSYSTEM_OWNER وحساب طالب؛ اختبر السماح والرفض لكل endpoint والواجهة، وسجل Audit يثبت الفاعل والهدف والسبب. لا تختبر عمليات حظر/فصل حسابات حقيقية دون بيانات QA معتمدة.

**الأدلة:** %TEMP%\tanweer-admin-round-20261120\evidence، ومراجعة permissions.ts وserver/src/index.ts وAdminDashboardScreen.kt.

---

# المرحلة 2 — P2: صحة البيانات والحالات والتواريخ والإشعارات

## P2-01 — السجلات المحلية FAILED تظهر كاختبارات معتمدة وتدخل العدادات

**مراجع التقرير:** TNW-UI-EXAM-001، TNW-EXAM-STATUS-002، TNW-CALENDAR-FAILED-LOCAL-EXAM-COUNT-001، نتائج أرشيف الشعبة في جولة 20 نوفمبر، وTNW-WHATDIDIMISS-COMPREHENSIVE-ROUND-20261120.
**الأولوية:** P2 — موثوقية الأرقام والسجل الأكاديمي.

**إعادة الإنتاج:** محاولة إنشاء اختبار شهري رُفضت من Worker بـHTTP 409 بسبب تعارض/عنوان QA غير مكتمل. بقي صف محلي في cached_exams بعنوان مختصر مثل SYNCQA_ أو _SYNCQA وحالته syncStatus=FAILED، دون سجل مقابل على الخادم.

**الأثر عبر الشاشات:** المحاكي أظهر 3 اختبارات مقابل 2 على الهاتف، و2 شهرية مقابل 1، وفي جولة أخرى 5 مقابل 4 في التقويم. أرشيف الشعبة أظهر 5 على المحاكي مقابل 4 على الهاتف. الصف نفسه يدخل في إجمالي الاختبارات والشهرية وعدد المراجعة والتقويم واليوم وماذا فاتني والأرشيف.

**السبب المسجل:** بعض استعلامات العرض تعد كل صفوف DAO دون استبعاد syncStatus=FAILED، والواجهة لا تميز الصف المحلي الفاشل عن السجل المعتمد.

**الإصلاح المطلوب:** فصل السجلات المؤكدة عن pending/failed في كل الشاشات والمجاميع. لا تحتسب FAILED ضمن إجمالي السجلات المنشورة. إما إظهارها في مساحة «تعذر الإرسال» مع إعادة محاولة/تعديل/تخلص آمن، أو استبعادها من القوائم الرئيسية حتى يحسم المستخدم مصيرها. لا تمسح الصف تلقائياً إذا كان يحتاج إعادة إرسال أو إصلاحاً.

**اختبارات القبول:** FAILED/PENDING/SYNCED، retry ناجح، retry مكرر، رفض 409، إعادة دخول، مقارنة جهازين، ومراجعة Exams/Calendar/Archive/WhatDidIMiss.

**الملفات:** TanweerRepository.syncExams، ExamsScreen.kt، CalendarScreen.kt، ClassKnowledgeBaseScreen.kt، WhatDidIMissScreen.kt.
**الأدلة:** %TEMP%\tanweer-exams-round-20261119، %TEMP%\tanweer-archive-knowledge-round-20261120\evidence، %TEMP%\tanweer-catchup-round-20261120\evidence، %TEMP%\tanweer-calendar-batch-20261119.

**حالة التنفيذ — 2026-11-21:** IMPLEMENTED / AUTOMATED REGRESSION TESTS PASS / PHASE 5 ACCEPTANCE OPEN.

- استعلامات القوائم المعتمدة والتاريخية واليومية تعيد SYNCED فقط؛ وهذا هو المصدر المشترك المستخدم في Exams وCalendar وClass Knowledge Base وAcademic History وHome وSearch وSubject Timeline وWhatDidIMiss والتنبيهات. لذلك لا تدخل FAILED أو المسودات غير المؤكدة في الأرقام المنشورة.
- شاشة الاختبارات تعرض PENDING/SYNCING/LOCAL في مساحة انتظار المزامنة، وFAILED في مساحة مستقلة مع إعادة محاولة أو التخلص المحلي المؤكد. لا تعرض حالة «الجدول فارغ» فوق هذه السجلات عند عدم وجود اختبارات مؤكدة.
- الرفض الدائم مثل HTTP 409 يحفظ سجل الاختبار الفاشل مع payload ورسالة الخطأ في Outbox. إعادة المحاولة ذرية ومحددة لعملية إنشاء الاختبار فقط، وتعيد الصف إلى PENDING وتمنع التكرار أثناء PENDING/SYNCING. عمليات التبديل والحذف والكيانات الأخرى لا يعاد إرسالها أو حذفها بواسطة هذا الزر.
- دمج اللقطات: عند غياب سجل فاشل عن الخادم تبقى المسودة محلياً؛ وإذا أكدت لقطة صحيحة سجلاً بالمعرّف نفسه، تنتصر نسخة الخادم وتصبح SYNCED. لا تُحذف السجلات الفاشلة تلقائياً لمجرد فشل الإرسال.
- **التحقق الآلي المحلي:** ExamVisibilityPolicyTest 5/5، PendingSnapshotRetentionTest 4/4، ExamDeleteOutboxTest 8/8 — المجموع 17/17 بلا فشل أو تخطي. المجموعة الكاملة 129 اختباراً: 128 ناجحة وفشل واحد فقط في StudentNotebookManagerTest.valid notebook records round trip through atomic storage بسبب سلوك File.renameTo على Windows/Robolectric؛ الاختبار لم يتغير، وقد اجتاز على Ubuntu في Android CI للمرحلة السابقة. لا تُخفَ هذه النتيجة ولا تُضعف توقع الاختبار.
- **بناء Staging Debug:** نجح :app:assembleStagingDebug. الناتج app/build/outputs/apk/staging/debug/app-staging-debug.apk، الحجم 33,554,805 بايت، SHA-256 81D6FDCC6876A26DC082F0A9AB04ACEBDD07EE3FA1F0D434BF0B7454DAD64D17. بُني فقط ولم يُثبت أو يُشغّل.
- **المتبقي لمرحلة 5:** تجربة Worker HTTP الحقيقية لرفض 409 ثم تصحيح السبب وإعادة إرسال ناجحة، إعادة فتح التطبيق بعد الفشل، مقارنة جهازين، ومراجعة الواجهة فعلياً في Exams/Calendar/Archive/WhatDidIMiss. لا تُعلَن هذه الاختبارات مقبولة قبل تنفيذها على الجهازين/البيئة الحقيقية وفق خطة Phase 5.

## P2-02 — نوع TASK/مشروع يُعرض ويُحسب كأنه واجب

**مراجع التقرير:** TNW-CALENDAR-UX-002، TNW-HW-COUNT-001، TNW-CALENDAR-TASK-TYPE-RENDERING-001، وملاحظات إشعار الواجبات بتاريخ 19–20 نوفمبر.
**الأولوية:** P2، مع امتداد للأرقام والإشعارات.

**الدليل:** QAPROJ1 محفوظ على الجهازين taskType=TASK وsyncStatus=SYNCED. شاشة الواجبات تعرضه «مشروع / بحث»، لكن يوم التقويم يعرض «واجب القرآن الكريم: QAPROJ1» وبأيقونة الواجب. رأس قائمة الواجبات يقول «3 واجب» مع أن عنصرًا واحداً من الثلاثة TASK. إشعار ID 2001 يقول «لديك 3 واجبات غير مكتملة، منها: QAPROJ1».

**السبب:** التصنيف غير موحد؛ TanweerNotificationManager.kt يبني pending من جميع homeworks دون استثناء TASK ثم يستخدم pending.size وpending.first().title.

**الإصلاح المطلوب:** مصدر موحد لتسمية HOMEWORK وTASK/Project. افصل العدادات والنصوص والأيقونات في Homework وCalendar واليوم والإشعار، ولا تضم المشاريع لعدد الواجبات.

**اختبارات القبول:** Homework list/header، calendar day/detail، اليوم، ماذا فاتني، إشعار الواجبات وفلاتر HOMEWORK/TASK.

**الأدلة:** %TEMP%\tanweer-sync-round-20261118، %TEMP%\tanweer-homework-round-20261119، %TEMP%\tanweer-calendar-batch-20261119\evidence، %TEMP%\tanweer-notifications-round-20261119\evidence.

**حالة التنفيذ — 2026-11-22:** IMPLEMENTATION + AUTOMATED CI VERIFIED / PHASE 5 ACCEPTANCE OPEN.

- أُضيف `HomeworkTaskTypePolicy` كمصدر تصنيف واحد: `HOMEWORK` والقيم القديمة null/blank تُعامل كواجب، و`TASK` كمشروع، والقيم غير المعروفة تبقى ظاهرة في «الكل» لكنها لا تدخل عداد الواجبات أو المشاريع.
- شاشة التكليفات تفصل إجمالي الواجبات والمشاريع والأنواع الأخرى، وتفصل ما يستحق اليوم، كما أصبحت فلاتر HOMEWORK وTASK والعدادات الزمنية تستخدم التصنيف الموحد. شارات العناصر وحالة عدم وجود نتائج تذكر نوع العنصر الصحيح.
- التقويم يفصل أعداد الواجبات والمشاريع والتكليفات غير المصنفة، ومؤشرات الأيام، وبطاقات تفاصيل اليوم والنافذة المنبثقة. لا تظهر TASK بعنوان أو لون واجب.
- بطاقة اليوم الرئيسية تفصل الواجبات والمشاريع والتكليفات الأخرى غير المنجزة. «ماذا فاتني» تحتفظ بقسم تكليفات موحد مع شارة نوع لكل عنصر ونص إكمال مناسب.
- إشعار ID 2001 يجمع HOMEWORK فقط. التذكير الفردي يحافظ على إمكانية تذكير المشروع لكنه يسميه مشروعاً، ويستخدم موارد عربية/إنجليزية.
- أُضيفت `HomeworkTaskTypePolicyTest` لتغطية القيم القديمة والتطبيع وTASK والأنواع غير المعروفة.
- **Exact-SHA CI:** commit `89e16d4ab99b7068ce13a4b7704acd196e501172` — Android run `45013081671` PASS: `testStagingDebugUnitTest`, `assembleStagingDebug`, and Debug artifact upload. Signed Release steps were skipped as expected. Server run `45013081388` PASS: TypeScript typecheck, dependency security baseline, 42 Vitest files / 293 tests, and Wrangler Production + Staging dry-run validation. Validation only; no deployment.
- **المتبقي لمرحلة 5:** مراجعة بصرية فعلية لفلاتر HOMEWORK/TASK والقائمة والتقويم واليوم و«ماذا فاتني»، والتحقق من إشعار ID 2001 وتذكير مشروع فردي على Android 10 و15. لا تُجرَ هذه الاختبارات الآن ولا تُسجل PASS قبل مرحلة 5.

## P2-03 — إيقاف مفتاح الإشعار لا يلغي الإشعار النشط فوراً

**مراجع التقرير:** عيوب دفعة الإشعارات المؤكدة 19 نوفمبر، وتأكيد الإعادة على Galaxy S9 وAndroid 15 بتاريخ 20 نوفمبر.
**الأولوية:** P2.

**إعادة الإنتاج:** مع إشعار فعال ID 2001 أو 2002، أوقف notify_homework أو notify_exams. SharedPreferences أصبحت false لكن NotificationManager ظل يعرض ID على المحاكي Android 15 وGalaxy S9 Android 10.

**السبب المؤكد:** دوال setNotifyHomework/setNotifySchedule/setNotifyExams في TanweerViewModel.kt تحفظ القيمة وتستدعي scheduleDailyReminder(app)، لكنها لا تستدعي checkAndSendReminders(app) إلا عند التفعيل. مسار cancel داخل TanweerNotificationManager.kt موجود ضمن الفحص، لكنه لا يُستدعى عند انتقال المفتاح إلى false. scheduleDailyReminder يعيد جدولة فحص يومي ولا يلغي الإشعار الحالي.

**الإصلاح المطلوب:** عند الانتقال إلى false، ألغِ الإشعار المرتبط فوراً، وألغِ المنبهات/الأعمال المجدولة التابعة للنوع عند الحاجة. افصل الإشعار الملخص عن المنبه الفردي حتى لا يبقى أحدهما بعد إيقاف الآخر.

**اختبارات القبول:** إيقاف كل نوع مع وجود إشعار منشور، بعد إعادة تشغيل العملية، وعلى Android 10 و15؛ تأكد من الإلغاء الفوري وعدم إعادة النشر في الفحص اللاحق. أعد مفاتيح QA إلى baseline.

**الأدلة:** %TEMP%\tanweer-notifications-round-20261119\evidence، phone-settings-academic-notifications.xml، tanweer_prefs.xml ولقطات NotificationManager.

### P2-03 implementation checkpoint (2026-11-22)

**Status:** IMPLEMENTATION + LOCAL TESTS/BUILD + EXACT-SHA ANDROID/SERVER CI VERIFIED / PHASE 5 DEVICE ACCEPTANCE OPEN.

- The three settings handlers now delegate to category-specific notification-manager operations. Turning homework, exams, or schedule notifications OFF cancels its summary ID (2001, 2002, or 2003) synchronously and does not cancel the other categories.
- Homework and exam individual reminder IDs are persisted in separate SharedPreferences string-set registries at the same locked point where AlarmManager accepts the PendingIntent. This lets the OFF path cancel scheduled alarms and already-posted item notifications even when an item was later removed from Room. Cached Room IDs are also checked as a compatibility/recovery fallback. Account purge clears both registries.
- OFF transitions increment a category generation. The settings values and generations are read atomically; summary/individual posts and new AlarmManager scheduling verify the enabled preference plus the same generation immediately before delivery/scheduling. This blocks old asynchronous checks and in-flight schedules from reviving alerts after OFF, including an OFF/ON race. Individual alarm receivers capture that generation before their asynchronous Room lookup and pass it through to the notification publisher, so an already-delivered stale broadcast cannot publish after a rapid OFF/ON transition.
- A persisted OFF setting is reconciled by the next reminder check after process recreation. Individual homework/exam scheduling returns NOTIFICATIONS_DISABLED while its global type is OFF, and the calling screens display a localized Arabic/English explanation.
- Local tests: NotificationToggleBehaviorTest 10/10 (Robolectric API 28), NotificationDeliveryPolicyTest 2/2, and existing AcademicReminderPolicyTest 3/3. Total 15/15, with zero failures, errors, or skips. These are automated local tests, not Android 10/15 physical-device acceptance.
- Local :app:assembleStagingDebug passed. APK: app/build/outputs/apk/staging/debug/app-staging-debug.apk; size 33,573,932 bytes; SHA-256 2FE0E0C405BF84C21F890C34884BBB1E93BCDA7D66244796BE58AB9E55DC0CB7. Built only; not installed or launched.
- git diff --check passed. The temporary Gradle memory/worker adjustments were reverted; gradle.properties matches its original tracked content.
- **Exact-SHA GitHub CI:** commit `35763ee8b6e8faa217df51798fcb2ec7274841b9` ? Android workflow run `45021785196` completed success: `testStagingDebugUnitTest` and `assembleStagingDebug` passed, and Debug APK artifact upload passed. Release signing/build/upload steps were skipped. Server workflow run `45021785246` completed success: TypeScript typecheck, dependency security baseline, 42 Vitest files / 293 tests, and Wrangler Production + Staging dry-run validation passed. No deployment occurred.
- **Still open for Phase 5:** verify the visible alert disappears immediately when toggled OFF and cannot reappear after a process restart on the actual Android 10 Galaxy S9 and Android 15 device; check both summary IDs and individual reminder IDs, then restore the QA preference baseline. Do not mark this physical-device acceptance as passed here.
- **CI closeout:** exact-SHA Android and Server workflow runs are both green for implementation commit 35763ee8b6e8faa217df51798fcb2ec7274841b9 (Android 45021785196; Server 45021785246). No Worker deployment, secrets change, D1 migration, production write, Release signing, app installation/launch, main update, or PR merge was performed.


## P2-04 — موعد الاختبار يُخلط مع حالة المراجعة الشخصية

**مراجع التقرير:** TNW-EXAM-STATUS-001 وTNW-EXAM-STATUS-002.
**الأولوية:** P2.

**الدليل:** على Galaxy S9 أُلغيت مراجعة QAORAL1؛ POST /api/v1/exams/{id}/toggle-completion أعاد HTTP 200، وRoom أظهر isCompleted=0 وsyncStatus=SYNCED، لكن رأس التفاصيل بقي «مكتمل ✓».

**السبب:** ExamDetailModal يحسب isDone = exam.isCompleted || isExamDatePassed(...) للحالة الرئيسية، بينما إجراء المراجعة يعتمد isCompleted وحده. كما أن completedCount الشهري يستخدم isCompleted OR isExamDatePassed، لذلك يعرض «تمت مراجعة 2 من 2» رغم أن isCompleted=0 لكليهما ويشمل صف FAILED.

**الإصلاح المطلوب:** فصل حالة الموعد (قادم/منقضي) عن حالة المراجعة الشخصية (تمت المراجعة/لم تتم). احسب عداد المراجعة من isCompleted فقط، مع تطبيق قاعدة السجلات FAILED في P2-01.

**الأدلة:** %TEMP%\tanweer-sync-round-20261118\phone-oral-unreviewed.png و%TEMP%\tanweer-exams-round-20261119\filter-monthly.xml وRoom snapshot.

### P2-04 implementation checkpoint (2026-11-22)

**Status:** IMPLEMENTED / FOCUSED LOCAL TESTS PASS (8/8) / LOCAL STAGING DEBUG BUILD PASS / EXACT-SHA ANDROID + SERVER CI VERIFIED / PHASE 5 DEVICE ACCEPTANCE OPEN.

- Added the shared ExamReviewPolicy. A review is true only when isCompleted is true on a SYNCED exam. FAILED, PENDING, SYNCING, LOCAL, and DELETE_PENDING rows are excluded from both the reviewed numerator and the confirmed denominator.
- The monthly Exams summary now derives its reviewed count from explicit completion state only; an elapsed exam date can no longer be counted as a completed personal review. The month count badge uses the same confirmed-only denominator.
- ExamDetailModal now shows schedule timing and personal review as separate statuses: passed / today / upcoming versus reviewed / not reviewed. Toggling review continues to use isCompleted only.
- Added localized Arabic and English strings for both status dimensions and monthly progress/count labels.
- Local focused tests: ExamReviewPolicyTest (3) and existing ExamVisibilityPolicyTest (5) passed, total 8/8 with zero failures, errors, or skips. Both resource XML files parse and contain no duplicate string keys. The previous combined isCompleted || isExamDatePassed expression no longer exists in ExamsScreen.kt.
- Local :app:assembleStagingDebug passed. APK: app/build/outputs/apk/staging/debug/app-staging-debug.apk; size 33,576,366 bytes; SHA-256 53636AECC1F93AF92487618B2852FA22CD7AE9D90E402B3FCCCB3405DC074EDF. Built only; not installed or launched.
- **Exact-SHA GitHub CI:** implementation commit 9e090054dfd2d0f9277a4ea9e4d6a208d40eb968 was validated by Android workflow run 45028829069 (success: testStagingDebugUnitTest, :app:assembleStagingDebug, and Debug APK artifact upload; release signing/build steps skipped) and Server workflow run 45028829155 (success: TypeScript typecheck, dependency security baseline, 42 Vitest files / 293 tests, and Wrangler Production + Staging dry-run validation). No deployment occurred.
- **Still open for Phase 5:** reproduce the QAORAL1 review toggle on the Galaxy S9 / Android 10 and Android 15 devices; verify the header and monthly count update after server ACK, after leaving/reopening details, and after process recreation. Do not report device acceptance from Robolectric or CI.
- No backend contract, Room schema, migration, or server code was changed. No deployment, secrets change, D1 apply/write, release signing, emulator/phone install or launch, main update, or PR merge was performed.

## P2-05 — نطاقات «يوم أمس/آخر 3 أيام/الأسبوع/آخر أسبوعين» تشمل يوماً إضافياً

**مراجع التقرير:** TNW-WHATDIDIMISS-RANGE-001 وTNW-WHATDIDIMISS-COMPREHENSIVE-ROUND-20261120.
**الأولوية:** P2.

**الدليل:** يوم أمس في جولة 18 نوفمبر أظهر درس 17 نوفمبر واختبار 18 نوفمبر. وعند فحص منطق WhatDidIMissScreen.kt بتاريخ 20 نوفمبر كانت الحدود الشاملة كالتالي:
- أمس: 19–20 نوفمبر (يومان).
- آخر 3 أيام: 17–20 نوفمبر (4 أيام).
- الأسبوع الحالي: 13–20 نوفمبر (8 أيام شاملة، وهو نطاق متحرك وليس أسبوعاً تقويمياً).
- آخر أسبوعين: 6–20 نوفمبر (15 يوماً شاملة).

**السبب:** حساب البداية يطرح daysBack من اليوم، ويجعل النهاية اليوم نفسه مع مقارنة شاملة.

**الإصلاح المطلوب:** تعريف دلالة كل مرشح بوضوح وتنفيذ الحدود الصحيحة: يوم أمس ينتهي عند أمس، آخر N أيام يحدد هل تشمل اليوم، الأسبوع التقويمي له بداية أسبوع واضحة، وآخر أسبوعين 14 يوماً وفق سياسة متفق عليها. أضف اختبارات حدود اليوم والشهر والسنة والمنطقة الزمنية.

**الأدلة:** %TEMP%\tanweer-core-round-20261118 و%TEMP%\tanweer-catchup-round-20261120\evidence.

### P2-05 implementation checkpoint (2026-11-22)

**Status:** IMPLEMENTED / FOCUSED LOCAL TESTS PASS (16/16) / LOCAL STAGING DEBUG BUILD PASS / EXACT-SHA ANDROID + SERVER CI VERIFIED / PHASE 5 DEVICE ACCEPTANCE OPEN.

- Replaced the inline daysBack arithmetic with MissedDateRangePolicy, which resolves ISO local-calendar dates using the device TimeZone and Calendar. It does not subtract fixed 24-hour milliseconds, so DST transitions do not shift date boundaries.
- Explicit range semantics: Yesterday is yesterday only; Last 3 days includes today plus the previous two local dates; This week is Sunday through today (calendar week-to-date); Last 2 weeks is exactly 14 local dates including today. Since last visit remains the stored prior-visit date through today.
- Invalid or future lastOpenDate values safely fall back to today; valid saved visit dates are retained. The range is recalculated on screen recomposition so an updated local date is used when the screen refreshes.
- Added Arabic and English localized range labels instead of hard-coded Arabic-only labels.
- Added 8 deterministic date-policy tests covering year/month transitions, leap day, Sunday week boundary, 14-day inclusive counting, since-last-visit validation/clamping, one fixed UTC instant across different time zones, and the New York daylight-saving transition.
- Local focused tests: MissedDateRangePolicyTest 8/8, ExamReviewPolicyTest 3/3, and ExamVisibilityPolicyTest 5/5; combined 16/16 with zero failures, errors, or skips. Arabic and English resource XML both parse and have no duplicate string keys. git diff --check passed.
- Local :app:assembleStagingDebug passed. APK: app/build/outputs/apk/staging/debug/app-staging-debug.apk; size 33,578,884 bytes; SHA-256 FC1BB359FF6687094468CC0C7D50C0673C9DA992AD90F832CD28BF9967EE3B6C. Built only; not installed or launched.
- **Exact-SHA GitHub CI:** implementation commit e217ff83ca8f3bb2c2b6fd5d6b05df891985f149 was validated by Android workflow run 45032029810 (successful retry: testStagingDebugUnitTest, :app:assembleStagingDebug, and Debug APK artifact upload ID 14758635756; signed Release steps skipped) and Server workflow run 45032029839 (success: TypeScript typecheck, dependency security baseline, 42 Vitest files / 293 tests, and Wrangler Production + Staging dry-run validations). The initial Android attempt failed during Robolectric MavenArtifactFetcher downloads for 12 test class initializations; rerunning the failed Android job succeeded. No deployment occurred.
- **Still open for Phase 5:** visual acceptance of each range against real lesson/homework/exam/event records and the device's configured local timezone. No emulator or physical device was used here.
- No backend contract, Room schema, migration, Worker, sync, or database code was changed. No deployment, secrets change, D1 apply/write, release signing, emulator/phone install or launch, main update, or PR merge was performed.

## P2-06 — تغيير الشهر يبقي اليوم القديم والعدادات ليست شهرية

**مراجع التقرير:** TNW-CALENDAR-COUNTERS-MONTH-001، TNW-CALENDAR-MONTH-SELECTED-DATE-001، TNW-CALENDAR-COUNTS-GLOBAL-001.
**الأولوية:** P2.

**الدليل:** عند الانتقال من نوفمبر 2026 إلى ديسمبر ثم يناير 2027 تغير عنوان الشهر وطلب API، لكن لوحة اليوم المختار بقيت على الخميس 19 نوفمبر. بطاقات العد بقيت 1/0/1 عند الانتقال بين الشهور.

**السبب:** selectedDateStr لم يتغير/يُمسح عند تحديث currentCalendar؛ كما أن العدادات تستخدم أحجام قوائم كل سجلات المجموعة دون تصفية currentCalendarMonth.

**الإصلاح المطلوب:** عند تغيير الشهر اختر تاريخاً صالحاً من الشهر الجديد أو أفرغ لوحة اليوم حتى يختار المستخدم. إما أن تحسب العدادات للشهر المعروض أو تسميها صراحة «إجمالي سجلات الشعبة».

**الملف:** CalendarScreen.kt.
**الأدلة:** %TEMP%\tanweer-core-round-20261118 و%TEMP%\tanweer-calendar-batch-20261119\evidence.

### P2-06 implementation checkpoint (2026-11-22)

**Status:** IMPLEMENTED / FOCUSED LOCAL TESTS PASS (27/27) / LOCAL STAGING DEBUG BUILD PASS / EXACT-SHA ANDROID + SERVER CI PASS / PHASE 5 DEVICE ACCEPTANCE OPEN.

- **Month navigation:** Previous/next month navigation resets the navigation calendar to day 1 before applying the month offset, then updates both selectedDateStr and the ViewModel selected date. The selected day number is retained when possible and clamped to the target month's last valid day (e.g. January 31 -> February 28/29), so day details and selection never remain attached to the previous month.
- **Monthly summaries:** Exams use examDate; events use eventDate; homework, projects and unknown assignment types use either studyDate or dueDate. Each record is counted at most once per month, while an assignment spanning two months appears in each month where its day markers appear. Counts continue to display — until the selected month has an authoritative calendar snapshot.
- **Classification / trust boundary:** Existing HomeworkTaskTypePolicy classification remains unchanged: legacy blank/null records remain homework, TASK remains projects, and unknown future types remain separated. No global/group-wide totals are presented as monthly counts.
- **Policy and tests:** Added CalendarMonthPolicy and CalendarMonthPolicyTest (7 tests), covering November/December/January rollover, leap and non-leap February, short months, invalid dates/months, and assignment study/due dates crossing month boundaries without double-counting within one month.
- **Focused local verification:** CalendarMonthPolicyTest 7/7, HomeworkTaskTypePolicyTest 4/4, MissedDateRangePolicyTest 8/8, ExamReviewPolicyTest 3/3, and ExamVisibilityPolicyTest 5/5 passed: 27/27 total, zero failures, errors or skips. git diff --check passed.
- **Local build:** :app:assembleStagingDebug passed. APK: app/build/outputs/apk/staging/debug/app-staging-debug.apk, 33,583,340 bytes, SHA-256 FAC6B0609DDC8750AEB95D8FC43B47E1C04EC5A03DEB00A865572263D3DF2E32. Built only; not installed or launched.
- **Exact-SHA CI:** Code commit a42983cf0c1a1ef8010b323b5f119746623ddfde verified. Android run 45034477749 (attempt 2) PASS: all unit tests passed, Staging Debug APK assembly passed (48s), and Debug APK upload succeeded (artifact ID 14759446663; artifact ZIP 31,091,107 bytes; SHA-256 b78e34736549241360a3bc399ac83d9c14d8e238e42ac4b6a0f50ffc1bc6c427). Attempt 1 had one transient NotificationToggleBehaviorTest Robolectric MavenArtifactFetcher IOException during external artifact download; rerunning the failed job succeeded without source changes or test exclusions. Server run 45034477898 PASS: dependency audit, TypeScript typecheck, 42 Vitest files, Wrangler Production dry-run and Staging dry-run. No deployment occurred.
- **Phase 5 remains open:** Visually verify month changes and monthly counters against the real November/December 2026 and January 2027 class records on the configured device timezone. No emulator or physical device was used.
- **Safety / scope:** No backend/API contract, Room schema, migration, Worker/server source, sync behavior, deployment, secrets change, D1 apply/write, staging/production write, Release signing, emulator/phone install or launch, main update, PR merge or new branch was performed. PR #6 remains Open + Draft + unmerged; WebGPT task remains RUNNING.

## P2-07 — نموذج حالات الاستفسار لا يطابق المرشحات والبطاقات والتفاصيل

**مراجع التقرير:** TNW-ISSUES-STATUS-001، وملاحظة تأخر التحديث بعد اعتماد إجابة الأستاذ ضمن TNW-TEACHER-ROUND-20261120.
**الأولوية:** P2.

**التناقض:** سجل OPEN يظهر في البطاقة «مفتوح للنقاش» وفي التفاصيل «قيد النقاش»، لكن مرشح «قيد النقاش» يطابق IN_DISCUSSION وحده ولا يعرض السجل.

**حالة أخرى:** بعد اعتماد إجابة نموذجية وإقرار الخادم HTTP 200، بقيت حالة التفاصيل المفتوحة «قيد النقاش» حتى مغادرة القسم وإعادة فتحه. الحفظ في الخادم صحيح؛ حالة الواجهة stale.

**نقص نموذج:** الخادم يعيد is_teacher_answer لكن IssueCommentItem في Android لا يحمل الحقل، فلا تميز الواجهة إجابة الأستاذ بصرياً قبل اعتمادها.

**الإصلاح المطلوب:** قاموس حالات واحد يربط OPEN/IN_DISCUSSION/SOLVED/VERIFIED مع أسماء عربية ومرشحات متوافقة؛ تحديث Issue/Comments في Room وStateFlow بعد ACK؛ وإضافة is_teacher_answer إلى DTO/domain/UI.

**الأدلة:** %TEMP%\tanweer-core-round-20261118 و%TEMP%\tanweer-teacher-round-20261120\evidence.

### P2-07 implementation checkpoint (2026-11-22)

**Status:** IMPLEMENTED / FOCUSED ANDROID TESTS PASS (20/20) / LOCAL STAGING DEBUG BUILD PASS / SERVER TYPES + TESTS PASS (43 FILES / 295 TESTS) / EXACT-SHA ANDROID + SERVER CI PASS / PHASE 5 DEVICE ACCEPTANCE OPEN.

- **Shared status contract:** Added IssueStatusPolicy and IssueFilter. OPEN and IN_DISCUSSION both belong to the active discussion filter; SOLVED and VERIFIED belong to resolved; CLOSED has its own filter; unknown future values remain visible under All. List cards, the detail header, filters and search results all use the same status mapping and Arabic/English string resources.
- **ACK and stale-state ordering:** IssueDataSyncCoordinator exposes one process-wide mutex shared by the repository and UnifiedMutationQueue. Issue list/details reads, add/retry comment, best-answer approval and deferred outbox comment retries are serialized. A confirmed best-answer ACK immediately commits the SOLVED state and then refreshes the issue and comments; a confirmed comment ACK changes OPEN to IN_DISCUSSION and refreshes the same snapshot. A later/older detail response can no longer race those mutations and overwrite the acknowledged state.
- **Authoritative comment reconciliation:** The shared detail reconciler validates the requested issue ID and unique/nonblank comment IDs, transactionally replaces server-confirmed comments, preserves local PENDING/SYNCING/FAILED/LOCAL/DELETE_PENDING comments not yet represented by the server, replaces a pending row when the matching server ID arrives, updates best-answer/teacher-answer markers and reconciles the parent commentsCount from the authoritative snapshot. Follow-up GET failures do not turn a confirmed POST ACK into a failed write.
- **Teacher-answer contract:** The issue-details SQL now selects is_teacher_answer; the server mapper returns isTeacherAnswer; Android API/domain/Room models retain it; and issue detail displays a distinct teacher-answer badge even before the comment is selected as the best answer. The comment POST ACK also carries the marker into Room, including the offline outbox path.
- **Room migration:** Room schema version advanced from 16 to 17. MIGRATION_16_17 adds isTeacherAnswer INTEGER NOT NULL DEFAULT 0, preserving existing cached comments and their content. DatabaseMigrationTest verifies that an old comment survives and defaults the new marker to false.
- **Regression tests:** IssueStatusPolicyTest 4/4; IssueCommentReconciliationTest 3/3; DatabaseMigrationTest 1/1; PendingSnapshotRetentionTest 4/4; ExamReviewPolicyTest 3/3; ExamVisibilityPolicyTest 5/5. Total Android focused tests: 20/20, zero failures/errors/skips. Server: npm run types passed; 43 Vitest files / 295 tests passed, including the new issue-details teacher-answer contract regression.
- **Local build (after Android CI compile fix):** :app:assembleStagingDebug passed (BUILD SUCCESSFUL). APK app/build/outputs/apk/staging/debug/app-staging-debug.apk, 33,583,629 bytes; SHA-256 6CEB9E1C08373AF8A6FED6BBA8F4706A8A0908120BBAFB459DAAA77017076E99. Built only; not installed or launched.
- **Exact-SHA CI:** Final corrected source commit bbeb3e95130cf099cd4dae34b2ea78c3f71ca745 passed both PR workflows. Android run 45041331045 PASS: testStagingDebugUnitTest, Staging Debug assembly (BUILD SUCCESSFUL in 48s), and Debug APK upload. Artifact ID 14760929667, name Tanweer-debug-aefa432b2eeea54a0d3919f9217cab0e33eeaeb7, size 31,110,581 bytes, ZIP SHA-256 1ed639b2f64152398d2689b563d9db99b85c9566ea989354f23d6058756d8c32. Server run 45041331041 PASS: npm audit, TypeScript, 43 Vitest files / 295 tests, Wrangler Production + Staging dry-runs. The earlier implementation SHA 50c05318cb3f9248edf73cf41a27f72661a69beb had one Android compile failure caused by a duplicate R import; correction commit bbeb3e9 removed the duplicate and the exact-SHA CI above is green. No deployment occurred.
- **Phase 5 remains open:** Visually verify OPEN/IN_DISCUSSION/SOLVED filters, cards, detail header, teacher-answer badge, accepted-answer refresh and outbox reconnect using real student/teacher accounts on the supported Android versions. No emulator or physical device was used for this implementation.
- **Safety / scope:** No Worker deployment, Cloudflare secret changes, D1 migration/apply/write, staging/production writes, Release signing, app installation/launch, main update, PR merge or new branch. The Room migration is only committed code; it has not been run against any real user database. PR #6 remains Open + Draft + unmerged; WebGPT task remains RUNNING.
## P2-08 — عداد ردود بطاقة الاستفسار يتأخر عن التفاصيل

**مراجع التقرير:** TNW-UI-ISSUE-001 وTNW-ISSUE-COUNT-STALE-001.
**الأولوية:** P2 للحالة التي بقيت صفراً رغم حفظ التعليق، وP3 للتأخر المؤقت بعد الرد الثاني.

**الدليل:** بعد إضافة R2A عرضت التفاصيل إجابة واحدة لكن البطاقة بقيت 0، وRoom commentsCount=0 رغم وجود cached_issue_comments مع syncStatus=SYNCED. وفي الجولة اللاحقة عرضت التفاصيل إجابتين بينما بقيت البطاقة واحدة حتى إعادة فتح القسم وجلب GET ناجح.

**الإصلاح المطلوب:** تحديث/إبطال commentsCount بعد ACK، ومصالحة العدد من قائمة التعليقات أو رد الخادم؛ لا تنتظر إعادة فتح القسم.

**الأدلة:** phone-issue2-reply-sent.png وphone-issue2-list-after-reply.png تحت %TEMP%\tanweer-sync-round-20261118، وجولة %TEMP%\tanweer-two-device-20261119.

### P2-08 implementation checkpoint (2026-11-22)

**Status:** IMPLEMENTED / FOCUSED ANDROID TESTS PASS (21/21) / LOCAL STAGING DEBUG BUILD PASS / EXACT-SHA ANDROID + SERVER CI PASS / PHASE 5 DEVICE ACCEPTANCE OPEN.

- **Root cause:** The issue card reads `cached_issues.commentsCount` through Room Flow, but the count was refreshed only by a successful issue-details GET. A confirmed comment POST ACK changed the comment row to `SYNCED` yet left the parent count untouched when the follow-up GET failed. The P2-07 coordinator also deliberately skipped replacing the parent issue while it carried a local mutation, inadvertently skipping its count update too.
- **ACK reconciliation:** `IssueDao` now derives the confirmed reply count from rows with `syncStatus = 'SYNCED'` and updates only `cached_issues.commentsCount`. The operation does not replace the parent issue or modify its status, best-answer ID, local sync state or other fields. Direct add, user retry and `UnifiedMutationQueue` outbox ACK paths run this reconciliation in the same Room transaction as comment confirmation.
- **Details reconciliation:** The shared details coordinator recalculates the count while replacing the server-confirmed comment snapshot. The count update is independent of the local-mutation guard; the guard still protects the rest of the issue row. Pending/failed local comments remain visible but are not counted as server-confirmed replies. The coordinator returns the reconciled Room count.
- **Reactive cards:** Existing issue list, class knowledge-base and search cards all observe the same Room `IssueItem.commentsCount`; no parallel UI counter or manual screen reopen is required.
- **Regression tests:** `IssueCommentReconciliationTest` now verifies server snapshot count refresh, zeroing from an empty authoritative snapshot while preserving unacknowledged local rows, and immediate ACK count refresh while the parent issue has a local mutation. Combined focused Android suite: 21/21 tests, zero failures/errors/skips.
- **Local build:** `:app:assembleStagingDebug` passed. APK: `app/build/outputs/apk/staging/debug/app-staging-debug.apk` (33,583,629 bytes). Built only; not installed or launched.
- **Exact-SHA CI:** P2-08 source commit `cf310f0dae231260e63eb8951db9f81cc76036e3` passed Android run `45045181237` (unit tests, Staging Debug assembly and Debug APK upload; artifact ID `14763174129`, name `Tanweer-debug-24cf6f16ebc9f3519c1a92272df6c351fa457706`, size 31,112,584 bytes, ZIP SHA-256 `4282ba97f6758c3706d21a7cc4de75abfac88d15d73459cf9d7543f1e9e8464f`). Server run `45045181236` passed dependency audit, TypeScript, 43 Vitest files / 295 tests and Wrangler Production + Staging dry-runs. No deployment occurred. The preceding docs-only SHA `995eeaf4bc35dac790a2020c01ca2390fc49d512` had one transient Android-only MavenArtifactFetcher download failure (13/113 setup failures); P2-08 exact-SHA run above completed successfully without source changes or test exclusions.
- **Phase 5 remains open:** Verify the card count against the detail replies for an online ACK, a failed follow-up GET, a second reply, an outbox reconnect and a process/re-entry cycle on real student/teacher accounts. No emulator or physical device was used.
- **Safety / scope:** Android-only source changes; no server source, API contract, schema, migration, deployment, secrets, D1 writes, staging/production writes, release signing, install/launch, `main` update or PR merge. PR #6 remains Open + Draft + unmerged; WebGPT task remains RUNNING.

## P2-09 — تاريخ الدراسة في نموذج الواجب غير ظاهر

**مرجع التقرير:** TNW-HW-STUDYDATE-001.
**الأولوية:** P2/P3 — توضيح سياق التاريخ، وليس فساد بيانات مثبتاً.

**الدليل:** واجب تاريخ استحقاقه 2026-11-26 حُفظ studyDate=2026-11-18 بينما تاريخ الجهاز أثناء الاختبار 2026-11-19. Preference selected_study_date كانت 2026-11-18 وViewModel يستخدم التاريخ المختار. نموذج الإضافة يعرض Due Date لكنه لا يوضح تاريخ الدراسة الذي سيُحفظ.

**الإصلاح المطلوب:** إظهار تاريخ الدراسة المختار بوضوح وتوفير اختيار صريح/العودة إلى اليوم. لا تفرض اليوم تلقائياً لأن التاريخ السابق قد يكون اختياراً مقصوداً.

**الأدلة:** %TEMP%\tanweer-homework-round-20261119\homework-filled-fields.xml وRoom/preference snapshot.

### P2-09 implementation checkpoint (2026-11-22)

**الحالة:** IMPLEMENTED / ANDROID FOCUSED TESTS PASS (22/22) / STAGING DEBUG BUILD PASS / EXACT-SHA ANDROID + SERVER CI PASS / PHASE 5 DEVICE ACCEPTANCE OPEN.

- **سبب المشكلة:** نموذج `AddHomeworkDialog` كان يعرض موعد التسليم فقط، بينما `TanweerViewModel.addHomework()` يلتقط `selected_study_date` المخزّن دون إظهار القيمة التي ستُحفظ للمستخدم.
- **تاريخ البداية:** يبدأ حقل تاريخ الدراسة من التاريخ المختار الحالي إذا كان ISO صالحاً؛ لا يُستبدل تلقائياً بتاريخ الجهاز. عند غياب قيمة صالحة فقط، يعود افتراضياً إلى تاريخ اليوم.
- **تحكم صريح:** حقل مستقل ظاهر في نموذج الواجب، مع منتقي تاريخ وزر لاستخدام تاريخ اليوم لهذا الواجب. زر اليوم يغيّر حالة النموذج المحلية فقط ولا يغيّر تفضيل `selected_study_date` العام.
- **مسارات الحفظ:** تمرير `studyDate` صراحةً من `HomeworkScreen` ومن `CreateOfficialHomeworkSheet` إلى ViewModel، ثم إلى `addHomework` و`addHomeworkWithImages`. القيمة الافتراضية في ViewModel تُبقي توافق الاستدعاءات القديمة التي لا تمرر التاريخ.
- **التعريب:** تمت إضافة مفاتيح العنوان والوصف والاختيار واليوم والتأكيد والإلغاء إلى `values/strings.xml` و`values-en/strings.xml`؛ XML صالح ولا توجد مفاتيح مكررة.
- **الاختبارات المحلية:** 22 اختباراً في 7 ملفات، صفر فشل/أخطاء/تخطي؛ تشمل حالتي الحفاظ على تاريخ سابق صالح والرجوع لليوم عند غياب قيمة أو عدم صلاحيتها.
- **البناء المحلي:** `:app:assembleStagingDebug` نجح. APK `app/build/outputs/apk/staging/debug/app-staging-debug.apk` بحجم 33,590,705 بايت وSHA-256 `4945E734A6C2E67E83A6F5E7ED4DA22CED96B560AD82D5711598B77B2279F8C0`. تم البناء فقط؛ لم يتم تثبيته أو تشغيله.
- **التحقق exact-SHA:** commit التنفيذ 07ccb85c55d92e8c3963c5bb523f0f8833fde5ac اجتاز Android run 45048086662: اختبارات الوحدة، assembleStagingDebug (BUILD SUCCESSFUL)، ورفع Debug APK. Artifact ID 14763848718، الاسم Tanweer-debug-79d852147c9027ff98be1d9ba2a5eb0da0363bb8، الحجم 31,121,214 بايت، ZIP SHA-256 a45fa419bee786c79430570aad3084846bf5c1a158d934a3a63c64d363c13ac0. Server run 45048086552 اجتاز npm audit وTypeScript و43 ملف Vitest / 295 اختباراً، وWrangler dry-run لـProduction وStaging. لم يحدث نشر.
- **Phase 5:** ما زال اختبار فتح النموذج واختيار التاريخ والحفظ/إعادة الفتح ومقارنة Room على الأجهزة الفعلية مؤجلاً للمرحلة الخامسة؛ لم تُستخدم أجهزة أو محاكيات.
- **النطاق:** Android/UI + ViewModel + موارد واختبارات فقط. لا API أو Server أو schema أو migration أو قاعدة بيانات بعيدة أو نشر أو أسرار أو release أو تعديل main أو دمج PR. التنفيذ 07ccb85c55d92e8c3963c5bb523f0f8833fde5ac؛ CI Android 45048086662 وServer 45048086552 ناجحان على SHA نفسه.
## P2-10 — مرشح المكتبة يطابق المادة الفرعية بسبب contains

**مرجع التقرير:** TNW-LIBRARY-SUBJECT-FILTER-001.
**الأولوية:** P2.

**الدليل:** مرشح «الأحياء (1)» أظهر كتاب الأحياء وكتاب «الأنشطة والتجارب - الأحياء» معاً. LibraryScreen.kt يستخدم احتواء جزئياً لمعرف المادة بدل التطابق الكامل. إزالة المرشح أعادت 26 كتاباً.

**الإصلاح المطلوب:** مطابقة subjectId canonical بدقة، مع دعم علاقة المادة/المادة الفرعية صراحة؛ لا تعتمد على احتواء نصي عام.

**الأدلة:** %TEMP%\tanweer-core-round-20261118.

### P2-10 completed (2026-11-22)

**الحالة:** IMPLEMENTED + COMMITTED / FOCUSED ANDROID TESTS PASS (32/32) / STAGING DEBUG BUILD PASS / EXACT-SHA ANDROID + SERVER CI PASS / PHASE 5 DEVICE ACCEPTANCE OPEN.

- **سبب الخلل:** LibraryScreen.kt كان يستخدم book.subjectId.contains(selectedSubjectFilter)، لذلك مرشح biology كان يلتقط biology_act أيضاً. هذا احتواء جزئي لمعرف معياري وليس علاقة مادة/مادة فرعية مقصودة.
- **المطابقة المعيارية:** أضيفت LibrarySubjectFilterPolicy. تطبيع المعرفات يقبل المعرفات المعيارية والمرادفات الكاملة المعروفة فقط؛ لا يغيّر السلاسل الأطول أو المتشابهة بالاحتواء النصي.
- **علاقات الأب/الفرع الصريحة:** biology -> biology_act، chemistry -> chemistry_act، physics -> physics_act، english -> english_work. الوضع الافتراضي يطابق المادة المحددة فقط. خيار «يشمل المواد الفرعية» يظهر فقط عند وجود كتب فرعية متاحة، وتفعيله يضم الفروع المسجلة صراحةً فقط.
- **واجهة المكتبة:** تجميع شرائح المواد وعداداتها يستخدم المعرف المعياري. عداد الشريحة يعرض كتب المادة المباشرة؛ اختيار إدراج الفروع منفصل وواضح، ويُلغى عند تغيير الصف أو المادة أو اختيار جميع المواد.
- **الموارد:** أضيفت تسمية الشريحة بالعربية والإنجليزية، وتم التحقق من XML ومن وجود المفتاح مرة واحدة في كل لغة.
- **اختبارات السياسة:** 5 اختبارات جديدة تغطي التطابق الدقيق، إدراج الفرع عند الطلب فقط، عدم إدراج الأب عند اختيار الفرع، المرادفات الكاملة، وعداد الفروع المتاحة. مجموعة Android المركزة اجتازت 32/32 عبر 9 ملفات، بلا failures/errors/skips.
- **البناء المحلي:** :app:assembleStagingDebug نجح. APK app/build/outputs/apk/staging/debug/app-staging-debug.apk، الحجم 33,593,426 بايت، SHA-256 2C24E82251CD84C95EF4C7E8B95589E8DB9567065F2532644B3DCC2F42849E0B. بُني فقط؛ لم يُثبت أو يُشغّل.
- **CI:** Exact-SHA Android + Server CI passed for P2-10 code+docs commit `65ee0bf19d6735ca3a76e59cc21df215795ef2e8`. على commit التوثيق السابق a667deb، Server run 45050588860 نجح؛ Android run 45050588857 تعطل أولاً بسبب MavenArtifactFetcher / HttpURLConnection عند تنزيل اعتماد Robolectric، ثم نجحت إعادة تشغيل الوظيفة دون تغيير المصدر أو تخطي اختبارات.
- **Exact-SHA CI closeout:** Android run `45146557597` passed unit tests, Staging Debug APK assembly, and artifact upload (signed Release steps skipped); Server run `45146557390` passed dependency audit, TypeScript, 43 Vitest files / 295 tests, and Wrangler Production + Staging dry-runs. Android artifact `14803404358`: 31,125,572 bytes; ZIP SHA-256 `507e246e0535011648739eb509ef51068ee1c3048066bfa9bd97312e43cac2f9`. No deployment occurred.
- **Phase 5:** القبول المرئي على جهاز حقيقي ما زال مفتوحاً: مرشح الأحياء وحده، ظهور خيار الفرع عند توفره، تفعيل/إلغاء الخيار، أعداد الشرائح، وبقاء البحث النصي مستقلاً. لم يُستخدم محاكي أو هاتف.
- **النطاق:** Android/UI + سياسة واختبارات وموارد فقط. لا تغييرات Server/API أو قاعدة بيانات أو migration أو نشر أو أسرار أو Release أو main أو دمج PR.

## P2-11 — البحث عن المعرّفات متعددة المقاطع يعيد سجلات لا تطابق المعرّف كاملاً

**مرجع التقرير:** جولة TNW-SEARCH-COMPREHENSIVE-ROUND-20261120، قسم P2.
**الأولوية:** P2.

**الدليل:** الاستعلام QA_HW_20261119_A أعاد 3 نتائج في مرشح الاستفسارات: المقصود، QATEST2، وSYNCQA_ISSUE_20261118_A1.

**السبب في المصدر:** ArabicSearchEngine.kt يحول underscore إلى مسافة ويحتفظ بالمقاطع الأطول من حرف واحد؛ calculateRelevance يمنح نقاطاً عند ظهور أي مقطع ويقبل score أكبر من صفر، لذا يكفي اشتراك السجل في QA لإظهاره.

**الإصلاح المطلوب:** اجعل التطابق الكامل للعبارة/المعرّف أعلى من الجزئي، أو اشترط تطابق جميع المقاطع عندما يتعرف المحرك على صيغة ID كاملة، مع الإبقاء على مرونة البحث الطبيعي.

**فرق منفصل غير محسوم:** مرشح «الكل» أعاد 19 نتيجة على المحاكي مقابل 18 على الهاتف، بينما مرشح الاستفسارات تطابق (3). لم يُعزل مصدر العنصر المختلف، فيبقى تحقيقاً لا عيباً مثبتاً.

**الأدلة:** %TEMP%\tanweer-search-round-20261120\evidence.



### P2-11 implementation checkpoint (2026-11-23)

**الحالة:** IMPLEMENTED + COMMITTED / FOCUSED ANDROID TESTS PASS (29/29 across 2 XML reports) / STAGING DEBUG BUILD PASS / EXACT-SHA ANDROID + SERVER CI PASS / PHASE 5 DEVICE ACCEPTANCE OPEN.

- **Root cause:** `ArabicSearchEngine.calculateRelevance` rewarded any query token found as a substring, so a segment such as `QA` matched inside `QATEST2` or `SYNCQA` even when the rest of `QA_HW_20261119_A` was absent.
- **Fix:** compact identifier queries with at least three alphanumeric segments separated by `_` or `-`, and at least one numeric segment, now use identifier mode. Every normalized segment is retained (including one-character `A`) and must exist as a complete token across the searchable record fields. A contiguous full-ID occurrence receives the highest ranking; natural-language and non-structured queries keep the previous partial-token behavior.
- **Regression coverage:** five new tests verify exact ID match, reject `QATEST2` and `SYNCQA_ISSUE_20261118_A1`, allow all exact segments across searchable fields, and preserve natural-language / non-structured partial search, including all-single-character ID `A_B_C_1`. Combined run with `PhaseFourUiPolicyTest`: 29 tests across 2 reports, zero failures/errors.
- **Build:** `:app:assembleStagingDebug` passed. APK `app/build/outputs/apk/staging/debug/app-staging-debug.apk`: 33,593,426 bytes; SHA-256 `0765538BEE3FD3EEE3F957335594384B8E40B977CCC0FD5350EACCEBD25E167C`. Built only; not installed or launched.
- **Exact-SHA CI:** P2-11 commit `5c661e85cf352eb869eff64e2d27208419e69aa7` passed Android run `45148258115` (unit tests, Staging Debug APK assembly, artifact upload; signed Release steps skipped) and Server run `45148258094` (dependency audit, TypeScript, 43 Vitest files / 295 tests, Wrangler Production + Staging dry-runs). Android artifact `14804897617`, 31,126,813 bytes, ZIP SHA-256 `8943bb0e357e72904a6530c42a4e6c2698042c912b75d8d058b0b9add62125d2`. No deployment occurred.
- **Phase 5:** real-device validation of exact ID search, false-positive rejection, and ordinary natural-language search remains open. No emulator or phone was used.
## P2-12 — نطاق صفحات الواجب يتحول من 10-12 إلى 1012

**مرجع التقرير:** TNW-ACADEMIC-ARCHIVE-KNOWLEDGE-BASE-COMPREHENSIVE-ROUND-20261120. **الأولوية:** P2.

**الدليل:** الواجب QA_HW_20261119_A يحمل pageNumbers="10-12" على الجهازين، لكن واجهة بنك المعرفة تعرض زر فتح صفحة 1012. السبب في ClassKnowledgeBaseScreen.kt هو إزالة كل المحارف غير الرقمية ثم التحويل إلى Integer، فتُلصق 10 و12. الزر يفتح pageNum-1، لذلك سيقفز لصفحة خاطئة.

**الإصلاح:** تحليل النطاق إلى حدين/قائمة أو عرضه كنطاق نصي 10–12، مع حالات الرقم المفرد والنطاق والمدخل غير الصحيح. لم يُضغط الزر لأن كتاب القرآن 52.9MB لم يكتمل تنزيله؛ الخلل مستنتج من التحويل والعرض، لا من فتح PDF مكتمل.

**الأدلة:** %TEMP%\tanweer-archive-knowledge-round-20261120\evidence.

### P2-12 implementation checkpoint (2026-11-23)

**الحالة:** IMPLEMENTED + COMMITTED / FOCUSED ANDROID TESTS PASS (69/69 across 12 XML reports; P2-12 policy 8/8) / STAGING DEBUG BUILD PASS / EXACT-SHA ANDROID + SERVER CI PASS / PHASE 5 DEVICE ACCEPTANCE OPEN.

- **Root cause:** `ClassKnowledgeBaseScreen.kt` removed all non-digits from `pageNumbers`; `10-12` therefore became `1012`, and the button navigated to page index 1011.
- **Parser:** added `HomeworkPageReferencePolicy` and `HomeworkPageReference`. A single number remains a single page; `10-12`, `10–12`, and `10—12` are parsed as two ordered endpoints, never concatenated. Supports Arabic-Indic and Persian digits, optional explicit Arabic/English page labels, and surrounding whitespace.
- **Safety:** the dedicated `pageNumbers` value has priority. `details` is only considered as a fallback when it begins with an explicit page label, so arbitrary homework text numbers are not used as PDF destinations. Zero, reversed ranges, overflow, malformed ranges, and ambiguous lists are rejected and produce no navigation button.
- **UI behavior:** the homework card visibly renders `الصفحات: 10–12`; the action states that it opens the first page of the range (page 10), using zero-based PDF index 9. Single-page references retain the direct-open action.
- **Regression coverage:** 8 new policy tests cover single pages, ranges, whitespace and hyphen variants, Arabic-Indic/Persian digits, explicit labels, details fallback, invalid/reversed/zero/overflow data, precedence, and equal endpoints. All 8 passed with zero failures/errors. The combined regression run for P2-10, P2-11, and P2-12 passed 69/69 tests across 12 XML reports, with zero failures/errors/skips.
- **Build:** `:app:assembleStagingDebug` passed. APK `app/build/outputs/apk/staging/debug/app-staging-debug.apk`: 33,593,388 bytes; SHA-256 `3DAEF25143B1BC0A1415242C666AA0030BD7B7FB8A19F016BB5033DF00143F1B`. Built only; not installed or launched.
- **Exact-SHA CI:** P2-12 commit `881a532902dcd7d89383d453823ecafbd41692b6` passed Android run `45150149560` (unit tests, Staging Debug APK assembly, Debug artifact upload; signed Release steps skipped) and Server run `45150150093` (dependency audit, TypeScript, unit/integration tests, Wrangler Production bundle validation, migration file listing). Android artifact `14806154824`, 31,128,739 bytes, ZIP SHA-256 `9ac7749df06d3c1ebf339b5b0ea072a313af01228c3513d316bb425f874e2d42`. The first two Android attempts hit a transient Maven/Robolectric download IOException in `NotificationToggleBehaviorTest`; the unchanged third attempt passed. No deployment occurred.
- **Phase 5:** actual device verification of the displayed range, first-page navigation against the fully downloaded subject PDF, single-page behavior and invalid-input suppression remains open. The reported 52.9 MB Quran PDF was not fully downloaded during the evidence session; no claim is made that the PDF was opened or navigated successfully.
## P2-13 — الرجوع من PDF يفقد مصدر الفتح في بنك المعرفة

**مرجع التقرير:** جولة الأرشيف/بنك المعرفة بتاريخ 20 نوفمبر. **الأولوية:** P2.

**الدليل:** فتح كتاب الأحياء من بنك المعرفة ثم الرجوع أعاد الجهازين إلى مكتبة الكتب العامة بدلاً من المادة/بنك المعرفة. السبب أن TanweerViewModel.closePdfReader() يعيّن SubScreen.LIBRARY دون حفظ الوجهة الأصلية.

**الإصلاح:** حفظ origin route ومادة/صفحة بنك المعرفة، واستعادتها عند الرجوع؛ مع إبقاء المكتبة العامة إذا كان مصدر القارئ هو المكتبة العامة.

**الأدلة:** %TEMP%\tanweer-archive-knowledge-round-20261120\evidence.

### P2-13 implementation checkpoint (2026-11-23)

**الحالة:** IMPLEMENTED + COMMITTED / FOCUSED ANDROID TESTS PASS (74/74 across 13 XML reports; P2-13 policy 5/5) / STAGING DEBUG BUILD PASS / EXACT-SHA ANDROID + SERVER CI PASS / PHASE 5 DEVICE ACCEPTANCE OPEN.

- **Root cause:** `TanweerViewModel.closePdfReader()` always replaced the origin with `SubScreen.LIBRARY`, so a PDF opened from the Subject Knowledge Base returned to the general library.
- **Origin capture:** `openBookInPdfReader()` now captures the source `SubScreen`, active `NavigationTab`, and `selectedKnowledgeBaseSubjectId` once when entering the reader. Reopening/swapping a book while already in the reader does not overwrite the original destination. User-scope reset clears the captured origin.
- **Return behavior:** `closePdfReader()` resolves and restores the saved screen, tab and subject; it refreshes the restored root tab or sub-screen through its corresponding sync entry point. The Knowledge Base returns to the same subject; a reader opened from general Library still returns to general Library. Missing/invalid origins safely fall back to Library while retaining the active tab.
- **Regression coverage:** `PdfReaderNavigationPolicyTest` adds 5 cases for the Knowledge Base + subject, general library, Search/other sub-screens, root-tab origin, and missing/invalid origin. Combined P2-10 through P2-13 regression run passed 74/74 tests across 13 XML reports, zero failures/errors/skips.
- **Build:** `:app:assembleStagingDebug` passed. APK `app/build/outputs/apk/staging/debug/app-staging-debug.apk`: 33,593,388 bytes; SHA-256 `11C04F8396BB14E558B8B4C8515611C356433CA4C0A3B44E0A8A3B9F3AD7B8D8`. Built only; not installed or launched.
- **Exact-SHA CI:** commit `f9808b27a42ee46e0f02a916d8486e2e1f06b78b` passed Android run `45152437101` (unit tests, Staging Debug APK assembly and Debug artifact upload; signed Release steps skipped) and Server run `45152437077` (dependency audit, TypeScript, unit/integration tests, Wrangler Production bundle and migration file listing). Android artifact `14806578590`: 31,130,609 bytes; ZIP SHA-256 `dc8b7fbfca631e8700e1871368849c26769ce47e43dba0538e7e7957ff50e0c6`. First Android attempt hit a transient Maven/Robolectric HTTP download failure; unchanged retry passed. No deployment.
- **Phase 5:** real-device validation must open a book from the Knowledge Base and verify Back returns to the same subject, and separately confirm general Library still returns to general Library. No emulator/phone validation was performed.
## P2-14 — موضع قراءة PDF لا يُحفظ قبل فتح Activity خارجية

**مرجع التقرير:** TNW-PROGRESS-002. **الأولوية:** P2.

**إعادة الإنتاج:** Room lastPage=100 (صفحة 101). انتقل المستخدم إلى الصفحة 2 وانتظر 6 ثوانٍ؛ بقيت الواجهة عند 2 لكن Room ظلت 100 لأن الصفحة موجودة مسبقاً في unique pages. فتح Sharesheet ثم ألغاه أعاد العارض إلى 101. عند BackHandler العادي حُفظ Room lastPage=1 ثم عاد الكتاب إلى 2.

**السبب:** persistReadingState() يستدعى عند BackHandler وDispose وتغير مجموعة الصفحات الفريدة، لكن LaunchedEffect لا يحفظ الموضع إذا كانت الصفحة مسجلة سابقاً؛ ولا يوجد حفظ مماثل عند onPause/Share Chooser.

**الإصلاح:** حفظ آخر صفحة عند كل انتقال مستقل عن عداد الصفحات الفريدة، وكذلك عند lifecycle pause/فتح Activity خارجية.

**الأدلة:** %TEMP%\tanweer-pdf-round-20261118\reading-state-check\before.db وafter-page2.db وafter-back.db مع WAL/SHM، وprogress-display-page2.png.

### P2-14 implementation checkpoint (2026-11-23)

**الحالة:** IMPLEMENTED + COMMITTED / COMBINED ANDROID REGRESSION TESTS PASS (78/78 across 14 XML reports; P2-14 policy 4/4) / STAGING DEBUG BUILD PASS / EXACT-SHA ANDROID + SERVER CI PASS / PHASE 5 DEVICE ACCEPTANCE OPEN.

- **Root cause:** the existing `LaunchedEffect(currentPageIndex, downloadState)` only persisted `lastPage` after the four-second dwell increased the number of unique pages. Revisiting an already-counted page left Room's previous `lastPage` untouched. Share chooser and other external Activity transitions did not have an awaited checkpoint.
- **Position checkpoint:** every page transition after PDF readiness now snapshots and saves the current zero-based `lastPageIndex` immediately, independently from the four-second unique-page/dwell metric. The unique counter still changes only after the existing dwell interval.
- **Write ordering:** each immutable snapshot includes page, zoom, theme, total study time and unique-page count. A per-book `Mutex` serializes Room writes so an older asynchronous checkpoint cannot finish after a newer one.
- **External transitions:** `ON_PAUSE` persists a backup checkpoint. Markdown-share chooser and page-snapshot Sharesheet are opened only after their checkpoint write completes. Both BackHandler and the top reader bar use the awaited save-before-close path.
- **Regression coverage:** 4 `ReadingStateCheckpointPolicyTest` tests cover revisiting an existing page while higher-index pages are already in the unique set, separation of current position from unique-page count, preservation of other reading fields, and safe clamping. Combined P2-10 through P2-14 regression run passed 78/78 tests across 14 XML reports with zero failures/errors/skips.
- **Build:** `:app:assembleStagingDebug` passed. APK `app/build/outputs/apk/staging/debug/app-staging-debug.apk`: 33,593,388 bytes; SHA-256 `D29B11914470A829DBEAB93E453860F16685F4986936A37DADEF3AB31D17622F`. Built only; not installed or launched.
- **Exact-SHA CI:** commit `c82fa87bee2cb796d7221e4701045f4e5df48f01` passed Android run `45155026400` (unit tests, Staging Debug APK assembly, Debug artifact upload; signed Release steps skipped) and Server run `45155027790` (dependency audit, TypeScript, unit/integration tests, Wrangler Production bundle and migration file listing). Android artifact `14810097213`: 31,138,690 bytes; ZIP SHA-256 `55c477bef8ca9985d450bd50897b980bd86a23e9d3e006fba3d5375bc22fa71d`. Android first attempts hit transient Maven/Robolectric HTTP download failures; unchanged third retry passed. No deployment.
- **Phase 5:** real-device validation remains open: navigate to an already-counted page, verify Room `lastPage` after the checkpoint interval, open/cancel both external share routes, background/resume the app, and verify the restored page and counters. No emulator/phone was used.
## P2-15 — ملاحظات PDF المعروضة لا تطابق Room وتختفي بعد إعادة فتح اللوحة

**مرجع التقرير:** TNW-DATA-002 في جولة PDF بتاريخ 18 نوفمبر. هذا ID مكرر في التقرير لمشكلة الأرشيف الشخصي؛ وهما مشكلتان مختلفتان. **الأولوية:** P2.

**التسلسل:** لوحة ملاحظات الصفحة 2 أظهرت ملاحظتي QA_NOTE_PAGE_2 مرتين. أُدخل QA_NOTE_TEMP_20261118 وضغط حفظ؛ فرغ الحقل لكن القائمة بقيت تعرض القديمة مرتين. نسخة Room الأولى أظهرت صفاً واحداً للقديمة وصفاً واحداً للجديدة. بعد إغلاق اللوحة وإعادة فتحها اختفت الملاحظة المؤقتة من القائمة وRoom؛ بقيت القديمة للصفحة 2 وسجل مستقل للصفحة 4. لم يُضغط حذف ولم تُكتب DB يدوياً.

**الاستنتاج:** الواجهة عرضت ملاحظة لم تبقَ مثبتة بعد إعادة التحميل. قد يكون الخلل في تأكيد الحفظ/optimistic state/Flow أو callback؛ التقرير لم يعزل DAO النهائي.

**الإصلاح المطلوب:** لا تمسح مدخل المستخدم قبل تأكيد insert وظهور السجل من Flow، واستخدم ID ثابتاً ومصالحة واحدة. اختبر إعادة فتح اللوحة بعد كل كتابة وقارن Room/WAL.

**الأدلة:** %TEMP%\tanweer-pdf-round-20261118\notes-before-delete.png وnotes-after-reopen.png ونسخ DB/WAL/SHM وinspect_all_notes.py.

### P2-15 implementation checkpoint (2026-11-23)

**الحالة:** IMPLEMENTED LOCALLY / COMBINED ANDROID REGRESSION TESTS PASS (84/84 across 15 XML reports; P2-15 flow/idempotency policy 6/6) / STAGING DEBUG BUILD PASS / EXACT-SHA CI PENDING / PHASE 5 DEVICE ACCEPTANCE OPEN.

- **Observed failure:** the November 18 report showed two identical-looking page-2 notes, a new note that cleared from the input before being confirmed, and a note visible in the first view but absent after reopening the sheet. The report did not isolate the original Room write that disappeared; the source fix addresses each unsafe layer rather than claiming one unproven cause.
- **ID/collision safety:** the old timestamp-derived `bookId + pageIndex + currentTimeMillis` identity plus `REPLACE` could silently replace an existing primary-key row if IDs collided. Notes now receive a stable per-request UUID; DAO insertion uses `ABORT`, never `REPLACE`. The repository looks up the request ID first, validates that an existing row has the same book/page/text, and reuses it on retry. A concurrent insert race is reconciled by reading and validating the same ID; reusing an ID for different content is rejected.
- **Room confirmation:** `BookNoteFlowConfirmation` waits for the exact inserted ID to appear in the Room Flow. Completed-without-match and timeout cases fail visibly rather than pretending the save completed. The save field is cleared only after Room confirms the row and the screen's `SnapshotStateList` emits that same ID; on failure the draft and its request ID remain for a safe retry.
- **UI reconciliation:** the page notes list is filtered directly during recomposition instead of memoizing against the identity of a mutable list; rows are distinct by ID and SQL ordering is deterministic (`pageIndex`, `createdAt`, `id`). The optimistic local tag insertion was removed; tags now use the same Room + UI Flow confirmation path.
- **Regression coverage:** 6 `BookNoteFlowConfirmationTest` cases cover eventual Room emission, identity matching despite equal text/page, idempotent same-payload retry, rejection of mismatched payloads, completed Flow without the ID, and timeout. The combined P2-10 through P2-15 Android regression run passed 84/84 tests across 15 XML reports with zero failures/errors/skips.
- **Build:** `:app:assembleStagingDebug` passed. APK `app/build/outputs/apk/staging/debug/app-staging-debug.apk`: 33,593,388 bytes; SHA-256 `DF8F306E17284611DE6A10ADF554A5DDB8273B13091E9ADBB399FB401BC89ECE`. Built only; not installed or launched.
- **CI:** exact-SHA Android + Server CI is pending the P2-15 source+docs commit. P2-14 exact-SHA CI is green on `c82fa87bee2cb796d7221e4701045f4e5df48f01` (Android `45155026400`, Server `45155027790`).
- **Phase 5:** real-device validation remains open: add two notes on one page, close/reopen the notes sheet, verify each ID and text exactly once, retry after simulated delayed Flow confirmation, use the exam-question tag action, and compare Room rows against the UI after reopen. No emulator/phone or manually edited DB was used in this implementation pass.
## P2-16 — حالة قلم التمييز المرئية لا تطابق إعداد الضربة المرسومة

**مرجع التقرير:** TNW-PDF-ANNO-001. **الأولوية:** P2.

**إعادة الإنتاج:** في الصفحة 12 فُعّل الرسم واختير قلم التمييز، ورُسم خط. Room سجل isHighlighter=true وwidth=10.4. بعد Undo والضغط مرة أخرى على زر التمييز، عاد لون الزر إلى خلفية الوضع غير المحدد، لكن خطاً جديداً مرسوماً دون إعادة تفعيل التمييز حُفظ isHighlighter=true وwidth=10.4.

**السبب المرجح:** CanvasPageCurl.kt يربط pointerInput بـisDrawingMode فقط، بينما onDragEnd يستخدم isHighlighter/currentPenColor/currentPenWidth الملتقطة؛ هذه القيم ليست ضمن مفاتيح إعادة تشغيل pointerInput، فيرجح استخدام قيم قديمة.

**الإصلاح المطلوب:** اجعل إعدادات الأداة ضمن state keys أو اقرأ أحدث state عند بدء كل stroke وحفظها. اختبر تغيير اللون والعرض والتمييز دون تبديل وضع الرسم.

**سلامة QA:** التقرير أكد إزالة ضربة الصفحة 12 التجريبية مع بقاء رسومات الصفحة 4. لا تحذف بيانات رسم قديمة.

**الأدلة:** %TEMP%\tanweer-pdf-round-20261118\drawing-highlighter-before-undo.png وdrawing-state-before.png وdrawing-cleared-final.png وRoom snapshot.


## P2-17 — إحصاءات PDF التراكمية تعرض كأنها يومية وتحتسب التقليب الآلي

**مراجع التقرير:** TNW-STATS-001، TNW-STATS-002، وملاحظة الأرشيف P2/P3. **الأولوية:** P2 — مؤشرات التقدم والتحفيز الدراسي غير صحيحة.

**النتيجة:** لوحة المذاكرة عرضت 83 من أصل 15 صفحة و553% وسرعة تقارب 400 صفحة/ساعة رغم أن جلسة اليوم نحو 12 دقيقة. في جولة سابقة ظهرت 71 من أصل 15 و473% وسرعة 100 صفحة/ساعة.

**السبب:** SharedPreferences tanweer_book_reading_stats.xml تستخدم unique_pages_book_10_biology بلا تاريخ، وتجمع صفحات فريدة عبر الأيام والجلسات. PdfReaderScreen.kt يمرر persistedUniquePages.size كـpagesRead للوحة اليومية؛ السرعة تستخدم البسط التراكمي مع مدة الجلسة الحالية. LaunchedEffect(currentPageIndex, downloadState) ينتظر 4 ثوان ثم يسجل الصفحة دون التفريق بين التقليب الآلي والتنقل اليدوي.

**إعادة الإثبات:** الانتقال الآلي من 99 إلى 100 أضاف صفحة بعد بقائها، كما أضاف التنقل اليدوي صفحة أخرى؛ ارتفع التراكم من 80 إلى 83 خلال الاختبار.

**الإصلاح المطلوب:** افصل cumulative unique pages عن pages read today/session؛ احسب السرعة من صفحات الجلسة فقط؛ استبعد autoplay من القراءة البشرية أو سجله بمقياس «تم تصفحه تلقائياً».

**الأدلة:** %TEMP%\tanweer-pdf-round-20261118\reading-stats-before-auto-test.xml وreading-stats-after-manual-99.xml وreading-stats-after-auto-100.xml. التقرير ذكر بقاء أثر اختبار 83 صفحة؛ لا تمسحه تلقائياً.

## P2-18 — قيم API/Enums وURI-encoded تظهر خاماً في الواجهة العربية

**مراجع التقرير:** TNW-UI-EVENT-001، TNW-CALENDAR-EXAM-TEXT-ENCODING-001، TNW-CALENDAR-EVENT-CATEGORY-RAW-001، وملاحظات بطاقات «ماذا فاتني». **الأولوية:** P2 للموضوعات وأنواع الاختبارات، وP3 لتصنيف الحدث الخام.

**المشاهدات:** تفاصيل الحدث تعرض ACTIVITY بدلاً من «نشاط». تقويم الاختبارات يعرض FINAL، وحقل الموضوعات CH1%2C_CH2%2C_Final_Review كما هو. بطاقات ماذا فاتني تكرر القيم الخام.

**السبب:** بعض composables تعرض event.category وexam.examType وrequiredChapters مباشرة دون mapper موحد للتعريب أو فك النص.

**الإصلاح المطلوب:** Enum-to-localized-label مركزي حسب locale، وتطبيع/فك chapter lists في mapping الطبقة المناسبة مع عدم تعديل القيمة الأصلية المخزنة.

**الأدلة:** %TEMP%\tanweer-sync-round-20261118\phone-evt-detail.png، %TEMP%\tanweer-calendar-batch-20261119\evidence، %TEMP%\tanweer-catchup-round-20261120\evidence.


## P2-19 — الشارة الرسمية لا تظهر رغم isOfficial=1

**مرجع التقرير:** TNW-TEACHER-ROUND-20261120، الملحق. **الأولوية:** P2.

**الدليل:** Room على هاتف الطالب بعد المزامنة أثبت isOfficial=1 وsyncStatus=SYNCED للواجب الذي أنشأه الأستاذ؛ لكن UI hierarchy بعد تحديث/إعادة فتح القائمة لم يعرض «رسمي • من الأستاذ 🎓». شرط الشارة موجود في HomeworkScreen.kt، ويرجح أن صف العنوان الطويل يزاحمها أو يقصها.

**الإصلاح المطلوب:** افصل الشارة في عنصر/سطر مستقل أو عدّل التفاف العنوان/قياس الصف بحيث تظهر حتى مع عنوان طويل وRTL وتكبير خط.

**الأدلة:** %TEMP%\tanweer-teacher-round-20261120\evidence، الملفات 81–87 وRoom snapshot.

## P2-20 — اختلاف الأرشيف الشخصي وأرشيف الشعبة يحتاج توضيح نطاق الأرقام

**مرجع التقرير:** TNW-DATA-002 في القسم المبكر من التقرير، وهو مختلف عن مشكلة ملاحظات PDF. **الأولوية:** P2 — وضوح المؤشرات، لا تضارب DB مثبت.

**الدليل:** تبويب «إنجازاتي ومشاركاتي» يعرض 0 منجز/0 موثق وصفر درس وثقه المستخدم؛ بينما «أرشيف الشعبة للعام» يعرض عنصرين، وفي مادة القرآن درس واختبار.

**الاستنتاج:** قد يكون الاختلاف صحيحاً لأن الأول شخصي والثاني لكل الشعبة. لا يثبت فساد البيانات.

**الإصلاح المطلوب:** عناوين/توصيفات صريحة تشرح نطاق الحساب الشخصي مقابل سجل الشعبة؛ لا تدمج العدادات ولا تغيّر البيانات.


# المرحلة 3 — P2: اكتمال مسارات الإدارة والتعاون والتنقل

## P2-21 — لا توجد إدارة تعديل/حذف لفعاليات المنشئ في Android وأرشيف الأحداث غير قابل للفتح

**مراجع التقرير:** TNW-UI-EVENT-002، TNW-EVENTS-ARCHIVE-CARD-NOT-CLICKABLE-001، TNW-EVENTS-MANAGEMENT-UI-MISSING-001. **الأولوية:** P2.

**الدليل:** حدث EVT1 المنشأ من الطالب يظهر في تفاصيل تقويم للقراءة فقط مع زر إغلاق، ولا توجد قائمة بالضغط المطول. بطاقات أرشيف EventsScreen.kt غير clickable. في المقابل PATCH/DELETE موجودان في API، واختُبر أن غير المالك يحصل على 403 وأن المالك يستطيع التعديل والحذف.

**الإصلاح المطلوب:** اجعل بطاقة الأرشيف تفتح التفاصيل، وأظهر Edit/Delete للمالك فقط، مع تأكيد حذف وتحقق Worker؛ لا تظهر الأفعال لغير المالك.

**الأدلة:** phone-evt-detail.png وphone-evt-longpress2.png في %TEMP%\tanweer-sync-round-20261118، و%TEMP%\tanweer-calendar-batch-20261119\evidence.

## P2-22 — لا يوجد تدفق تعديل للاختبار الذي أنشأه الطالب

**مرجع التقرير:** TNW-EXAM-EDIT-001. **الأولوية:** P2.

**الدليل:** TanweerApiService يعلن PATCH api/v1/exams/{id} والخادم يسمح لمنشئ الاختبار بالتحديث أو لمن يملك MANAGE_EXAMS، لكن Android Repository/ViewModel لا يعرض updateExam أو edit state/dialog في ExamsScreen. فتح الاختبار يعرض القراءة فقط. حذف الاختبار يبقى محكوماً بـMANAGE_EXAMS.

**الإصلاح المطلوب:** زر Edit للمنشئ على سجله فقط مع إعادة تحقق الخادم؛ حافظ على قيود الحذف ولا تمنح الطالب MANAGE_EXAMS.

**الأدلة:** مراجعة ExamsScreen.kt وTanweerViewModel.kt وTanweerRepository.kt وTanweerApiService.kt وserver/src/services/exams.ts.

## P2-23 — لا توجد أدوات تعديل/حذف لواجب الطالب المملوك له

**مرجع التقرير:** TNW-HW-QA-20261119-001 وملاحظة Student-side Edit/Delete. **الأولوية:** P2.

**الدليل:** بطاقة الواجب/المشروع الذي أنشأه الطالب لا تعرض Edit أو Delete؛ long-press لا يظهر action menu. التقرير لم يختبر بعد تدفق المعلم/المدير في هذه الجولة، فلا نستنتج غيابه من نتيجة حساب الطالب.

**الإصلاح المطلوب:** توفير Edit/Delete للمالك وفق ownership policy، مع فصل صلاحية delete عن edit، وتأكيد الحذف وحالة ACK، ومنع تعديل سجلات الآخرين.

**الأدلة:** %TEMP%\tanweer-homework-round-20261119\evidence.


## P2-24 — حذف الرسالة غير متاح من واجهة Android

**مرجع التقرير:** TNW-GROUP-CHAT-MESSAGE-MENU-001. **الأولوية:** P2 لإجراء الحذف الذي يدعمه الخادم.

**الدليل:** long-press على رسالة مملوكة للمستخدم لا يعرض تحديداً أو قائمة. GroupsScreen.kt يعرض الاسم والنص وحالة التسليم فقط دون click/long-click. يوجد DELETE REST route مع owner/moderator authorization وDurable Object يفهم delete_message، لكن لا يوجد Android @DELETE أو Repository delete method أو UI action.

**الإصلاح المطلوب:** ربط حذف الرسالة للمالك/المشرف فقط مع ACK ومصالحة القائمة؛ لا تعتمد على إخفاء فقاعة محلياً دون تأكيد الخادم.

**نطاق مستقبلي مستبعد:** edit/reply/pin/reactions/forward/attachments/search كانت اتجاهاً مستقبلياً للمنتج وليست وظائف موجودة يثبت التقرير فقدانها. لا تعاملها كأخطاء هذه النسخة قبل اعتماد النطاق.

**الأدلة:** %TEMP%\tanweer-groups-round-20261119 و%TEMP%\tanweer-live-chat-20261119.

## P2-25 — الرسائل الجديدة لا تتبع أسفل المحادثة ولا يظهر مؤشر رسائل جديدة

**مراجع التقرير:** TNW-GROUP-CHAT-AUTOSCROLL-001 وTNW-GROUP-CHAT-AUTOSCROLL-KEYBOARD-002. **الأولوية:** P2.

**الدليل:** رسائل وصلت إلى cached_chat_messages وRoom بحالة SENT لكنها بقيت أسفل viewport حتى قام المستخدم بسحب القائمة يدوياً. عندما تكون القائمة عند الأسفل والكيبورد مفتوحاً، المساحة تكفي؛ الخلل هو غياب المتابعة التلقائية أو unread affordance.

**الإصلاح المطلوب:** إذا كان المستخدم قريباً من الأسفل، اتبع الرسالة بحركة ناعمة واحترم IME. إذا كان يقرأ رسائل أقدم، لا تنتزع scroll؛ أظهر «رسائل جديدة» وعدداً وزر انتقال للأسفل.

**الأدلة:** phone_chat_after_manual_scroll.xml/png وphone_chat_bottom_keyboard.xml/png وRoom snapshots في جولة 19 نوفمبر.

## P2-26 — نموذج تعليق Android لا يحمل is_teacher_answer

**مرجع التقرير:** TNW-TEACHER-ROUND-20261120، قسم سؤال الطالب وإجابة الأستاذ. **الأولوية:** P2/P3.

**الدليل:** الخادم يميز إجابة الأستاذ بواسطة is_teacher_answer، لكن IssueCommentItem في Android لا يحمل الحقل؛ لذلك لا تميز الواجهة إجابة الأستاذ بصرياً قبل اعتمادها كإجابة نموذجية.

**الإصلاح المطلوب:** تضمين الحقل في DTO/domain mapping وإظهار شارة مصدر الإجابة، مع استمرار تمييز الإجابة النموذجية بعد الاعتماد.

**الأدلة:** %TEMP%\tanweer-teacher-round-20261120\evidence.


## P2-27 — الرجوع من PDF والمجموعات يفقد سياق الشاشة المصدر

**مراجع التقرير:** ملاحظة TNW-SEARCH-COMPREHENSIVE-ROUND-20261120، نتيجة بنك المعرفة في TNW-ACADEMIC-ARCHIVE-KNOWLEDGE-BASE-COMPREHENSIVE-ROUND-20261120، TNW-MOD-NAV-001. **الأولوية:** P2/P3.

**المشاهدات:**
- فتح كتاب من البحث ثم الرجوع من PDF يعيد إلى المكتبة/علامة تبويب سابقة بدلاً من نتائج البحث. **الحالة:** ملاحظة تنقل غير محسومة؛ التقرير نفسه ينص على أنه لم يثبت أن حفظ قائمة نتائج البحث بعد الرجوع مطلب تصميم. لا تعاملها كعيب مؤكد قبل تأكيد السلوك المطلوب.
- فتح كتاب من بنك المعرفة ثم الرجوع يعيد إلى المكتبة العامة، لا إلى المادة.
- بطاقة الأعضاء في لوحة المشرف تنقل إلى GROUPS، لكن الرجوع يعيد إلى تبويب اليوم بدلاً من MODERATOR_DASHBOARD.

**السبب المؤكد للمسارين الآخرين:** closePdfReader() وsetSubScreen()/navigateBack() تستخدم وجهات ثابتة ولا تحفظ origin route/arguments في بعض المسارات. أعد إثبات كل مصدر على HEAD قبل التعديل.

**الإصلاح المطلوب:** Back stack/source token يحفظ الشاشة والمادة والصفحة والفلتر/الاستعلام والتبويب/لوحة الدور؛ اختبر الدخول من كل مصدر والعودة خطوة واحدة.

**الأدلة:** %TEMP%\tanweer-search-round-20261120\evidence، %TEMP%\tanweer-archive-knowledge-round-20261120\evidence، %TEMP%\tanweer-moderator-round-20261120\evidence.

## P2-28 — زر «كتاب المادة» في تفاصيل الاختبار يفتح المكتبة العامة

**مراجع التقرير:** TNW-EXAM-BOOK-NAV-001 وملحق الأستاذ. **الأولوية:** P2/P3.

**الدليل:** من اختبار القرآن، ضغط «كتاب المادة» ونقل إلى مكتبة الأول الثانوي العامة (26 كتاباً) دون تحديد القرآن أو فتحه. ExamsScreen.kt فقط يضبط MORE وLIBRARY ولا يمرر subject/book ID. رابط الصفحات في واجب بنك المعرفة فتح سجل الكتاب الصحيح؛ المشكلة تخص CTA من تفاصيل الاختبار تحديداً.

**الإصلاح المطلوب:** deep-link إلى الكتاب المناسب إن وجد association، وإلا سمّ الإجراء «فتح المكتبة العامة».

**الأدلة:** %TEMP%\tanweer-exams-round-20261119\exam-book-destination.xml و%TEMP%\tanweer-teacher-round-20261120\evidence.


## P2-29 — لا يوجد مركز إشعارات عام في واجهة Android

**مرجع التقرير:** استكمال دفعة الإشعارات بتاريخ 19 نوفمبر. **الأولوية:** P2 — فجوة منتج تحتاج تثبيت نطاقها.

**الدليل:** GET /api/v1/notifications أعاد HTTP 200، لكن صفحة الملف الشخصي لا تعرض قائمة عامة لعناصر AppNotification ولا أدوات قراءة/أرشفة/حذف؛ الموجود بطاقة استرداد الحساب فقط.

**الإصلاح المطلوب:** احسم أولاً هل Inbox مطلوب في نطاق المنتج الحالي. إن كان كذلك، أنشئ شاشة تعرض unread/read وتدفقات القراءة/الأرشفة بحسب API، ولا تخلط بينها وبين إشعارات Android المحلية.

**الأدلة:** %TEMP%\tanweer-notifications-round-20261119\evidence وphone-profile.xml.

## P2-30 — تعارض حالة مقترح الجدول مع الأصوات والنصاب

**مراجع التقرير:** TNW-GOV-STATE-001، TNW-MODERATOR-ROUND-20261120، TNW-TEACHER-ROUND-20261120. **الأولوية:** P2 مع ضرورة التحقق من مصدر الحالة قبل تعديل البيانات.

**الدليل:** مقترح الأحد القديم (الأحياء إلى الكيمياء) ظهر «مقبول ومطبق» رغم أن العداد 1 موافق و0 معارض، وإجمالي 1/4 بينما الواجهة تعرض أن النصاب المطلوب 3 أصوات. تقرير سابق سجل المقترح نفسه «قيد التصويت» مع العد نفسه. لا يوجد تصويت مصطنع في الاختبار لشرح تحول حالة المقترح القديم.

**المصدر الذي يلزم تتبعه:** server/src/services/governance.ts يحسب ثلاثة أصوات لازمة عند 4 مؤهلين وحد 60% ويطبق عند بلوغ العدد؛ الترحيل 0029_schedule_proposals_unified_decisions.sql ينسخ حالة وعدادات تاريخية. لا يُفترض أن الترحيل هو السبب النهائي دون قراءة سجل الخادم والبيانات.

**الإصلاح المطلوب:** تتبع تاريخ القرار والحالة والأصوات والنصاب وسجل الترحيل؛ حدد هل هناك حالة تاريخية نُسخت بشكل غير متسق أو مسار apply يتجاوز quorum. أضف tests تضمن عدم قبول/تطبيق القرار دون quorum وعدم عرض accepted/applied إذا كان عدد الأصوات أقل من الحد.

**سلامة الاختبار:** لا تعِد التصويت أو تطبق القرار على سجل حقيقي دون نسخة QA معتمدة؛ لم يصوت المختبر على المقترح القديم.

**الأدلة:** %TEMP%\tanweer-moderator-round-20261120\evidence و%TEMP%\tanweer-teacher-round-20261120\evidence.


# المرحلة 4 — P2/P3: UX وRTL وإمكانية الوصول والاستجابة

## P2-31 — طبقة تفاصيل الكتاب لا تحجب الخلفية ويتسرب اللمس إلى العناصر أسفلها

**مرجع التقرير:** TNW-UX-005. **الأولوية:** P2 — خلل تفاعل وظيفي.

**إعادة الإنتاج:** من المكتبة فتح تفاصيل «الأنشطة والتجارب - الأحياء» ثم لمس الخلفية عند (720,1000) لإغلاق الطبقة؛ تغير الصف في الخلفية إلى الصف الثامن. ثم فتح تفاصيل كتاب الأحياء والضغط على CTA السفلي «فتح ومطالعة الكتاب» ضمن bounds تقريبية [435,2820][907,2910]؛ أغلقت الطبقة وانتقلت الواجهة إلى جدول الاختبارات بدلاً من فتح القارئ.

**الاستنتاج:** modal layer/touch scrim لا يستهلك اللمسة أو ترتيب Z/Bounds خاطئ؛ CTA يتداخل مكانياً مع navigation bar.

**الإصلاح المطلوب:** جعل الطبقة modal فعلياً تستهلك لمس الخلفية، ونقل CTA فوق شريط التنقل مع safe area، واختبار الإغلاق خارج الطبقة والزر الأعلى والأسفل.

**الأدلة:** تفاصيل TNW-UX-005 ومجلد %TEMP%\tanweer-core-round-20261118.

## P2-32 — قارئ PDF مزدحم وأهداف لمس أصغر من المناسب

**مراجع التقرير:** TNW-UX-004 وإعادة القياس في 18 نوفمبر. **الأولوية:** P2.

**القياس:** Pixel 7 Pro Emulator، 1440×3120، 560dpi (نحو 411×891dp). عنوان الكتاب ومعلومات الصفحة والوقت محشورة بجوار ستة أزرار سريعة. بعض الأزرار قُدرت حدودها بنحو 34dp، وأزرار أخرى في القياس اللاحق كانت 60×60px تقريباً (17dp) أو 63×63px (18dp)، أقل من هدف 48dp. الشريط السفلي قريب من Navigation Bar.

**الأثر:** عنوان الكتاب/الوقت/الصفحة مضغوط، والأدوات متقاربة وغير مرتبة هرمياً؛ قد تتفاقم المشكلة على أجهزة أضيق أو ذات cutout.

**الإصلاح المطلوب:** رأس مرن لمعلومات الكتاب، نقل الأدوات الثانوية إلى قائمة/لوحة أو صف واضح، ضبط WindowInsets والمساحة الآمنة، أهداف لمس لا تقل عن 48dp، واختبار عروض 360/393/411dp وgesture navigation.

**الملفات:** PdfReaderScreen.kt، TopReaderBar وBottomReaderBar.
**الأدلة:** pdf-reader-full.png ولقطات accessibility bounds في %TEMP%\tanweer-pdf-round-20261118.

## P2-33 — بطاقات Hub كبيرة وتستهلك المساحة العمودية

**مرجع التقرير:** TNW-UX-002. **الأولوية:** P2.

**القياس:** بطاقات «ماذا فاتني» و«جدول الحصص» و«الأرشيف» و«الحوكمة» تقارب 444–511px (127–146dp) على 1440×3120/560dpi، وتضم وصفاً ومسمى وزراً.

**الأثر:** يظهر 2–4 بطاقات فقط قبل التمرير، فيطول الوصول للخدمات السفلى.

**الإصلاح المطلوب:** بطاقات أقصر/شبكة/صفوف، وصف لا يتجاوز سطرين، وجعل كامل البطاقة قابلاً للنقر مع هدف لمس مناسب.

## P2-34 — شرائط الفلاتر أفقية ومضغوطة ولا تكشف التمرير بسهولة

**مرجع التقرير:** TNW-UX-003. **الأولوية:** P2.

**الدليل:** شريط فلاتر الواجبات (الكل/اليوم/غداً/الأسبوع/متأخر/المكتملة) قابل للتمرير أفقياً، وبعض العناصر تقارب 61×48dp. فلاتر الاستفسارات كذلك أفقية، والأيقونة والنص عقدتان منفصلتان في شجرة الوصول.

**الأثر:** قد لا يكتشف المستخدم بقية الخيارات أو الفلتر المحدد.

**الإصلاح المطلوب:** مؤشر حواف/تدرج تمرير، Chips مدمجة بعناوين واضحة، وربط الأيقونة والنص في عنصر وصول واحد.


## P2-35 — ورقة إضافة اليوم في التقويم تقص الخيارات الأخيرة

**مراجع التقرير:** TNW-CALENDAR-UX-001 وTNW-EVENT-FORM-UX-001. **الأولوية:** P2.

**الدليل:** في portrait القياسي تظهر أول ثلاث خيارات فقط (درس/واجب/اختبار)؛ «إضافة فعالية» أسفل الحافة ولا يمكن الوصول إليها بالتمرير العادي، ويتبعها خيار الإجازة الرسمية المقيد بالدور. ظهر الخيار فقط عند زيادة logical display إلى 1440×4200.

**السبب في التقرير:** showAddActionSheet في CalendarScreen.kt يستخدم Column داخل ModalBottomSheet دون verticalScroll/LazyColumn.

**الإصلاح المطلوب:** sheet قابلة للتمرير أو LazyColumn/adaptive layout، مع بقاء كل خيار متاح للدور قابلاً للوصول.

**ملاحظات مرتبطة:** ورقة إضافة الحدث تتطلب expansion يدوياً والتاريخ حقل نص YYYY-MM-DD. مع الكيبورد تصبح أزرار النموذج السفلية صعبة الوصول، لكن لم يثبت فشل POST؛ لا تسجل فشل submit قبل إعادة اختبار مضبوط.

**الأدلة:** phone-calendar-action-expanded.png وphone-calendar-action-lower.png وphone-daily-plan-dialog.png تحت %TEMP%\tanweer-sync-round-20261118 و%TEMP%\tanweer-calendar-batch-20261119\evidence.

## P2-36 — لوحة المفاتيح تغطي نموذج الواجب/أزرار الإرسال

**مراجع التقرير:** TNW-UX-001، وملاحظة نموذج الحدث في TNW-EVENT-FORM-UX-001. **الأولوية:** P2.

**الدليل:** لوحة المفاتيح تغطي جزءاً من نموذج الواجب والزر السفلي؛ الضغط على رجوع لإخفائها أغلق نافذة الإضافة بدلاً من إبقائها مفتوحة.

**المراجعة:** windowSoftInputMode=adjustResize، IME/window insets، BackHandler، focus والتمرير التلقائي إلى الحقل.

**الإصلاح:** إبقاء النموذج مفتوحاً عند إخفاء IME، وضمان ظهور الأزرار/تمرير المحتوى، وعدم فقد المسودة.

## P2-37 — معاينة الكاميرا تستخدم صورة منخفضة التفاصيل

**مرجع التقرير:** TNW-PHOTO-002. **الأولوية:** P2.

**الدليل:** UploadLessonDialog.kt وHomeworkScreen.kt يستخدمان ActivityResultContracts.TakePicturePreview() ويحفظان Bitmap? في rawBitmap. هذا Preview صغير لا ملف كاميرا كامل الدقة، وغير مناسب لسبورة أو صفحة كتاب تحتاج قراءة وتكبيراً.

**الإصلاح:** TakePicture() مع FileProvider وملف كامل الدقة، إدارة مؤقت الملف وتنظيفه عند الإلغاء، ورفع الملف بعد تأكيد المستخدم.


## P2-38 — فتح وجهة الإشعار بطيء بسبب إعادة سلسلة بدء كاملة

**مرجع التقرير:** دفعة الإشعارات، قياسات UX. **الأولوية:** P2.

**الدليل:** الضغط على إشعار الاختبار على Galaxy S9 أعاد تشغيل MainActivity ثم سلسلة طلبات بدء (version/account/groups/today/archive/content/homeworks/exams/events/schedule/issues/calendar/chat). قياس أولي ذكر نحو 35 ثانية، وقياس أحدث وجد Splash عند +4 ثوانٍ وظهور صفحة الواجبات ضمن فحص لا يتجاوز +16 ثانية؛ لا تعتبر 16/35 ثانية زمناً ثابتاً مؤكداً.

**الإصلاح المطلوب:** معالجة intent والوجهة مبكراً وعرض loading موضعي، وتأجيل الطلبات الثانوية دون تخطي التحقق من الجلسة/الصلاحية.

## P2-39 — مفاتيح Switch الإشعارات بلا اسم وصول

**مرجع التقرير:** Accessibility review لدفعة الإشعارات. **الأولوية:** P2 مع اختبار TalkBack.

**الدليل:** عقدة كل Switch ظهرت checkable وبحالة checked في UI hierarchy لكن دون text أو contentDescription، والعنوان والوصف في عقد منفصلة. TalkBack الفعلي لم يُختبر.

**الإصلاح:** ربط switch semantics بعناوينه ووصفه وstateDescription مناسب، ثم اختبار TalkBack والتنقل.

## P2-40 — مرشح «مراكز الإدارة» مقصوص وفئة الطالب تبقى فارغة دون توضيح

**مرجع التقرير:** TNW-ROLE-GATE-READINESS-ROUND-20261120. **الأولوية:** P3.

**الدليل:** قبل التمرير لا يظهر من «مراكز الإدارة» سوى جزء صغير من النص (10px تقريباً على المحاكي و16px على الهاتف)؛ بعد السحب يظهر ويعمل. بحساب الطالب، اختيار الفئة يعرض صفحة فارغة دون شرح أنها تتطلب رتبة أعلى.

**الإصلاح:** مؤشر تمرير للفئة، ورسالة/بطاقة مقفلة توضح الرتبة المطلوبة دون كشف أو استدعاء شاشات إدارية لحساب الطالب.

**الأدلة:** %TEMP%\tanweer-role-readiness-20261120\evidence.

## P2-41 — حوار إدارة أعضاء المشرف لا يوضح أن الصف الأخير أسفل viewport

**مرجع التقرير:** TNW-MOD-UI-001. **الأولوية:** P3.

**الدليل:** أزرار العضو الرابع لا تظهر أول فتح بسبب قائمة ذات max-height؛ بعد التمرير تظهر كاملة وقابلة للنقر. لم تُضغط لأنها تغيّر عضويات حقيقية.

**الإصلاح:** إشارة تمرير/تدرج حافة أو مؤشر عناصر إضافية، وضمان وضوح كل أزرار الصفوف.

**الأدلة:** %TEMP%\tanweer-moderator-round-20261120\evidence.


## P2-42 — أفعال تفاصيل الاختبار وحفظ الجدول خارج الجزء المرئي الأول

**مراجع التقرير:** TNW-EXAM-DETAIL-UX-001 وTNW-TEACHER-ROUND-20261120. **الأولوية:** P3.

**الدليل:** أزرار «تذكير بالموعد» و«كتاب المادة» تظهر بعد swipe في ورقة تفاصيل الاختبار. حقل الفصول وملاحظات الأستاذ/القاعة كذلك أسفل الورقة. زر «تثبيت في الجدول» في ورقة تحرير الحصة يحتاج تمرير عمودي للوصول؛ الحفظ يعمل بعد الوصول.

**الإصلاح:** علامة تمرير واضحة أو sticky actions، وعدم دفن الإجراء الأساسي أسفل محتوى طويل.

## P2-43 — جدول اليوم طويل ولا يوضح وجود عناصر أسفل الشاشة

**مرجع التقرير:** TNW-CALENDAR-LONG-DAY-LIST-001. **الأولوية:** P2/P3.

**الدليل:** يوم 18 نوفمبر يضم ثلاثة اختبارات وثلاثة واجبات/مشاريع وفعالية؛ احتاج الوصول لآخر العناصر عدة scroll gestures. 26 نوفمبر وضع واجب QA أسفل viewport، وأثبت Room في الهاتف أنه موجود ومتزامن.

**الإصلاح:** count/continuation cue أو تصميم أكثر اكتشافاً؛ لا تعرض القائمة كأنها فارغة لمجرد أن العناصر أسفل viewport.

## P2-44 — شريط المزامنة الأخضر دائم ومزاحم لشريط النظام

**مراجع التقرير:** TNW-SYNC-UX-001، TNW-SYNC-BANNER-UX-RECHECK-20261119، TNW-GROUP-CHAT-SYNC-BANNER-RECHECK-20261119. **الأولوية:** P2.

**الدليل:** شريط «مُحدّث • آخر مزامنة: منذ لحظات» ثابت في أعلى الشاشة بين status bar والمحتوى على Home/Homework/Issues/chat. داخل المحادثة ظهر «آخر مزامنة: منذ 1 ساعة» رغم GET حديث ناجح، ما يبين أنه timestamp عام/قديم وليس حالة المحادثة نفسها.

**الإصلاح:** استمر في الخلفية/عند دخول القسم دون banner دائم. أظهر transient state عند refresh صريح، وpersistent alert فقط عند خطأ يتطلب إجراء. لا تغط status bar أو المحتوى.

**الأدلة:** %TEMP%\tanweer-sync-round-20261118، %TEMP%\tanweer-two-device-20261119، ولقطات المحادثة.

## P2-45 — توقيت الرسائل محفوظ لكنه غير ظاهر

**مرجع التقرير:** TNW-GROUP-CHAT-TIMESTAMP-001. **الأولوية:** P3.

**الدليل:** ChatMessageEntity يخزن timestamp لكن GroupsScreen.kt لا يعرض msg.timestamp في فقاعات الرسائل؛ يظهر sender/grade والنص وحالة التسليم فقط.

**الإصلاح:** وقت مختصر لكل رسالة، وفواصل تاريخية عند تغير اليوم، وعلامة edited/deleted حين تتوفر هذه الوظائف.

## P2-46 — حالة بحث بلا نتائج غير واضحة، وبحث بوابة الأقسام حساس لحالة الأحرف

**مراجع التقرير:** TNW-LIBRARY-SEARCH-EMPTY-001 وTNW-SECTIONS-SEARCH-001. **الأولوية:** P3.

**الدليل:** بحث المكتبة الذي لا يطابق أي كتاب يخفي البطاقات دون رسالة «لا توجد نتائج» أو مسح بحث واضح. في بوابة الأقسام، البحث pdf يعرض بطاقة المكتبة، بينما PDF لا يعرضها رغم وجود شارة PDF؛ ExtraSectionsHubScreen.kt يستخدم contains دون تطبيع case ولا يعرض no results state.

**الإصلاح:** empty state واضحة مع إجراء مسح/تعديل، وتطبيع الأحرف وفق locale مع إبقاء النص الأصلي.

## P2-47 — ملخص الواجبات يسمي جميع الأنواع واجبات

**مرجع التقرير:** TNW-HW-COUNT-001؛ السبب الوظيفي يندمج مع P2-02. **الأولوية:** P3.

**الدليل:** القائمة فيها عنصران HOMEWORK وعنصر TASK؛ العنوان يقول «3 واجب» وALL «الكل (3)» بينما المرشحات النوعية تفرق بينهما.

**الإصلاح:** بعد توحيد النوع في P2-02، استخدم «3 تكليفات» أو أعداداً منفصلة. لا تنشئ إصلاحاً مكرراً لحساب TASK.

## P2-48 — نصوص تفاصيل الاختبار وروابط الكتاب تقص المعلومات أو تبالغ في الوعد

**مراجع التقرير:** TNW-EXAM-UI-TRUNCATION-001 وTNW-EXAM-DETAIL-UX-001. **الأولوية:** P3.

**الدليل:** حقل الأستاذ/القاعة استخدم exam.notes.take(18) دون ellipsis، فظهر QA_Teacher_Mr_Test فقط بينما التفاصيل تعرض النص الكامل؛ وإجراءات تفاصيل الاختبار أسفل viewport؛ وزر كتاب المادة يفتح مكتبة عامة.

**الإصلاح:** لا تقص النص في model؛ استخدم ellipsis واضحاً/تفاصيل، حسّن اكتشاف الأزرار، واربط كتاب المادة بوجهة واضحة (P2-28).


# المرحلة 5 — التحقق والقبول: غير محسوم / لم يُختبر بعد

هذه المرحلة لا تضيف أخطاء جديدة؛ غرضها إغلاق الأدلة غير المكتملة ومنع تحويل NOT RUN أو UNVERIFIED إلى FAIL.

| العنصر | الحالة في التقرير | المطلوب قبل الحسم |
|---|---|---|
| TNW-EVENT-UI-OBS-001 | UNVERIFIED؛ UI hierarchy أعاد null root | تفاعل يدوي بحدود clickable مؤكدة أو instrumentation موثوق |
| TNW-HW-ADD-PHONE-UNCONFIRMED-001 | INCONCLUSIVE؛ العنصر clickable لكن ADB taps لم تفتح النموذج | إعادة اختبار لمس يدوي/scrcpy؛ لا تسجل فشل التطبيق من ADB فقط |
| exact alarm Android 15 | مسار رفض الإذن/فتح الإعدادات جرى، لكن firing لم يُختبر | اختبار scheduling/cancel بعد منح الإذن عمداً على QA |
| TNW-QUESTIONS-001 | فُتح نموذج الاستفسار وأُدخل QA_UNSENT_QUESTION ثم أُلغي؛ لم يُنشأ أو يُرسل استفسار | لا عيب مثبت؛ لا تُحوّل محاولة الإلغاء إلى فشل، وأعد الاختبار على حساب QA فقط إن لزم |
| TalkBack | UI hierarchy يشير إلى غياب label للمفاتيح؛ TalkBack الفعلي لم ينفذ | اختبار قارئ الشاشة والتنقل بين المفتاح والعنوان |
| pinch PDF بإصبعين | لم ينفذ عبر ADB لأن الأوامر لا تمثل multi-touch | جهاز/أداة تدعم multi-touch |
| أرشيف سنوات سابقة | غُطيت السنة النشطة 2026-2027 فقط | اختبار سنة قديمة إذا كانت متاحة |
| كتاب القرآن PDF | 52.9MB؛ التنزيل أُلغي ولم يكتمل | اختبار تنزيل/استئناف في اتصال مناسب وبموافقة |
| إكمال quorum للمقترح الجديد | لم يتوفر حساب ثالث مستقل؛ 2/4 والمطلوب 3 | حساب QA ثالث مستقل، لا تصنع صوتاً ولا تعدل DB |
| فرق نتائج البحث 19 مقابل 18 | لم يُعزل مصدر العنصر الإضافي | مقارنة Room/source snapshots |
| TNR-010 | المستخدم أكد أن التطبيق غير منشور وما زال قيد التطوير | خارج الإصلاح الحالي؛ يعاد قبل أول نشر عام فقط |

### نتائج PASS لا يعاد تسجيلها كأخطاء

- Bookmarks والتنقل الأساسي بالمصغرات وتدوير PDF وفتح Android Chooser لمشاركة لقطة دون إرسال خارجي واستعادة موضع القراءة في المسار العادي اجتازت المسارات التي اختُبرت.
- إنشاء/مزامنة الفعاليات، ونجاح update/delete للمالك ورفض non-owner بـ403.
- تدفقات إنشاء الواجب/الاختبار/الإجابة وتعديل الجدول وتقديم المقترح والتصويت عند بلوغ الحالات التي اختُبرت.
- مرشحات البحث والفلاتر التي اجتازت الحالات التي اختُبرت؛ مشكلة ID matching منفصلة عن البحث العربي العام.

---

## 3. فهرس أدلة %TEMP% المحلية

تم جرد المجلدات المباشرة التي تحتوي أسماءها على tanweer. العدد المسجل وقت إعداد الخطة 31 مجلداً. لا تُنقل قواعد Room/WAL/SHM أو ملفات Logcat والصور إلى Git تلقائياً؛ هذه مراجع محلية فقط.

| المجلد تحت %TEMP% | عدد الملفات وقت الجرد | الاستخدام |
|---|---:|---|
| tanweer-admin-round-20261120 | 67 | P1-06/P1-07، هوية ADMIN ولوحة المدير |
| tanweer-archive-knowledge-round-20261120 | 132 | FAILED في الأرشيف، page range، PDF origin، الإحصاءات |
| tanweer-calendar-batch-20261119 | 14 | تصنيف TASK والتقويم والفعاليات |
| tanweer-calendar-round-20261119 | 72 | التقويم والفعاليات والأرشيف |
| tanweer-catchup-round-20261120 | 118 | نطاقات التاريخ وسجلات FAILED |
| tanweer-chat-round-20261119 | 8 | محادثات |
| tanweer-core-round-20261118 | 514 | المكتبة والتنقل وPDF والأقسام |
| tanweer-exams-round-20261119 | 33 | FAILED exams، status/review والتفاصيل |
| tanweer-groups-round-20261119 | 18 | مجموعات ومحادثات |
| tanweer-homework-round-20261119 | 85 | فقد النموذج، التاريخ، CRUD |
| tanweer-live-chat-20261119 | 30 | HTTP ACK وWS/reconnect والتمرير |
| tanweer-moderator-round-20261120 | 39 | governance، member list، الرجوع |
| tanweer-notifications-round-20261119 | 130 | إلغاء الإشعارات والتصنيف والوصول |
| tanweer-pdf-round-20261118 | 52 | TTS، notes، drawing، progress، stats |
| tanweer-profile-settings-round-20261120 | 66 | الملف الشخصي والإعدادات |
| tanweer-role-readiness-20261120 | 30 | بوابة الرتب |
| tanweer-search-round-20261120 | 133 | دقة البحث وفهرس الجهازين |
| tanweer-sync-round-20261118 | 387 | HTTP ACK وWebSocket والمزامنة |
| tanweer-teacher-round-20261120 | 161 | الأستاذ والواجب الرسمي والحوكمة |
| tanweer-two-device-20261119 | 51 | مزامنة الحسابين |
| tanweer_chat_snapshot_phone_after_join_all | 2 | snapshot مساعد |
| tanweer-next-section-discovery-20261120 | 4 | اكتشاف الشاشات |
| tanweer-bookmark-check | 1 | اختبار bookmarks PASS، ليس عيباً |
| tanweer-drawing-test | 1 | اختبار الرسم |
| tanweer-tag-check | 1 | أداة فحص مساعدة |
| tanweer-live | 1 | أثر اختبار سابق |
| tanweer-live-morning | 1 | أثر اختبار سابق |
| tanweer-ro-20261118 | 3 | نسخ Room للقراءة فقط |
| tanweer-server-security-direct-dep-test | 2455 | fixtures اختبار اعتماديات Server |
| tanweer-server-security-install-test | 123 | fixtures تثبيت اعتماديات |
| tanweer-server-security-tarball-install-test | 124 | fixtures tarball install |

> الأعداد لقطة وقت إنشاء الخطة وقد تتغير. استخدم المسار الكامل عبر Join-Path $env:TEMP <folder>. لا ترفع DB/WAL/SHM أو صوراً أو سجلات قد تكشف بيانات حسابات إلى Git.

---

## 4. ملفات المصدر الرئيسية التي ينبغي مراجعتها عند الإصلاح

هذه أسماء متابعة من التقرير، ويجب التحقق من المسارات والمواضع الحالية على HEAD قبل العمل.

### Android / UI / state
- app/src/main/java/com/magd/tanweer/ui/screens/UploadLessonDialog.kt
- app/src/main/java/com/magd/tanweer/ui/screens/HomeworkScreen.kt
- app/src/main/java/com/magd/tanweer/ui/TanweerApp.kt
- app/src/main/java/com/magd/tanweer/ui/TanweerViewModel.kt
- app/src/main/java/com/magd/tanweer/ui/screens/ExamsScreen.kt
- app/src/main/java/com/magd/tanweer/ui/screens/CalendarScreen.kt
- app/src/main/java/com/magd/tanweer/ui/screens/WhatDidIMissScreen.kt
- app/src/main/java/com/magd/tanweer/ui/screens/IssuesScreen.kt
- app/src/main/java/com/magd/tanweer/ui/screens/LibraryScreen.kt
- app/src/main/java/com/magd/tanweer/ui/screens/ExtraSectionsHubScreen.kt
- app/src/main/java/com/magd/tanweer/ui/screens/ClassKnowledgeBaseScreen.kt
- app/src/main/java/com/magd/tanweer/ui/screens/GroupsScreen.kt
- app/src/main/java/com/magd/tanweer/ui/screens/TeacherDashboardScreen.kt
- app/src/main/java/com/magd/tanweer/ui/screens/ModeratorDashboardScreen.kt
- app/src/main/java/com/magd/tanweer/ui/screens/AdminDashboardScreen.kt
- app/src/main/java/com/magd/tanweer/ui/screens/pdf/PdfReaderScreen.kt
- app/src/main/java/com/magd/tanweer/ui/screens/pdf/CanvasPageCurl.kt
- app/src/main/java/com/magd/tanweer/util/ArabicSearchEngine.kt
- TanweerApiService.kt
- TanweerRepository/repository layer (تحقق من المسار الفعلي في HEAD)
- TanweerNotificationManager وRealtimeChatManager (تحقق من المسار الفعلي في HEAD)

### Worker / backend
- server/src/services/chat.ts
- server/src/realtime/GroupChatDO.ts
- server/src/services/governance.ts
- server/src/services/dashboard.ts
- server/src/services/auth.ts
- server/src/services/groups.ts
- server/src/lib/permissions.ts
- server/src/index.ts
- server/src/services/exams.ts

---

## 5. قواعد تنفيذ البنود وإثبات إغلاقها

1. قبل بدء كل بند، اقرأ GPT.md وSESSION_HANDOFF.md وPROJECT_ARCHITECTURE.md وHEAD الحالي، ثم راجع diff والكود الحالي. لا تفترض أن تقرير QA يعكس HEAD الحالي دون تحقق.
2. أعد إنتاج السبب على Staging/QA بأقل بيانات ممكنة. لا تستخدم حساباً أو بيانات حقيقية لإجراء حذف أو حظر أو ترقية دون موافقة.
3. إذا كان السبب غير محسوم، اجمع الدليل أولاً ولا تكتب إصلاحاً تخمينياً.
4. أضف regression tests تغطي الحالة العادية والفشل وإعادة المحاولة والرجوع من الشاشة عند الحاجة.
5. نفذ جميع الأعمال داخل D:\MyApps\tanweer وعلى work/release-readiness فقط.
6. راجع diff وgit diff --check؛ وتأكد من عدم تعديل main أو الأسرار أو قواعد Production.
7. بعد التعديل، شغّل Android tests/build إذا مس التطبيق، وTypeScript/Vitest/Wrangler validation إذا مس الخادم. إذا تغير عقد client/server يجب نجاح المسارين.
8. Push إلى origin/work/release-readiness ثم تحقق من SHA البعيد ونتائج CI على ذلك SHA. لا تعتبر نجاح CI اختبار جهاز فعلي.
9. عند إغلاق بند، حدّث حالة هذا الملف وTanweerReport.md وSESSION_HANDOFF.md بالأدلة في commit مناسب. احتفظ بتفاصيل إعادة الإنتاج الأصلية.
10. لا تغلق PR #6 ولا تدمج إلى main ولا تنشر Production دون طلب صريح.

---


### بنود تحقيق إضافية — لا تُصنف كعيوب مؤكدة قبل إتمام التحقق

#### TNW-UI-002 — تناقض نص الصلاحيات في جدول الحصص
- **الأولوية في التقرير:** P1 بسبب احتمال تضليل المستخدم بشأن صلاحية تعديل البيانات.
- **المشاهدة:** رأس الجدول يعرض "طالب"، بينما تظهر أيضاً عبارات «تحرير مباشر (معلم / إدارة)» و«بصفتك معلماً أو مديراً» و«تحرير مباشر مفعل».
- **إعادة الإنتاج الواردة:** فتح الأقسام ← جدول الحصص الأسبوعي؛ إضافة حصة في أول خانة فارغة؛ ثم ملاحظة شريط الدور والشرح بعد نجاح الحفظ/إعادة التحميل.
- **المطلوب قبل التصنيف النهائي:** التحقق من currentUser.role وhasPermission(MANAGE_SCHEDULE) وcanFillEmptyScheduleSlots ومقارنة النص المعروض بالقرار الفعلي من Worker. التقرير لا يثبت تجاوز صلاحية من جهة الخادم؛ لا تكتب إصلاح صلاحيات قبل إثباته.
- **مرجع المصدر:** TanweerReport.md، TNW-UI-002، الأسطر المبكرة 43–49.

#### TNW-DATA-001 — بيانات قليلة/فارغة في الشعبة التجريبية
- **الأولوية:** P2 (بيانات/تكامل)، والسبب غير محسوم.
- **الدليل:** الواجبات صفر، و«ماذا فاتني» يعرض أصفاراً، بينما رحلة المادة تعرض درساً واحداً بعنوان ومحتوى «مممم».
- **المطلوب:** فحص الشعبة النشطة والتاريخ والفلاتر ومزامنة الهاتف الآخر، ثم مقارنة النتيجة بعد Refresh وإعادة فتح التطبيق. لا تصنفها فقد بيانات قبل عزل scope أو sync.
- **مرجع المصدر:** TanweerReport.md، TNW-DATA-001، الأسطر 51–56.

#### TNW-SYNC-DUPLICATE-GET-001 — طلبا Issues متطابقان عند دخول القسم
- **الأولوية:** P3 (عمل شبكي زائد محتمل).
- **الدليل:** دخول واحد إلى Issues نتج عنه طلبان متقاربان زمنياً ومتطابقان GET /api/v1/issues?groupId=class_10_C على الهاتف والمحاكي، وكلاهما HTTP 200. رُصد ذلك عند 12:44 وتكرر في دخول لاحق.
- **المطلوب:** تحديد هل الطلب الأول من startup/global sync والثاني من section-entry refresh أم أنهما ازدواج غير ضروري. لا تزل أحدهما قبل فهم الاعتماديات وتأكيد عدم كسر refresh.
- **مرجع المصدر:** TanweerReport.md، TNW-SYNC-DUPLICATE-GET-001، قرب السطر 939.

#### TNW-SEARCH-BOOK-ACCESS-001 — الرجوع من بحث الكتب إلى النتائج
- **الحالة:** فتح الكتاب واستعادة الصفحة PASS؛ العودة إلى المكتبة بدلاً من نتائج البحث ملاحظة UX. التقرير يقول صراحةً إن استعادة نتائج البحث لم تثبت كمتطلب تصميم.
- **المطلوب:** أكد السلوك المقصود قبل ضمه إلى عيوب التنقل المؤكدة أو تغييره. لا تخلطه مع فقد سياق بنك المعرفة المؤكد في P2-27.

#### TNW-PDF-THUMB-001 — تسميات الصف السفلي في المصغرات قرب منطقة النظام
- **الحالة:** التنقل من المصغرات 1 و9 و12 PASS؛ الملاحظة الوحيدة أن تسميات الصف الثاني (4–6) في اللوحة المطوية تقع عند y≈3062 من ارتفاع 3120، قرب منطقة التنقل النظامي. توسيع اللوحة حسّن العرض.
- **المطلوب:** مراجعة WindowInsets والحافة السفلية ضمن P2-32، لا تصنف التنقل نفسه فاشلاً.

#### TNW-QUESTIONS-001 — إنشاء استفسار لم يُرسل عمداً
- **الحالة:** فُتح النموذج وأُدخل QA_UNSENT_QUESTION ثم أُلغي؛ لم يُنشأ أو يُرسل استفسار.
- **التعامل:** لا يُعد فشل نشر ولا عيباً. أي اختبار إنشاء لاحق يكون على حساب QA وسجل مستقل.

## 6. سجل التنفيذ

| المرحلة/البند | الحالة | Commit الإصلاح | CI / دليل القبول |
|---|---|---|---|
| Phase 1 — P1 trust / persistence / stability | IMPLEMENTATION + AUTOMATED CI VERIFIED / PHASE 5 ACCEPTANCE OPEN | f98371e10aa3ad303f28815e73ad90416f50c799 | Android Ubuntu: testStagingDebugUnitTest PASS; assembleStagingDebug PASS. Server: TypeScript, dependency security baseline, Wrangler bundle validation PASS; 42 files / 293 tests PASS. Local Windows/Robolectric AtomicFile issue is host-specific and the unchanged API34 test passed on Ubuntu. Migration 0034 remains memory-SQLite-only; P1-02 explicitly deferred and P1-03 remains a text-quality guard pending real PDF extraction work/verification in Phase 5. Device acceptance Phase 5. |
| Phase 2 - P2 data correctness / sync truth | P2-01 through P2-14 implementation and exact-SHA Android/Server CI verified; P2-15 local implementation, 84/84 combined regression tests (15 reports) and Staging Debug build passed, exact-SHA CI pending; Phase 5 device acceptance open | P2-01: 412bba30859426a891ba860a92aa201446007537; P2-02: 89e16d4ab99b7068ce13a4b7704acd196e501172; P2-03: 35763ee8b6e8faa217df51798fcb2ec7274841b9; P2-04: 9e090054dfd2d0f9277a4ea9e4d6a208d40eb968; P2-05: e217ff83ca8f3bb2c2b6fd5d6b05df891985f149; P2-06: a42983cf0c1a1ef8010b323b5f119746623ddfde; P2-07: bbeb3e95130cf099cd4dae34b2ea78c3f71ca745; P2-08: cf310f0dae231260e63eb8951db9f81cc76036e3; P2-09: 07ccb85c55d92e8c3963c5bb523f0f8833fde5ac; P2-10: 65ee0bf19d6735ca3a76e59cc21df215795ef2e8; P2-11: 5c661e85cf352eb869eff64e2d27208419e69aa7; P2-12: 881a532902dcd7d89383d453823ecafbd41692b6; P2-13: f9808b27a42ee46e0f02a916d8486e2e1f06b78b; P2-14: c82fa87bee2cb796d7221e4701045f4e5df48f01; P2-15: implementation commit pending | P2-10 Android 45146557597 + Server 45146557390 passed. P2-11 Android 45148258115 + Server 45148258094 passed. P2-12 Android 45150149560 + Server 45150150093 passed (unchanged retry after Maven/Robolectric download failures). P2-13 Android 45152437101 + Server 45152437077 passed (unchanged retry after Maven/Robolectric download failure). P2-14 Android 45155026400 + Server 45155027790 passed (unchanged third retry after transient Maven/Robolectric downloads). P2-15: 84/84 local regression tests across 15 reports, Staging Debug passed; exact-SHA CI pending. No deployment or database apply/write. Physical acceptance remains Phase 5. |
| Phase 3 — P2 CRUD / collaboration / navigation | NOT STARTED | — | — |
| Phase 4 — P2/P3 UX / accessibility | NOT STARTED | — | — |
| Phase 5 — final acceptance | NOT STARTED | — | — |
