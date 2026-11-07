# Real Windows on Android — Windows VM powered by QEMU

**This project boots a REAL Windows installer/installation on Android.** It is not a Windows 10 theme, browser mock-up, launcher, or EXE compatibility layer. Termux runs QEMU `qemu-system-x86_64` in software emulation (TCG). A separate Android app lets you start/stop the guest and view its framebuffer through the VNC (RFB 3.8) protocol. QEMU guest NAT lets Windows browse the internet using the Android device's connection.

> Reality check: Modern phones/tablets have ARM CPUs. Running x64 Windows via QEMU full-system emulation is **very slow**. Visual Studio is a heavyweight application and may install/run very poorly, or fail from resource constraints. The repository is source code with an untested-on-physical-device emulator stack, not a guaranteed working Studio workstation. For productive Visual Studio development, use a real Windows PC / cloud Windows VM accessed by Remote Desktop. Windows 10 support ended in October 2025; for Visual Studio 2026 use supported Windows 11, which requires a more advanced TPM/UEFI VM configuration than this Windows 10-focused starter provides.

## What is in this repository

- `termux/install.sh`: installs QEMU and QEMU image tools in **official Termux**.
- `termux/winvm.sh`: creates a virtual disk, imports ISO, starts Windows installer / installed guest, provides power control, logs, network, and localhost VNC.
- `android/`: complete Java Android APK project with native VNC viewer and Termux `RUN_COMMAND` integration.
- `.github/workflows/build-real-winvm-apk.yml` at the GitHub repository root builds APK (if enabled/successful).

## Requirements

- A 64-bit (arm64/aarch64 or x86_64) Android phone/tablet, preferably **8–12 GB RAM** or more and plenty of available storage (32–80 GB or more depending on Windows and developer tools).
- Official Termux from [F-Droid](https://f-droid.org/packages/com.termux/) or [official GitHub releases](https://github.com/termux/termux-app/releases). Do not mix Termux and Termux plugins from different signing sources.
- A legitimate **Windows 10 x64 installation ISO** obtained separately, and license/activation if required. Microsoft copyrighted operating system binaries are not redistributed here.
- Wi-Fi or cellular internet if you want to browse inside the Windows guest.

## Android setup (first time)

1. Install Termux and open it.
2. Download `RealWindowsAndroid-source.zip` from this repository and place it in the Android Downloads folder.
3. In Termux type:

   ```sh
   pkg update -y && pkg install -y unzip
   termux-setup-storage
   unzip ~/storage/downloads/RealWindowsAndroid-source.zip -d ~
   bash ~/RealWindowsAndroid/termux/install.sh
   ~/winvm/winvm.sh init 64
   ```

4. Copy your licensed Windows installation ISO into Android Downloads and **rename it** to `Windows.iso` if necessary. Then:

   ```sh
   ~/winvm/winvm.sh import-iso ~/storage/downloads/Windows.iso
   ~/winvm/winvm.sh start --install
   ```

5. Install Windows using the real Microsoft installation wizard visible at **VNC `127.0.0.1:5900`**. Use the included Android app's **OPEN LIVE WINDOWS SCREEN** button, or a third-party VNC client. Disk is `~/winvm/windows.qcow2` and is initially sparse.
6. After Windows has finished installing, use the guest's Shut down control. Then boot without the installer ISO:

   ```sh
   ~/winvm/winvm.sh start
   ```

Inside Windows, QEMU offers a standard emulated Intel e1000 Ethernet adapter and user-mode NAT. Most ordinary browsing should work if Windows has a compatible driver and the phone is online. VNC stays bound to `127.0.0.1` for security, rather than exposing an unauthenticated port to your Wi-Fi network.

## Optional Android APK controller

- Build the Android project in Android Studio, or obtain the debug APK from a successful GitHub Actions run.
- In Termux, open `~/.termux/termux.properties` and add the line `allow-external-apps=true`, then restart Termux. This is a **security-sensitive setting** allowing apps with the special permission to send Termux commands.
- Grant the Android controller **Run commands in Termux environment** permission via Android Settings → Apps → Real Windows VM → Permissions → Additional permissions (exact menus differ by Android skin).
- Open controller and press **Start installed Windows** or **Boot Windows installer (ISO)**, then **OPEN LIVE WINDOWS SCREEN**.
- Do not expose this VM's VNC server to public addresses; VNC is intentionally unauthenticated because it is localhost-only.

The APK does not contain QEMU binaries, a Windows image, or a Windows license; it requires the Termux backend. If the Android controller fails, the shell `~/winvm/winvm.sh ...` commands remain usable.

## Terminal commands

```sh
~/winvm/winvm.sh status
~/winvm/winvm.sh logs
~/winvm/winvm.sh stop        # graceful ACPI shutdown
~/winvm/winvm.sh force-stop  # emergency kill / may corrupt data
```

To set guest RAM (within 1024–8192 MiB) when running via Termux CLI: `WINVM_RAM_MB=3072 ~/winvm/winvm.sh start`. Never allocate most of your device's RAM to the guest.

## Limitations

- Full CPU emulation **without KVM hardware acceleration** is slow. Some Windows versions may not boot; Visual Studio, Android Studio, modern games, drivers, or GPU-dependent applications may be impractical.
- Windows 11 normally requires UEFI/Secure Boot/TPM support not implemented by this starter. This setup targets legacy BIOS-booting Windows 10 x64 installers.
- QEMU `user` networking supports guest-initiated outbound internet but not all inbound services, VPNs, or unusual protocols. This isn't a way to get internet without a network connection.
- Some Android versions kill background processes under memory/CPU pressure (especially Android 12+). Disable battery optimization for Termux if needed; no root is needed for the basic design.
- The native RFB viewer handles RAW rectangles, mouse, keyboard and Ctrl+Alt+Delete; it is intentionally minimal.

## Sources / reference

- [Termux official app](https://github.com/termux/termux-app)
- [Termux QEMU headless package](https://github.com/termux/termux-packages/blob/master/packages/qemu-system-x86-64-headless/build.sh)
- [Termux RUN_COMMAND Intent guide](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
- [QEMU documentation](https://www.qemu.org/docs/master/)
- [Microsoft Visual Studio 2026 requirements](https://learn.microsoft.com/en-us/visualstudio/releases/2026/vs-system-requirements)

**Disclaimer:** Unofficial research/prototype project. It is neither endorsed by nor affiliated with Microsoft, QEMU, or Termux. Windows and Visual Studio trademarks belong to Microsoft.
