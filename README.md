# Ominous Vault Track — Minecraft 26.2 port

This is a Minecraft 26.2 Fabric port of [Damomo11/ominous-vault-track].
The upstream project is MIT licensed.

## Features

- Highlights ominous vaults only; normal vaults are ignored.
- Renders the outline through walls.
- Renders a through-wall tracer from the camera to each matching ominous vault.
- Right-click an ominous vault to locally exclude it from rendering.
- Stores excluded vaults separately by server and dimension.
- Configurable highlight/tracer colors, render range, tracer item requirement, and refresh behavior.
- Configurable single-key shortcut for opening the config screen (default: `B`).
- Cloth Config settings screen with optional Mod Menu integration.

## 26.2 requirements

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.3+
- Fabric API 0.158.0+26.2
- Cloth Config 26.2.155+
- Mod Menu 20.0.1+ (optional)

## Important 26.2 port changes

Minecraft 26.2 replaced the old direct feature vertex upload path with submit nodes.
This port therefore:

1. Registers rendering on `LevelRenderEvents.COLLECT_SUBMITS` instead of drawing directly at `END_MAIN`.
2. Uses `SubmitNodeCollector.submitShapeOutline` for vault boxes.
3. Uses `SubmitNodeCollector.submitCustomGeometry` for tracer lines.
4. Rebuilds the see-through line pipeline from `RenderPipelines.LINES_SNIPPET` instead of manually constructing the old 26.1 `RenderPipeline.Snippet`.
5. Removes the old access widener because `RenderType.create(String, RenderSetup)` is public in 26.2.

## Building

This source tree intentionally does not include the binary `gradle-wrapper.jar` copied from upstream.
Either copy `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, and `gradlew.bat` from the upstream repository unchanged, or use a local Gradle 9.5.1 installation.

With Java 25 active:

```text
gradle build
```

The remapped mod jar will be written under `build/libs/`.

## Verification status

The 26.2 API migration has been checked against the current Fabric/Minecraft 26.2 interfaces used by this mod. This package has **not been locally compiled in the generation environment**, because that environment has JDK 21 while Minecraft 26.2 requires Java 25. Build it with JDK 25 before installing the resulting JAR.
