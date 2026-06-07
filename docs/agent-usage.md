# AgentPreview Usage for AI Agents

AgentPreview is a Gradle plugin for turning AndroidX Compose `@Preview` functions into agent-readable bundles:

```text
<module>/build/agentPreviewSnapshots/<sanitized-preview-id>/<platform>-<viewport>/
  screenshot.png
  snapshot.json
```

Use it when you need to inspect, compare, or iterate on Compose UI without launching an app manually.

Output path note: preview ids are sanitized for file-system paths. For example, `:app:main:LoginPreview` becomes `app-main-LoginPreview`, and a `phone` Android viewport is stored under `android-phone`. Use `listComposePreviews` for the logical id and `find <module>/build/agentPreviewSnapshots -name snapshot.json` when locating files programmatically.

## 1. Install AgentPreview

For normal use, apply the published Gradle Plugin Portal plugin to the module that owns the previews.

```kotlin
plugins {
    id("dev.staticvar.agentpreview") version "0.1.0"
}
```

For local AgentPreview development, add this checkout as an included build in the target project's `settings.gradle.kts`, then apply `id("dev.staticvar.agentpreview")` without a version.

```kotlin
pluginManagement {
    includeBuild("/path/to/AgentPreview")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

### Convention plugin usage

If your project centralizes module setup in a build-logic or convention plugin, apply AgentPreview inside that convention plugin and configure the existing `agentPreview` extension. This keeps each UI module small while still letting teams standardize viewports and capture defaults.

```kotlin
// build-logic/src/main/kotlin/com/example/AgentPreviewConventionPlugin.kt
package com.example

import dev.staticvar.agentpreview.AgentPreviewExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AgentPreviewConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("dev.staticvar.agentpreview")

        target.extensions.configure<AgentPreviewExtension>("agentPreview") {
            // Include deeper accessibility semantics in snapshots.
            includeUnmergedSemantics.set(true)
            // Limit @PreviewParameter states per preview.
            maxPreviewParameterValues.set(3)
            // Refuse accidentally broad capture plans.
            maxCaptures.set(8)
            // Keep rendering deterministic while iterating.
            maxParallelRenders.set(1)
            // Collect remaining outputs after failures.
            continueOnError.set(true)

            android {
                // Render against the debug runtime classpath.
                variant.set("debug")
                // Standard phone viewport for responsive previews.
                viewport("phone", widthDp = 393, heightDp = 852)
                // Larger viewport for tablet layout checks.
                viewport("tablet", widthDp = 800, heightDp = 1280)
                screenshot {
                    // Crop screenshots to meaningful UI bounds.
                    cropToContent.set(true)
                    // Keep context around cropped content.
                    cropPaddingDp.set(20)
                }
            }
        }
    }
}
```

The convention plugin build needs AgentPreview on its compile classpath so it can reference `AgentPreviewExtension`:

```kotlin
// build-logic/build.gradle.kts
plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("dev.staticvar:plugin:0.1.0")
}
```

When developing against a local checkout, add AgentPreview as an included build for plugin resolution and as a top-level included build for dependency substitution if the convention plugin compiles against `AgentPreviewExtension`. See [`samples/convention-plugin`](../samples/convention-plugin) for a complete build-logic sample.


### Android app module

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.staticvar.agentpreview")
}

android {
    namespace = "com.example.app"
    compileSdk = 36

    defaultConfig { minSdk = 23 }
    buildFeatures { compose = true }
}

agentPreview {
    android {
        variant.set("debug")
        viewport("phone", widthDp = 393, heightDp = 852)
        viewport("tablet", widthDp = 800, heightDp = 1280)
    }
}
```

```bash
./gradlew :app:listComposePreviews
./gradlew :app:captureComposePreviews
```

### Android library module

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.staticvar.agentpreview")
}

android {
    namespace = "com.example.ui"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    buildFeatures { compose = true }
}

agentPreview {
    android {
        variant.set("debug")
        viewport("phone", widthDp = 393, heightDp = 852)
    }
}
```

```bash
./gradlew :ui:listComposePreviews
./gradlew :ui:captureComposePreviews
```

### Compose Multiplatform module with Android target

```kotlin
plugins {
    id("com.android.application") // or com.android.library
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("dev.staticvar.agentpreview")
}

