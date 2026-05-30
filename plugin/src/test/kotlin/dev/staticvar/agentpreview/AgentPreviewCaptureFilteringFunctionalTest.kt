/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun `list preview name filter for classpath shorthand preview parameter id shows parent without concrete expansion`() {
        writeSettings()
        writeBuildFile(
            """
            agentPreview {
                previewClassesDirs.from(files("${testClassesDir().invariantSeparatorsPath}"))
            }
            """.trimIndent(),
        )

        val listedParentId = listedParameterizedPreviewId()
        val result = runList("-PagentPreview.previewNameFilter=previewParam-1")

        assertTrue(result.output.contains("$listedParentId  Parameterized"), result.output)
        assertTrue(result.output.contains("capture ids append :previewParam-N"), result.output)
        assertFalse(result.output.contains("$listedParentId:previewParam-1"), result.output)
        assertFalse(result.output.contains("No Compose previews discovered."), result.output)
    }

    @Test
    fun `list preview name filter for classpath expanded preview parameter id shows parent without concrete expansion`() {
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

        assertTrue(result.output.contains("$listedParentId  Parameterized"), result.output)
        assertTrue(result.output.contains("capture ids append :previewParam-N"), result.output)
        assertFalse(result.output.contains("$listedParentId:previewParam-0"), result.output)
        assertFalse(result.output.contains("$listedParentId:previewParam-1"), result.output)
        assertFalse(result.output.contains("No Compose previews discovered."), result.output)
    }

    @Test
    fun `list does not advertise unverified classpath preview parameter index`() {
        writeSettings()
        writeBuildFile(
            """
            agentPreview {
                previewClassesDirs.from(files("${testClassesDir().invariantSeparatorsPath}"))
            }
            """.trimIndent(),
        )

        val listedParentId = listedParameterizedPreviewId()
        val result = runList("-PagentPreview.previewNameFilter=previewParam-2")

        assertTrue(result.output.contains("$listedParentId  Parameterized"), result.output)
        assertFalse(result.output.contains("$listedParentId:previewParam-2"), result.output)
        assertFalse(result.output.contains("No Compose previews discovered."), result.output)
    }

    @Test
    fun `capture preview name filter for out of range preview parameter index selects nothing`() {
        writeSettings()
        writeBuildFile(
            """
            agentPreview {
                previewClassesDirs.from(files("${testClassesDir().invariantSeparatorsPath}"))
            }
            """.trimIndent(),
        )

        val listedParentId = listedParameterizedPreviewId()
        val result = runCapture("-PagentPreview.fakeRenderer=true", "-PagentPreview.previewNameFilter=previewParam-2")

        assertTrue(result.output.contains("No Compose previews selected for capture."), result.output)
        assertFalse(result.output.contains("$listedParentId:previewParam-2"), result.output)
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
    fun `JSON index parameterized parent with limit is listed once when unfiltered`() {
        writeSettings()
        writeBuildFile()
        val parentId = ":app:main:dev.example.Parameterized"
        writeParameterizedIndex(id = parentId, limit = 2)

        val result = runList()

        assertTrue(result.output.contains("$parentId  Parameterized"), result.output)
        assertTrue(result.output.contains("capture ids append :previewParam-N"), result.output)
        assertFalse(result.output.contains("$parentId:previewParam-0"), result.output)
        assertFalse(result.output.contains("$parentId:previewParam-1"), result.output)
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
    fun `dry run prints plan report and does not create snapshots`() {
        writeSettings()
        writeBuildFile()
        writeSinglePreviewIndex()

        val result = runCapture("-PagentPreview.fakeRenderer=true", "-PagentPreview.dryRun=true")

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureComposePreviews")?.outcome)
        assertTrue(result.output.contains("planned 1 viewport(s)"), result.output)
        assertFalse(result.output.contains("Captured :app:commonMain:LoginPreview"), result.output)
        assertFalse(projectDir.resolve("build/agentPreviewSnapshots/app-commonMain-LoginPreview/android-preview/screenshot.png").exists())
        val report = projectDir.resolve("build/agentPreviewReports/capture-report.json")
        assertTrue(report.isFile)
        val reportJson = Json.parseToJsonElement(report.readText()).jsonObject
        assertEquals("true", reportJson.getValue("dryRun").jsonPrimitive.content)
        assertEquals("1", reportJson.getValue("plannedViewportCaptureCount").jsonPrimitive.content)
        assertEquals("0", reportJson.getValue("capturedViewportCaptureCount").jsonPrimitive.content)
    }

    @Test
    fun `max captures fails before rendering`() {
        writeSettings()
        writeBuildFile()
        writeSinglePreviewIndex()

        val result = runCaptureAndFail("-PagentPreview.fakeRenderer=true", "-PagentPreview.maxCaptures=0")

        assertTrue(
            result.output.contains("agentPreview.maxCaptures planned 1 capture(s), which exceeds the configured limit of 0"),
            result.output,
        )
        assertFalse(projectDir.resolve("build/agentPreviewSnapshots/app-commonMain-LoginPreview/android-preview/screenshot.png").exists())
        val report = projectDir.resolve("build/agentPreviewReports/capture-report.json")
        assertTrue(report.isFile)
        val reportJson = Json.parseToJsonElement(report.readText()).jsonObject
        assertEquals("1", reportJson.getValue("plannedViewportCaptureCount").jsonPrimitive.content)
        assertEquals("0", reportJson.getValue("capturedViewportCaptureCount").jsonPrimitive.content)
    }

    @Test
    fun `max captures zero permits an empty capture plan`() {
        writeSettings()
        writeBuildFile()
        writeEmptyIndex()

        val result = runCapture("-PagentPreview.fakeRenderer=true", "-PagentPreview.maxCaptures=0")

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureComposePreviews")?.outcome)
        assertTrue(result.output.contains("planned 0 viewport(s)"), result.output)
    }

    @Test
    fun `capture fails with actionable error for non numeric max captures`() {
        writeSettings()
        writeBuildFile()
        writeEmptyIndex()

        val result = runCaptureAndFail("-PagentPreview.fakeRenderer=true", "-PagentPreview.maxCaptures=many")

        assertTrue(result.output.contains("agentPreview.maxCaptures must be a non-negative integer"), result.output)
    }

    @Test
    fun `capture fails with actionable error for negative max captures`() {
        writeSettings()
        writeBuildFile()
        writeEmptyIndex()

        val result = runCaptureAndFail("-PagentPreview.fakeRenderer=true", "-PagentPreview.maxCaptures=-1")

        assertTrue(result.output.contains("agentPreview.maxCaptures must be a non-negative integer"), result.output)
    }

    @Test
    fun `capture fails with actionable error for invalid dry run and does not render`() {
        writeSettings()
        writeBuildFile()
        writeSinglePreviewIndex()

        val result = runCaptureAndFail("-PagentPreview.fakeRenderer=true", "-PagentPreview.dryRun=tru")

        assertTrue(result.output.contains("agentPreview.dryRun must be true or false"), result.output)
        assertFalse(projectDir.resolve("build/agentPreviewSnapshots/app-commonMain-LoginPreview/android-preview/screenshot.png").exists())
    }

    @Test
    fun `capture fails with actionable error for invalid continue on error`() {
        writeSettings()
        writeBuildFile()
        writeEmptyIndex()

        val result = runCaptureAndFail("-PagentPreview.fakeRenderer=true", "-PagentPreview.continueOnError=tru")

        assertTrue(result.output.contains("agentPreview.continueOnError must be true or false"), result.output)
    }

    @Test
    fun `default rendering fails fast on first failure`() {
        writeSettings()
        writeBuildFile()
        writeTwoPreviewIndex()

        val result = runCaptureAndFail()

        assertTrue(result.output.contains(":app:commonMain:LoginPreview"), result.output)
        assertFalse(result.output.contains(":app:commonMain:SettingsPreview"), result.output)
    }

    @Test
    fun `parallel default rendering fails fast without continue on error`() {
        writeSettings()
        writeBuildFile()
        writeThreePreviewIndex()

        val result = runCaptureAndFail("-PagentPreview.maxParallelRenders=2")

        assertTrue(result.output.contains("parallel render workers: 2"), result.output)
        assertTrue(result.output.contains(":app:commonMain:LoginPreview"), result.output)
        assertTrue(result.output.contains(":app:commonMain:SettingsPreview"), result.output)
        assertFalse(result.output.contains(":app:commonMain:QueuedPreview"), result.output)
        assertTrue(result.output.contains("AgentPreview capture failed"), result.output)
        val reportText = projectDir.resolve("build/agentPreviewReports/capture-report.json").readText()
        assertFalse(reportText.contains("\":app:commonMain:QueuedPreview\""), reportText)
        val reportJson = Json.parseToJsonElement(reportText).jsonObject
        assertEquals("false", reportJson.getValue("continueOnError").jsonPrimitive.content)
        assertEquals("2", reportJson.getValue("maxParallelRenders").jsonPrimitive.content)
        assertEquals("2", reportJson.getValue("failedViewportCaptureCount").jsonPrimitive.content)
    }

    @Test
    fun `continue on error attempts remaining captures and reports failures`() {
        writeSettings()
        writeBuildFile()
        writeTwoPreviewIndex()

        val result = runCaptureAndFail("-PagentPreview.continueOnError=true")

        assertTrue(result.output.contains(":app:commonMain:LoginPreview"), result.output)
        assertTrue(result.output.contains(":app:commonMain:SettingsPreview"), result.output)
        assertTrue(result.output.contains("AgentPreview capture failed for 2 viewport(s)"), result.output)
        val report = projectDir.resolve("build/agentPreviewReports/capture-report.json")
        assertTrue(report.isFile)
        val reportText = report.readText()
        assertTrue(reportText.contains("\":app:commonMain:LoginPreview\""), reportText)
        assertTrue(reportText.contains("\":app:commonMain:SettingsPreview\""), reportText)
        val reportJson = Json.parseToJsonElement(reportText).jsonObject
        assertEquals("true", reportJson.getValue("continueOnError").jsonPrimitive.content)
        assertEquals("2", reportJson.getValue("failedViewportCaptureCount").jsonPrimitive.content)
    }

    @Test
    fun `continue on error records checked exceptions from individual fake captures`() {
        writeSettings()
        writeBuildFile()
        writeTwoPreviewIndex()
        projectDir.resolve("build/agentPreview/render").apply {
            parentFile.mkdirs()
            writeText("not a directory")
        }

        val result = runCaptureAndFail("-PagentPreview.fakeRenderer=true", "-PagentPreview.continueOnError=true")

        assertTrue(result.output.contains(":app:commonMain:LoginPreview"), result.output)
        assertTrue(result.output.contains(":app:commonMain:SettingsPreview"), result.output)
        assertTrue(result.output.contains("AgentPreview capture failed for 2 viewport(s)"), result.output)
        val reportText = projectDir.resolve("build/agentPreviewReports/capture-report.json").readText()
        assertTrue(reportText.contains("\":app:commonMain:LoginPreview\""), reportText)
        assertTrue(reportText.contains("\":app:commonMain:SettingsPreview\""), reportText)
    }

    @Test
    fun `CLI capture controls are wired to task inputs`() {
        writeSettings()
        writeBuildFile(
            """
            agentPreview {
                maxCaptures.set(5)
                maxParallelRenders.set(1)
                continueOnError.set(false)
            }
            """.trimIndent(),
        )
        writeSinglePreviewIndex()

        val result =
            runCapture(
                "-PagentPreview.fakeRenderer=true",
                "-PagentPreview.dryRun=true",
                "-PagentPreview.continueOnError=true",
                "-PagentPreview.maxCaptures=2",
                "-PagentPreview.maxParallelRenders=3",
            )

        assertTrue(result.output.contains("dry run"), result.output)
        val reportText = projectDir.resolve("build/agentPreviewReports/capture-report.json").readText()
        assertTrue(reportText.contains("\"continueOnError\": true"), reportText)
        assertTrue(reportText.contains("\"maxCaptures\": 2"), reportText)
        assertTrue(reportText.contains("\"maxParallelRenders\": 3"), reportText)
    }

    @Test
    fun `capture fails with actionable error for invalid CLI max preview parameter values`() {
        writeSettings()
        writeBuildFile()
        writeEmptyIndex()

        val result = runCaptureAndFail("-PagentPreview.fakeRenderer=true", "-PagentPreview.maxPreviewParameterValues=0")

        assertTrue(result.output.contains("agentPreview.maxPreviewParameterValues must be a positive integer"), result.output)
    }

    @Test
    fun `capture fails with actionable error for non numeric max parallel renders`() {
        writeSettings()
        writeBuildFile()
        writeEmptyIndex()

        val result = runCaptureAndFail("-PagentPreview.fakeRenderer=true", "-PagentPreview.maxParallelRenders=many")

        assertTrue(result.output.contains("agentPreview.maxParallelRenders must be a positive integer"), result.output)
    }

    @Test
    fun `capture fails with actionable error for zero max parallel renders`() {
        writeSettings()
        writeBuildFile()
        writeEmptyIndex()

        val result = runCaptureAndFail("-PagentPreview.fakeRenderer=true", "-PagentPreview.maxParallelRenders=0")

        assertTrue(result.output.contains("agentPreview.maxParallelRenders must be a positive integer"), result.output)
    }

    @Test
    fun `capture fails with actionable error for negative max parallel renders`() {
        writeSettings()
        writeBuildFile()
        writeEmptyIndex()

        val result = runCaptureAndFail("-PagentPreview.fakeRenderer=true", "-PagentPreview.maxParallelRenders=-1")

        assertTrue(result.output.contains("agentPreview.maxParallelRenders must be a positive integer"), result.output)
    }

    @Test
    fun `max parallel renders captures expected outputs in fake mode`() {
        writeSettings()
        writeBuildFile()
        writeTwoPreviewIndex()

        val result = runCapture("-PagentPreview.fakeRenderer=true", "-PagentPreview.maxParallelRenders=2")

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureComposePreviews")?.outcome)
        assertTrue(result.output.contains("parallel render workers: 2"), result.output)
        assertTrue(projectDir.resolve("build/agentPreviewSnapshots/app-commonMain-LoginPreview/android-preview/snapshot.json").isFile)
        assertTrue(projectDir.resolve("build/agentPreviewSnapshots/app-commonMain-SettingsPreview/android-preview/snapshot.json").isFile)
        val reportText = projectDir.resolve("build/agentPreviewReports/capture-report.json").readText()
        assertTrue(reportText.contains("\"maxParallelRenders\": 2"), reportText)
    }

    @Test
    fun `continue on error aggregates failures with parallel renders`() {
        writeSettings()
        writeBuildFile()
        writeTwoPreviewIndex()

        val result = runCaptureAndFail("-PagentPreview.continueOnError=true", "-PagentPreview.maxParallelRenders=2")

        assertTrue(result.output.contains(":app:commonMain:LoginPreview"), result.output)
        assertTrue(result.output.contains(":app:commonMain:SettingsPreview"), result.output)
        assertTrue(result.output.contains("AgentPreview capture failed for 2 viewport(s)"), result.output)
        val reportJson = Json.parseToJsonElement(projectDir.resolve("build/agentPreviewReports/capture-report.json").readText()).jsonObject
        assertEquals("2", reportJson.getValue("failedViewportCaptureCount").jsonPrimitive.content)
        assertEquals("2", reportJson.getValue("maxParallelRenders").jsonPrimitive.content)
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

    private fun writeSinglePreviewIndex() {
        projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
            parentFile.mkdirs()
            writeText(
                """
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
                    "heightDp": 852
                  }
                ]
                """.trimIndent(),
            )
        }
    }

    private fun writeTwoPreviewIndex() {
        projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
            parentFile.mkdirs()
            writeText(
                """
                [
                  {
                    "id": ":app:commonMain:LoginPreview",
                    "name": "Login",
                    "sourceSet": "commonMain",
                    "fullyQualifiedFunctionName": "dev.staticvar.LoginPreviewKt.LoginPreview",
                    "sourceFile": "LoginPreview.kt",
                    "widthDp": 393,
                    "heightDp": 852
                  },
                  {
                    "id": ":app:commonMain:SettingsPreview",
                    "name": "Settings",
                    "sourceSet": "commonMain",
                    "fullyQualifiedFunctionName": "dev.staticvar.SettingsPreviewKt.SettingsPreview",
                    "sourceFile": "SettingsPreview.kt",
                    "widthDp": 393,
                    "heightDp": 852
                  }
                ]
                """.trimIndent(),
            )
        }
    }

    private fun writeThreePreviewIndex() {
        projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
            parentFile.mkdirs()
            writeText(
                listOf(
                    previewJson("Login"),
                    previewJson("Settings"),
                    previewJson("Queued"),
                ).joinToString(prefix = "[", postfix = "]"),
            )
        }
    }

    private fun previewJson(name: String): String =
        """{"id":":app:commonMain:${name}Preview","name":"$name","sourceSet":"commonMain","fullyQualifiedFunctionName":"dev.staticvar.${name}PreviewKt.${name}Preview","sourceFile":"${name}Preview.kt","widthDp":393,"heightDp":852}"""

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
