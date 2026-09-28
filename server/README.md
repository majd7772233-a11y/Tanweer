# Tanweer Backend (Cloudflare Workers + D1 + R2 + Durable Objects)

خادم منصة تنوير التعليمية الجماعية المبني على تقنيات Cloudflare الحديثة.

## البنية التحتية
- **Worker (TypeScript)**: معالجة الطلبات وإدارة واجهات برمجة التطبيقات (API).
- **Cloudflare D1 (SQLite)**: إدارة البيانات المنظمة (المستخدمين، الشعب، الحصص، الدروس، الواجبات، الاختبارات، الاستفسارات).
- **Cloudflare R2**: تخزين الصور والملفات ومستندات الكتب بصيغة Object Storage مباشر.
- **Cloudflare Durable Objects**: إدارة المحادثات الفورية اللحظية (WebSocket).

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
```

## النشر إلى Cloudflare
```bash
# تطبيق الـ Migrations على قاعدة D1 البعيدة
npm run db:migrate:prod

# النشر
npm run deploy
```
