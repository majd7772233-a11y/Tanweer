# GPT.md — Tanweer Internal Project Memory

> **هذا الملف مخصص للمساعد (GPT)، وليس وثيقة مستخدم أو مواصفات نهائية للمشروع.**
> يوضع في جذر مستودع Tanweer حتى تستطيع أي محادثة جديدة قراءته أولًا وفهم المشروع بسرعة قبل البحث في الكود.
>
> **قاعدة ذهبية:** هذا الملف مرجع سياقي، وليس مصدر الحقيقة النهائي. مصدر الحقيقة عند تنفيذ أي مهمة هو **آخر Commit فعلي في GitHub**. يجب إعادة التحقق من HEAD والكود قبل اقتراح إصلاحات أو الادعاء بأن مشكلة ما ما زالت موجودة.

---

## 0) هوية المشروع

- **اسم المشروع:** تنوير / Tanweer
- **الفكرة:** منظومة مدرسية تعاونية بديلة لخلطات مجموعات WhatsApp، تجمع الدراسة اليومية، الدروس، الواجبات، الاختبارات، الأحداث، الجدول، الأسئلة والأجوبة، المحادثة، الكتب وPDF، الإشعارات، الحوكمة والمساهمات المجتمعية في نظام أكاديمي منظم.
- **المبدأ الأساسي:** التطبيق ليس نظامًا إداريًا تقليديًا يكون فيه الطلاب مجرد مشاهدين. **الطلاب هم الأغلبية والمتوقع أن تكون مساهمتهم أساسية.**
- **المشكلة التي يحلها:** في WhatsApp تضيع الدروس والواجبات والأخبار داخل الرسائل. Tanweer يحولها إلى بيانات أكاديمية منظمة قابلة للبحث والتاريخ والمزامنة والعمل دون اتصال.
- **الهوية البصرية المرغوبة:** Liquid Glass / زجاج سائل، Blur، عمق مائي/3D، بطاقات جميلة، حركة ناعمة، مظهر حديث وليس واجهات CRUD بدائية.
- **اللغة الأساسية:** العربية أولًا، الإنجليزية ثانوية. يجب تجنب خلط اللغات في الواجهات النهائية.

---

## 1) الحالة الحالية المعروفة عند إنشاء هذا الملف

### المستودع الرئيسي

- GitHub: `majd7772233-a11y/Tanweer`
- الفرع المستهدف: `main`
- آخر Commit معروف ومتحقق منه:
  - SHA: `1babe65c890f55c14ec38fac308301790801cb1a`
  - Message: `Fixing Some Bugs`
  - التاريخ المعروف من الفحص: 2026-10-04 15:27:52Z
- الـParent المعروف: `332d2ffa2eb4e8bfe7fe1f8bdad630cb7d47755f`
- التغيير التقريبي في آخر Commit: +782 / -264 (حوالي 1046 سطر تغيير وفق الفحص السابق).
- حالة commit status الملتقطة عبر GitHub connector وقت إنشاء الملف: لا توجد status entries معروضة (`statuses: []`). هذا **لا يساوي نجاح CI** ولا يثبت أن كل Actions خضراء.

### آخر Commit ماذا أصلح/غير؟

أهم التغييرات المؤكدة من diff:

1. إعادة تصميم تنقل قسم MORE من Bottom Sheet إلى `Extra Sections Hub` كـSubScreen.
2. عند فتح SubScreen أصبح زر الرجوع يعيد إلى Hub بدل الخروج مباشرة.
3. إضافة عناوين علويّة ديناميكية لبعض الصفحات الفرعية.
4. تغيير تسمية/أيقونة تبويب MORE ديناميكيًا حسب الـSubScreen المفتوح.
5. تحسين صلاحيات `ADMIN` و`SYSTEM_OWNER` في Permission evaluator.
6. إضافة صلاحيات Moderation للمعلّم مثل chat/group members وفق الكود الحالي.
7. تنظيف أدوار `group_members` عند خفض الدور العالمي إلى STUDENT أو MODERATOR.
8. منع تعيين `SYSTEM_OWNER` داخل مجموعة.
9. منع منح TEACHER مباشرة لعضو غير Teacher عالميًا إلا بواسطة ADMIN/SYSTEM_OWNER.
10. تقييد تعيين ADMIN داخل المجموعة.
11. إضافة pagination للوحة التحكم للمحتوى/الواجبات/الاختبارات/الأحداث/الأسئلة.
12. إصلاح تطبيق قرار جماعي من لوحة المالك بحيث ينفذ الأثر الفعلي عبر `applyDecisionExecution` بدل تغيير الحالة فقط.
13. محرك Governance أصبح يحدد `thresholdPercent` وفق نوع القرار من السيرفر بدل الثقة بالعميل.
14. أخذ Snapshot للمصوتين المؤهلين عند إنشاء القرار الجماعي.
15. منع عضو لم يكن ضمن Snapshot من التصويت لاحقًا.
16. إضافة حد أدنى فعلي للتصويت في المجموعات متعددة الأعضاء (على الأقل صوتان).
17. تطبيع نوع القرار (`normType`) على السيرفر.
18. حفظ بيانات Snapshot داخل JSON في حقل `description` حاليًا.

---

## 2) قواعد عمل المشروع التي يجب ألا أنساها

### 2.1 فلسفة الصلاحيات

الرتب الرسمية بالترتيب:

`STUDENT → MODERATOR → TEACHER → ADMIN → SYSTEM_OWNER`

المستخدم يقصد بـ"نظام الأولوية" هنا **هرمية الرتب فقط**، وليس أولوية للمحتوى. لا تقل إن نظام أولوية المحتوى ناقص.

### 2.2 كل الطلاب مساهمون

الطالب ليس مجرد قارئ. الطالب يستطيع من حيث الفلسفة العامة:

- رفع الدروس والمحتوى.
- رفع صور السبورة/الملفات.
- إضافة الواجبات.
- إضافة الاختبارات.
- إضافة الأحداث.
- طرح الأسئلة.
- الإجابة عن الأسئلة.
- المشاركة في المحادثة.
- اقتراح تعديلات للجدول.
- المشاركة في الحوكمة والتصويت.
- طلب رفع الرتبة.

### 2.3 قاعدة الملكية

- **الناشر/صاحب المحتوى الأصلي يستطيع تعديل/حذف محتواه الذي يملكه** حتى لو كان طالبًا.
- تعديل/حذف محتوى الآخرين يحتاج صلاحية مناسبة أو مسار مجتمعي/حوكمي حسب نوع العملية.
- لا تحول المشروع إلى Admin-only content management.

### 2.4 قاعدة الجدول

- أي عضو يمكنه المساعدة في إنشاء الجدول الأولي.
- بعد وجود جدول أولي مستقر، تعديل الجدول من غير المصرح له لا يكون تعديلًا صامتًا؛ يجب أن يكون عبر **proposal/vote** أو دور مخول.

### 2.5 اعتماد الأستاذ

- رفع طالب إلى TEACHER ليس مجرد اختيار role داخل المجموعة.
- المسار المفاهيمي: طلب ترقية → مراجعة/تحقق يدوي → مالك المنظومة يتحقق عبر WhatsApp → يمنح كودًا من **8 أرقام** صالحًا لمدة **24 ساعة** → إكمال الاعتماد.
- رقم WhatsApp المعروف المستخدم لهذا المسار: `+967 735465673`.
- لا تعيد اقتراح إضافة Secret باسم آخر إن كان الإعدادات الحالية تعتمد `TANWEER_OWNER_SECRET` ما لم يتطلب الكود ذلك فعلًا.

### 2.6 Owner

