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
| Android Gradle Plugin | 8.13.0 | Maven metadata checked on 2026-05-20; selected because AGP 9.2.1 conflicts with Kotlin Multiplatform plugin in this spike |
| Kotlin | 2.3.21 | Maven metadata checked on 2026-05-20 |
| Compose Multiplatform | 1.11.0 | Maven metadata checked on 2026-05-20; plugin used with AndroidX runtime/foundation in commonMain |
| Compose BOM / AndroidX Compose UI | BOM 2026.05.01 / UI 1.11.2 | Maven metadata checked on 2026-05-20 |
| Roborazzi | 1.63.0 | Maven metadata checked on 2026-05-20 |
| ComposablePreviewScanner | 0.9.0 | Maven metadata checked on 2026-05-20 |
| Robolectric | 4.16.1 | Maven metadata checked on 2026-05-20 |

## Discovery Findings

Record command, result, scanner API, discovered metadata, and limitations.

## Rendering Findings

Record command, result, screenshot path, rendering API, Java/Robolectric SDK mode, and limitations.

## Semantics Findings

Record command, result, semantics API, observed fields, and limitations.

## Decision

Pending.