kotlin {
    androidTarget()

    sourceSets {
        androidMain.dependencies {
            implementation("androidx.compose.ui:ui-tooling-preview:1.11.2")
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }
}

android {
    namespace = "com.example.cmp"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
}

agentPreview {
    android {
        viewport("phone", widthDp = 393, heightDp = 852)
        viewport("tablet", widthDp = 800, heightDp = 1280)
    }
}
```

```bash
./gradlew :composeApp:listComposePreviews
./gradlew :composeApp:captureComposePreviews
```

### Android KMP library wiring

For Android Kotlin Multiplatform library-shaped modules, apply the plugin to the module with the Android target. AgentPreview wires Android-backed previews from Gradle providers: Android Components variant artifacts/runtime configurations for Android KMP plugin modules, and Kotlin compilation outputs/runtime dependency files only for Kotlin MPP Android-shaped targets that do not apply the Android KMP plugin. It does not rely on stale build output directories being present before the task graph is created.

```kotlin
plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("dev.staticvar.agentpreview")
}

kotlin {
    androidLibrary {
        namespace = "com.example.shared.ui"
        compileSdk = 36
        minSdk = 23
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation("androidx.compose.ui:ui-tooling-preview:1.11.2")
        }
    }
}

agentPreview {
    android {
        viewport("phone", widthDp = 393, heightDp = 852)
    }
}
```

```bash
./gradlew :shared-ui:listComposePreviews
./gradlew :shared-ui:captureComposePreviews
```

If auto-wiring misses a custom module shape, wire it manually:

```kotlin
agentPreview {
    previewClassesDirs.from(tasks.named("generatePreviewClasses"))
    previewRuntimeClasspath.from(configurations.named("debugRuntimeClasspath"))
}
```

## 2. List previews first

```bash
./gradlew :app:listComposePreviews
```

Output is one preview per line:

```text
:app:main:LoginPreview  Login
:app:main:ParameterizedLoginPreview  Parameterized Login  [@PreviewParameter provider=..., limit=...; capture ids append :previewParam-N]
```

Use the full left-hand id for exact focused captures. Short filters such as `Login` are useful while exploring, but they can match several previews; dry-run first when combining broad filters with `maxCaptures`.

## 3. Run a focused capture

Prefer a narrow, cheap run while editing UI. Start with a dry run, inspect `capture-report.json`, then render the same exact target:

```bash
./gradlew :app:captureComposePreviews \
  -PagentPreview.previewNameFilter=:app:main:LoginPreview \
  -PagentPreview.viewportFilter=preview \
  -PagentPreview.maxCaptures=1 \
  -PagentPreview.dryRun=true

./gradlew :app:captureComposePreviews \
  -PagentPreview.previewNameFilter=:app:main:LoginPreview \
  -PagentPreview.viewportFilter=preview \
  -PagentPreview.maxCaptures=1