- حساب `SYSTEM_OWNER` هو حساب المالك/صاحب المنظومة.
- له المستوى 5.
- لا يجوز منح هذه الرتبة عبر Group role.
- Dashboard المالك منفصل أمنيًا عن جلسات Android.
- المفتاح الحالي المتوقع: `TANWEER_OWNER_SECRET`.
- لا يوجد fallback سري افتراضي للمالك في الكود الحالي الذي تم فحصه؛ عندما لا يكون secret مهيأ يجب أن تكون غرفة التحكم معطلة.

---

## 3) بنية المشروع المعمارية

## 3.1 Android

- Package معروف: `com.magd.tanweer`
- الواجهة: Kotlin + Jetpack Compose.
- التطبيق Android حديث نسبيًا مقارنة بمشاريع المستخدم القديمة.
- ViewModel مركزي/حالة تنقل مركزية ظاهرة في `TanweerApp.kt`.
- توجد طبقة محلية Room/DAO للحفظ والكاش والمزامنة.
- هناك خدمة/طبقة Sync وOffline queue بدرجات مختلفة من النضج.
- هناك PDF subsystem متقدم (`PdfBookManager` + `PdfReader` ونحوها).

### تبويبات التنقل الرئيسية المعروفة

- TODAY
- CALENDAR
- HOMEWORK
- EXAMS
- ISSUES
- MORE

### SubScreens المعروفة

من `TanweerApp.kt` الحالي/المفحوص:

- `EXTRA_SECTIONS_HUB`
- `GROUPS`
- `LIBRARY`
- `SCHEDULE`
- `TIMELINE`
- `PROFILE`
- `SETTINGS`
- `WHAT_DID_I_MISS`
- `SEARCH`
- `SUBJECT_KNOWLEDGE_BASE`
- `ACADEMIC_HISTORY`
- `TEACHER_DASHBOARD`
- `MODERATOR_DASHBOARD`
- `ADMIN_DASHBOARD`
- `COMMUNITY_DECISIONS`
- `PDF_VIEWER`
- وقد توجد Screens أخرى في آخر HEAD؛ لا تفترض أن هذه القائمة شاملة دون قراءة الـenum/الشجرة الحالية.

### Screens / مجالات وظيفية رئيسية معروفة

- Home / Today
- Calendar
- Homework
- Exams
- Issues / Q&A
- Search
- Knowledge Base
- Academic History
- Subject Timeline
- What Did I Miss
- Groups
- Library
- Schedule
- Profile
- Settings
- Teacher Dashboard
- Moderator Dashboard
- Admin Dashboard
- Community Decisions
- Chat
- PDF Reader
- Upload / lesson content dialogs
- Role/recovery/auth UI

---

## 3.2 Backend

Cloudflare Worker + D1 + Durable Objects.

### Backend endpoint/host concept

- Worker URL المقصود: `https://tanweer.magd.workers.dev/`
- Dashboard URL معروف داخل التعليقات: `https://tanweer.magd.workers.dev/dashboard`

### التخزين

- Cloudflare D1 للبيانات المنظمة.
- Durable Object للمحادثة الحية لكل Group.
- **لا R2 في MVP.**
- الصور/الوسائط في D1 BLOB وفق التصميم الحالي.
- حد صورة Android المتوقع بعد الضغط: ≤ 1.25 MB.
- حد الخادم المذكور سابقًا تقريبًا: حول 1.8 MB للصورة.

### بنية Worker المعروفة

```text
server/src/
  index.ts
  router.ts
  env.ts
  lib/
    roles.ts
    permissions.ts
    response.ts
    ids.ts
    crypto.ts
    mappers.ts
    ...
  middleware/
    auth.ts
    permissions.ts
    rateLimit.ts
    ...
  services/
    dashboard.ts
    governance.ts
    groups.ts
    voting.ts
    ...
  realtime/
    GroupChatDO.ts
    ...
```

> توجد ملفات أخرى كثيرة. عند العمل على مهمة محددة يجب استخدام GitHub fetch/search على آخر Commit بدل بناء شجرة مزعومة من الذاكرة.

---

## 4) نموذج الرتب والصلاحيات الحالي

### `server/src/lib/roles.ts`

الـenum الحالي المعروف:

```text
STUDENT
MODERATOR
TEACHER
ADMIN
SYSTEM_OWNER
```

المستويات:

```text
STUDENT       = 1
MODERATOR     = 2
TEACHER       = 3
ADMIN         = 4
SYSTEM_OWNER  = 5
```

الموجود أيضًا:

- `RoleScope.GLOBAL`
- `RoleScope.GROUP`
- `RoleScope.SUBJECT`
- `RoleRequestStatus = PENDING | APPROVED | REJECTED`
- `normalizeRole()` لدعم legacy:
  - MEMBER → STUDENT
  - VERIFIED_TEACHER → TEACHER
  - OWNER → SYSTEM_OWNER
  - ADMINISTRATOR → ADMIN

### `server/src/lib/permissions.ts`

الصلاحيات العالمية المعروفة:

#### مساهمات الجميع

- `CONTRIBUTE_CONTENT`
- `PROPOSE_SCHEDULE`
- `CREATE_HOMEWORK`
- `CREATE_EXAM`
- `CREATE_EVENT`
- `ASK_QUESTION`
- `ANSWER_QUESTION`
- `SEND_CHAT_MESSAGE`
- `UPLOAD_MEDIA`
- `REQUEST_ROLE_UPGRADE`

#### Moderation / Governance

- `MANAGE_SCHEDULE`
- `MODERATE_CONTENT`
- `DELETE_ANY_CONTENT`
- `PIN_CONTENT`
- `MODERATE_HOMEWORK`
- `MODERATE_EXAM`
- `MODERATE_EVENT`
- `MODERATE_ISSUES`
- `VERIFY_BEST_ANSWER`
- `MODERATE_CHAT`
- `DELETE_ANY_CHAT_MESSAGE` (alias)

#### Group administration

- `MANAGE_GROUP_MEMBERS`
- `MANAGE_GROUP_SETTINGS`

#### System administration

- `REVIEW_ROLE_REQUESTS`
- `MANAGE_USERS`
- `REVOKE_SESSIONS`
- `VIEW_AUDIT_LOG`
- `ACCESS_OWNER_DASHBOARD`

ملاحظة مهمة: بعض الأسماء عبارة عن aliases لنفس قيمة enum مثل `MANAGE_CONTENT = MODERATE_CONTENT`. لا تفترض أنها صلاحيات مستقلة في قاعدة البيانات.

### تقييم الصلاحيات

`hasPermission()` يفترض أن:

1. SYSTEM_OWNER = كل شيء.
2. ACCESS_OWNER_DASHBOARD غير متاح لغير Owner.
3. ADMIN = صلاحيات إدارية كاملة.
4. الصلاحيات العالمية الخاصة بالمساهمة متاحة للطلاب.
5. المالك الأصلي لبعض الموارد لديه صلاحيات معينة على نفسه.
6. يتم حل الدور الفعال للمجموعة من `group_members` عند الحاجة.
7. بعدها يطبق الدور على permission-specific switch.

**تحذير معماري:** وجود `RoleScope` لا يعني أن كل Permission في النظام تحترم scope بشكل موحد. يجب دائمًا فحص نقطة الاستدعاء الفعلية.

---

## 5) المصادقة والحسابات

### التسجيل

الفكرة الحالية:

- رقم هاتف يمني + كلمة مرور.
- البريد الإلكتروني اختياري.
- الاسم الكامل ظاهر في الملف/المجموعة.
- الصف والشعبة جزء من هوية الطالب الأكاديمية.
- كل مستخدم يبدأ **STUDENT**.
- توجد sessions/devices.
- توجد recovery code.
- يوجد PBKDF2 / peppering وفق البنية الحالية.

