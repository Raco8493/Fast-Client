# Windows 10 QEMU Virtual Machine for Android (real OS)

**This is not a visual Windows desktop imitation.** It is a native Android controller + integrated basic VNC viewer for a **real x86_64 virtual machine** booted by open-source **QEMU** in **Termux**. You provide a legitimate Windows 10 ISO (not included for licensing/size reasons). The Windows operating system then installs on a virtual disk. Windows sees a virtual Intel E1000 network card connected through QEMU user-mode networking (NAT); its Internet connection normally works when Android's connection works.

## What works

- PC emulation by QEMU TCG (the Android ARM CPU **emulates** an x86_64 CPU; no root needed)
- Real Windows installer from ISO, persistent 32/48/64 GB sparse QCOW2 disk
- Virtualized RAM 2/3/4 GB and 1/2/4 CPU cores (subject to host RAM)
- Windows Ethernet adapter with Internet via QEMU NAT (`-netdev user` + `e1000`)
- On-device VNC viewer, basic mouse/touch and keyboard input; local-only TCP port 5901
- Launch, stop, install, ISO import from an Android file picker

## What does not work / performance limits

**No performance or app compatibility guarantee.** Software x86 emulation on ARM phones/tablets is **very slow**, often unsuitable for heavy IDEs. **Visual Studio, Android Studio and Roblox Studio are not known to work reliably here**: RAM needs, Windows x64 translation, graphics drivers and 3D GPU support are major obstacles. The virtual display uses standard VGA, **without modern DirectX/3D acceleration**. For those programs, use Remote Desktop to a real Windows PC/cloud VM (or code directly on Android/Termux). Windows 10 reached end of normal support on **14 Oct 2025**; avoid using an unsupported system for sensitive browsing/accounts.

This Android APK is **not self-contained**: it uses official Termux and the Termux QEMU package. There is no Windows binary, ISO or pre-installed Windows copy inside the APK.

## Installation on Android 10+ (ARM64 recommended)

1. Install [Termux (F-Droid)](https://f-droid.org/packages/com.termux/) and open it once. Do **not** mix incompatible Termux app installations from different stores.
2. Install this project's **debug APK** from the [Actions build](../../actions/workflows/build-real-qemu-windows-apk.yml), or build with Android Studio and Java 17.
3. Tap **Copy one-time Termux setup command** in the APK, return to Termux and paste/run it:

   ```sh
   mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings && termux-setup-storage
   ```

   Grant Android storage access to Termux. This enables the Termux service for the controller. **WARNING: Termux RUN_COMMAND grants this APK permission to execute commands within Termux**; only install trusted APKs and grant it intentionally.
4. In Android settings → Apps → **Windows 10 QEMU VM** → Permissions → Additional permissions, allow **Run commands in Termux environment** when prompted.
5. Tap **Install QEMU engine** inside the app. Termux runs `pkg update` and installs `qemu-system-x86-64-headless` and `qemu-utils`; internet is needed for this first setup.
6. Download a legitimate **Windows 10 x64 ISO** from [Microsoft installation media guidance](https://support.microsoft.com/en-us/windows/deployment/install-upgrade/create-installation-media-for-windows), and tap **Import genuine Windows 10 ISO**. Choose the ISO in Android's file picker. This app makes a byte-for-byte copy in `Download/Win10-VM/Windows10.iso` using Android MediaStore. **Have several GB of available internal storage for the installer and virtual disk**.
7. Select VM RAM, CPU cores and sparse disk capacity. Tap **Start real Windows VM**, watch the Termux output (it opens a session), then switch back to this app and tap **Open VM screen (VNC)**. It auto-retries connecting to `127.0.0.1:5901` while QEMU starts.
8. Follow the actual Microsoft Windows setup in the VNC screen and install Windows onto the empty virtual disk. Subsequent starts preserve the disk. The ISO stays attached to the VM; when you see “Press any key to boot from CD or DVD” after installation, **do not press anything** so it boots the installed system.

If Termux cannot read `~/storage/downloads/Win10-VM/Windows10.iso`, use a file manager to move/copy the ISO into Termux's **private** `$HOME/win10-qemu/windows10.iso` (using a supported transfer mechanism). This private path takes precedence.

### Network

QEMU runs with `-netdev user,id=nat0 -device e1000,netdev=nat0`, giving the guest outbound NAT and DNS through the Android host. No internet port is opened to other devices. VNC listens only on `127.0.0.1:5901`, so external machines cannot connect to it. Guest networking requires Windows E1000 support; drivers and network configuration may still be needed.

### VM disk / file locations

- Windows disk: `$HOME/win10-qemu/windows10.qcow2` in Termux private storage
- Optional private ISO: `$HOME/win10-qemu/windows10.iso`
- Shared Android ISO: `$HOME/storage/downloads/Win10-VM/Windows10.iso` (after `termux-setup-storage`)
- VM process ID: `$HOME/win10-qemu/qemu.pid`

Shutting down Windows via Start → Power → Shut down is safest. **Stop VM** in the app sends TERM to QEMU and is an emergency power-off that might corrupt unsaved files.

## Build APK (GitHub Actions)

The GitHub workflow `build-real-qemu-windows-apk.yml` checks out this branch and builds the Android debug APK using Java 17, Android SDK 35, Android Gradle Plugin 8.7.3 and Gradle 8.10.2. Open **Actions** → **Build Real Windows QEMU Android APK** → latest successful run → **Artifacts** → `Windows10-QEMU-Android-debug-APK`.

To build locally, open the `Windows10-QEMU-Android` directory in Android Studio and sync Gradle, or use a local Gradle 8.10.2 installation with `gradle assembleDebug`.

## Credit and licenses

QEMU is copyright its contributors, GPL-licensed. It is installed separately via official Termux repositories; this repo **does not embed QEMU or Windows**. Termux RUN_COMMAND integration follows [upstream official docs](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent). This repository's original launcher and VNC code is provided under the MIT License (see LICENSE).