```

Use `viewportFilter=preview` for previews with explicit `@Preview(widthDp/heightDp)` dimensions. Use configured viewport names such as `phone`, `tablet`, `android-phone`, or `android-tablet` when the preview omits explicit dimensions and AgentPreview expands it across configured viewports.

Useful capture flags:

| Flag | Use |
| --- | --- |
| `-PagentPreview.previewNameFilter=:app:main:LoginPreview,EmptyState` | Capture matching preview ids, names, groups, function names, or expanded `:previewParam-N` ids. Prefer full ids for exact targeting. |
| `-PagentPreview.viewportFilter=preview,phone,android-tablet` | Capture only named viewports. Explicit-dimension previews use `preview`; configured viewports can match by name or `platform-name`. |
| `-PagentPreview.maxPreviewParameterValues=3` | Cap `@PreviewParameter` expansion. |
| `-PagentPreview.dryRun=true` | Write the capture plan/report without rendering screenshots. |
| `-PagentPreview.maxCaptures=10` | Fail before rendering if the plan is too broad. Use `0` to assert no captures. |
| `-PagentPreview.continueOnError=true` | Keep rendering other previews after a failure; task still fails at end if any failed. |
| `-PagentPreview.maxParallelRenders=2` | Render concurrently. Start with `1`; raise only when renders are stable. |
| `-PagentPreview.cropToContent=false` | Disable default content cropping and export the full render viewport. |
| `-PagentPreview.cropPaddingDp=12` | Override the default 20dp crop padding for this invocation. |
| `-PagentPreview.fakeRenderer=true` | Debug discovery/index wiring only; screenshots and nodes are placeholders. |
| `-Dagentpreview.fontProbe=true` | Print bounded CMP font asset diagnostics. |
| `-Dagentpreview.java.executable=/path/to/java` | Use this Java for isolated renderer JVM. |
| `-PagentPreview.javaMajorVersion=17` | Diagnostic Java-version warning override only; does not switch JVM. |

Configure defaults in Gradle when you want stable agent behavior:

```kotlin
agentPreview {
    includeUnmergedSemantics.set(true)
    previewNameFilter.add("Login")
    viewportNameFilter.add("phone")
    maxPreviewParameterValues.set(3)
    maxCaptures.set(8)
    maxParallelRenders.set(1)
    continueOnError.set(true)
    android {
        screenshot {
            cropToContent.set(true) // default
            cropPaddingDp.set(20)   // default
        }
    }
}
```

## 4. Inspect outputs

After capture, inspect:

```bash
ls app/build/agentPreviewSnapshots
python3 -m json.tool app/build/agentPreviewReports/capture-report.json
python3 -m json.tool app/build/agentPreviewSnapshots/<sanitized-preview-id>/<platform>-<viewport>/snapshot.json
```

Open `screenshot.png` for visual state only when `render.mode` is `robolectric`. Real rendered screenshots crop to meaningful layout/semantics content by default with 20dp padding. When bounds are unavailable or effectively the full viewport, AgentPreview exports the full viewport instead. Fake and diagnostic-fallback captures are placeholders. Read `snapshot.json` for machine-checkable structure and crop metadata. Schema details are in [snapshot-schema.md](snapshot-schema.md).

Important `snapshot.json` fields:

| Field | Meaning for agents |
| --- | --- |
| `schemaVersion` | Current snapshot schema. Ignore unknown fields for forward compatibility. |
| `preview.id` | Stable capture id. Parameterized captures append `:previewParam-N`. |
| `preview.name`, `preview.group` | Human labels from `@Preview`; useful for filter selection. |
| `preview.source`, `preview.sourceSet` | Best-effort preview source location. |
| `preview.previewParameter` | Provider class, parameter type, cap/limit, and expanded value index. |
| `viewport.platform`, `viewport.name` | Render target, e.g. `android` + `phone`. |
| `viewport.width`, `viewport.height`, `viewport.density` | Original render viewport dimensions. `@Preview(widthDp/heightDp)` overrides configured viewport dimensions. |
| `screenshot.width`, `screenshot.height` | Exported PNG dimensions after crop/fallback. |
| `screenshot.crop` | Crop settings, original-viewport crop rectangle when cropped, or fallback reason such as `disabled` or `ambiguous-content-bounds`. |
| `render.mode` | `robolectric`, `fake`, or `diagnostic-fallback`. Only `robolectric` is visual evidence. |
| `nodes` | Semantics nodes: text/content descriptions, roles, bounds, actions, tags, optional source. Use this for accessibility and interaction assertions. |
| `layoutTree` | Experimental layout nodes with bounds and Compose/source hints. Use as a clue, not ground truth. |

`layoutTree` source hint fields are best-effort:

- `sourceName`, `sourceFile`, `sourceLine`: nearest useful app/framework source hint when Compose tooling data is available.
- `sourceHintKind`: why that source was chosen, such as `tooling-nearest-app-ancestor` or `preview-entrypoint-fallback`.
- `componentHint`, `modifierHint`, `classHint`: implementation-level fallbacks.
- `semantics` / `semanticsId`: correlation back to semantics when possible.

## 5. Suggested UI-agent loop

1. Run `listComposePreviews`.
2. Pick the smallest relevant preview. Prefer its full left-hand id for exact targeting.
3. Dry-run `captureComposePreviews` with `previewNameFilter`, `viewportFilter`, and `maxCaptures`.
4. Inspect `capture-report.json`; confirm the planned count, selected preview, viewport, and zero failures.
5. Render the same focused command without `dryRun=true`.
6. Inspect `screenshot.png` visually only when `render.mode` is `robolectric`.
7. Inspect `snapshot.json` for labels, bounds, semantics roles/actions, and layout hints.
8. Edit Compose code and re-run the same focused command.
9. Broaden to more viewports/previews only after the focused case is correct.

Good default command while iterating:

```bash
./gradlew :app:captureComposePreviews \
  -PagentPreview.previewNameFilter=:app:main:LoginPreview \
  -PagentPreview.viewportFilter=preview \
  -PagentPreview.maxPreviewParameterValues=2 \
  -PagentPreview.maxCaptures=1 \
  -PagentPreview.continueOnError=true