### Recovery Code

الفكرة الحالية من الخادم:

- كود recovery مؤلف من 16 حرفًا/رقمًا على مجموعات 4، أي شكل من نمط:
  `XXXX-XXXX-XXXX-XXXX`
- توجد واجهة Recovery Login.
- يوجد عدم تطابق تاريخي/معروف في أمثلة placeholders داخل Android مثل `SEC-8X92-K4M1` أو `K4M1` مقارنة بالشكل الحقيقي.
- يجب عند تحسين UX إضافة حفظ/نسخ/تأكيد النسخ/توجيه المستخدم للاحتفاظ بالكود.
- لا تؤدِ عملية recovery إلى كشف الكود بلا قيود.

### كلمة المرور

هناك mismatch معروف:

- Android `ValidationUtils.kt`: كان يتطلب على الأقل 8 أحرف مع أحرف + أرقام.
- Server password evaluator: الحد الأدنى المعروف 6 ويقبل شروطًا أضعف حسب score.

هذه ليست مجرد مسألة compile؛ هي **اختلاف سلوك بين العميل والخادم** يجب توحيده.

### مشاكل UX معروفة في التسجيل

- Default grade/section معروف في AuthScreen (11/B) وقد يربك المستخدم إن لم ينتبه للاختيار.
- لا يوجد تأكيد كلمة مرور واضح في الواجهة المفحوصة سابقًا.
- recovery placeholder لا يطابق الشكل النهائي.
- Recovery dialog يقبل أحيانًا طولًا قصيرًا جدًا في الواجهة مقارنة بالكود الحقيقي.

---

## 6) الصفوف والشعب

المعروف في Tanweer:

- الصف 7: شعبة A فقط
- الصف 8: A / B
- الصف 9: A / B
- الصف 10: A / B / C / D
- الصف 11: A / B / C / D
- الصف 12: A / B / C

إجمالي الشعب: **18 شعبة**.

الأقسام متوقعة بأسماء A/B/C/D.

هناك فكرة دعم مجموعات مشتركة بين الشعب وليس فقط group per section.

### Migration عند تغيير الصف/الشعبة

تغيير grade/section ليس مجرد تعديل field؛ قد يؤثر على:

- group membership
- books
- subjects
- schedule
- academic history
- cached data
- current group

لذلك المستقبل الصحيح هو `Section Migration Flow` وليس تعديلًا صامتًا في Profile.

---

## 7) المحتوى الأكاديمي

### Lessons / Contents

المحتوى يتضمن على الأقل:

- `id`
- `group_id`
- `subject_id`
- `created_by`
- `title`
- `description`
- `type`
- views/useful/pin وغيرها حسب schema
- media relation
- تاريخ الدراسة / `study_date` في مسارات القراءة اليومية.

#### مبدأ النشر

أي طالب يستطيع المساهمة، لكن يجب أن يكون:

- المصدر واضحًا.
- صاحب المنشور معروفًا.
- المحتوى قابلاً للتصحيح والتدقيق.
- المحتوى الرسمي قابلًا للاعتماد من Teacher/Moderator/إدارة حسب النظام.

### Media

- `content_media` علاقة المحتوى بالوسائط.
- Server يخزن BLOB في D1 للـMVP.
- Android يضغط الصور قبل الرفع.

---

## 8) الواجبات والاختبارات والأحداث

### Homework

يفترض دعم:

- إضافة واجب من أي طالب.
- تاريخ الدراسة / due date.
- إكمال الواجب شخصيًا.
- تحرير/مراجعة رسمي حسب الصلاحية.

مشكلة معروفة: local completion/status يمكن أن يبدو ناجحًا حتى لو فشل طلب السيرفر، أو يتباعد local/server.

### Exams

يفترض دعم:

- إضافة اختبار.
- تاريخ الاختبار.
- المنهج/الوحدات المرتبطة.
- إدارة رسمية عند Teacher/Admin.

مقترح تقوية: Exam Center بدلاً من مجرد list.

### Events

توجد Events في DB/API/UI، لكنها كانت تاريخيًا أقل حضورًا من Homework/Exam.

مشكلة UX معروفة:

- Search لا يشمل Events.
- Calendar markers لا تمثل جميع الأحداث على قدم المساواة.
- يجب أن تكون Event entity من الدرجة الأولى.

---

## 9) البحث — حالة ومشكلة جوهرية

### الخطأ الجوهري القديم/المعروف

`SearchScreen` استخدم في مرحلة سابقة:

```kotlin
getDayContents(activeGroupId, "")
```

لكن `ContentDao` يستخدم query تعتمد على exact `studyDate`:

```sql
WHERE groupId = :groupId AND studyDate = :date
```

إذن empty date لا يعني “كل المحتوى”. والنتيجة: البحث قد لا يرى الدروس أصلًا.

نفس أصل المشكلة ظهر في:

- `ClassKnowledgeBaseScreen`
- `AcademicHistoryScreen`

### البحث الحالي ليس Search Engine حقيقيًا

حتى لو تم إصلاح query، الفكرة القديمة كانت مجرد contains/filter محلي. المطلوب لاحقًا:

```text
Query
 → Arabic normalization
 → tokenizer
 → local index
 → server search
 → ranking
 → grouped results
 → deep link
```

### الخطة المستهدفة

يدعم:

- exact match
- prefix match
- Arabic normalization
- fuzzy/typo tolerant search
- stemming/normalization عند الحاجة
- ranking
- recent searches
- suggestions
- filters
- operators
- server + local index
- offline search

### فلاتر مستهدفة

- الكل
- الدروس
- الواجبات
- الاختبارات
- الأحداث
- الأسئلة
- الكتب
- الرسائل
- الجدول
- المستخدمون
- المجموعات

### Operators مقترحة

```text
subject:رياضيات
kind:exam
type:homework
date:2026-10-05
from:اسم
in:group
```

### Deep linking

نتيجة البحث يجب أن تفتح العنصر نفسه، وليس الشاشة العامة فقط، مع PDF page عند الإمكان.

---

## 10) Knowledge Base

`SUBJECT_KNOWLEDGE_BASE` موجود كفكرة/واجهة.

الحالة القديمة: التجميع موجود لكنه ليس Knowledge Graph / Index حقيقيًا.

الهدف:

```text
Subject
 ├─ lessons
 ├─ homework
 ├─ exams
 ├─ questions
 ├─ verified answers
 ├─ events
 ├─ PDFs/pages
 └─ timeline
```

ويجب أن يعرف النظام:

- ما تم شرحه.
- ما لم يوثق بعد.
- أكثر الأسئلة المتكررة.
- المصادر المرتبطة بالموضوع.
- محتوى موثق رسميًا.

---

## 11) Timeline / Academic History / What Did I Miss

### Subject Timeline

مشكلة معروفة:

- screen قد تعتمد على محتوى اليوم الحالي فقط للدروس، بينما Homeworks/Exams تُقرأ من مدى مختلف.
- هذا يجعل timeline غير متسقة زمنيًا.

المطلوب:

- timeline تراكمية حقيقية.
- sorting موحد.
- month/week filters.
- روابط مباشرة لكل عنصر.

### Academic History

الموجود تاريخيًا كان يعرض سنوات/أرشيفات حتى عندما لا يكون backend archive حقيقيًا لها.

يجب إما:

- تنفيذ archive حقيقي، أو
- عدم عرض سنوات توحي بوجود بيانات غير موجودة.

### What Did I Miss?

الهدف: عند غياب الطالب، جمع:

- الدروس الفائتة
- الواجبات
- الاختبارات
- الأحداث المهمة
- announcements
- schedule changes
- الأسئلة التي تحتاج معرفة

