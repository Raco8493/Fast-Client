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
