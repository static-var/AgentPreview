# AgentPreview

AgentPreview is a Gradle plugin that captures AndroidX Compose `@Preview` functions into files an AI agent can inspect:

```text
<module>/build/agentPreviewSnapshots/<sanitized-preview-id>/<platform>-<viewport>/
  screenshot.png
  snapshot.json
```

Preview ids are sanitized for file paths: `:app:main:LoginPreview` becomes `app-main-LoginPreview`, and viewport folders include the platform, e.g. `android-phone`.

Use it when an agent is editing Compose UI and needs a quick screenshot plus structured preview data without driving the full app.

## Setup

Use the published Gradle Plugin Portal plugin in the target module that owns the previews:

```kotlin
plugins {
    id("dev.staticvar.agentpreview") version "0.1.0"
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

## Basic commands

List previews first:

```bash
./gradlew :app:listComposePreviews
```

Capture previews:

```bash
./gradlew :app:captureComposePreviews
```

Real rendered screenshots crop to detected Compose content by default with 20dp padding. If layout/semantics bounds are ambiguous, AgentPreview keeps the full viewport and records the fallback in `snapshot.json`.

For a focused capture while iterating:

```bash
./gradlew :app:captureComposePreviews \
  -PagentPreview.previewNameFilter=Login \
  -PagentPreview.viewportFilter=phone \
  -PagentPreview.maxCaptures=4
```

Use `-PagentPreview.cropToContent=false` for full-viewport diagnostic captures, or `-PagentPreview.cropPaddingDp=12` to override the default crop padding for one run.

Outputs are written under:

- snapshots: `<module>/build/agentPreviewSnapshots/<sanitized-preview-id>/<platform>-<viewport>/`
- capture report: `<module>/build/agentPreviewReports/capture-report.json`

## More docs

- Detailed setup and agent workflow: [`docs/agent-usage.md`](docs/agent-usage.md)
- Agent workflow skill: [`skills/agentpreview-compose-iteration/SKILL.md`](skills/agentpreview-compose-iteration/SKILL.md)
- `snapshot.json` schema: [`docs/snapshot-schema.md`](docs/snapshot-schema.md)
- Release process: [`docs/releasing.md`](docs/releasing.md)

## Current support and limitations

- Supports Android app/library modules and Compose Multiplatform modules through an Android target.
- Rendering is Android-backed; desktop and web Compose renderers are not separate targets yet.
- `@PreviewParameter` is supported for one annotated user parameter.
- Layout-tree source hints are experimental, best-effort, and sometimes missing.
- Render modes: `robolectric`, `fake`, `diagnostic-fallback`. Do not judge UI from `fake` or `diagnostic-fallback` screenshots.
- Android/CMP assets are wired for real Android captures, including CMP `composeResources` fonts/assets. Fake renderer ignores assets. Android `res/` contents are not fully provided.
- Android SDK lookup: `ANDROID_HOME`, `ANDROID_SDK_ROOT`, then root `local.properties` `sdk.dir`. Install `platforms;android-35` for exact Robolectric SDK 35 matching.

## License

MIT. See [`LICENSE`](LICENSE).
