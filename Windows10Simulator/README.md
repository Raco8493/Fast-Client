# Windows 10 Simulator for Android
مشروع أندرويد بمحاكاة تفاعلية لواجهة Windows 10، مش ويندوز حقيقي، ومش بيشغل EXE.

**Source code:** [Download Android Studio project ZIP](Win10-Android-Simulator-source.zip)

**APK:** Open the repository [Actions tab](../../actions/workflows/build-win10-android.yml) and download the "Windows10-Android-Simulator-debug-APK" artifact from a successful run (you may need to sign into GitHub).

This simulator includes Start Menu, desktop and taskbar, movable windows, File Explorer with virtual locally stored files, Notepad, Calculator, Paint, Settings, and browser links.

On a computer, unzip the source and open the `Win10-Android-Simulator` folder in Android Studio. Requires Java 17, Gradle 8.11.1, Android SDK 35. An APK is only available after the GitHub Actions build succeeds.

This is an unofficial, educational Windows-inspired UI emulator; no operating-system virtualization or EXE compatibility.
