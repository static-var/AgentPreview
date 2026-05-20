# Renderer Spike

## Goal

Determine whether Preview For Agents should use Roborazzi plus ComposablePreviewScanner as the first production backend for discovering and rendering AndroidX Compose `@Preview` functions on the JVM.

## Requirements To Prove

- Existing `androidx.compose.ui.tooling.preview.Preview` functions are discoverable without Google's `@PreviewTest`.
- At least one preview screenshot renders on macOS without an emulator, device, Android Studio, IntelliJ, or UiAutomator.
- The same setup is expected to work on Linux in CI.
- A merged Compose semantics tree can provide text, content descriptions, roles, actions, test tags, and bounds.
- The approach does not write generated Kotlin files into user source directories.

## Candidate Backend

- Roborazzi
- Roborazzi Compose support
- Roborazzi Compose preview scanner support
- ComposablePreviewScanner
- AndroidX Compose UI test APIs

## Version Matrix

Resolved versions for the spike sample. AGP 9.2.1 requires Gradle 9.4.1+, so the spike project owns a local Gradle wrapper at `spikes/renderer-android-compose/gradlew`. AGP 9 also has built-in Kotlin support, so the sample does not apply `org.jetbrains.kotlin.android`.

| Dependency | Version | Source |
| --- | --- | --- |
| Android Gradle Plugin | 9.2.1 | Maven metadata checked on 2026-05-19 |
| Kotlin | 2.3.21 | Maven metadata checked on 2026-05-19 |
| Compose BOM / Compose UI | BOM 2026.05.01 / UI 1.11.2 | Maven metadata checked on 2026-05-19 |
| Roborazzi | 1.63.0 | Maven metadata checked on 2026-05-19 |
| ComposablePreviewScanner | 0.9.0 | Maven metadata checked on 2026-05-19 |

## Discovery Findings

- Command: `ANDROID_HOME=$HOME/Library/Android/sdk build-brief spikes/renderer-android-compose/gradlew -p spikes/renderer-android-compose :app:testDebugUnitTest --tests dev.staticvar.agentpreview.spike.PreviewDiscoverySpikeTest`
- Result: PASS, 1 test passed.
- Scanner API used: `sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner().scanPackageTrees("dev.staticvar.agentpreview.spike").getPreviews()`.
- Preview result API used via reflection in the spike test: `getPreviewInfo`, `getDeclaringClass`, `getMethodName`, `getMethodParametersType`; Android preview info exposes `getName`, `getGroup`, `getWidthDp`, and `getHeightDp`.
- Notes: AndroidX `@Preview` discovery works without Google's `@PreviewTest`. The Kotlin compiler did not expose all Java-style getters as direct Kotlin members in this test source, so the spike uses reflection for assertions. Production code can use Java reflection or revisit typed access in the plugin classpath.

## Rendering Findings

Record screenshot API, sample command output, screenshot path, and whether rendering works here.

## Semantics Findings

Record semantics API, sample output shape, and missing fields here.

## Limitations

Record limitations that production code must account for here.

## Decision

Pending.
