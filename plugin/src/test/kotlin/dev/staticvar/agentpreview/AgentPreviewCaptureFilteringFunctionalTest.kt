/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AgentPreviewCaptureFilteringFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `capture limits preview parameter values from DSL max`() {
        writeSettings()
        writeBuildFile(
            """
            agentPreview {
                previewClassesDirs.from(files("${testClassesDir().invariantSeparatorsPath}"))
                maxPreviewParameterValues.set(1)
            }
            """.trimIndent(),
        )

        val result = runCapture("-PagentPreview.fakeRenderer=true")

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureComposePreviews")?.outcome)
        assertTrue(result.output.contains("parameterizedPreview:previewParam-0"), result.output)
        assertFalse(result.output.contains("parameterizedPreview:previewParam-1"), result.output)
    }

    @Test
    fun `CLI max preview parameter values overrides DSL max`() {
        writeSettings()
        writeBuildFile(
            """
            agentPreview {
                previewClassesDirs.from(files("${testClassesDir().invariantSeparatorsPath}"))
                maxPreviewParameterValues.set(2)
            }
            """.trimIndent(),
        )

        val result = runCapture("-PagentPreview.fakeRenderer=true", "-PagentPreview.maxPreviewParameterValues=1")

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureComposePreviews")?.outcome)
        assertTrue(result.output.contains("parameterizedPreview:previewParam-0"), result.output)
        assertFalse(result.output.contains("parameterizedPreview:previewParam-1"), result.output)
    }

    @Test
    fun `CLI preview name filter matches base and expanded preview parameter ids`() {
        writeSettings()
        writeBuildFile(
            """
            agentPreview {
                previewClassesDirs.from(files("${testClassesDir().invariantSeparatorsPath}"))
            }
            """.trimIndent(),
        )

        val listedParentId = listedParameterizedPreviewId()
        val baseResult = runCapture("-PagentPreview.fakeRenderer=true", "-PagentPreview.previewNameFilter=$listedParentId")

        assertTrue(baseResult.output.contains("$listedParentId:previewParam-0"), baseResult.output)
        assertTrue(baseResult.output.contains("$listedParentId:previewParam-1"), baseResult.output)

        val expandedId = "$listedParentId:previewParam-1"
        val expandedResult = runCapture("-PagentPreview.fakeRenderer=true", "-PagentPreview.previewNameFilter=$expandedId")

        assertFalse(expandedResult.output.contains("$listedParentId:previewParam-0"), expandedResult.output)
        assertTrue(expandedResult.output.contains(expandedId), expandedResult.output)
    }

    @Test
    fun `CLI preview name filter matches shorthand preview parameter id`() {
        writeSettings()
        writeBuildFile(
            """
            agentPreview {
                previewClassesDirs.from(files("${testClassesDir().invariantSeparatorsPath}"))
            }
            """.trimIndent(),
        )

        val listedParentId = listedParameterizedPreviewId()
        val result = runCapture("-PagentPreview.fakeRenderer=true", "-PagentPreview.previewNameFilter=previewParam-1")

        assertFalse(result.output.contains("$listedParentId:previewParam-0"), result.output)
        assertTrue(result.output.contains("$listedParentId:previewParam-1"), result.output)
    }

    @Test
    fun `list preview name filter matches shorthand preview parameter id`() {
        writeSettings()
        writeBuildFile(
            """
            agentPreview {
                previewClassesDirs.from(files("${testClassesDir().invariantSeparatorsPath}"))
            }
            """.trimIndent(),
        )

        val result = runList("-PagentPreview.previewNameFilter=previewParam-1")

        assertTrue(result.output.contains("parameterizedPreview:previewParam-1  Parameterized"), result.output)
        assertTrue(result.output.contains("capture ids append :previewParam-N"), result.output)
        assertFalse(result.output.contains("No Compose previews discovered."), result.output)
    }

    @Test
    fun `list preview name filter matches expanded preview parameter id`() {
        writeSettings()
        writeBuildFile(
            """
            agentPreview {
                previewClassesDirs.from(files("${testClassesDir().invariantSeparatorsPath}"))
            }
            """.trimIndent(),
        )

        val listedParentId = listedParameterizedPreviewId()
        val result = runList("-PagentPreview.previewNameFilter=$listedParentId:previewParam-1")

        assertTrue(result.output.contains("$listedParentId:previewParam-1  Parameterized"), result.output)
        assertTrue(result.output.contains("capture ids append :previewParam-N"), result.output)
        assertFalse(result.output.contains("$listedParentId:previewParam-0"), result.output)
        assertFalse(result.output.contains("No Compose previews discovered."), result.output)
    }

    @Test
    fun `JSON index already expanded preview parameter id can be listed and captured by exact expanded filter`() {
        writeSettings()
        writeBuildFile()
        val expandedId = ":app:main:dev.example.Parameterized:previewParam-1"
        writeParameterizedIndex(id = expandedId, index = 1)

        val listResult = runList("-PagentPreview.previewNameFilter=$expandedId")
        assertTrue(listResult.output.contains(expandedId), listResult.output)
        assertFalse(listResult.output.contains(":app:main:dev.example.Parameterized:previewParam-10"), listResult.output)

        val captureResult = runCapture("-PagentPreview.fakeRenderer=true", "-PagentPreview.previewNameFilter=$expandedId")
        assertTrue(captureResult.output.contains("Captured $expandedId"), captureResult.output)
        assertFalse(captureResult.output.contains("No Compose previews selected for capture."), captureResult.output)
    }

    @Test
    fun `JSON index parameterized parent can synthesize requested shorthand preview parameter id for list and capture`() {
        writeSettings()
        writeBuildFile()
        val parentId = ":app:main:dev.example.Parameterized"
        writeParameterizedIndex(id = parentId, limit = 3)

        val listResult = runList("-PagentPreview.previewNameFilter=previewParam-1")
        assertTrue(listResult.output.contains("$parentId:previewParam-1"), listResult.output)
        assertFalse(listResult.output.contains("$parentId:previewParam-0"), listResult.output)

        val captureResult = runCapture("-PagentPreview.fakeRenderer=true", "-PagentPreview.previewNameFilter=previewParam-1")
        assertTrue(captureResult.output.contains("Captured $parentId:previewParam-1"), captureResult.output)
        assertFalse(captureResult.output.contains("Captured $parentId:previewParam-0"), captureResult.output)
    }

    @Test
    fun `CLI preview name filter matches simple function name fragment`() {
        writeSettings()
        writeBuildFile(
            """
            agentPreview {
                previewClassesDirs.from(files("${testClassesDir().invariantSeparatorsPath}"))
            }
            """.trimIndent(),
        )

        val result = runCapture("-PagentPreview.fakeRenderer=true", "-PagentPreview.previewNameFilter=parameterizedPreview")

        assertTrue(result.output.contains("parameterizedPreview:previewParam-0"), result.output)
        assertTrue(result.output.contains("parameterizedPreview:previewParam-1"), result.output)
    }

    @Test
    fun `capture fails with actionable error for invalid CLI max preview parameter values`() {
        writeSettings()
        writeBuildFile()
        writeEmptyIndex()

        val result = runCaptureAndFail("-PagentPreview.fakeRenderer=true", "-PagentPreview.maxPreviewParameterValues=0")

        assertTrue(result.output.contains("agentPreview.maxPreviewParameterValues must be a positive integer"), result.output)
    }

    private fun writeSettings() {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    google()
                    mavenCentral()
                }
            }
            """.trimIndent(),
        )
    }

    private fun writeBuildFile(agentPreviewBlock: String = "") {
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.staticvar.agentpreview")
            }

            $agentPreviewBlock
            """.trimIndent(),
        )
    }

    private fun listedParameterizedPreviewId(): String =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments("listComposePreviews", "--warning-mode", "all")
            .withPluginClasspath()
            .build()
            .output
            .lineSequence()
            .first { it.contains("  Parameterized") }
            .trim()
            .substringBefore("  ")

    private fun writeEmptyIndex() {
        projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
            parentFile.mkdirs()
            writeText("[]")
        }
    }

    private fun writeParameterizedIndex(
        id: String,
        limit: Int? = null,
        index: Int? = null,
    ) {
        val parameterFields =
            listOfNotNull(
                "\"providerClassName\": \"dev.example.Provider\"",
                "\"parameterType\": \"kotlin.String\"",
                limit?.let { "\"limit\": $it" },
                index?.let { "\"index\": $it" },
            ).joinToString(",\n                      ")
        projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
            parentFile.mkdirs()
            writeText(
                """
                [
                  {
                    "id": "$id",
                    "name": "Parameterized",
                    "sourceSet": "main",
                    "fullyQualifiedFunctionName": "dev.example.Parameterized",
                    "sourceFile": "Parameterized.kt",
                    "widthDp": 393,
                    "heightDp": 852,
                    "previewParameter": {
                      $parameterFields
                    }
                  }
                ]
                """.trimIndent(),
            )
        }
    }

    private fun runCapture(vararg arguments: String) =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments("captureComposePreviews", *arguments)
            .withPluginClasspath()
            .build()

    private fun runList(vararg arguments: String) =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments("listComposePreviews", *arguments)
            .withPluginClasspath()
            .build()

    private fun runCaptureAndFail(vararg arguments: String) =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments("captureComposePreviews", *arguments)
            .withPluginClasspath()
            .buildAndFail()

    private fun testClassesDir(): File =
        javaClass.protectionDomain.codeSource.location
            .toURI()
            .let(::File)
}