وتقديمها كتقرير واحد.

---

## 12) Calendar

الـCalendar يقرأ المحتوى المحدد لذلك التاريخ.

المعروف:

- lessons/day contents حسب اليوم.
- homeworks حسب study/due date.
- exams حسب exam date.
- markers في grid كانت تاريخيًا تركز على homework/exam أكثر من كل الأنواع.
- زر Add لليوم كان يفتح lesson upload فقط.

التصميم المستهدف: **Academic Command Center** تجمع فيه كل الأشياء حسب اليوم.

زر Add يجب أن يتيح:

- Lesson
- Homework
- Exam
- Event
- Announcement
- Question
- وربما schedule change

---

## 13) Home / Current Class

مشكلة معروفة مهمة:

- HomeScreen كان يحتوي `periodTimes` hardcoded بدل الاعتماد على start/end المخزن للـschedule.
- قد يعرض `sortedSlots.firstOrNull()` كأنه current period خارج ساعات المدرسة.
- weekday/weekend policy كانت hardcoded.

المطلوب:

- current period مبني على schedule الفعلي.
- school-day policy قابلة للضبط.
- حالة واضحة: before school / break / current / after school.

---

## 14) Schedule Engine

يوجد:

- schedule slots
- day-of-week
- startTime/endTime
- proposals
- versions
- direct manage path
- initial schedule path

المبدأ الصحيح:

1. إنشاء أولي بسيط حتى لو بواسطة طلاب.
2. بعد الاستقرار، كل تعديل غير مباشر يمر عبر proposal/vote أو role.
3. لا يوجد silent mutation يغير الجدول الرسمي دون trace.

### دين تقني معروف

بعد قبول proposal قد تُنشأ slots جديدة بدون جميع metadata المطلوبة (name/icon/color/time details) حسب المسار القديم الذي تم تدقيقه.

### تعارض governance

يوجد:

- Schedule Proposal engine
- Community Decision engine

وهما مساران متوازيان. يجب توضيح علاقتهما أو دمجهما كي لا يجد المستخدم طريقتين بقرارات مختلفة للشيء نفسه.

---

## 15) Governance / Community Decisions

### المحرك الحالي

`server/src/services/governance.ts`

يدعم Content Corrections وUnified Community Decisions.

### نسب النصاب المعروفة في آخر Commit

السيرفر يفرض النسبة حسب type:

- Deletion / Content Deletion = **75%**
- Verification / Official Verification = **65%**
- Schedule / Schedule Change / Pin Content = **60%**
- Correction / default = **50%**

لا يجب الوثوق بنسبة يرسلها العميل.

### Snapshot

عند إنشاء القرار:

- يجمع active group member IDs.
- يحفظهم Snapshot.
- يضمن creator في القائمة إن كان نشطًا.
- يحفظ metadata حاليًا داخل `description` على شكل JSON:

```json
{
  "text": "الوصف الحقيقي",
  "voters": ["user1", "user2"]
}
```

وعند العرض يفكها ويعيد `description = text`.

### المشكلة المعمارية

استخدام `description` كحاوية metadata غير نظيف. الأفضل مستقبلًا:

- عمود metadata مستقل، أو
- جدول decision_voter_snapshot.

### Minimum votes

إذا كان group > 1:

```text
requiredVotes = max(2, ceil(totalEligible * threshold / 100))
```

أما مجموعة ذات عضو واحد فتتعامل بمنطق خاص.

### تطبيق القرار

`applyDecisionExecution()` ينفذ الأثر الحقيقي في DB.

لوحة المالك أصبحت تستدعي هذا بدلاً من وضع status=`APPLIED` فقط.

### مشكلة خطيرة يجب عدم نسيانها: Legacy voting

يوجد أيضًا:

`server/src/services/voting.ts`

وفيه نظام قديم لـdeletion requests:

- deletion_requests
- إجماع/requiredVotes حسب الأعضاء
- requester auto-vote yes
- أي NO قد يرفض العملية مباشرة في المسار القديم

هذا يعني وجود **محركي حوكمة للحذف**:

1. النظام القديم deletion_requests
2. النظام الجديد community_decisions

القواعد مختلفة جدًا. يجب اعتبار هذا **دينًا معماريًا عالي الأولوية** حتى يتم توحيد/ترحيل النظام القديم.

---

## 16) Content Corrections

الخادم الحالي المعروف:

`handleCreateContentCorrection()` يتطلب:

- fieldName
- proposedValue
- reason

ويحفظ:

- originalValue
- proposedValue
- reason
- status PENDING

`handleApproveCorrection()` يطبق:

- TITLE
- DESCRIPTION

ثم يجعل status=`APPROVED`.

`handleRejectCorrection()` يجعل status=`REJECTED`.

### مشاكل UX/logic معروفة

- واجهة قديمة عرضت حقولًا مثل `studyDate`, `title`, `order` بينما backend التنفيذ المعروف يتعامل مع `TITLE` / `DESCRIPTION`.
- واجهة/نص سابق كان يقول السبب optional بينما backend يرفضه إذا كان مفقودًا.

عند أي تطوير جديد يجب مقارنة request body في Android مع implementation server حرفيًا.

---

## 17) Chat / Durable Objects

### الفكرة

- WebSocket حقيقي لكل group.
- `GroupChatDO` هو Durable Object.
- `server/src/index.ts` يتحقق من token + group membership قبل تحويل WebSocket للـDO.
- server يحقن headers موثقة:
  - X-User-Id
  - X-User-Name
  - X-User-Grade-Section
  - X-User-Role
  - X-Group-Id

### WebSocket path معروف

```text
/ws/groups/:groupId
/wss/groups/:groupId
```

Token يمكن أن يأتي:

- Query `?token=`
- Authorization Bearer

### المشكلة المعروفة

رسالة Android قد تبقى `SENDING` إذا ضاع ACK/confirmation.

المطلوب مستقبلًا:

- idempotency/message ID
- ACK timeout
- retry
- reconciliation
- state: SENDING → SENT → FAILED
- resend action

### أمن مهم

لا تعتبر headers القادمة من العميل مصدر هوية. في `index.ts` الحالي يتم استبدال/حقن headers بعد التحقق من token.

---

## 18) Offline-first / Sync

المشروع يريد العمل عندما لا يكون الإنترنت متاحًا، لا مجرد cache للقراءة.

المفهوم المستهدف:

```text
UI mutation
 → local transaction
 → outbox / pending operation
 → network attempt
 → ACK
 → server ID / version
 → reconciliation
```

### مشاكل معروفة

- optimistic UI قد تظهر success قبل server confirmation.
- ليس كل mutation ممثل بنفس outbox strategy.
- delete flows قد تحذف local فقط دون ضمان server deletion.
- homework completion قد يختلف local/server.
- schedule delete قد تمسح المحلي قبل نجاح الخادم.
- بعض catch blocks فارغة وتخفي السبب.

### المطلوب

مركز Sync موحد وper-item state:

- synced
- syncing
- pending
- failed
- conflict

---

## 19) PDF / Books

### Repo منفصل

- `majd7772233-a11y/tanweer-books`
- الكتب الدراسية في repo مستقل.

### Library

Android manager معروف:

`PdfBookManager`

- cache path تقريبًا `filesDir/pdf_books`
- download عند الحاجة.
- temp file ثم copy إلى target.
- read timeout معروف 60s.
- LRU bitmap cache حوالي 25% من memory.
- renderer يغلق.

### PdfReader

قارئ كبير ومتقدم (~2200 سطر تقريبًا في الفحص القديم)، مع أفكار مثل:

- bookmarks
- notes
- drawing
- vocabulary
- أدوات دراسة

