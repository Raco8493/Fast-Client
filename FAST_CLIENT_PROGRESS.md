# Fast Client — implementation status

This fork customizes LeviLaunchroid for Android (ARM64).

## Implemented in this commit

- Fast Client's existing in-game movable mod-menu circle now uses a small red-gem vector drawable.
- The existing in-game Mod Menu retains its working click/drag/menu logic and is styled with red accents and Fast Client branding.
- Android adaptive icon foreground uses the red gem vector, so it no longer requires replacing every density-specific PNG manually.
- Android CI now attempts a real debug APK build even when the Firebase secret is not configured, and uploads the result as `FastClient-debug-apk` if the build succeeds.
- Debug build disables Crashlytics symbol/mapping upload.

## Not yet implemented or verified

- True Cave Culling is NOT hooked into Minecraft's native renderer. No menu item falsely claims it is working.
- GitHub Actions and physical Android device installation/runtime tests must succeed before this build can be called tested.
- If Firebase is absent, CI uses a placeholder configuration. This is a development build, not a production release; Firebase-dependent features may be unavailable.
- Minecraft Bedrock must be a compatible, legally owned Google Play installation.

## Where the real game overlay already lives

- Floating draggable button: `app/src/main/java/org/levimc/launcher/core/mods/inbuilt/overlay/ModMenuButton.java`
- In-game menu: `ModMenuOverlay.java` and `app/src/main/res/layout/overlay_mod_menu.xml`
- Native rendering optimization must be a separate tested integration compatible with the current Minecraft binary; a launcher UI toggle alone cannot suppress cave drawing.

## Testing

Open the **Actions** tab, inspect the Android CI run, and download `FastClient-debug-apk` only if a successful build has produced it.
