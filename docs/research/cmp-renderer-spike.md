# CMP Renderer Spike

## Goal

Determine whether Preview For Agents can support Compose Multiplatform previews declared in `commonMain` with AndroidX `@Preview` by reusing the Android renderer backend.

## Requirements To Prove

- A Compose Multiplatform project can declare a preview in `commonMain` using `androidx.compose.ui.tooling.preview.Preview`.
- The Android target compiles that common preview without old JetBrains preview annotations.
- ComposablePreviewScanner can discover the preview from Android target outputs.
- Roborazzi can render the preview through the Android target on Java 17 when Robolectric runtime SDK is forced to 35.
- Compose UI test APIs can inspect basic semantics for the common preview content.

## Version Matrix

| Dependency | Version | Source |
| --- | --- | --- |
| Android Gradle Plugin | | |
| Kotlin | | |
| Compose Multiplatform | | |
| Compose BOM / AndroidX Compose UI | | |
| Roborazzi | | |
| ComposablePreviewScanner | | |
| Robolectric | | |

## Discovery Findings

Record command, result, scanner API, discovered metadata, and limitations.

## Rendering Findings

Record command, result, screenshot path, rendering API, Java/Robolectric SDK mode, and limitations.

## Semantics Findings

Record command, result, semantics API, observed fields, and limitations.

## Decision

Pending.
