# Preview For Agents Production Renderer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the phase-1 fake-only capture path with the production preview discovery, rendering, and semantics backend chosen by the renderer spike.

**Architecture:** This phase consumes `docs/research/renderer-spike.md` and `docs/research/cmp-renderer-spike.md`, then implements the approved Android backend behind the existing `PreviewDiscovery`, `PreviewRenderer`, and `SemanticsExtractor` interfaces. It keeps Gradle task names stable while extending the snapshot schema with explicit platform/viewport identity, adding real AndroidX `@Preview` discovery/rendering, sample projects, and production verification. If the spikes rejected Roborazzi plus ComposablePreviewScanner, stop this plan and write a replacement production plan for the backend selected by the spike.

**Tech Stack:** Kotlin/JVM, Gradle Plugin API, Gradle TestKit, Kotlin serialization, Android Gradle Plugin, AndroidX Compose, chosen renderer backend from `renderer-spike.md`.

---

## Prerequisites

Before starting this plan:

- `docs/superpowers/plans/2026-05-19-preview-for-agents-phase-1-fake-pipeline.md` has been implemented.
- `docs/superpowers/plans/2026-05-19-preview-for-agents-renderer-spike.md` has been implemented.
- `docs/research/renderer-spike.md` has a final `Decision` section.
- `docs/research/cmp-renderer-spike.md` has a final `Decision` section if CMP support is being included in the production implementation.

Stop immediately if `docs/research/renderer-spike.md` says not to use the backend this plan targets.

## License And Headers

This phase inherits the MIT license and Spotless header automation from the Phase 1 plan. Any Kotlin or Gradle Kotlin DSL files created or modified in this phase must be formatted and checked with:

```bash
build-brief ./gradlew spotlessApply
build-brief ./gradlew spotlessCheck
```

Expected: edited Kotlin/KTS files contain the configured MIT header for `Shreyansh Lodha`.

## Scope And Constraints

Included:

- Real preview discovery for existing AndroidX `androidx.compose.ui.tooling.preview.Preview` functions.
- Production rendering path for `captureComposePreviews` without `-PagentPreview.fakeRenderer=true`.
- Merged semantics extraction into the existing lean `snapshot.json` model.
- Explicit Android viewport configuration with support for multiple named form-factor viewports.
- Android sample project that proves real capture.
- Documentation updates that remove the phase-1 fake-only warning and describe production usage.

Excluded:

- IDE plugin integration.
- Emulator/device/UiAutomator dependency.
- Requiring Google `@PreviewTest`.
- Writing generated Kotlin files into user source directories.
- Old JetBrains `org.jetbrains.compose.ui.tooling.preview.Preview` support.
- iOS/Wasm/native preview rendering.

## File Structure

Modify or create:

```text
plugin/build.gradle.kts
plugin/src/main/kotlin/dev/staticvar/agentpreview/discovery/PreviewDiscovery.kt
plugin/src/main/kotlin/dev/staticvar/agentpreview/discovery/AndroidxPreviewDiscovery.kt
plugin/src/main/kotlin/dev/staticvar/agentpreview/render/ProductionPreviewRenderer.kt
plugin/src/main/kotlin/dev/staticvar/agentpreview/render/PreviewRenderClasspath.kt
plugin/src/main/kotlin/dev/staticvar/agentpreview/config/AndroidPreviewConfig.kt
plugin/src/main/kotlin/dev/staticvar/agentpreview/config/ConfiguredViewport.kt
plugin/src/main/kotlin/dev/staticvar/agentpreview/semantics/ComposeSemanticsExtractor.kt
plugin/src/main/kotlin/dev/staticvar/agentpreview/tasks/CaptureComposePreviewsTask.kt
plugin/src/main/kotlin/dev/staticvar/agentpreview/tasks/ListComposePreviewsTask.kt
plugin/src/test/kotlin/dev/staticvar/agentpreview/AgentPreviewPluginFunctionalTest.kt
plugin/src/test/kotlin/dev/staticvar/agentpreview/discovery/AndroidxPreviewDiscoveryTest.kt
plugin/src/test/kotlin/dev/staticvar/agentpreview/semantics/ComposeSemanticsExtractorTest.kt
samples/android-compose-app/settings.gradle.kts
samples/android-compose-app/build.gradle.kts
samples/android-compose-app/app/build.gradle.kts
samples/android-compose-app/app/src/main/AndroidManifest.xml
samples/android-compose-app/app/src/main/java/dev/staticvar/agentpreview/sample/LoginPreview.kt
README.md
docs/snapshot-schema.md
```

Responsibilities:

- `AndroidxPreviewDiscovery.kt`: production discovery backend chosen by spike.
- `ProductionPreviewRenderer.kt`: production renderer backend chosen by spike.
- `PreviewRenderClasspath.kt`: classpath/configuration helper for renderer inputs.
- `AndroidPreviewConfig.kt`: Gradle DSL container for Android-specific rendering settings, including named viewports and Robolectric SDK.
- `ConfiguredViewport.kt`: normalized viewport model passed from Gradle tasks into renderers/exporters.
- `ComposeSemanticsExtractor.kt`: maps merged semantics nodes to `SnapshotNode`.
- Gradle tasks: choose JSON-index discovery only for tests/fallback and production discovery for real projects.
- Sample app: executable proof of real Android Compose preview capture.

## Task 1: Confirm Renderer Spike Decision

