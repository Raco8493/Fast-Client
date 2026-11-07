# Android controller + native RFB viewer

The Android APK controls a separate genuine QEMU x86_64 VM started inside official Termux.

- **Not a fake Windows skin:** canvas shows pixels streamed from QEMU's actual virtual VGA adapter.
- **In-app VNC/RFB 3.8 viewer:** no separate VNC client needed; full-colour RAW framebuffer, basic touchscreen mouse, keyboard input and Ctrl+Alt+Delete.
- **Termux integration:** commands sent to official `RunCommandService`, gated by Termux permission and `allow-external-apps=true` opt-in.
- **No bundled Windows:** the user imports a genuine ISO and provides a Windows license as required.
- **Not a standalone QEMU APK:** Termux and its QEMU packages are a runtime dependency.

Built with Java 17, Android Gradle Plugin 8.7.3, compile SDK 35. Includes no ads, trackers or third-party runtime libraries.

Potential limitations: raw VNC can be bandwidth/CPU intensive even on localhost; RFB supports one standard pixel format and raw encoding; no clipboard synchronization or multi-touch gestures. Unexpected Android/Termux permission or process restrictions can cause startup failures. Android 12+ may kill CPU intensive Termux processes.
