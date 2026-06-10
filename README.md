<div align="center">

# AgentPreview

<strong>Visual proof for Compose UI changes made by coding agents.</strong>

<br />
<br />

[![CI](https://github.com/static-var/AgentPreview/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/static-var/AgentPreview/actions/workflows/ci.yml)
[![Pages](https://github.com/static-var/AgentPreview/actions/workflows/pages.yml/badge.svg?branch=main)](https://github.com/static-var/AgentPreview/actions/workflows/pages.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/dev.staticvar.agentpreview?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/dev.staticvar.agentpreview)
[![GitHub release](https://img.shields.io/github/v/release/static-var/AgentPreview?include_prereleases&sort=semver)](https://github.com/static-var/AgentPreview/releases)
[![License](https://img.shields.io/github/license/static-var/AgentPreview)](LICENSE)
[![skills.sh](https://www.skills.sh/b/static-var/AgentPreview)](https://www.skills.sh/static-var/AgentPreview)

[Website](https://ap.staticvar.dev/) |
[Agent workflow](docs/agent-usage.md) |
[Snapshot schema](docs/snapshot-schema.md) |
[Agent skill](https://www.skills.sh/static-var/AgentPreview)

</div>

AgentPreview is a Gradle plugin that captures AndroidX Compose `@Preview` functions into screenshots and JSON metadata an AI agent can inspect while editing UI. It is built for the tight loop where an agent changes Compose code, captures the affected preview, reads the visual output, and iterates without opening Android Studio or driving the full app.

```text
<module>/build/agentPreviewSnapshots/<sanitized-preview-id>/<platform>-<viewport>/
  screenshot.png
  snapshot.json
```

Preview ids are sanitized for file paths: `:app:main:LoginPreview` becomes `app-main-LoginPreview`, and viewport folders include the platform, for example `android-phone`.

## What agents get

| Artifact | What it is for |
| --- | --- |
| `screenshot.png` | Real Android-backed pixels for visual inspection. |
| `snapshot.json` | Preview metadata, viewport data, semantics, layout hints, and source hints. |
| `capture-report.json` | Capture plan, selected previews, output paths, and render failures. |

## Where each tool fits

AgentPreview is one part of a broader Android and agent-UI toolbox. These tools overlap around UI feedback, but they work at different layers:

| Tool | Best for | Not for |
| --- | --- | --- |
| AgentPreview | Pre-launch Compose iteration: discover `@Preview` functions, dry-run focused capture plans, and export `screenshot.png` plus `snapshot.json` for agents. | Full app journeys, runtime permissions, backend/device state, or behavior that only exists after app launch. |
| Android CLI | Android SDK setup, emulator/device lifecycle, APK deployment, runtime screen capture, layout-tree inspection, and visual element targeting on a running app. | Discovering or rendering isolated Compose previews from Gradle without launching the app. |
| A2UI | Agent-generated product UI: an agent sends declarative UI messages that a host app renders with trusted native components, catalogs, data binding, and actions. | Inspecting Android builds or proving what an existing Compose preview currently renders. |
| Compose Preview Screenshot Testing | Golden-image regression checks for selected Compose previews, including validation tasks and HTML diff reports. | Fast ad hoc agent iteration when you do not want to create or maintain reference images. |

Use them together when the task crosses layers. For example, A2UI can define the protocol for an agent-rendered experience, AgentPreview can inspect the Compose components that implement that experience, and Android CLI can validate the final running app on an emulator or device.

## Install

Use the published Gradle Plugin Portal plugin in the target module that owns the previews. Gradle requires a concrete plugin version, so pin the latest version shown on the [Plugin Portal](https://plugins.gradle.org/plugin/dev.staticvar.agentpreview) or in the badge above.

```kotlin
plugins {
    id("dev.staticvar.agentpreview") version "<current-version>"
}
```

If your project uses a version catalog:

```toml
# gradle/libs.versions.toml
[versions]
agentPreview = "<current-version>"

[plugins]
agentPreview = { id = "dev.staticvar.agentpreview", version.ref = "agentPreview" }
```

```kotlin
plugins {
    alias(libs.plugins.agentPreview)
}
```

For local development against this checkout, add it as an included build in the target project's `settings.gradle.kts` and omit the version where the plugin is applied:

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

```kotlin
plugins {
    id("dev.staticvar.agentpreview")
}
```

## Agent skill

Install the AgentPreview workflow skill with the open `skills` CLI:

```bash
npx skills add static-var/AgentPreview --skill agentpreview
```

For Codex global install:

```bash
npx skills add static-var/AgentPreview --skill agentpreview -g -a codex -y
```

## Core loop

List previews first:

```bash
./gradlew :app:listComposePreviews
```

Capture previews:

```bash
./gradlew :app:captureComposePreviews
```

For a focused capture while iterating, use the full left-hand id from `listComposePreviews`, dry-run the plan, then render the same target:

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

Outputs are written under:

| Output | Location |
| --- | --- |
| Snapshots | `<module>/build/agentPreviewSnapshots/<sanitized-preview-id>/<platform>-<viewport>/` |
| Capture report | `<module>/build/agentPreviewReports/capture-report.json` |
| Accessibility report | `<module>/build/agentPreviewReports/accessibility-report.html` when `-PagentPreview.accessibilityCheck=true` |

To generate an advisory accessibility report for the same captured previews:

```bash
./gradlew :app:captureComposePreviews \
  -PagentPreview.accessibilityCheck=true
```

The report copies current-run screenshots into `build/agentPreviewReports/accessibility-assets/`, checks only previews captured by that command, and does not fail the build for accessibility findings.

## Capture controls

| Option | Use when |
| --- | --- |
| `-PagentPreview.viewportFilter=preview` | The preview defines explicit `@Preview(widthDp/heightDp)` dimensions. |
| `-PagentPreview.viewportFilter=android-phone` | You want one configured viewport from a broader viewport set. |
| `-PagentPreview.cropToContent=false` | You need a full-viewport diagnostic capture. |
| `-PagentPreview.cropPaddingDp=12` | You want to override the default 20dp crop padding for one run. |
| `-PagentPreview.dryRun=true` | You want the planned captures without rendering screenshots. |
| `-PagentPreview.accessibilityCheck=true` | You want an advisory HTML report for labels, roles/actions, touch targets, duplicate labels, and traversal heuristics. |

Real rendered screenshots crop to detected Compose content by default. If layout or semantics bounds are ambiguous, AgentPreview keeps the full viewport and records the fallback in `snapshot.json`.

## Current support

| Area | Status |
| --- | --- |
| Android app and library modules | Supported. |
| Compose Multiplatform | Supported through an Android target. |
| Desktop and web Compose renderers | Not separate render targets yet. |
| `@PreviewParameter` | Supported for one annotated user parameter. |
| Layout-tree source hints | Experimental and best-effort. |
| Render modes | `robolectric`, `fake`, and `diagnostic-fallback`. Do not judge UI from `fake` or `diagnostic-fallback` screenshots. |
| Android and CMP assets | Wired for real Android captures, including CMP `composeResources` fonts and assets. Fake renderer ignores assets. |
| Android SDK lookup | `ANDROID_HOME`, `ANDROID_SDK_ROOT`, then root `local.properties` `sdk.dir`. Install `platforms;android-35` for exact Robolectric SDK 35 matching. |

## Docs

| Topic | Link |
| --- | --- |
| Detailed setup and agent workflow | [`docs/agent-usage.md`](docs/agent-usage.md) |
| Generated compatibility matrix | [`docs/compatibility-matrix.md`](docs/compatibility-matrix.md) |
| `snapshot.json` schema | [`docs/snapshot-schema.md`](docs/snapshot-schema.md) |
| Release process | [`docs/releasing.md`](docs/releasing.md) |

## License

MIT. See [`LICENSE`](LICENSE).