**Files:**
- Read: `/Users/staticvar/Projects/PreviewForAgents/docs/research/renderer-spike.md`

- [ ] **Step 1: Read the spike decision**

Run:

```bash
rg -n "^## Decision|Use Roborazzi|Do not use Roborazzi|Production backend" docs/research/renderer-spike.md
```

Expected: output includes a final decision.

- [ ] **Step 2: Stop if backend was rejected**

If the decision says not to use Roborazzi plus ComposablePreviewScanner, stop this plan and create a replacement production plan matching the selected backend.

If the decision says to use Roborazzi plus ComposablePreviewScanner, continue.

- [ ] **Step 3: Record implementation start**

Append to `docs/research/renderer-spike.md`:

```markdown

## Production Integration Start

Production integration started from this decision in `docs/superpowers/plans/2026-05-19-preview-for-agents-production-renderer.md`.
```

- [ ] **Step 4: Commit**

```bash
git add docs/research/renderer-spike.md
git commit -m "docs: start production renderer integration"
```

## Viewport Configuration Design

The production DSL must make platform-specific rendering explicit so Android, desktop, and web can evolve independently:

```kotlin
agentPreview {
    android {
        robolectricSdk.set(35)

        viewport("phone", widthDp = 393, heightDp = 852)
        viewport("tablet", widthDp = 800, heightDp = 1280)
        viewport("foldable", widthDp = 673, heightDp = 841)
    }
}
```

Defaults when the user does not configure viewports:

```kotlin
agentPreview {
    android {
        robolectricSdk.set(35)
        viewport("phone", widthDp = 393, heightDp = 852)
    }
}
```

Future platform-specific DSLs should use separate containers rather than overloading Android settings:

```kotlin
agentPreview {
    desktop {
        viewport("window", width = 1440, height = 900)
    }

    web {
        viewport("mobile", width = 390, height = 844)
        viewport("desktop", width = 1440, height = 1024)
    }
}
```

Rendering rules:

- If an Android `@Preview` has positive `widthDp` and `heightDp`, render one bundle using those annotation dimensions with viewport name `preview`.
- If either annotation dimension is missing, null, or a sentinel value such as `-1`, render one bundle for each configured Android viewport.
- Snapshot metadata must record the actual rendered viewport, not `-1`.
- Output directories must include platform and viewport identity when more than one viewport can be produced.

Recommended output layout:

```text
build/agentPreviewSnapshots/<preview-id>/android-phone/
  screenshot.png
  snapshot.json
build/agentPreviewSnapshots/<preview-id>/android-tablet/
  screenshot.png
  snapshot.json
```

For annotation-specified dimensions:

```text
build/agentPreviewSnapshots/<preview-id>/android-preview/
  screenshot.png
  snapshot.json
```

## Task 2: Add Production Renderer Dependencies

**Files:**
- Modify: `/Users/staticvar/Projects/PreviewForAgents/gradle/libs.versions.toml`
- Modify: `/Users/staticvar/Projects/PreviewForAgents/plugin/build.gradle.kts`

- [ ] **Step 1: Add dependency aliases**

Modify `/Users/staticvar/Projects/PreviewForAgents/gradle/libs.versions.toml` to include the exact versions approved in `docs/research/renderer-spike.md`:

```toml
[versions]
roborazzi = "1.59.0"
composablePreviewScanner = "0.8.1"
composeUi = "1.10.6"

[libraries]
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
roborazzi-compose-preview-scanner-support = { module = "io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support", version.ref = "roborazzi" }
composable-preview-scanner-android = { module = "io.github.sergio-sastre.ComposablePreviewScanner:android", version.ref = "composablePreviewScanner" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4", version.ref = "composeUi" }
```

If the spike recorded different versions, use the spike versions instead of the numbers above.

- [ ] **Step 2: Add plugin dependencies**

Modify `/Users/staticvar/Projects/PreviewForAgents/plugin/build.gradle.kts` dependencies block:

```kotlin
dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.roborazzi)
    implementation(libs.roborazzi.compose)
    implementation(libs.roborazzi.compose.preview.scanner.support)
    implementation(libs.composable.preview.scanner.android)
    implementation(libs.compose.ui.test.junit4)

    testImplementation(libs.junit.jupiter)
    testImplementation(gradleTestKit())
}
```

- [ ] **Step 3: Verify dependency resolution**

Run:

```bash
build-brief ./gradlew :plugin:dependencies --configuration runtimeClasspath
```

