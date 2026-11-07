# PhoneCraft — Android Minecraft Java + Bedrock Host

**Your Android phone runs the actual Minecraft server.** GitHub only stores the source. No PC, paid hosting, root access or VPS is required for LAN play.

> Prototype / best-effort, created 2026-11-08. Tested for Python syntax, HTTP panel and local logic; a real Minecraft join test on an Android device has **not** been performed. You download Minecraft server JARs from their official publishers on your phone. No Minecraft binaries are bundled in this repository.

## Features

- **Phone is the host**: OpenJDK + Minecraft Java server launched locally in Termux.
- **One shared world for Java and Bedrock**: Paper + Geyser + Floodgate; Bedrock joins the Java server through Geyser (it is not a separate Mojang Bedrock Dedicated Server binary).
- **Server type**: **Paper** (plugins, `.jar`) or **Fabric** (server-side mods, `.jar`), each with its own separate world folder.
- Automatically install **Paper 26.3** from the official PaperMC v3 Fill stable-build API, or Fabric 26.3 using official Fabric Meta API, then Geyser and Floodgate (and Fabric API for Fabric).
- Web dashboard: install, start/stop, console, OP/de-OP, editable world settings, plugins/mods, RAM settings, approximate core count, and offline backups.
- Binds dashboard **only** to `127.0.0.1:8765` and requires a random per-launch token for each mutation.
- **No root** required. Written in Python standard library; Java 25 provided by Termux.

## العربي

[خطوات التشغيل بالعربي](README_AR.md).

## Direct install from the GitHub feature branch

```bash
pkg update -y && pkg install -y git
git clone --depth 1 -b phonecraft-android-host https://github.com/Raco8493/Fast-Client.git PhoneCraft
cd PhoneCraft
bash install-termux.sh
~/phonecraft-start
```

Open http://127.0.0.1:8765 on the same phone. The `main` branch of Fast-Client is untouched; this project lives in the separate `phonecraft-android-host` branch.

## Install on Android (no PC needed)

