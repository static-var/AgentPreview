# Preview For Agents

Preview For Agents is a Gradle-first tool for capturing Compose previews into a lean bundle for coding agents:

```text
build/agentPreviewSnapshots/<preview-id>/
  screenshot.png
  snapshot.json
```

## Phase 1 Status

Phase 1 provides the Gradle plugin scaffold, snapshot schema, JSON-index discovery stub, and fake-renderer capture pipeline.

Production preview rendering is not implemented yet. Normal `captureComposePreviews` fails with an explanatory message until a later phase adds a real renderer. For scaffold testing, pass:

```bash
./gradlew :app:captureComposePreviews -PagentPreview.fakeRenderer=true
```

## Plugin Usage

```kotlin
plugins {
    id("dev.staticvar.agentpreview")
}
```

The phase-1 discovery stub reads preview descriptors from:

```text
build/agentPreview/discovered-previews.json
```

Example descriptor:

```json
[
  {
    "id": ":app:commonMain:LoginPreview",
    "name": "Login",
    "group": "Auth",
    "sourceSet": "commonMain",
    "fullyQualifiedFunctionName": "dev.staticvar.LoginPreviewKt.LoginPreview",
    "sourceFile": "LoginPreview.kt",
    "sourceLine": 12,
    "widthDp": 393,
    "heightDp": 852,
    "locale": null,
    "uiMode": null,
    "fontScale": null
  }
]
```

List indexed previews:

```bash
./gradlew :app:listComposePreviews
```

Capture fake phase-1 bundles:

```bash
./gradlew :app:captureComposePreviews -PagentPreview.fakeRenderer=true
```

## License And Code Headers

Preview For Agents is licensed under the MIT License. See `LICENSE`.

Kotlin and Gradle Kotlin DSL files use Spotless with ktlint to apply formatting and license headers. Detekt provides conservative static analysis.

Local quality gates:

```bash
./gradlew spotlessApply
./gradlew spotlessCheck detekt :plugin:test
```

Run `spotlessApply` before committing code changes. CI runs the same `spotlessCheck detekt :plugin:test` verification on pull requests and pushes to `main`.

## Intended Production Scope

Later phases are expected to support existing AndroidX Compose previews that use `androidx.compose.ui.tooling.preview.Preview`, including AndroidX multipreview annotations and custom annotations meta-annotated with AndroidX `@Preview`.

Out of scope for this project are emulator/device-only hierarchy extraction, IDE-only current-preview capture, UiAutomator, and generated Kotlin files in user source sets.
