# Hostphone — تطبيق استضافة Minecraft على Android

**Hostphone 1.1 beta**: واجهة حمراء بالطول (Portrait) مع زر «إنشاء سيرفر» في الرئيسية، واختيارات Java + Bedrock، إصدار Minecraft Java، وPaper أو Fabric. أزرار التنقل الدائرية أسفل الشاشة: الرئيسية / إنشاء / السيرفر / إعدادات. عند اتصال المحرك تفتح لوحة السيرفر بنفس الستايل، فيها تشغيل وإيقاف وكونسول وOP والـMods/Plugins والـRAM.

> التطبيق **ليس** بديلاً عن Termux. ملفات سيرفر Minecraft Java تشتغل فعليًا **على نفس الهاتف** عبر OpenJDK في Termux. GitHub يستضيف المصدر والبناء فقط. Bedrock تدخل نفس عالم Java باستخدام Geyser + Floodgate، وليست نسخة Mojang Bedrock Dedicated Server مستقلة. Forge/Folia يظهران في قائمة الأنواع مع توضيح أنهما غير مدعومين حاليًا، ولا يمكن اختيارهما. أول استخدام يحتاج إنترنت.

## الحصول على APK

- افتح https://github.com/Raco8493/Fast-Client/actions/workflows/build-apk.yml?query=branch%3Aphonecraft-android-host
- اختر آخر Build ناجح ثم Artifact باسم **Hostphone-APK**.
- فك الملف ZIP وثبت `Hostphone-debug.apk` على الموبايل.

## استعمال التطبيق

1. افتح **Hostphone** واضغط الزر الأحمر «إنشاء سيرفر» في وسط الرئيسية.
2. اختر اسم السيرفر، **Java + Bedrock** أو Java فقط أو Bedrock عبر Geyser، وإصدار Minecraft Java، ثم **Paper** أو **Fabric**.
3. اضغط متابعة. إذا المحرك لم يثبت بعد، ستظهر صفحة إعداد Termux خطوة بخطوة.
4. نزّل [Termux من F-Droid](https://f-droid.org/packages/com.termux/) وافتحه مرة، وفعّل الإذن بعد تنفيذ أمر الربط المنسوخ من التطبيق، ثم «تثبيت المحرك» وبعد انتهاء التثبيت «تشغيل المحرك».
5. ارجع واضغط «المحرك شغّال؟ كمّل إنشاء السيرفر»، ووافق على [Minecraft EULA](https://aka.ms/MinecraftEULA)، ثم «إنشاء وتثبيت السيرفر».
6. بعد انتهاء تحميل السيرفر اضغط «تشغيل». لو دي أول مرة مع Geyser اعمل Stop ثم Start مرة ثانية.

**ملاحظات**: النسخة التجريبية لا تضمن دعم كل إصدار أو كل مود. تنزيل ملفات السيرفر من الجهات الرسمية قد يفشل لو النسخة غير متاحة. Paper للإضافات Plugins، Fabric للمودات server-side؛ بعض مودات Fabric تتطلب تعديلات عند اللاعب أو لا تدعم Bedrock. إبقاء Termux شغال وتقليل قيود البطارية يساعدان؛ الأندرويد ممكن يقفله تحت ضغط الحرارة أو البطارية. JAVA TCP 25565 وBedrock UDP 19132 على نفس الواي فاي، ومن الإنترنت الخارجي تحتاج إعدادات الشبكة.

## البناء

Android Java بدون AndroidX. `android/app/build.gradle` يستخدم Gradle 8.10.2 / AGP 8.8.2 وSDK 35. أمر البناء داخل مجلد `android`:

```bash
gradle --no-daemon :app:assembleDebug
```

ملف APK موقّع بمفتاح Debug تجريبي. أزل النسخة السابقة عند وجود تعارض بالتوقيع.
