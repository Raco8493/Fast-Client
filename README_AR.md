# PhoneCraft — خلي موبايل أندرويد يستضيف Minecraft Java + Bedrock

دي نسخة أولية **قابلة للتشغيل عن طريق Termux**. الموبايل نفسه بيشغّل Java server، مش كمبيوتر ولا GitHub. GitHub وظيفته حفظ المشروع والتحديثات فقط.

## التشغيل مباشرة من GitHub (أسهل طريقة)

نزّل Termux من F-Droid، وبعدها في Termux اكتب:

```bash
pkg update -y && pkg install -y git
git clone --depth 1 -b phonecraft-android-host https://github.com/Raco8493/Fast-Client.git PhoneCraft
cd PhoneCraft
bash install-termux.sh
~/phonecraft-start
```

افتح المتصفح على **http://127.0.0.1:8765**.

## التثبيت من الموبايل

1. نزّل [Termux من F-Droid](https://f-droid.org/packages/com.termux/).
2. نزّل الملف المضغوط `PhoneCraft-Android-Host.zip` في مجلد Downloads.
3. افتح Termux وانسخ الأوامر بالترتيب:

```bash
pkg update -y && pkg install -y unzip
termux-setup-storage
cd ~
unzip -o ~/storage/downloads/PhoneCraft-Android-Host.zip
cd PhoneCraft-Android-Host
bash install-termux.sh
~/phonecraft-start
```

4. افتح متصفح الموبايل على **http://127.0.0.1:8765**.
5. اختار **Paper** للبلاجِنز أو **Fabric** للمودات، وافق على Minecraft EULA، واضغط «تثبيت».
6. اضغط «تشغيل». بعد أول تشغيل اضغط إيقاف وتشغيل مرة تانية علشان إعدادات Geyser تتظبط.

## الدخول من جهاز تاني على نفس الواي فاي

- **Java**: عنوان IP الخاص بموبايل السيرفر مع بورت `25565` (TCP).
- **Bedrock**: عنوان IP الخاص بموبايل السيرفر مع بورت `19132` (TCP وUDP في إصدار Geyser الجديد).

لو صاحبك خارج البيت أو على شبكة تانية، ممكن تحتاج Port Forwarding أو Tunnel يدعم TCP وUDP. مش كفاية إن الموبايل متصل بالإنترنت، لأن شبكات المحمول بتستخدم CGNAT كتير.

## الإمكانيات

- تشغيل وإيقاف من لوحة تحكم على الموبايل.
- Console Commands، وOP وDe-OP.
- رفع `.jar` Plugins لو Paper أو Mods لو Fabric.
- Geyser + Floodgate عشان Bedrock وJava يلعبوا على نفس العالم.
- اختيار RAM حتى 85% كحد أقصى لهيب Java **مع ترك احتياطي للرام حسب المتاح**.
- اختيار نسبة الأنوية الظاهرة لـJava. الإعداد ده **مش معناه** إن المعالج هيستخدم 90% ثابت، ولا ممكن نضمن ثبات الأداء ضد حرارة الموبايل.
- Backup للعالم بعد إيقاف السيرفر.

## ملاحظات مهمة

- لازم **Termux يفضل شغال**، وتلغي تحسين البطارية ليه. أندرويد ممكن يوقف أي سيرفر تحت ضغط أو حرارة حتى لو الموبايل مفتوح.
- لا ترفع `.jar` من مصدر غير موثوق.
- بعض مودات Fabric محتاجة نفس المود عند لاعب Java؛ دي مش هتشتغل تلقائيًا مع لاعب Bedrock.
- المشروع مش APK مستقل، لكنه برنامج يعمل على Android مع لوحة تحكم ممكن تختصرها على الشاشة الرئيسية. تنزيل ملفات السيرفر بيتم أول تشغيل من المصادر الرسمية.
- النسخة دي اختبرنا كود الباك إند واختبارات محلية، لكن لسه محتاجة اختبار دخول فعلي من Java وBedrock على موبايل أندرويد.

للتفاصيل والمصادر والتطوير شوف [README.md](README.md).