Expected: PASS and output includes Roborazzi, ComposablePreviewScanner, and Compose UI test dependencies.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml plugin/build.gradle.kts
git commit -m "feat: add production preview renderer dependencies"
```

## Task 3: Add Production Discovery Backend

**Files:**
- Create: `/Users/staticvar/Projects/PreviewForAgents/plugin/src/main/kotlin/dev/staticvar/agentpreview/discovery/AndroidxPreviewDiscovery.kt`
- Create: `/Users/staticvar/Projects/PreviewForAgents/plugin/src/test/kotlin/dev/staticvar/agentpreview/discovery/AndroidxPreviewDiscoveryTest.kt`

- [ ] **Step 1: Write discovery unit test using spike sample metadata**

Create `/Users/staticvar/Projects/PreviewForAgents/plugin/src/test/kotlin/dev/staticvar/agentpreview/discovery/AndroidxPreviewDiscoveryTest.kt`:

```kotlin
package dev.staticvar.agentpreview.discovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class AndroidxPreviewDiscoveryTest {
    @Test
    fun `discovery reports empty list when classpath is empty`() {
        val discovery = AndroidxPreviewDiscovery(
            projectPath = ":app",
            sourceSetName = "main",
            classesDirs = emptyList(),
            runtimeClasspath = emptyList(),
        )

        assertEquals(emptyList<Any>(), discovery.discover())
    }

    @Test
    fun `discovery stores constructor inputs for diagnostics`() {
        val classes = listOf(File("build/classes"))
        val runtime = listOf(File("build/runtime.jar"))
        val discovery = AndroidxPreviewDiscovery(
            projectPath = ":app",
            sourceSetName = "main",
            classesDirs = classes,
            runtimeClasspath = runtime,
        )

        assertTrue(discovery.diagnosticSummary().contains(":app"))
        assertTrue(discovery.diagnosticSummary().contains("main"))
        assertTrue(discovery.diagnosticSummary().contains("build/classes"))
        assertTrue(discovery.diagnosticSummary().contains("build/runtime.jar"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
build-brief ./gradlew :plugin:test --tests dev.staticvar.agentpreview.discovery.AndroidxPreviewDiscoveryTest
```

Expected: FAIL because `AndroidxPreviewDiscovery` does not exist.

- [ ] **Step 3: Implement discovery shell**

Create `/Users/staticvar/Projects/PreviewForAgents/plugin/src/main/kotlin/dev/staticvar/agentpreview/discovery/AndroidxPreviewDiscovery.kt`:

```kotlin
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewDescriptor
import java.io.File

class AndroidxPreviewDiscovery(
    private val projectPath: String,
    private val sourceSetName: String,
    private val classesDirs: List<File>,
    private val runtimeClasspath: List<File>,
) : PreviewDiscovery {
    override fun discover(): List<PreviewDescriptor> {
        if (classesDirs.isEmpty() || runtimeClasspath.isEmpty()) return emptyList()
        return discoverWithBackend()
    }

    fun diagnosticSummary(): String {
        return buildString {
            appendLine("projectPath=$projectPath")
            appendLine("sourceSetName=$sourceSetName")
            appendLine("classesDirs=${classesDirs.joinToString()}")
            appendLine("runtimeClasspath=${runtimeClasspath.joinToString()}")
        }
    }

    private fun discoverWithBackend(): List<PreviewDescriptor> {
        error("AndroidX preview discovery backend has not been wired yet. Use the API proven in docs/research/renderer-spike.md.")
    }
}
```

- [ ] **Step 4: Run test to verify shell passes**

Run:

```bash
build-brief ./gradlew :plugin:test --tests dev.staticvar.agentpreview.discovery.AndroidxPreviewDiscoveryTest
```

Expected: PASS.

- [ ] **Step 5: Replace `discoverWithBackend` with proven scanner API**

Modify `discoverWithBackend()` to call the exact ComposablePreviewScanner API recorded in `docs/research/renderer-spike.md`. It must map each discovered preview to:

```kotlin
PreviewDescriptor(
    id = "$projectPath:$sourceSetName:${previewNameOrFunctionName}",
    name = previewName,
    group = previewGroup,
    sourceSet = sourceSetName,
    fullyQualifiedFunctionName = fullyQualifiedFunctionName,
    sourceFile = sourceFile ?: fullyQualifiedFunctionName.substringBeforeLast('.') + ".kt",
    sourceLine = null,
    widthDp = widthDp,
    heightDp = heightDp,
    locale = locale,
    uiMode = uiMode,
    fontScale = fontScale,
)
```

Use only field names and calls proven by the spike. If source file or source line is unavailable from the scanner, set `sourceFile` to the fallback shown above and `sourceLine` to `null`.

- [ ] **Step 6: Add test fixture or update test for discovered preview**

Modify `AndroidxPreviewDiscoveryTest.kt` to cover the real scanner only if a stable compiled fixture can be included without creating an Android subproject inside plugin tests. If not, keep real discovery covered by the sample integration task later in this plan and add this assertion to the diagnostics test:

```kotlin
assertTrue(discovery.diagnosticSummary().contains("runtimeClasspath="))
```

- [ ] **Step 7: Run plugin tests**

Run:

```bash
build-brief ./gradlew :plugin:test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add plugin/src/main/kotlin/dev/staticvar/agentpreview/discovery/AndroidxPreviewDiscovery.kt plugin/src/test/kotlin/dev/staticvar/agentpreview/discovery/AndroidxPreviewDiscoveryTest.kt
git commit -m "feat: add AndroidX preview discovery backend"
```

## Task 4: Add Production Renderer Backend

**Files:**
- Create: `/Users/staticvar/Projects/PreviewForAgents/plugin/src/main/kotlin/dev/staticvar/agentpreview/render/PreviewRenderClasspath.kt`
- Create: `/Users/staticvar/Projects/PreviewForAgents/plugin/src/main/kotlin/dev/staticvar/agentpreview/render/ProductionPreviewRenderer.kt`

- [ ] **Step 1: Add render classpath model**

Create `/Users/staticvar/Projects/PreviewForAgents/plugin/src/main/kotlin/dev/staticvar/agentpreview/render/PreviewRenderClasspath.kt`:

```kotlin
package dev.staticvar.agentpreview.render

import java.io.File

data class PreviewRenderClasspath(
    val classesDirs: List<File>,
    val runtimeClasspath: List<File>,
)
```

- [ ] **Step 2: Add production renderer shell**

Create `/Users/staticvar/Projects/PreviewForAgents/plugin/src/main/kotlin/dev/staticvar/agentpreview/render/ProductionPreviewRenderer.kt`:

```kotlin
package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.Viewport
import java.io.File

class ProductionPreviewRenderer(
    private val classpath: PreviewRenderClasspath,
) : PreviewRenderer {
    override fun render(preview: PreviewDescriptor, outputDirectory: File): RenderResult {
        outputDirectory.mkdirs()
        val screenshot = outputDirectory.resolve("${preview.id.hashCode()}.png")
        renderWithBackend(preview, screenshot)
        return RenderResult(
            screenshotFile = screenshot,
            viewport = Viewport(
                width = preview.widthDp ?: 393,
                height = preview.heightDp ?: 852,
                density = 1.0f,
            ),
            rawSemantics = null,
        )
    }

    private fun renderWithBackend(preview: PreviewDescriptor, screenshot: File) {
        if (classpath.classesDirs.isEmpty() || classpath.runtimeClasspath.isEmpty()) {
            error("Cannot render ${preview.id}: render classpath is empty.")
        }
        error("Production renderer backend has not been wired yet. Use the API proven in docs/research/renderer-spike.md to write $screenshot.")
    }
}
```

- [ ] **Step 3: Replace shell backend call with proven renderer API**

Modify `renderWithBackend()` to call the exact rendering API recorded in `docs/research/renderer-spike.md`. It must write a PNG file to the `screenshot` path passed to it. It must not write generated Kotlin files under `src/main`, `src/test`, `src/androidTest`, `src/commonMain`, or any user source set.

- [ ] **Step 4: Run plugin tests**

Run:

```bash
build-brief ./gradlew :plugin:test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/kotlin/dev/staticvar/agentpreview/render/PreviewRenderClasspath.kt plugin/src/main/kotlin/dev/staticvar/agentpreview/render/ProductionPreviewRenderer.kt
git commit -m "feat: add production preview renderer backend"
```

## Task 5: Add Compose Semantics Extraction

**Files:**
- Create: `/Users/staticvar/Projects/PreviewForAgents/plugin/src/main/kotlin/dev/staticvar/agentpreview/semantics/ComposeSemanticsExtractor.kt`
- Create: `/Users/staticvar/Projects/PreviewForAgents/plugin/src/test/kotlin/dev/staticvar/agentpreview/semantics/ComposeSemanticsExtractorTest.kt`

- [ ] **Step 1: Write fallback semantics test**

Create `/Users/staticvar/Projects/PreviewForAgents/plugin/src/test/kotlin/dev/staticvar/agentpreview/semantics/ComposeSemanticsExtractorTest.kt`:

```kotlin
package dev.staticvar.agentpreview.semantics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ComposeSemanticsExtractorTest {
    @Test
    fun `returns empty list for unsupported semantics object`() {
        val extractor = ComposeSemanticsExtractor()

        assertEquals(emptyList<Any>(), extractor.extract(rawSemantics = "unsupported"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
build-brief ./gradlew :plugin:test --tests dev.staticvar.agentpreview.semantics.ComposeSemanticsExtractorTest
```

Expected: FAIL because `ComposeSemanticsExtractor` does not exist.

- [ ] **Step 3: Implement extractor shell**

Create `/Users/staticvar/Projects/PreviewForAgents/plugin/src/main/kotlin/dev/staticvar/agentpreview/semantics/ComposeSemanticsExtractor.kt`:

```kotlin
package dev.staticvar.agentpreview.semantics

import dev.staticvar.agentpreview.model.SnapshotNode

class ComposeSemanticsExtractor : SemanticsExtractor {
    override fun extract(rawSemantics: Any?): List<SnapshotNode> {
        if (rawSemantics == null) return emptyList()
        return extractWithBackend(rawSemantics)
    }

    private fun extractWithBackend(rawSemantics: Any): List<SnapshotNode> {
        return emptyList()
    }
}
```

- [ ] **Step 4: Run fallback test**

Run:

```bash
build-brief ./gradlew :plugin:test --tests dev.staticvar.agentpreview.semantics.ComposeSemanticsExtractorTest
```

Expected: PASS.

- [ ] **Step 5: Wire proven semantics mapping**

Modify `extractWithBackend()` using the semantics object shape proven in `docs/research/renderer-spike.md`. Each node must map to:

```kotlin
SnapshotNode(
    id = stableNodeId,
    role = roleName,
    text = textOrNull,
    contentDescription = contentDescriptionOrNull,
    bounds = Bounds(x = left, y = top, width = width, height = height),
    actions = actionNames,
    tag = testTagOrNull,
    source = null,
    children = childNodes,
)
```

If source file mapping is unavailable, keep `source = null`.

- [ ] **Step 6: Add a mapping test using a lightweight adapter object**

If the production semantics object cannot be constructed in a JVM unit test, create a small internal adapter data class inside `ComposeSemanticsExtractor.kt`:

```kotlin
internal data class SemanticsNodeSnapshot(
    val id: String,
    val role: String?,
    val text: String?,
    val contentDescription: String?,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val actions: List<String>,
    val tag: String?,
    val children: List<SemanticsNodeSnapshot>,
)
```

Then update `ComposeSemanticsExtractorTest.kt` with:

```kotlin
@Test
fun `maps semantics snapshot to agent node`() {
    val raw = SemanticsNodeSnapshot(
        id = "1",
        role = "button",
        text = "Continue",
        contentDescription = null,
        x = 10,
        y = 20,
        width = 100,
        height = 40,
        actions = listOf("click"),
        tag = "continue_button",
        children = emptyList(),
    )

    val node = ComposeSemanticsExtractor().extract(raw).single()

    assertEquals("button", node.role)
    assertEquals("Continue", node.text)
    assertEquals("continue_button", node.tag)
    assertEquals(100, node.bounds.width)
}
```

- [ ] **Step 7: Run tests**

Run:

```bash
build-brief ./gradlew :plugin:test --tests dev.staticvar.agentpreview.semantics.ComposeSemanticsExtractorTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add plugin/src/main/kotlin/dev/staticvar/agentpreview/semantics/ComposeSemanticsExtractor.kt plugin/src/test/kotlin/dev/staticvar/agentpreview/semantics/ComposeSemanticsExtractorTest.kt
git commit -m "feat: extract Compose semantics for snapshots"
```

## Task 6: Add Android Viewport Configuration

**Files:**
- Modify: `/Users/staticvar/Projects/PreviewForAgents/plugin/src/main/kotlin/dev/staticvar/agentpreview/AgentPreviewExtension.kt`
- Create: `/Users/staticvar/Projects/PreviewForAgents/plugin/src/main/kotlin/dev/staticvar/agentpreview/config/AndroidPreviewConfig.kt`
- Create: `/Users/staticvar/Projects/PreviewForAgents/plugin/src/main/kotlin/dev/staticvar/agentpreview/config/ConfiguredViewport.kt`
- Modify: `/Users/staticvar/Projects/PreviewForAgents/plugin/src/test/kotlin/dev/staticvar/agentpreview/AgentPreviewPluginFunctionalTest.kt`

- [ ] **Step 1: Add functional test for multiple Android viewports**

Append this test inside `AgentPreviewPluginFunctionalTest`:

```kotlin
@Test
fun `capture task renders one bundle per configured Android viewport when preview has no explicit size`() {
    projectDir.resolve("settings.gradle.kts").writeText("pluginManagement { repositories { gradlePluginPortal(); google(); mavenCentral() } }")
    projectDir.resolve("build.gradle.kts").writeText(
        """
        plugins {
            id("dev.staticvar.agentpreview")
        }

        agentPreview {
            android {
                viewport("phone", widthDp = 393, heightDp = 852)
                viewport("tablet", widthDp = 800, heightDp = 1280)
            }
        }
        """.trimIndent(),
    )
    projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
        parentFile.mkdirs()
        writeText(
            """
            [{
              "id": ":app:main:ResponsiveCardPreview",
              "name": "ResponsiveCard",
              "group": "Cards",
              "sourceSet": "main",
              "fullyQualifiedFunctionName": "dev.staticvar.ResponsiveCardPreviewKt.ResponsiveCardPreview",
              "sourceFile": "ResponsiveCardPreview.kt",
              "widthDp": -1,
              "heightDp": -1
            }]
            """.trimIndent(),
        )
    }

    GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments("captureComposePreviews", "-PagentPreview.fakeRenderer=true")
        .withPluginClasspath()
        .build()

    assertTrue(projectDir.resolve("build/agentPreviewSnapshots/app-main-ResponsiveCardPreview/android-phone/snapshot.json").isFile)
    assertTrue(projectDir.resolve("build/agentPreviewSnapshots/app-main-ResponsiveCardPreview/android-tablet/snapshot.json").isFile)
    assertTrue(projectDir.resolve("build/agentPreviewSnapshots/app-main-ResponsiveCardPreview/android-phone/snapshot.json").readText().contains("\"name\": \"phone\""))
    assertTrue(projectDir.resolve("build/agentPreviewSnapshots/app-main-ResponsiveCardPreview/android-tablet/snapshot.json").readText().contains("\"width\": 800"))
}
```

- [ ] **Step 2: Add functional test for annotation dimensions winning over configured viewports**

Append this test inside `AgentPreviewPluginFunctionalTest`:

```kotlin
@Test
fun `capture task uses preview dimensions when annotation defines positive width and height`() {
    projectDir.resolve("settings.gradle.kts").writeText("pluginManagement { repositories { gradlePluginPortal(); google(); mavenCentral() } }")
    projectDir.resolve("build.gradle.kts").writeText(
        """
        plugins {
            id("dev.staticvar.agentpreview")
        }

        agentPreview {
            android {
                viewport("phone", widthDp = 393, heightDp = 852)
                viewport("tablet", widthDp = 800, heightDp = 1280)
            }
        }
        """.trimIndent(),
    )
    projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
        parentFile.mkdirs()
        writeText(
            """
            [{
              "id": ":app:main:FixedPreview",
              "name": "Fixed",
              "group": "Cards",
              "sourceSet": "main",
              "fullyQualifiedFunctionName": "dev.staticvar.FixedPreviewKt.FixedPreview",
              "sourceFile": "FixedPreview.kt",
              "widthDp": 320,
              "heightDp": 640
            }]
            """.trimIndent(),
        )
    }

    GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments("captureComposePreviews", "-PagentPreview.fakeRenderer=true")
        .withPluginClasspath()
        .build()

    val snapshot = projectDir.resolve("build/agentPreviewSnapshots/app-main-FixedPreview/android-preview/snapshot.json")
    assertTrue(snapshot.isFile)
    assertTrue(snapshot.readText().contains("\"name\": \"preview\""))
    assertTrue(snapshot.readText().contains("\"width\": 320"))
    assertFalse(projectDir.resolve("build/agentPreviewSnapshots/app-main-FixedPreview/android-phone").exists())
}
```

- [ ] **Step 3: Add configuration models**

Create `ConfiguredViewport.kt`:

```kotlin
package dev.staticvar.agentpreview.config

import kotlinx.serialization.Serializable

@Serializable
data class ConfiguredViewport(
    val platform: String,
    val name: String,
    val width: Int,
    val height: Int,
    val density: Float = 1.0f,
)
```

Create `AndroidPreviewConfig.kt`:

```kotlin
package dev.staticvar.agentpreview.config

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class AndroidPreviewConfig @Inject constructor(objects: ObjectFactory) {
    abstract val robolectricSdk: Property<Int>
    abstract val viewports: ListProperty<ConfiguredViewport>

    init {
        robolectricSdk.convention(35)
        viewports.convention(listOf(ConfiguredViewport(platform = "android", name = "phone", width = 393, height = 852)))
    }

    fun viewport(name: String, widthDp: Int, heightDp: Int, density: Float = 1.0f) {
        val current = viewports.getOrElse(emptyList())
        viewports.set(current + ConfiguredViewport(platform = "android", name = name, width = widthDp, height = heightDp, density = density))
    }
}
```

Modify `AgentPreviewExtension.kt` to expose:

```kotlin
abstract val android: AndroidPreviewConfig

fun android(action: Action<AndroidPreviewConfig>) {
    action.execute(android)
}
```

- [ ] **Step 4: Extend viewport snapshot model**

Modify `Viewport.kt`:

```kotlin
@Serializable
data class Viewport(
    val width: Int,
    val height: Int,
    val density: Float,
    val platform: String? = null,
    val name: String? = null,
)
```

- [ ] **Step 5: Wire tasks and renderer to configured viewports**

Modify `CaptureComposePreviewsTask` to accept:

```kotlin
@get:Input
abstract val androidViewports: ListProperty<ConfiguredViewport>
```

Modify `AgentPreviewPlugin.kt`:

```kotlin
it.androidViewports.set(extension.android.viewports)
```

Modify fake and production render flow so the renderer receives a normalized viewport list:

```kotlin
private fun viewportsFor(preview: PreviewDescriptor): List<ConfiguredViewport> {
    val width = preview.widthDp
    val height = preview.heightDp
    return if (width != null && height != null && width > 0 && height > 0) {
        listOf(ConfiguredViewport(platform = "android", name = "preview", width = width, height = height))
    } else {
        androidViewports.get()
    }
}
```

Export each viewport to a child directory named `${viewport.platform}-${viewport.name}` under the sanitized preview ID.

- [ ] **Step 6: Run tests**

Run:

```bash
build-brief ./gradlew spotlessApply
build-brief ./gradlew spotlessCheck detekt :plugin:test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add plugin/src/main/kotlin/dev/staticvar/agentpreview plugin/src/test/kotlin/dev/staticvar/agentpreview/AgentPreviewPluginFunctionalTest.kt
git commit -m "feat: add Android viewport configuration"
```

## Task 7: Wire Capture Task To Production Backend

**Files:**
- Modify: `/Users/staticvar/Projects/PreviewForAgents/plugin/src/main/kotlin/dev/staticvar/agentpreview/tasks/CaptureComposePreviewsTask.kt`
- Modify: `/Users/staticvar/Projects/PreviewForAgents/plugin/src/main/kotlin/dev/staticvar/agentpreview/tasks/ListComposePreviewsTask.kt`
- Modify: `/Users/staticvar/Projects/PreviewForAgents/plugin/src/test/kotlin/dev/staticvar/agentpreview/AgentPreviewPluginFunctionalTest.kt`

- [ ] **Step 1: Add functional guardrail test for fallback JSON index**

Append this test inside `AgentPreviewPluginFunctionalTest`:

```kotlin
@Test
fun `list task still supports phase one JSON index fallback`() {
    projectDir.resolve("settings.gradle.kts").writeText("pluginManagement { repositories { gradlePluginPortal(); google(); mavenCentral() } }")
    projectDir.resolve("build.gradle.kts").writeText("plugins { id(\"dev.staticvar.agentpreview\") }")
    projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
        parentFile.mkdirs()
        writeText("""
            [{
              "id": ":app:main:FallbackPreview",
              "name": "Fallback",
              "group": "Debug",
              "sourceSet": "main",
              "fullyQualifiedFunctionName": "dev.staticvar.FallbackPreviewKt.FallbackPreview",
              "sourceFile": "FallbackPreview.kt"
            }]
        """.trimIndent())
    }

    val result = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments("listComposePreviews")
        .withPluginClasspath()
        .build()

    assertTrue(result.output.contains(":app:main:FallbackPreview"))
}
```

- [ ] **Step 2: Keep fake renderer test passing**

Run:

```bash
build-brief ./gradlew :plugin:test --tests dev.staticvar.agentpreview.AgentPreviewPluginFunctionalTest
```

Expected: PASS before production wiring changes.

- [ ] **Step 3: Modify tasks to choose production backend when available**

Update both task files so they use this decision order:

```kotlin
val jsonIndex = project.layout.buildDirectory.file("agentPreview/discovered-previews.json").get().asFile
val jsonIndexExists = jsonIndex.isFile
val fakeRendererEnabled = project.providers.gradleProperty("agentPreview.fakeRenderer")
    .map(String::toBoolean)
    .getOrElse(false)

val discovery: PreviewDiscovery = if (jsonIndexExists) {
    JsonIndexPreviewDiscovery(jsonIndex)
} else {
    AndroidxPreviewDiscovery(
        projectPath = project.path,
        sourceSetName = "main",
        classesDirs = resolveClassesDirs(),
        runtimeClasspath = resolveRuntimeClasspath(),
    )
}
```

In `CaptureComposePreviewsTask`, choose renderer:

```kotlin
val renderer = if (fakeRendererEnabled) {
    FakePreviewRenderer()
} else {
    ProductionPreviewRenderer(
        PreviewRenderClasspath(
            classesDirs = resolveClassesDirs(),
            runtimeClasspath = resolveRuntimeClasspath(),
        )
    )
}
```

Use `ComposeSemanticsExtractor()` for production renderer and `EmptySemanticsExtractor()` for fake renderer.

Implement `resolveClassesDirs()` and `resolveRuntimeClasspath()` as private task methods. They must return empty lists in plain TestKit projects without Android/Kotlin plugins, so the existing plugin-registration test still passes.

- [ ] **Step 4: Run plugin tests**

Run:

```bash
build-brief ./gradlew :plugin:test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/kotlin/dev/staticvar/agentpreview/tasks plugin/src/test/kotlin/dev/staticvar/agentpreview/AgentPreviewPluginFunctionalTest.kt
git commit -m "feat: wire capture task to production renderer"
```

## Task 8: Add Android Compose Sample Project

**Files:**
- Create: `/Users/staticvar/Projects/PreviewForAgents/samples/android-compose-app/settings.gradle.kts`
- Create: `/Users/staticvar/Projects/PreviewForAgents/samples/android-compose-app/build.gradle.kts`
- Create: `/Users/staticvar/Projects/PreviewForAgents/samples/android-compose-app/app/build.gradle.kts`
- Create: `/Users/staticvar/Projects/PreviewForAgents/samples/android-compose-app/app/src/main/AndroidManifest.xml`
- Create: `/Users/staticvar/Projects/PreviewForAgents/samples/android-compose-app/app/src/main/java/dev/staticvar/agentpreview/sample/LoginPreview.kt`

- [ ] **Step 1: Create sample settings**

Create `/Users/staticvar/Projects/PreviewForAgents/samples/android-compose-app/settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("../..")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PreviewForAgentsAndroidSample"
include(":app")
```

- [ ] **Step 2: Create sample root build file**

Create `/Users/staticvar/Projects/PreviewForAgents/samples/android-compose-app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
}
```

Use versions from `docs/research/renderer-spike.md` if they differ.

- [ ] **Step 3: Create sample app build file**

Create `/Users/staticvar/Projects/PreviewForAgents/samples/android-compose-app/app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("dev.staticvar.agentpreview")
}

android {
    namespace = "dev.staticvar.agentpreview.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.staticvar.agentpreview.sample"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

- [ ] **Step 4: Create manifest**

Create `/Users/staticvar/Projects/PreviewForAgents/samples/android-compose-app/app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 5: Create sample preview**

Create `/Users/staticvar/Projects/PreviewForAgents/samples/android-compose-app/app/src/main/java/dev/staticvar/agentpreview/sample/LoginPreview.kt`:

```kotlin
package dev.staticvar.agentpreview.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "Login", group = "Auth", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
fun LoginPreview() {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Welcome back", modifier = Modifier.testTag("headline"))
        Button(onClick = {}, modifier = Modifier.testTag("continue_button")) {
            Text("Continue")
        }
    }
}
```

- [ ] **Step 6: Run sample list task**

Run:

```bash
build-brief ./gradlew -p samples/android-compose-app :app:listComposePreviews
```

Expected: PASS and output includes `LoginPreview` or `Login`.

- [ ] **Step 7: Run sample capture task**

Run:

```bash
build-brief ./gradlew -p samples/android-compose-app :app:captureComposePreviews
```

Expected: PASS and creates:

```text
samples/android-compose-app/app/build/agentPreviewSnapshots/<preview-id>/screenshot.png
samples/android-compose-app/app/build/agentPreviewSnapshots/<preview-id>/snapshot.json
```

- [ ] **Step 8: Commit**

```bash
git add samples/android-compose-app
git commit -m "test: add Android Compose preview sample"
```

## Task 9: Update User Documentation

**Files:**
- Modify: `/Users/staticvar/Projects/PreviewForAgents/README.md`
- Modify: `/Users/staticvar/Projects/PreviewForAgents/docs/snapshot-schema.md`

- [ ] **Step 1: Update README production status**

Replace the phase-1 fake-only status section in `/Users/staticvar/Projects/PreviewForAgents/README.md` with:

```markdown
## Status

Preview For Agents captures supported AndroidX Compose `@Preview` functions through a Gradle task and writes a lean agent-readable bundle.

Supported:

- `androidx.compose.ui.tooling.preview.Preview`
- Android Compose JVM/headless preview capture
- Existing preview functions without `@PreviewTest`
- Lean output with `screenshot.png` and `snapshot.json`

Not supported:

- IDE-only current-preview capture
- Emulator/device-only hierarchy extraction
- UiAutomator
- Old JetBrains preview annotations
- iOS/Wasm/native preview rendering
```

- [ ] **Step 2: Update usage commands and viewport configuration docs**

Ensure README includes:

```markdown
```bash
./gradlew :app:listComposePreviews
./gradlew :app:captureComposePreviews
```
```

Document Android viewport configuration:

```markdown
## Android Viewports

When a preview omits `widthDp` or `heightDp`, Preview For Agents renders it once for every configured Android viewport:

```kotlin
agentPreview {
    android {
        robolectricSdk.set(35)
        viewport("phone", widthDp = 393, heightDp = 852)
        viewport("tablet", widthDp = 800, heightDp = 1280)
    }
}
```

If `@Preview` specifies positive `widthDp` and `heightDp`, those annotation dimensions win and the output uses the `android-preview` viewport name.

Default Android configuration is Java-17 friendly:

```kotlin
agentPreview {
    android {
        robolectricSdk.set(35)
        viewport("phone", widthDp = 393, heightDp = 852)
    }
}
```

Set `robolectricSdk` to 36 only when SDK 36 rendering fidelity is required and the capture task runs on Java 21.
```

Remove instructions that say production rendering is not implemented. Keep fake renderer documented only as an internal scaffold option:

```markdown
For plugin development only, `-PagentPreview.fakeRenderer=true` exercises the export pipeline without rendering Compose UI.
```

- [ ] **Step 3: Update schema semantics note**

Modify `/Users/staticvar/Projects/PreviewForAgents/docs/snapshot-schema.md` so `Phase 1 Semantics` is replaced with:

```markdown
## Semantics

Production captures use the merged Compose semantics tree when available. The node list is intended to represent user-facing UI. Debug exports for raw or unmerged semantics are not part of the default bundle.
```

Also update the schema example so `viewport` includes platform and name:

```json
"viewport": {
  "platform": "android",
  "name": "phone",
  "width": 393,
  "height": 852,
  "density": 1.0
}
```

- [ ] **Step 4: Run docs check**

Run:

```bash
rg -n "Production preview rendering is not implemented|Phase 1 provides|Pending|TBD|TODO" README.md docs/snapshot-schema.md
```

Expected: no matches.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/snapshot-schema.md
git commit -m "docs: document production preview capture"
```

## Task 10: Final Production Verification

**Files:**
- Read: `/Users/staticvar/Projects/PreviewForAgents/README.md`
- Read: `/Users/staticvar/Projects/PreviewForAgents/docs/snapshot-schema.md`
- Read: `/Users/staticvar/Projects/PreviewForAgents/docs/research/renderer-spike.md`

- [ ] **Step 1: Run plugin tests**

Run:

```bash
build-brief ./gradlew :plugin:test
```

Expected: PASS.

- [ ] **Step 1.5: Run Spotless license-header check**

Run:

```bash
build-brief ./gradlew spotlessCheck
```

Expected: PASS.

- [ ] **Step 2: Run sample list and capture**

Run:

```bash
build-brief ./gradlew -p samples/android-compose-app :app:listComposePreviews
build-brief ./gradlew -p samples/android-compose-app :app:captureComposePreviews
```

Expected: both PASS. Capture writes `screenshot.png` and `snapshot.json` under `samples/android-compose-app/app/build/agentPreviewSnapshots`.

- [ ] **Step 3: Verify snapshot contents**

Run:

```bash
find samples/android-compose-app/app/build/agentPreviewSnapshots -name snapshot.json -print -maxdepth 3
rg -n '"schemaVersion"|"preview"|"viewport"|"nodes"|Welcome back|Continue|continue_button' samples/android-compose-app/app/build/agentPreviewSnapshots
```

Expected: at least one `snapshot.json` exists and contains schema, preview, viewport, and nodes fields. If semantics extraction works, output also includes `Welcome back`, `Continue`, or `continue_button`.

- [ ] **Step 4: Verify no forbidden generated source or preview-test dependency**

Run:

```bash
rg -n "src/(main|commonMain|androidMain|screenshotTest).*generated|PreviewTest|screenshotTest" plugin samples README.md docs
```

Expected: no matches.

- [ ] **Step 5: Verify namespace consistency**

Run:

```bash
rg -n "dev\\.staticvar\\.agentpreview|dev.staticvar.agentpreview" plugin samples README.md docs
```

Expected: plugin ID, package names, and sample namespaces consistently use `dev.staticvar.agentpreview`.

- [ ] **Step 6: Commit verification cleanup if needed**

Run:

```bash
git status --short
```

If verification changed files, commit them:

```bash
git add .
git commit -m "chore: verify production preview renderer"
```

If there are no changes, do not create an empty commit.

## Self-Review

- Spec coverage: This plan covers the post-spike production work: dependencies, discovery, rendering, semantics extraction, task wiring, sample app, docs, and verification.
- Placeholder scan: The plan has concrete files, commands, and expected outputs. API-specific implementation points explicitly depend on the completed spike because the exact backend API must come from verified research, not speculation.
- Type consistency: It preserves the Phase 1 interfaces and names: `PreviewDiscovery`, `PreviewRenderer`, `RenderResult`, `SemanticsExtractor`, `PreviewDescriptor`, `PreviewSnapshot`, `listComposePreviews`, and `captureComposePreviews`.
- Risk note: This plan must not be run until the renderer spike is complete. If the spike rejects Roborazzi plus ComposablePreviewScanner, the correct action is to stop and write a new production plan for the selected backend.