**قاعدة:** لا نستبدل PDF Reader ببساطة. الأفضل تقويته وتحويله إلى Study OS.

### Books naming

المستخدم لديه مجلد كتب على الهاتف:

`/storage/3165-6434/Books`

يوجد حوالي 58 PDF في سياق سابق، والـrepo منفصل عن التطبيق.

---

## 20) Dashboard / Control Center

### Owner Dashboard

من `server/src/services/dashboard.ts` و`index.ts` المعروف:

- `/dashboard/login`
- `/dashboard`
- `/api/dashboard/...`

التحقق يتم عبر:

- `X-Owner-Secret` أو
- Bearer token أو
- Cookie `tanweer_owner_token`

Cookie الحالي المعروف:

- HttpOnly
- Secure
- SameSite=Lax
- Max-Age=86400

`TANWEER_OWNER_SECRET` إذا لم يكن موجودًا → dashboard disabled.

### لوحة التحكم تتعامل مع

- stats
- users
- roles
- change role
- disable/enable
- revoke sessions
- role requests
- groups
- audit logs
- contents
- homework
- exams
- events
- issues
- schedules
- decisions
- chat messages
- corrections
- SQL executor (حساس جدًا)

### Pagination

آخر Commit أضاف limit/offset إلى عدة endpoints.

### Owner Dashboard ليس بالضرورة "God Mode مكتمل"

يجب عدم افتراض أن وجود endpoint اسمه dashboard يعني اكتمال جميع CRUD flows في UI. عند تدقيقه، افحص:

- هل القراءة موجودة؟
- هل التعديل موجود؟
- هل الحذف موجود؟
- هل التأثير الفعلي ينفذ؟
- هل permissions صحيحة؟
- هل pagination/retry/loading/error states موجودة؟

---

## 21) Admin / Teacher / Moderator Dashboards

### Teacher Dashboard

الهدف:

- official homework
- exams
- content verification
- teacher answers
- schedule control
- student guidance

### Moderator Dashboard

الهدف:

- moderation queue
- corrections
- schedule proposals
- inappropriate members/messages
- review community activity

### Admin Dashboard

يجب أن يكون أكثر من شاشة إحصائيات. يجب أن يدير المدرسة عمليًا.

### Known UX problem

الأدوار قد تكون موجودة backend أكثر من UI depth. عند كل feature role-based يجب اختبار visibility + authorization + scope.

---

## 22) Groups

المفهوم يدعم:

- شعبة/صف.
- أعضاء.
- group roles.
- join requests في بعض الحالات.
- group chat.
- group settings.
- cross-section groups مستقبلًا.

### آخر إصلاحات مهمة

`handleUpdateGroupMemberRole()`:

- يمنع SYSTEM_OWNER group role.
- يقرأ global role للهدف.
- TEACHER لا يمنح مباشرة لطالب غير Teacher عالميًا إلا من Admin/Owner.
- ADMIN assignment مقيد بـAdmin/Owner.

### الخطر المستمر

وجود global role + member role قد يؤدي إلى divergence ما لم توجد سياسة واضحة.

مثال:

```text
users.role = TEACHER
 group A member role = TEACHER
 group B member role = STUDENT
```

هذا قد يكون مقصودًا أو قد يكون تناقضًا حسب السياسة. لا تفترض واحدة دون مراجعة scope.

---

## 23) Search / Content Query Architecture — ملاحظة هندسية مهمة جدًا

المشكلة المشتركة وراء عدة Bugs قديمة:

**استخدام DAO يومي exact-date لتغذية شاشات تحتاج بيانات عبر أيام/أشهر/سنوات.**

ظهر ذلك في:

- Search
- Knowledge Base
- Academic History
- Timeline

حل معماري أفضل:

```text
DayContentDao
  ← لليوم فقط

ContentRepository
  ← range queries / subject / group / author / type

SearchIndex
  ← full-text / normalized tokens
```

لا تحاول جعل `getDayContents(groupId, date)` يفعل كل شيء.

---

## 24) Notification System

إعدادات Android المعروفة تتضمن:

- homework notifications
- schedule notifications
- exam notifications
- auto-sync

المطلوب مستقبلًا:

- contextual notifications
- reminders
- digest
- deduplication
- quiet hours
- per-category settings
- offline scheduled reminders

---

## 25) Settings

المعروف:

- font scale
- theme (DARK / AMOLED)
- haptics
- auto-sync
- image quality
- notifications
- cache cleanup
- server health check

مشاكل معروفة:

- localization/language setting ليست مكتملة/واضحة.
- بعض النصوص hardcoded داخل Compose.
- toast مستخدم أكثر من اللازم بدل inline/snackbar/status components.

---

## 26) Localization / Arabic UX

لا تفترض أن "واجهة عربية" تعني localization مكتمل.

ملاحظات معروفة:

- بعض النصوص مثل:
  - `Class Knowledge Base`
  - `Scanner`
  - `Recovery Code`
  كانت/قد تكون ظاهرة بالإنجليزية.
- توجد hardcoded strings كثيرة.
- RTL/LTR يجب أن يكون مدروسًا.
- اتجاه الأسهم والأيقونات يجب احترامه.
- التواريخ والأيام يجب أن تكون عربية/محلية عندما تكون الواجهة عربية.

**لا تعرّب مصطلحًا تقنيًا بطريقة تبدو غريبة للمستخدم.** استخدم صياغة مدرسية طبيعية.

---

## 27) قائمة أخطاء المنتج/المنطق المعروفة (51) — مرجع التدقيق الأساسي

> القائمة أدناه ليست "51 compile bugs". كلمة bug هنا تعني سلوكًا/منطقًا/UX/معمارية: التطبيق قد يبني وينجح تقنيًا لكن يزعج المستخدم أو يعطي بيانات خاطئة أو يوحي بشيء غير صحيح.