```

When reporting results, include the preview id, viewport, screenshot path, snapshot path, `render.mode`, capture-report failure count, and any remaining visual differences.

## Failure handling

- No previews listed: ensure the plugin is applied to the module with compiled preview classes, then run the module task path (`:app:listComposePreviews`, not root task).
- Too many captures: rerun with `-PagentPreview.dryRun=true`, inspect `build/agentPreviewReports/capture-report.json`, then narrow filters or raise `maxCaptures`.
- One preview fails: rerun with `continueOnError=true` to collect remaining outputs. Task still fails; inspect report, then focus the failing id.
- Parameterized preview explodes: lower `maxPreviewParameterValues` or filter an expanded id like `ParameterizedLoginPreview:previewParam-0`.
- Empty `nodes`: fake renderer was used, semantics were not emitted, or the preview has no useful semantics. Prefer real rendering and add semantics/test tags when appropriate.
- Missing/weak `layoutTree` hints: expected for some Compose/CMP cases. Fall back to screenshot, semantics nodes, and preview source.
- Renderer complains about Android wiring: use an Android app/library or CMP Android target; for discovery-only checks use `-PagentPreview.fakeRenderer=true`.
- Configuration-cache issues: rerun with `--no-configuration-cache` to confirm whether the problem is cache-specific.

### Fonts, assets, and Android resources

When a screenshot renders but fonts, images, strings, dimensions, or other resources look wrong, first confirm the capture is real evidence:

1. Inspect `snapshot.json` and require `render.mode` to be `robolectric`. Fake and diagnostic-fallback captures are placeholders and cannot prove font or resource behavior.
2. Confirm AgentPreview is applied to the Android app/library or CMP module with the Android target that owns the preview, then confirm `agentPreview { android { variant.set("debug") } }` matches the variant whose resources you expect.
3. Identify where the missing asset actually comes from:
   - Android `res/font`, `res/drawable`, `res/values`, and dependency resources should normally come through AGP-linked Android resource artifacts for the selected variant.
   - Android `src/<variant>/assets`, Compose Multiplatform `composeResources`, generated assets, and custom asset folders must be visible through the Android merged-assets output or `android.assetsDirs`.
   - Classes and synthetic `R` jars are not enough for resource contents; the renderer needs the actual Android resource artifact or asset files.

For CMP fonts/assets or custom generated asset folders that are not auto-wired, add the asset root in Gradle. Use the directory whose children should be visible through `LocalContext.current.assets`:

```kotlin
agentPreview {
    android {
        // Add custom assets visible to Robolectric AssetManager.
        assetsDirs.from(layout.projectDirectory.dir("src/commonMain/composeResources"))
        // Add generated CMP assets with task dependencies.
        assetsDirs.from(tasks.named("copyAndroidMainComposeResourcesToAndroidAssets"))
    }
}
```

If multiple asset roots contain the same relative path with different bytes, AgentPreview fails instead of guessing precedence. Prefer a single AGP merged-assets directory when available.

If Android `res/font` or other `res/` values still do not load, check the selected module and variant first. The real renderer passes AGP resource APK/linked-resource artifacts, merged manifest, namespace, and optional merged assets into Robolectric. Resource failures after that are usually variant/module wiring gaps or renderer resource-loading edge cases. Use `capture-report.json` for the failing preview id, then rerun the focused command with `-Dagentpreview.fontProbe=true` when debugging CMP font asset visibility.

## Known limitations

- Rendering is Android-backed. Desktop/web Compose targets are not separate renderers here.
- `layoutTree` and source hints are experimental and nullable.
- Compose Multiplatform Android-target captures may only emit preview-entrypoint fallback source hints when tooling composition data is unavailable.
- `@PreviewParameter` support expects one user parameter annotated with `@PreviewParameter`; multiple user parameters are unsupported.
- Fake and diagnostic-fallback screenshots are placeholders; do not use them for UI judgment.
- Real Android captures include Android merged assets and Android resource artifacts where available; see the fonts/assets troubleshooting section for manual `assetsDirs` wiring and resource edge cases.
- SDK lookup: `ANDROID_HOME`, `ANDROID_SDK_ROOT`, then Gradle-root `local.properties` `sdk.dir`. Missing requested `android-35` may fall back to highest installed platform with warning; install `platforms;android-35`.
