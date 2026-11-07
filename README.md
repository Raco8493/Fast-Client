# Hostphone — on-device Minecraft hosting for Android

Hostphone (v1.1 beta) is a **portrait-oriented Android app**, with a red interface, a centered Create Server button, server setup wizard, and circular bottom navigation. Choose the Minecraft Java version and **Paper** (plugins) or **Fabric** (server-side mods). The server process runs on the phone via Termux/OpenJDK; Geyser + Floodgate let Bedrock clients connect to the Java world. It does **not** use a VPS or run game processes on GitHub.

**Forge and Folia are visibly marked unsupported** (not fake menu items). An actual standalone Bedrock Dedicated Server binary is not part of this project. For Minecraft Java version availability and Java runtime compatibility, refer to the official publishers.

## Install

1. Download the latest **Hostphone-APK** from [GitHub Actions](https://github.com/Raco8493/Fast-Client/actions/workflows/build-apk.yml?query=branch%3Aphonecraft-android-host), extract `Hostphone-debug.apk`, and install on Android.
2. Open Hostphone -> Create Server. Enter a name and select edition, version, and server software.
3. First run needs [Termux from F-Droid](https://f-droid.org/packages/com.termux/), opened once. Follow the app's copy/setup/permission/engine installation buttons.
4. Start the local Termux host engine. Return to the app, agree to [Minecraft EULA](https://aka.ms/MinecraftEULA) and install the chosen Paper/Fabric server; then start it.
5. Access **Console**, **OP**, **JAR plugins/mods**, **backup** and **RAM/CPU settings** from the red management dashboard.

LAN: Java TCP `25565`, Bedrock UDP `19132`. Remote connections require your own network setup or a TCP+UDP supporting tunnel. Android battery management/thermal throttling may stop the host even if the screen is on.

Android sources: [`android/`](android/). Python engine: [`phonecraft.py`](phonecraft.py). Run local engine tests with `python -m unittest discover -s tests -v`.

**Prototype status:** Python tests and JavaScript syntax can be checked offline. Android builds via GitHub Actions; physical-device join and compatibility tests are still outstanding. `85% RAM` applies to Java's maximum heap cap and respects available memory. `90% CPU` is an ActiveProcessorCount hint, not a forced CPU load.