1. Install **Termux** from [F-Droid](https://f-droid.org/packages/com.termux/) or the official [Termux GitHub releases](https://github.com/termux/termux-app/releases). Avoid mixing installation sources for Termux add-ons.
2. Download the project ZIP onto your phone (usually `Downloads`). Open Termux:

```bash
pkg update -y && pkg install -y unzip
termux-setup-storage
cd ~
unzip -o ~/storage/downloads/PhoneCraft-Android-Host.zip
cd PhoneCraft-Android-Host
bash install-termux.sh
~/phonecraft-start
```

3. On the same phone, open **http://127.0.0.1:8765** in Chrome. Choose **Paper** or **Fabric**, accept the Minecraft EULA explicitly, and tap **Install**.
4. Tap **Start**. On the first launch, the server creates Geyser config; tap **Stop** and **Start** one extra time to apply the Geyser **Floodgate/NetherNet** config automatically.
5. Keep Termux alive. Disable Android battery optimization for Termux in system Settings; Android may kill even a wake-locked process when resources are scarce. Long sessions on a phone need cooling and power management.

**Optional**: Install Termux:Widget to launch a Termux shortcut from your home screen:

```bash
mkdir -p ~/.shortcuts
cp widget/PhoneCraft.sh ~/.shortcuts/PhoneCraft.sh
chmod +x ~/.shortcuts/PhoneCraft.sh
```

**Optional**: With Termux:Boot installed, set up autostart (Android can still block boot background launches):

```bash
mkdir -p ~/.termux/boot
cp widget/termux-boot-PhoneCraft.sh ~/.termux/boot/PhoneCraft.sh
chmod +x ~/.termux/boot/PhoneCraft.sh
```

## Connect players

When everyone is on the **same Wi-Fi**, find the host phone's Wi-Fi IPv4 address in Android Settings:

- **Java:** `PHONE_IP:25565` (TCP)
- **Bedrock:** `PHONE_IP:19132` (Geyser, **TCP and UDP** for new NetherNet-compatible Bedrock versions)

**Not** `127.0.0.1` from a *different* phone; localhost is only for a player on the host phone itself.

For players across the internet, mobile networks commonly use **CGNAT**. Either forward Java `25565/TCP` and Bedrock `19132/TCP+UDP` at the home router (when possible), or use an appropriately configured tunnel with support for both TCP and UDP, such as the methods [Geyser documents](https://geysermc.org/wiki/geyser/setup/). This project does not configure networking or a tunnel automatically.

Some Bedrock platforms such as game consoles have extra limitations on adding custom servers.

## How resources really work

- The `RAM %` slider controls the **maximum Java heap** `-Xmx`, bounded by `RAM Total * selected %`, *currently available memory minus headroom*, and *total memory minus OS reserve*. Native memory (JVM, Minecraft networking, plugins) is additional; the host can still be killed by Android.
- The `CPU %` slider determines the number of **processor cores visible to the JVM** using `-XX:ActiveProcessorCount`. **This does not force exactly 90% CPU usage or make the phone faster**. Android throttling, heat, architecture and other processes determine real CPU performance.
- **85% RAM** is a risky max on Android. A good starting point is 50–65% with view distance 4–6, then increase gradually if the phone remains stable.
- Battery, heat, foreground restrictions and network connection can still interrupt the server even if the phone is powered on.

## Mods and plugins

**Paper mode:** Add trusted compatible `.jar` plugins from the dashboard. Geyser-Spigot and Floodgate-Spigot are preinstalled. Paper plugins and Fabric mods are *different* formats, not interchangeable.

**Fabric mode:** Add trusted compatible `.jar` server-side mods. Fabric Loader + Fabric API + Geyser-Fabric + Floodgate-Fabric are installed on demand. **Bedrock clients generally cannot use Java mods requiring a matching client mod or custom client-side content.** Only install server-side compatible mods for mixed Java/Bedrock. You may have to adjust compatibility and version-specific mod dependencies yourself.

To give Java username `Steve` OP, enter `Steve` in the UI. For Floodgate Bedrock players, the in-game Java-side name often uses a dot prefix, e.g. `.Alex`, with spaces replaced by underscores. OP grants full server privileges, so use it only for trusted people.

## World backups and location

```
~/.phonecraft/
    config.json
    servers/
        paper/       # Paper world, plugins, jars
        fabric/      # Fabric world, mods, jars
    backups/         # ZIP world backups
```

Stop the server before backing up to prevent inconsistent world saves. You can copy the resulting ZIP out of Termux using standard Android storage commands after granting permissions.

**Security:** Never upload unknown mods/plugins: a `.jar` runs executable code with your Termux app privileges. The control panel is localhost-only; do not port-forward port `8765`. Leave `online-mode=true`; this is important even when Floodgate is installed. Don't upload Floodgate private keys (`key.pem`) to GitHub or share them.

## Development

Requires Python 3.10+ for the controller; target platform is Android + Termux + Java 25.

```bash
python -m unittest discover -s tests -v
python phonecraft.py
```

The browser dashboard is served from `dashboard.html`. Backend is `phonecraft.py`. Build distributions are assembled using `.github/workflows/build-zip.yml` GitHub Actions (ZIP only, *not* a standalone APK).

## Official sources

- [Termux](https://termux.dev/)
- [PaperMC downloads service](https://docs.papermc.io/misc/downloads-service/)
- [PaperMC Java requirement](https://docs.papermc.io/paper/getting-started/)
- [Geyser setup](https://geysermc.org/wiki/geyser/setup/)
- [Floodgate setup](https://geysermc.org/wiki/floodgate/setup/)
- [Fabric Server](https://fabricmc.net/use/?page=server)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Floodgate Fabric](https://modrinth.com/mod/floodgate)

No official affiliation with Mojang/Microsoft, PaperMC, FabricMC or GeyserMC. Respect those projects' licences, server terms and third-party mods' licences.