1. **Search query range bug:** Search كان يستخدم empty date مع DAO يومي.
2. **Knowledge Base range bug:** نفس نمط الاستعلام اليومي لشاشة تراكمية.
3. **Academic History fake archive:** السنوات قد تظهر بلا backend archive حقيقي.
4. **Dashboard Apply used to only flip status (fixed in latest):** تم إصلاحه باستخدام `applyDecisionExecution`.
5. **Global role downgrade left stale group roles (fixed in latest).**
6. **Direct Teacher-from-group escalation bypass (fixed in latest).**
7. **Group could receive SYSTEM_OWNER role (fixed in latest).**
8. **Global/Group role divergence can remain conceptually ambiguous.**
9. **RoleScope موجود لكنه غير مطبق بالتساوي على كل permission.**
10. **Admin group-role semantics بحاجة سياسة واضحة.**
11. **Governance voter rules historically غير واضحة للمستخدم.**
12. **Snapshot voter model stored as JSON metadata rather than dedicated relation.**
13. **Schedule Proposal + Community Decision محركان متوازيان.**
14. **Passed quorum قد يبقى ينتظر manual approval في بعض المسارات؛ يجب توضيح ذلك.**
15. **APPROVED/APPLIED تحتاج تسميات UX مفهومة للطلاب.**
16. **Correction detail UI لا يشرح before/after بشكل كافٍ.**
17. **Correction field mismatch: UI fields قد لا تطابق backend execution.**
18. **Correction reason optional في UI بينما backend يتطلبه.**
19. **Optimistic offline UI قد يوهم بنجاح غير مثبت.**
20. **Delete local/server divergence.**
21. **Homework completion local/server divergence.**
22. **Schedule create fallback قد يتحول إلى proposal بصمت عند فشل direct create.**
23. **Schedule delete قد يحذف محليًا قبل confirmation.**
24. **Schedule proposal application يحتاج atomic transaction semantics.**
25. **New schedule slots قد تفقد metadata بعد approval.**
26. **HomeScreen hardcodes period times بدل schedule الحقيقي.**
27. **Home قد يعرض first slot كcurrent خارج ساعات المدرسة.**
28. **Weekend policy hardcoded.**
29. **Calendar لا يعرض كل الأكاديميات كtimeline موحدة.**
30. **Calendar Add لا يدعم كل أنواع العناصر.**
31. **Events أقل تكاملًا من Homework/Exam.**
32. **Search ليس search engine حقيقيًا.**
33. **Empty search query لا يعطي recent/trending/suggestions بصورة ممتازة.**
34. **Search يستبعد Events.**
35. **Search يستبعد Chat.**
36. **Search لا يفتح target deep-link/صفحة PDF مباشرة.**
37. **Knowledge Base ليست Knowledge Graph حقيقية.**
38. **Subject Timeline range غير متسق.**
39. **Timeline sorting يحتاج unified chronology.**
40. **Academic History يعرض سنوات غير مستندة إلى archive حقيقي.**
41. **Personal academic history يعتمد بشدة على identity الحالية وقد لا يحافظ على هوية أكاديمية مستقرة عبر النقل.**
42. **Recovery code UX يحتاج save/copy/backup/reveal policies.**
43. **Recovery input length mismatch.**
44. **Sessions/devices UI غير مكتمل مقارنة بالbackend.**
45. **Changing grade/section يحتاج Migration Flow حقيقي.**
46. **Group switching قد يعرض Room/cache القديم مؤقتًا.**
47. **Sync status ليس per-mutation بشكل موحد.**
48. **Offline queue ليست موحدة لكل mutation.**
49. **Empty catches في Chat/Schedule/Sync تخفي الأخطاء.**
50. **Chat SENDING state قد تبقى إذا ضاع ACK.**
51. **الجذر المعماري المشترك: DAO يومي exact-date يستخدم كقاعدة لسياقات cross-day/historical/search.**

### ملاحظة عن الحالة

لا تفترض أن كل رقم أعلاه ما زال unresolved. الأرقام 4–7 تم إصلاحها في آخر Commit المعروف. الأرقام الأخرى تحتاج إعادة فحص عند كل audit جديد.

---

## 28) كيف أقوي الميزات الموجودة بدل مجرد إصلاحها؟

### 28.1 Search → Search Engine حقيقي

المستهدف:

```text
Input
→ normalize Arabic
→ tokenize
→ local index
→ remote index/search
→ merge
→ rank
→ group results
→ deep link
```

Ranking المقترح:

- exact title
- exact phrase
- prefix
- field match
- subject/group relevance
- recency
- verified/official boost
- engagement/useful boost
- fuzzy similarity

واجهة النتائج:

- نوع العنصر
- العنوان
- مقتطف
- صاحب المحتوى
- المادة
- الشعبة
- التاريخ
- badge رسمي/موثوق
- quick actions

### 28.2 Knowledge Base → Academic Knowledge Graph

يربط كل concept بمصادره، دروسه، أسئلته، الواجبات والكتب.

### 28.3 Lesson Upload → Documentation Pipeline

بعد الرفع:

```text
photo/file
→ classify
→ OCR optional
→ clean title
→ subject/date
→ attach media
→ suggest duplicates
→ publish
```

### 28.4 Homework → Task Manager

يدعم:

- due date
- completion
- priority للطالب الشخصي (ليس role priority)
- reminder
- attachment
- status
- overdue
- recurrence عند الحاجة

### 28.5 Exams → Exam Center

- countdown
- scope
- study checklist
- linked lessons/PDF pages
- status
- reminders

### 28.6 Calendar → Command Center

اليوم يجب أن يحوي stream موحدة:

```text
07:00 schedule
08:00 lesson
09:30 homework
12:00 event
...
```

### 28.7 Schedule → Versioned Engine

- versions
- diff
- change reason
- proposal
- approval
- rollback

### 28.8 Governance → Governance Center

كل قرار يوضح:

- من طرحه؟
- ماذا سيغير؟
- لمن التصويت؟
- النصاب.
- من صوت؟
- النتيجة.
- الأثر بعد التطبيق.
- audit trail.

### 28.9 Chat → Academic Chat

تحويل الرسالة إلى كيان أكاديمي:

`Message → Lesson/Homework/Exam/Event/Issue`

### 28.10 Q&A → Solved Knowledge

أفضل إجابة تصبح مرجعًا قابلاً للبحث، مع verified answer badge وربط بالمادة/PDF.

### 28.11 PDF Reader → Study OS

أضف:

- note linking
- page references
- vocabulary
- highlight
- question from page
- homework/exam links
- progress
- offline bundles

### 28.12 Offline-first → Offline Operating Model

الهدف ليس "بدون انترنت يعرض cache" بل:

```text
read
write
queue
retry
resolve
sync
recover
```

### 28.13 Notifications → Contextual Intelligence

بدل notification لكل شيء:

- digest
- due-soon
- schedule conflict
- missed class
- exam tomorrow
- unresolved question

### 28.14 Dashboard → Control Center

يجب أن يرى Owner/authorized staff:

- data health
- permissions
- governance
- users
- content
- schedule
- logs
- security
- system flags

---

## 29) ميزات جديدة كليًا — backlog طويل (70 فكرة)

1. Smart Daily Brief
2. Smart “What Did I Miss?” summary
3. Personal Study Planner
4. Smart Revision Map
5. Class Knowledge Graph
6. Contribution Reputation
7. Content Quality Score
8. Duplicate Detection
9. Smart Merge
10. Verified Content badge
11. Teacher Official Channel
12. School Announcements stream
13. Cross-section Groups
14. Subject Communities
15. Shared Exam Room
16. Exam Countdown Center
17. Smart contextual reminders
18. Message → Academic Item converter
19. Notification Digest
20. Personal Home Dashboard
21. Academic Streak
22. Study Statistics
23. Personal Bookmarks
24. Universal Favorites
25. Recently Viewed
26. Offline Downloads Center
27. Storage Manager
28. Conflict Center
29. Public-ish content audit trail
30. Content History
31. Restore Content
32. Smart Moderation Queue
33. Abuse/Spam Detection
34. Report System
35. Safety Mode
36. Group Rules
37. Group Welcome Board
38. Section Migration Assistant
39. Academic Year Closure
40. School Calendar Layer
41. Current School Time
42. Quick Actions
43. Share as Tanweer Card
44. Deep Links
45. Smart QR Codes
46. Group QR Join
47. Teacher Assignment System
48. Teacher Availability
49. Smart FAQ from solved questions
50. Related Questions / Suggested Answers
51. Knowledge Gap Detection
52. “Most Needs Documentation” detector
53. Contribution Suggestions
54. Daily Summary
55. Weekly Academic Digest
56. Teacher/Admin Broadcast
57. Expiring Content
58. Pinned Daily Content
59. Important Academic Alerts
60. Owner System Health
61. Data Integrity Checker
62. Permission Simulator
63. Governance Simulator
64. Feature Flags
65. Maintenance Mode
66. Emergency Lock
67. Backup/Export
68. Import/Recovery
69. Student Data Export
70. Privacy Center

---

## 30) أعمدة تصميم مستقبلية مهمة

### 30.1 Contribution reputation

ليس "تسلسل هرمي جديد" للطلاب. هدفه:

- إبراز المساهمات الجيدة.
- كشف spam.
- تشجيع الجودة.
- مساعدة moderation.

لا تستخدم السمعة لتجاوز الصلاحيات الأمنية.

### 30.2 Content verification

حالة محتوى ممكن تكون مثل:

```text
COMMUNITY
VERIFIED
OFFICIAL
ARCHIVED
DISPUTED
```

لكن لا تخلط verification مع global role.

### 30.3 Versioning

لكل عنصر مهم:

```text
version
updated_by
updated_at
change_reason
previous_version
```

### 30.4 Audit

كل mutation الإداري أو الحوكمي المهم يجب أن يمكن تتبعه.

### 30.5 Deep Links

مثال مفاهيمي:

```text
 tanweer://content/<id>
 tanweer://homework/<id>
 tanweer://exam/<id>
 tanweer://issue/<id>
 tanweer://book/<id>/page/42
```

---

## 31) Data / DB mental model

الكيانات التي يجب توقعها عند قراءة schema/migrations الحالية أو المستقبلية:

```text
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

chat/realtime persistence-related tables where applicable
```

قد تختلف الأسماء الدقيقة. لا تنشئ schema مبنية على هذه القائمة قبل فحص migrations الحالية.

---

## 32) الأمن — أشياء لا ينبغي إفسادها

1. Server هو المصدر الحقيقي للصلاحيات.
2. UI permission checks ليست Security boundary.
3. لا تثق في role القادم من العميل.
4. لا تثق في groupId وحده دون membership check.
5. لا تقبل Owner dashboard بلا `TANWEER_OWNER_SECRET` صحيح.
6. لا تضف fallback secret افتراضي.
7. لا تخزن secret في Android.
8. WebSocket يجب أن يتحقق من token + membership.
9. Audit logs للعمليات الحساسة.
10. Role downgrade يجب أن يحافظ على الاتساق بين global/group.
11. SYSTEM_OWNER ليس group role.
12. Teacher verification يجب ألا يتحول إلى self-service privilege escalation.
13. التصويت Snapshot يجب أن يمنع late joiners من تغيير electorate.
14. يجب منع double voting.
15. يجب منع تطبيق القرار مرتين أو تطبيق decision في حالة غير مناسبة.
16. لا تجعل SQL executor متاحًا لأي دور غير Owner.

---

## 33) جودة المنتج — ما الذي يعنيه "جاهز" هنا؟

"يبني بنجاح" ≠ "جاهز".

نعتبر feature جيدة فقط عندما:

- UI واضحة.
- النصوص عربية/مترجمة.
- loading state موجود.
- empty state ذكي.
- error state واضح.
- retry متاح عند الحاجة.
- offline behavior معروف.
- server result يؤكد النجاح.
- local cache لا تكذب.
- deep link يعمل.
- permission الصحيح مطبق على server.
- role scope صحيح.
- audit/ownership واضح.
- لا يوجد duplicate engine يؤدي نفس المهمة بقواعد مختلفة.

---

## 34) قواعد تدقيق GitHub التي يجب على GPT اتباعها

عندما يقول المستخدم: "روح آخر Commit وافحصه"، اتبع التالي:

### المرحلة 1 — تحديد الحقيقة

1. تحقق من `main`/الفرع المقصود.
2. احصل على latest SHA.
3. اقرأ Commit metadata/diff.
4. لا تعتمد على Commit قديم من الذاكرة.

### المرحلة 2 — قراءة البنية

حد أدنى:

- Android entry/navigation
- ViewModel/state
- repositories/DAO
- auth
- sync/offline
- key screens
- router
- permissions
- roles
- relevant backend services
- migrations/schema
- CI/deploy عند الحاجة

### المرحلة 3 — البحث عن أخطاء المنتج

لا تكتفِ ب:

- syntax
- compile
- missing import
- Gradle

ابحث عن:

- false success
- stale state
- missing data
- wrong scope
- UI/backend mismatch
- duplicate systems
- broken navigation
- hidden English
- empty catch
- missing permission
- bad default
- wrong date/time logic
- orphan records
- race conditions
- offline divergence
- security bypass

### المرحلة 4 — إخراج النتيجة

يفضل ترتيب:

1. أخطاء حرجة P0
2. أخطاء مهمة P1
3. UX/product P2
4. polish P3
5. ما تم إصلاحه بالفعل في آخر Commit
6. ما بقي
7. تقوية الميزات
8. ميزات جديدة

---

## 35) قواعد تعديل الكود التي يفضلها المستخدم

هذه قواعد عملية مهمة عند إعطاء patch:

- **ملف جديد:** أرسل الملف كاملًا.
- **تعديل صغير في ملف موجود:** أرسل الدالة كاملة + مكان وضعها بدقة.
- **ملف صغير وتعديلات كثيرة:** أرسل الملف كاملًا.
- **ملف كبير وتعديلات كثيرة:** أرسل جميع دوال الإصلاح كاملة مع أماكنها الدقيقة.
- لا تتكلم فقط عن "اختبر ثم قل لي". أعطِ عدة خطوات عملية معًا.
- كل patch يجب أن يُفحص مقابل **آخر Commit**.
- أعطِ Commit message إن كان التعديل متعلقًا بـGit.

### تفضيلات تقنية للمستخدم

- يميل إلى Java/XML في المشاريع الأخرى، لكن Tanweer الحالي معروف بأنه Kotlin/Compose.
- يريد واجهة مصقولة جدًا، وليس Basic CRUD.
- يهتم بالتفاصيل الصغيرة والمنطق الفعلي أكثر من مجرد البناء.

---

## 36) ما لا ينبغي اقتراحه بدون سبب

- لا تقترح Wrangler CLI للمستخدم على Android؛ سبق أن وضح أن `npx wrangler` لا يعمل لديه بسبب architecture.
- يمكن الحديث عن Wrangler/Cloudflare في CI/server نفسه عندما يكون ذلك مناسبًا.
- لا تفترض R2 متاحًا لـMVP؛ التصميم الحالي يتجنب R2 بسبب احتياج بطاقة دفع.
- لا تعد إلى نموذج Admin-centric يقتل مساهمة الطلاب.

---

## 37) الأولويات الحالية المقترحة

### P0 — قبل اعتبار المنتج موثوقًا

- توحيد governance engines، خصوصًا legacy voting vs community decisions.
- توحيد client/server validation.
- إصلاح offline false-success.
- توحيد deletion/update semantics.
- منع role/scope inconsistencies.
- إصلاح Search data source جذريًا.
- إظهار errors بدل empty catch.
- جعل WebSocket delivery reliable.
- تدقيق Owner/SQL/dashboard security.

### P1 — تقوية تجربة المنتج

- Search Engine حقيقي.
- Calendar Command Center.
- Knowledge Base حقيقي.
- Schedule versioning.
- Governance Center واضح.
- Sessions/devices UI.
- Academic History حقيقي.
- Group migration.

### P2 — differentiation

- Study OS.
- Contribution reputation.
- Duplicate detection.
- smart reminders.
- related questions.
- Knowledge gap detection.
- cross-section communities.

### P3 — polish

- animation.
- haptics.
- richer glass.
- customizable home.
- share cards.
- advanced personalization.

---

## 38) Roadmap عملي مقترح

### Phase A — Trust Layer

- auth consistency
- permissions consistency
- role scope
- audit
- governance unification
- sync correctness

### Phase B — Academic Core

- Search
- Knowledge Base
- Timeline
- Calendar
- Schedule
- Homework
- Exam Center

### Phase C — Collaboration

- Chat
- Q&A
- groups
- contributions
- moderation

### Phase D — Intelligence

- summaries
- suggestions
- duplicate detection
- knowledge graph
- smart reminders

### Phase E — Control & Scale

- Owner Control Center
- monitoring
- data integrity
- backups
- maintenance mode
- feature flags

---

## 39) الفرق الذي يجب أن يصنعه Tanweer عن WhatsApp

Tanweer ليس:

