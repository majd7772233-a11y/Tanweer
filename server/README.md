# Tanweer Backend (Cloudflare Workers + D1 + Durable Objects)

خادم منصة تنوير التعليمية الجماعية المبني على معمارية Cloudflare الحديثة وسريعة الاستجابة.

## البنية التحتية
- **Worker (TypeScript)**: معالجة الطلبات، التوثيق الأمني (Auth & Session Security)، وإدارة واجهات برمجة التطبيقات (REST APIs).
- **Cloudflare D1 (SQLite)**: إدارة وتخزين البيانات المنظمة وجدول الوسائط (`media_blobs`) كبيانات BLOB مشفرة ومضغوطة تحت سقف أمان 1.8 ميجابايت مع فحص التوقيعات الثنائية (Magic Numbers).
- **Cloudflare Durable Objects**: إدارة المحادثات الفورية اللحظية (WebSocket) مع التحقق الأمني من الـ Tokens وتصاريح العضوية الفعالة.

## التشغيل المحلي والاختبار
```bash
# تثبيت الحزم
npm install

# تشغيل الخادم محلياً
npm run dev

# تطبيق الـ Migrations محلياً
npm run db:migrate:local

# تشغيل الاختبارات
npm test

# فحص الأنواع البرمجية (Type Check)
npm run types
```

## النشر إلى Cloudflare (Production)
```bash
# تطبيق الـ Migrations على قاعدة D1 الإنتاجية
npm run db:migrate:prod

# نشر الـ Worker
npm run deploy
```

## تفاصيل تخزين الوسائط (Media Storage Architecture)
- يتم تخزين الصور في جدول `media_blobs` كـ BLOB في قاعدة D1.
- التحقق الصارم من صحة صيغة الملفات عبر فحص الـ Magic Numbers (JPEG, PNG, WebP, GIF) لمنع رفع أي ملفات تنفيذية أو غير مصرح بها.
- يتم إرجاع الصور عبر مسار الكاش السريع `/api/v1/media/:id` مع ترويسات Cache-Control دائمة لسرعة العرض على أجهزة الطلاب.
