# AgentPreview Usage for AI Agents

AgentPreview is a Gradle plugin for turning AndroidX Compose `@Preview` functions into agent-readable bundles:

```text
<module>/build/agentPreviewSnapshots/<sanitized-preview-id>/<platform>-<viewport>/
  screenshot.png
  snapshot.json
```

Use it when you need to inspect, compare, or iterate on Compose UI without launching an app manually. For a reusable agent workflow, see [`skills/agentpreview-compose-iteration/SKILL.md`](../skills/agentpreview-compose-iteration/SKILL.md).

Output path note: preview ids are sanitized for file-system paths. For example, `:app:main:LoginPreview` becomes `app-main-LoginPreview`, and a `phone` Android viewport is stored under `android-phone`. Use `listComposePreviews` for the logical id and `find <module>/build/agentPreviewSnapshots -name snapshot.json` when locating files programmatically.

## 1. Install from a local checkout

Until the plugin is published, add the AgentPreview checkout as an included build in the target project's `settings.gradle.kts`.

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

Apply the plugin to the module that owns the previews.

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

For Android Kotlin Multiplatform library-shaped modules, apply the plugin to the module with the Android target. AgentPreview wires Android-backed previews from Gradle providers: Android Components variant artifacts/runtime configurations when they are exposed by the Android KMP plugin, and Kotlin compilation outputs/runtime dependency files as a fallback. It does not rely on stale build output directories being present before the task graph is created.

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

Use the left-hand id for focused captures.

## 3. Run a focused capture

Prefer a narrow, cheap run while editing UI:

```bash
./gradlew :app:captureComposePreviews \
  -PagentPreview.previewNameFilter=LoginPreview \
  -PagentPreview.viewportFilter=phone \
  -PagentPreview.maxCaptures=4
```

Useful capture flags:

| Flag | Use |
| --- | --- |
| `-PagentPreview.previewNameFilter=LoginPreview,EmptyState` | Capture matching preview ids, names, groups, function names, or expanded `:previewParam-N` ids. |
| `-PagentPreview.viewportFilter=phone,tablet` | Capture only named viewports. |
| `-PagentPreview.maxPreviewParameterValues=3` | Cap `@PreviewParameter` expansion. |
| `-PagentPreview.dryRun=true` | Write the capture plan/report without rendering screenshots. |
| `-PagentPreview.maxCaptures=10` | Fail before rendering if the plan is too broad. Use `0` to assert no captures. |
| `-PagentPreview.continueOnError=true` | Keep rendering other previews after a failure. |
| `-PagentPreview.maxParallelRenders=2` | Render concurrently. Start with `1`; raise only when renders are stable. |
| `-PagentPreview.fakeRenderer=true` | Debug discovery/index wiring only; screenshots and nodes are placeholders. |

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
}
```

## 4. Inspect outputs

After capture, inspect:

```bash
ls app/build/agentPreviewSnapshots
python3 -m json.tool app/build/agentPreviewReports/capture-report.json
python3 -m json.tool app/build/agentPreviewSnapshots/<sanitized-preview-id>/<platform>-<viewport>/snapshot.json
```

Open `screenshot.png` for visual state. Read `snapshot.json` for machine-checkable structure. Schema details are in [snapshot-schema.md](snapshot-schema.md).

Important `snapshot.json` fields:

| Field | Meaning for agents |
| --- | --- |
| `schemaVersion` | Current snapshot schema. Ignore unknown fields for forward compatibility. |
| `preview.id` | Stable capture id. Parameterized captures append `:previewParam-N`. |
| `preview.name`, `preview.group` | Human labels from `@Preview`; useful for filter selection. |
| `preview.source`, `preview.sourceSet` | Best-effort preview source location. |
| `preview.previewParameter` | Provider class, parameter type, cap/limit, and expanded value index. |
| `viewport.platform`, `viewport.name` | Render target, e.g. `android` + `phone`. |
| `viewport.width`, `viewport.height`, `viewport.density` | Rendered viewport dimensions. `@Preview(widthDp/heightDp)` overrides configured viewport dimensions. |
| `render.mode` | Renderer used: usually `robolectric`; `fake` means discovery-only placeholder output. |
| `nodes` | Semantics nodes: text/content descriptions, roles, bounds, actions, tags, optional source. Use this for accessibility and interaction assertions. |
| `layoutTree` | Experimental layout nodes with bounds and Compose/source hints. Use as a clue, not ground truth. |

`layoutTree` source hint fields are best-effort:

- `sourceName`, `sourceFile`, `sourceLine`: nearest useful app/framework source hint when Compose tooling data is available.
- `sourceHintKind`: why that source was chosen, such as `tooling-nearest-app-ancestor` or `preview-entrypoint-fallback`.
- `componentHint`, `modifierHint`, `classHint`: implementation-level fallbacks.
- `semantics` / `semanticsId`: correlation back to semantics when possible.

## 5. Suggested UI-agent loop

1. Run `listComposePreviews`.
2. Pick the smallest relevant preview and viewport.
3. Run `captureComposePreviews` with `previewNameFilter`, `viewportFilter`, and `maxCaptures`.
4. Inspect `screenshot.png` visually.
5. Inspect `snapshot.json` for labels, bounds, semantics roles/actions, and layout hints.
6. Edit Compose code.
7. Re-run the same focused command.
8. Broaden to more viewports/previews only after the focused case is correct.

Good default command while iterating:

```bash
./gradlew :app:captureComposePreviews \
  -PagentPreview.previewNameFilter=Login \
  -PagentPreview.viewportFilter=phone \
  -PagentPreview.maxPreviewParameterValues=2 \
  -PagentPreview.maxCaptures=4 \
  -PagentPreview.continueOnError=true
```

## Failure handling

- No previews listed: ensure the plugin is applied to the module with compiled preview classes, then run the module task path (`:app:listComposePreviews`, not root task).
- Too many captures: rerun with `-PagentPreview.dryRun=true`, inspect `build/agentPreviewReports/capture-report.json`, then narrow filters or raise `maxCaptures`.
- One preview fails: rerun with `continueOnError=true` to collect remaining outputs, then focus the failing id.
- Parameterized preview explodes: lower `maxPreviewParameterValues` or filter an expanded id like `ParameterizedLoginPreview:previewParam-0`.
- Empty `nodes`: fake renderer was used, semantics were not emitted, or the preview has no useful semantics. Prefer real rendering and add semantics/test tags when appropriate.
- Missing/weak `layoutTree` hints: expected for some Compose/CMP cases. Fall back to screenshot, semantics nodes, and preview source.
- Renderer complains about Android wiring: use an Android app/library or CMP Android target; for discovery-only checks use `-PagentPreview.fakeRenderer=true`.
- Configuration-cache issues: rerun with `--no-configuration-cache` to confirm whether the problem is cache-specific.

## Known limitations

- Rendering is Android-backed. Desktop/web Compose targets are not separate renderers here.
- `layoutTree` and source hints are experimental and nullable.
- Compose Multiplatform Android-target captures may only emit preview-entrypoint fallback source hints when tooling composition data is unavailable.
- `@PreviewParameter` support expects one user parameter annotated with `@PreviewParameter`; multiple user parameters are unsupported.
- Fake renderer is only for discovery/debugging; do not use its screenshots or empty nodes for UI judgment.
