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
| Compose Multiplatform | 1.11.0 | Maven metadata checked on 2026-05-20 |
| Compose Multiplatform Material3 | 1.11.0-alpha07 | JetBrains Compose 1.11.0 dependency table checked on 2026-05-20 |
| Compose BOM / AndroidX Compose UI | BOM 2026.05.01 / UI 1.11.2 | Maven metadata checked on 2026-05-20 |
| Roborazzi | 1.63.0 | Maven metadata checked on 2026-05-20 |
| ComposablePreviewScanner | 0.9.0 | Maven metadata checked on 2026-05-20 |
| Robolectric | 4.16.1 | Maven metadata checked on 2026-05-20 |

## Discovery Findings

- Command: `ANDROID_HOME=$HOME/Library/Android/sdk build-brief spikes/renderer-cmp-compose/gradlew -p spikes/renderer-cmp-compose :composeApp:testDebugUnitTest --tests dev.staticvar.agentpreview.cmp.CmpPreviewDiscoverySpikeTest`
- Result: PASS, 1 test passed on Java 17.
- Scanner API used: `AndroidComposablePreviewScanner().scanPackageTrees("dev.staticvar.agentpreview.cmp").getPreviews()`.
- Discovered metadata: declaring class `dev.staticvar.agentpreview.cmp.ProfilePreviewKt`, method `ProfilePreview`, preview name `Profile`, group `Account`, width `393`, height `852`.
- Notes: The `commonMain` preview compiled into the Android target and was discoverable without old JetBrains preview annotations or Google's `@PreviewTest`.

## Rendering Findings

- Command: `ANDROID_HOME=$HOME/Library/Android/sdk build-brief spikes/renderer-cmp-compose/gradlew -p spikes/renderer-cmp-compose :composeApp:recordRoborazziDebug --tests dev.staticvar.agentpreview.cmp.CmpPreviewRenderingSpikeTest`
- Result: PASS, 2 tests passed on Java 17 with Robolectric runtime SDK 35 after switching the common preview to JetBrains Compose Multiplatform Material3 `Text`.
- Screenshot path: `spikes/renderer-cmp-compose/composeApp/build/outputs/renderer-cmp-spike/ProfilePreview.png`.
- Rendering API used: `AndroidComposablePreviewScanner().scanPackageTrees(...).getPreviews().single().captureRoboImage(screenshot.absolutePath)`.
- Notes: Rendering works through the Android target when `testOptions.unitTests.isIncludeAndroidResources = true` is enabled and `androidx.activity.ComponentActivity` is declared in the Android manifest.

## Semantics Findings

- Command: same `CmpPreviewRenderingSpikeTest` command above.
- Result: PASS.
- Semantics API used: `createComposeRule`, `onAllNodesWithText`, `onNodeWithTag`, `onRoot`, and `fetchSemanticsNode()`.
- Proven fields: text lookup for `Static Var` and `Compose Multiplatform preview`, test tag lookup for `profile_name`, and non-zero bounds.
- Notes: Semantics were proven by directly composing the common preview function in the Android unit test. Production can use the same Android-target invocation path for common preview functions.

## Decision

Use the Android renderer backend for first-pass CMP support when a Compose Multiplatform project has an Android target. Common previews using AndroidX `@Preview` can be discovered and rendered through Android target outputs. Default to Robolectric runtime SDK 35 for Java 17 compatibility, with an opt-in SDK 36 mode that requires Java 21.

CMP previews rendered through the Android target should use the same explicit Android viewport configuration as Android-only previews. If a common `@Preview` omits positive `widthDp` and `heightDp`, render one output per configured Android viewport. Future desktop/web CMP rendering should use separate platform-specific viewport containers instead of reusing Android viewports.

## Limitations

- The spike used AGP 8.13.0 because AGP 9.2.1 currently conflicts with applying `org.jetbrains.kotlin.multiplatform` in this sample; AGP 9 reports that a `kotlin` extension is already registered.
- The spike uses JetBrains Compose Multiplatform Material3 via `org.jetbrains.compose.material3:material3:1.11.0-alpha07`, matching the Compose Multiplatform 1.11.0 dependency table. Material3 is alpha in this release, but it is the documented M3 dependency for the new JetBrains Compose release.
- The Android target must include Android resources for Robolectric tests via `testOptions.unitTests.isIncludeAndroidResources = true`.
- The Android manifest must declare `androidx.activity.ComponentActivity` for `createComposeRule` and Roborazzi preview capture.
- The Gradle build emits D8 Kotlin metadata warnings with Kotlin 2.3.21, AGP 8.13.0, and JetBrains Material3 alpha artifacts, but the build and tests pass.
- After adding JetBrains Material3, the Gradle daemon hit a local Metaspace limit once. Re-running with `--no-daemon` and `GRADLE_OPTS='-XX:MaxMetaspaceSize=1024m -Xmx4g'` passed.
