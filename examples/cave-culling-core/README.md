# True Cave Culling — visibility engine foundation

**Status: C++ core implemented and unit-tested; Minecraft renderer integration NOT implemented.**

This module computes the exact six-face visibility graph for each 16x16x16 section using flood fill through non-occluding blocks. It then traverses only connected section portals from the camera's local cell. Fully solid sections and disconnected caves are never traversed beyond their visible exterior faces.

## Why this isn't a complete Bedrock mod yet

Fast Client is an Android launcher wrapping an official Minecraft Bedrock runtime. The launcher does not expose a stable public API to replace Bedrock's native RenderDragon chunk draw-submission list. To create real True Cave Culling rather than a misleading toggle, a Minecraft-version-specific native renderer hook is needed. The hook must:
1. Read chunk-section block occluder data *without treating glass, water, leaves, open doors, or unknown blocks as opaque*.
2. Invalidate the visibility cache on block updates and chunk loads/unloads.
3. Snapshot camera position and section portals and run the graph traversal when needed.
4. Exclude sections outside the visible set *before* GPU draw submission; never delete blocks or interfere with world loading/networking.
5. Fail open for unknown data/renderer versions and test against holes, transparent blocks, frustum, spectator, and fast camera movement.

The in-game Mod Menu currently displays the status **Renderer hook not installed**. There is deliberately no fake enable toggle.

## Unit tests

```sh
c++ -std=c++17 -O2 -Wall -Wextra -Iexamples/cave-culling-core/include \
  examples/cave-culling-core/tests/visibility_test.cpp -o /tmp/cave_visibility_test
/tmp/cave_visibility_test
```

Examples include solid walls, fully empty sections, tunnels, disconnected cave components, block updates, chunk removal, and camera-cell fail-open behavior.