> "WhatsApp لكن للدراسة"

بل:

> **Academic Operating System للشعبة والمدرسة.**

القيمة يجب أن تأتي من:

- structured academic memory
- search
- history
- schedule
- homework/exam context
- verified knowledge
- offline reliability
- governance
- student contributions

---

## 40) Launch Readiness Checklist

### Product

- [ ] كل screen لها purpose واضح.
- [ ] لا توجد screens توهم ببيانات غير موجودة.
- [ ] empty states صحيحة.
- [ ] errors قابلة للفهم.
- [ ] navigation لا تضيف خطوات غريبة.

### Data

- [ ] no local/server silent divergence.
- [ ] migration paths واضحة.
- [ ] deletion cascades مدروسة.
- [ ] versions/audit للبيانات المهمة.

### Security

- [ ] owner auth آمن.
- [ ] permissions server-side.
- [ ] role upgrades verified.
- [ ] no scope bypass.
- [ ] no leaked secrets.

### Governance

- [ ] محرك تصويت واحد واضح.
- [ ] snapshot ثابت.
- [ ] thresholds واضحة.
- [ ] result execution فعلي.
- [ ] no double-apply.

### Offline

- [ ] outbox.
- [ ] retry.
- [ ] reconciliation.
- [ ] conflicts.
- [ ] status per item.

### Localization

- [ ] Arabic-first.
- [ ] no unexplained English remnants.
- [ ] RTL checked.
- [ ] date/time formatting.

---

## 41) بروتوكول تحديث هذا الملف بعد كل رسالة مهمة

**هذا القسم مهم جدًا.** المستخدم يريد أن يبقى `GPT.md` ذاكرة حية للمشروع، وأن يتم تحديثه مع الأحداث الكبيرة.

بعد كل رسالة مهمة تؤثر على المشروع، يجب تسجيل قطعة تحديث صغيرة قابلة لإضافتها إلى نهاية الملف.

لا تعِد إرسال الملف كاملًا للمستخدم كل مرة؛ يكفي عند الطلب/حسب السياق **قطعة Change Fragment** تحتوي الجديد.

### متى تكون الرسالة "مهمة"؟

مثال:

- تم إنشاء/تعديل feature كبيرة.
- تم إصلاح bug منطقي مهم.
- تغير schema/API.
- تغير role/permission.
- تغير architecture.
- تغير navigation.
- تم اعتماد قرار تصميم نهائي.
- تم حذف feature.
- تم تغيير roadmap/priority.
- تم اكتشاف bug جديد خطير.
- تم تأكيد نجاح/فشل deploy/CI مهم.
- تم الوصول إلى Commit جديد.

### صيغة القطعة المطلوبة في المحادثة

استخدم block بهذا الشكل:

```md
## GPT.md UPDATE — 2026-10-05

### Current state change
- [ما الذي تغير؟]

### Verified at
- Commit: `<sha>`
- Branch: `main`

### New facts
- [حقيقة جديدة]

### Fixed
- [ما تم إصلاحه]

### Still open
- [ما بقي]

### Decisions
- [قرار تصميمي تم اعتماده]

### Next checkpoint
- [ما يجب أن أتذكره في المرة القادمة]
```

إذا لم يوجد Commit جديد بعد، اكتب:

```text
Commit: working tree / not yet committed
```

ولا تخترع SHA.

### قاعدة مهمة

التحديث يجب أن يسجل **التغيير** فقط، لا يعيد نسخ كل الذاكرة.

---

## 42) CHANGELOG داخلي — البداية

### 2026-10-05 — إنشاء GPT.md

- تم إنشاء هذا الملف كذاكرة داخلية للمساعد.
- آخر HEAD معروف وقت الإنشاء: `1babe65c890f55c14ec38fac308301790801cb1a`.
- آخر رسالة Commit معروفة: `Fixing Some Bugs`.
- تم توثيق بنية Android + Worker/D1/DO + Roles + Governance + Offline + Search + PDF + Dashboards.
- تم فصل known bugs عن الإصلاحات التي تمت بالفعل.
- تم إضافة 70 فكرة ميزات جديدة.
- تم إضافة protocol لتحديث هذا الملف بعد الرسائل المهمة.

---

## 43) سجل قرارات تصميمية يجب الحفاظ عليها

1. الاسم الرسمي: **Tanweer / تنوير**.
2. الطلاب مساهمون، لا مجرد viewers.
3. ownership للمحتوى محفوظ.
4. role hierarchy هي: Student → Moderator → Teacher → Admin → System Owner.
5. "priority system" = role hierarchy، وليس content priority.
6. Teacher verification رسمي وليس self-service.
7. SYSTEM_OWNER لا يُمنح كGroup role.
8. Backend server هو مصدر الحقيقة للصلاحيات.
9. Cloudflare Workers للbackend/coordination، لا R2 في MVP.
10. Books في repo مستقل.
11. Search يجب أن يتحول إلى Search Engine حقيقي.
12. Knowledge Base يجب أن تصبح Academic Knowledge Graph.
13. Offline يجب أن يكون mutation-safe وليس cache فقط.
14. Governance يجب أن يكون موحدًا، وليس محركين مختلفين للقرار نفسه.
15. Owner Dashboard يجب أن يكون Control Center حقيقيًا، لا مجرد إحصائيات.
16. Arabic-first + Liquid Glass هو معيار UX.

---

## 44) أشياء يجب إعادة التحقق منها دائمًا ولا أعتبرها حقيقة أبدية

أي شيء من التالي قد يتغير في Commit جديد:

- أسماء الملفات الدقيقة.
- أسماء DAO/functions.
- endpoint paths.
- migration numbers.
- CI state.
- server schema.
- UI screen names.
- thresholds.
- owner secret variable.
- exact PDF implementation.
- عدد الاختبارات.
- حجم APK.
- عدد الكتب.
- حالة أي Bug من القائمة 51.

عند ذكر شيء "حالي" للمستخدم، ارجع إلى GitHub إذا كان ذلك مهمًا.

---

## 45) كيف أتعامل مع مهمة جديدة في محادثة جديدة؟

عند بدء محادثة جديدة ووجود هذا الملف في الجذر:

1. اقرأ GPT.md بالكامل أو الأجزاء ذات الصلة.
2. حدد ما إذا كان المستخدم يريد تحليلًا أم تعديلًا أم feature جديدة.
3. افحص أحدث Commit قبل العمل على الكود.
4. قارن الواقع مع GPT.md.
5. لا تعتبر أي bug قديم مؤكدًا بلا إعادة فحص.
6. عند التعديل، احترم قواعد patch الخاصة بالمستخدم.
7. بعد الرسالة/القرار المهم، أنشئ **Update Fragment** وأعطه للمستخدم ليضيفه إلى GPT.md.

---

## 46) Template جاهز لكل تحديث مستقبلي

```md
## GPT.md UPDATE — YYYY-MM-DD HH:MM (+03:00)

### Current state change
- 

### Verified at
- Repository: `majd7772233-a11y/Tanweer`
- Branch: `main`
- Commit: `<sha>`
- Message: `<commit message>`

### Architecture changes
- 

### Product changes
- 

### Security / permissions changes
- 

### Data / API changes
- 

### Bugs discovered
- 

### Bugs fixed
- 

### UX changes
- 

### Decisions
- 

### Still open
- 

### Do not forget
- 

### Next checkpoint
- 
```

---

# END OF GPT.md

> **آخر تذكير للمساعد:** لا تتعامل مع هذه الوثيقة كبديل لقراءة الكود. اقرأها أولًا لتعرف "ما الذي يعنيه المشروع"، ثم ارجع إلى آخر Commit لتعرف "ما الذي هو موجود فعليًا الآن".
