# Fast Client status

## Implemented
- Fast Client launcher name and red gem adaptive icon
- Branded red gem splash, navigation, and the draggable in-game Mod Menu button
- Red theme, 59 custom navigation and utility vector icons and in-game mod status styling
- Working Android CI artifact flow for installable debug APK, provided the workflow succeeds
- Portable C++ 16³-section flood-fill portal visibility engine with tests run in CI

## Not implemented
- **True Cave Culling in Minecraft itself**: no renderer hook and therefore no gameplay benefit from the new portable core yet. UI explicitly identifies this missing integration.
- Runtime validation on an Android device.
- Complete replacement of third-party service logos and every raster game asset (these are intentionally retained for legibility and to preserve product identity).

## Next engineering dependency
Implement and validate a version-pinned Bedrock/RenderDragon native hook that filters chunk section draws using the portable visibility graph. Unknown or transparent geometry must fail open.

Build verification: the new icon pack and the C++ visibility-core test are included in Android CI. The APK remains unverified until CI finishes.

## Icon revision requested by owner
- Restored the original shapes of 60 internal vector icons from pre-redesign commit d78b4f0; no diamond-shaped background is forced onto internal navigation or action icons.
- Only black/green vector accents were converted to ruby red where applicable. White/light foregrounds remain for accessible contrast.
- The red gem remains the Fast Client brand/logo and floating menu identity.
- Cave Culling remains experimental visibility-core code with no Minecraft RenderDragon renderer hook yet.

## Black-on-red button revision

- In-game custom More Buttons (including a sword SVG when configured) now use the **original button shape**, recolored **red only in its background**, with the **original SVG silhouette drawn in black** in both released and pressed states.
- Built-in floating button symbols use the original art tinted black on a red oval button; the main launcher background stays black and its surfaces remain dark.
- The menu's separate Fast Client red ruby branding stays intact.
- This does not add or enable native Minecraft cave renderer hooks. True Cave Culling is still not integrated into RenderDragon.
- Android CI needs to succeed before a new APK is available; tests on a device are still pending.
