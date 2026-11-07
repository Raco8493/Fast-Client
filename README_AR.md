# Hostphone — استضافة Minecraft Java + Bedrock على أندرويد

**Hostphone 1.1 Beta** — تطبيق أندرويد باللون الأحمر وبالوضع الرأسي. عند فتح التطبيق يظهر زر «إنشاء سيرفر» في منتصف الرئيسية؛ ثم تختار اسم السيرفر، Java + Bedrock، إصدار Java Minecraft، وتختار Paper للـPlugins أو Fabric للمودات. يوجد شريط تنقل سفلي بأزرار دائرية. Forge وFolia ظاهرين لكن غير متاحين بعد؛ لم يتم الادعاء بأنهما يعملان.

**موبايلك هو الاستضافة الحقيقية**: داخل Termux يعمل Java server المحلي، وGeyser + Floodgate يسمحان للاعب Bedrock بدخول نفس العالم. GitHub ليس سيرفر اللعبة.

## خطوات التشغيل

1. نزّل APK من [GitHub Actions — Hostphone-APK](https://github.com/Raco8493/Fast-Client/actions/workflows/build-apk.yml?query=branch%3Aphonecraft-android-host)، أو ابنِه بنفسك. مصدر Android في `android/`.
2. افتح Hostphone ← **إنشاء سيرفر**. حدّد اسم السيرفر وJava version وPaper أو Fabric.
3. عند ظهور شاشة الإعداد نزّل Termux من F-Droid. افتحه، انسخ أمر الربط الموجود في التطبيق، امنح صلاحية تشغيل الأوامر ثم ثبّت محرك Hostphone وشغّله.
4. اكمل التثبيت من التطبيق واقبل Minecraft EULA. التطبيق يحمل ملف السيرفر من PaperMC أو Fabric بالإضافة إلى Geyser وFloodgate.
5. ابدأ Minecraft server. استخدم أسفل التطبيق للوصول إلى Console / OP / Mods / إعدادات RAM.

### الاتصال

Java TCP 25565 وBedrock UDP 19132 على نفس شبكة الواي فاي. من خارج الشبكة يلزم ضبط الراوتر أو حل يمرر البروتوكولين. **لا يمكن ضمان الاستمرارية** إذا أغلق Android عملية Termux، أو واجه الجهاز حرارة أو نقص ذاكرة. إعداد RAM هو حد Java Heap وليس 85% ضمانًا من رامات الهاتف، وCPU يحدد الأنوية الظاهرة وليس استهلاك 90% إجباريًا.

**حالة المشروع**: اختبارات Python والمنطق المحلي يمكن تشغيلها في CI؛ لا يعتبر هذا اختبار سيرفر على هاتف أندرويد حقيقي. Forge/Folia غير مفعّلين. بعض إصدارات Java القديمة لا تشتغل مع إضافات Geyser الأحدث بالضرورة. راجع [تعليمات التطبيق](android/README_AR.md).
