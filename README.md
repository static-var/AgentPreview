# AgentPreview

AgentPreview is a Gradle plugin that captures AndroidX Compose `@Preview` functions into files an AI agent can inspect:

```text
<module>/build/agentPreviewSnapshots/<preview-id>/<viewport>/
  screenshot.png
  snapshot.json
```

Use it when an agent is editing Compose UI and needs a quick screenshot plus structured preview data without driving the full app.

## Local setup

The plugin is not published yet. Add this checkout as an included build in the target project's `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("/Users/staticvar/Projects/PreviewForAgents")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Apply the plugin to the Android app/library or Compose Multiplatform module that owns the previews:

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

For a focused capture while iterating:

```bash
./gradlew :app:captureComposePreviews \
  -PagentPreview.previewNameFilter=Login \
  -PagentPreview.viewportFilter=phone \
  -PagentPreview.maxCaptures=4
```

Outputs are written under:

- snapshots: `<module>/build/agentPreviewSnapshots/<preview-id>/<viewport>/`
- capture report: `<module>/build/agentPreviewReports/capture-report.json`

## More docs

- Detailed setup and agent workflow: [`docs/agent-usage.md`](docs/agent-usage.md)
- `snapshot.json` schema: [`docs/snapshot-schema.md`](docs/snapshot-schema.md)

## Current support and limitations

- Supports Android app/library modules and Compose Multiplatform modules through an Android target.
- Rendering is Android-backed; desktop and web Compose renderers are not separate targets yet.
- `@PreviewParameter` is supported for one annotated user parameter.
- Layout-tree source hints are experimental, best-effort, and sometimes missing.
- Fake renderer mode (`-PagentPreview.fakeRenderer=true`) is for discovery/debug wiring only; do not judge UI from its placeholder outputs.

## License

MIT. See [`LICENSE`](LICENSE).
