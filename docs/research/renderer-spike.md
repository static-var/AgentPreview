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

- Command: `ANDROID_HOME=$HOME/Library/Android/sdk build-brief spikes/renderer-android-compose/gradlew -p spikes/renderer-android-compose :app:recordRoborazziDebug --tests dev.staticvar.agentpreview.spike.PreviewRenderingSpikeTest`
- Result: PASS, 2 tests passed under Java 21 with Robolectric SDK 36.
- Java 17 compatibility command after adding `@Config(sdk = [35])`: `ANDROID_HOME=$HOME/Library/Android/sdk build-brief spikes/renderer-android-compose/gradlew -p spikes/renderer-android-compose :app:recordRoborazziDebug --tests dev.staticvar.agentpreview.spike.PreviewRenderingSpikeTest`
- Java 17 compatibility result: PASS, 2 tests passed with compile SDK 36 and Robolectric runtime SDK 35.
- Screenshot path: `spikes/renderer-android-compose/app/build/outputs/renderer-spike/LoginPreview.png`.
- Rendering API used: `AndroidComposablePreviewScanner().scanPackageTrees(...).getPreviews().single().captureRoboImage(screenshot.absolutePath)` from `com.github.takahirom.roborazzi.captureRoboImage`.
- Notes: Rendering requires the Roborazzi Gradle plugin and the `recordRoborazziDebug` task, plus Robolectric. With Robolectric runtime SDK 36, Java 17 failed with `Android SDK 36 requires Java 21`. Forcing Robolectric runtime SDK 35 with `@Config(sdk = [35])` allows the same compile SDK 36 project to render on Java 17.

## Semantics Findings

- Command: same `PreviewRenderingSpikeTest` command above.
- Result: PASS for direct `LoginPreview()` composition using `createComposeRule()`.
- Semantics API used: `createComposeRule`, `onAllNodesWithText`, `onNodeWithTag`, `onRoot`, and `fetchSemanticsNode()` from AndroidX Compose UI test APIs.
- Proven fields: text lookup, test tag lookup, and non-zero node/root bounds.
- Notes: The spike proves semantics extraction from the same composable content in a Robolectric Compose test. It does not yet prove semantics extraction from the Roborazzi preview wrapper object itself; production code may need to host/invoke the preview composable in a Compose test rule to collect semantics alongside screenshot capture.

## Limitations

- AGP 9.2.1 requires Gradle 9.4.1 or newer. The spike project uses a local Gradle 9.4.1 wrapper because the repository wrapper is currently older.
- AGP 9 has built-in Kotlin support. Applying `org.jetbrains.kotlin.android` fails, so Android sample projects should not apply that plugin when using AGP 9.
- Compile SDK 36 itself does not require Java 21 for normal project builds in this spike. `:app:assembleDebug` passed on Java 17 with compile SDK 36.
- Robolectric runtime SDK 36 requires Java 21. Java 17 failed before test execution with `Android SDK 36 requires Java 21`.
- Java 17 is viable for preview capture when the Robolectric runtime SDK is forced to 35 with `@Config(sdk = [35])`, while keeping compile SDK 36.
- Roborazzi preview screenshot capture writes output when run through the Roborazzi `recordRoborazziDebug` task. A plain `testDebugUnitTest` run did not record the screenshot file.
- Roborazzi preview capture requires Robolectric and an Android test runner. Without Robolectric instrumentation, capture failed with `No instrumentation registered`.
- Compose test semantics were proven by directly composing `LoginPreview()` in a `createComposeRule` test. The spike did not prove extracting semantics from the Roborazzi preview wrapper object itself.
- The scanner API is viable, but typed Kotlin property access was awkward in the spike test. Reflection worked for reading preview metadata.

## Decision

Use Roborazzi plus ComposablePreviewScanner for the first production Android Compose backend. Default production integration should prefer Java 17 compatibility by running the Roborazzi/Robolectric renderer with runtime SDK 35, even when the client project compiles with SDK 36. Offer an opt-in high-fidelity SDK 36 mode that requires Java 21. Keep the backend behind `PreviewDiscovery`, `PreviewRenderer`, and `SemanticsExtractor` interfaces so it can be replaced if upstream APIs or AGP behavior change.

Production Android rendering should expose explicit Android viewport configuration. If an Android `@Preview` declares positive `widthDp` and `heightDp`, render that preview using those annotation dimensions. If either dimension is missing or a sentinel value such as `-1`, render one bundle per configured Android viewport, defaulting to a Java-17-friendly `phone` viewport of 393x852 dp.
