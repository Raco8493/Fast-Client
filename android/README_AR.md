# PhoneCraft APK Beta

ده APK لواجهة إدارة سيرفر Minecraft على الموبايل. **محتاج Termux** اللي بيشغل محرك Java وPython على نفس الهاتف.

## تحميل الملف

افتح https://github.com/Raco8493/Fast-Client/actions/workflows/build-apk.yml?query=branch%3Aphonecraft-android-host

اختار آخر تشغيل ناجح (علامة خضراء)، ثم تحت **Artifacts** نزل **PhoneCraft-Host-APK**، فك ملف ZIP وثبّت **PhoneCraft-Host-debug.apk** على Android.

## أول تشغيل

1. نزّل Termux من https://f-droid.org/packages/com.termux/ وافتحه مرة.
2. من PhoneCraft اضغط نسخ أمر الربط، افتح Termux، الصق الأمر واضغط Enter. ده يفعل allow-external-apps=true.
3. من PhoneCraft اطلب إذن Run commands in Termux environment. لو مش ظاهر: إعدادات التطبيقات > PhoneCraft > Permissions > Additional permissions.
4. اضغط تثبيت المحرك. تابع شاشة Termux لحد انتهاء التنزيل والتثبيت.
5. اضغط تشغيل المحرك وارجع من Termux إلى PhoneCraft، ثم افتح لوحة التحكم.
6. اختار Paper للـPlugins أو Fabric للمودات، وافق بنفسك على EULA، واضغط تثبيت Minecraft server، وبعدها Start.

**تنبيه:** مفيش APK مستقل فيه Java server جاهز بدون Termux؛ الموبايل نفسه هو المستضيف. تعطيل Battery optimization مهم لكنه لا يضمن استمرار السيرفر في الخلفية. النسخة تجريبية، ولسه محتاجة اختبار على أندرويد فعلي.

## Dev build
Gradle 8.10.2 + AGP 8.8.2 + JDK 17 + Android SDK 35:
`cd android && gradle :app:assembleDebug`
