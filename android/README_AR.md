# Hostphone v1.3 alpha — محرك Minecraft حقيقي يعمل محليًا على Android ARM64

المشروع يشمل `ServerEngine` لتحميل Paper/PowerNukkitX من الإصدارات الرسمية و`HostService` لخدمة تشغيل Java فعلية في جهازك. كل شيء على نفس موبايلك، ومفيش Termux أو VPS أو Remote Server.

**المحرك غير مبني داخل ملفات المصدر وحدها**: GitHub Actions بتنزل Android OpenJDK 25 ARM64 وبتتحقق من SHA-256 قبل بناء APK وتضمّنه كـ runtime25.zip وlibhostphone_java.so. ملف APK الناتج من GitHub Actions المفروض يحتوي على المحرك، أما APK الإصدار 1.2 القديم فليس به محرك.

## تشغيل
1. تحتاج Android ARM64 وRAM متاحة لا تقل عن 512 MB للسيرفر فوق ذاكرة النظام، ويُفضّل مساحة عدة GB.
2. تثبيت APK الناتج من GitHub Actions (مش مجرد Source zip)؛ افتح Hostphone واختر Java أو Bedrock أو Geyser مع إصدار مناسب.
3. وافق على Minecraft EULA، ثم اضغط تثبيت؛ سيجري تنزيل JAR من PaperMC أو PowerNukkitX.
4. اضغط تشغيل. حالة Online لا تظهر إلا بعد قراءة رسالة بدء السيرفر من الـ JVM، والسجل يعرض أي خطأ فعلي.
5. من نفس شبكة الواي فاي، Java على TCP 25565، وBedrock على UDP 19132 (PowerNukkitX أو Geyser).

## قيود حقيقية
- Java: Paper بيدعم Plugins، وليس Mods بتاعة Fabric/Forge. Geyser مع Floodgate يحتاج ضبط `auth-type: floodgate` في إعدادات الجسر وقد يحتاج إعادة تشغيل بعد الإنشاء لأول مرة.
- Bedrock: PowerNukkitX هو بديل Java لسيرفر Bedrock، مش Mojang Bedrock الرسمي؛ بعض خصائص Bedrock مختلفة. 26.50 مستقر و26.60 تجريبي، والنسخ بتتغير.
- التطبيق مش بيعمل Port Forwarding تلقائي للراوتر، ولا يقدر يضمن 90% من CPU أو يبقى شغال لو Android قتله بسبب الحرارة/البطارية.
- Java Xmx يُحسب من الاختيار ومن ذاكرة الجهاز *المتاحة*، مع 512 MB احتياطي للأندرويد؛ تقدر تختار أنوية ActiveProcessorCount في واجهة الأداء.
- **اختبار تشغيل Minecraft فعليًا على موبايل لم يُنفذ حتى الآن**؛ Android runtime launcher وإصدارات JAR قد تواجه مشاكل توافق. لا يعتبر مجرد نجاح APK build دليلاً على أن اللاعبين دخلوا سيرفر.
- لو تطبيقك Android 15+ وقيود الخلفية تمنع استمرار الخدمة، جرّب إلغاء تحسين البطارية ومتابعة الإشعار.

المراجع والمصادر: Android JRE 25 مبني بواسطة AngelAuraMC/OpenJDK (راجع تراخيصها)، وPaperMC، وPowerNukkitX (LGPL-3.0)، وGeyserMC. ملفات Minecraft غير موزعة ضمن الـ APK؛ يقوم التطبيق بتنزيلها من جهات النشر رسميًا.
