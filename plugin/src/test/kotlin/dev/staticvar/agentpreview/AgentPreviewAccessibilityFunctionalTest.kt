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

class AgentPreviewAccessibilityFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `default fake capture prints accessibility opt-in tip and does not write accessibility report`() {
        writeSettings()
        writeBuildFile()
        writePreviewIndex(loginPreviewJson())

        val result =
            runner("captureComposePreviews", "-PagentPreview.fakeRenderer=true")
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureComposePreviews")?.outcome)
        assertTrue(result.output.contains("Tip: run with -PagentPreview.accessibilityCheck=true"), result.output)
        assertFalse(accessibilityReport().exists())
    }

    @Test
    fun `fake capture with accessibility check writes skipped report without findings`() {
        writeSettings()
        writeBuildFile()
        writePreviewIndex(loginPreviewJson())

        val result =
            runner(
                "captureComposePreviews",
                "-PagentPreview.fakeRenderer=true",
                "-PagentPreview.accessibilityCheck=true",
            ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureComposePreviews")?.outcome)
        val report = accessibilityReport()
        assertTrue(report.isFile)
        val html = report.readText()
        assertTrue(html.contains("render.mode=fake"), html)
        assertTrue(html.contains("No bundles were checked for accessibility"), html)
        assertTrue(html.contains("Findings</span><strong>0</strong>"), html)
        assertFalse(html.contains("ERROR -"), html)
    }

    @Test
    fun `dry-run with accessibility check logs rendered snapshot requirement without report`() {
        writeSettings()
        writeBuildFile()
        writePreviewIndex(loginPreviewJson())

        val result =
            runner(
                "captureComposePreviews",
                "-PagentPreview.fakeRenderer=true",
                "-PagentPreview.dryRun=true",
                "-PagentPreview.accessibilityCheck=true",
            ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureComposePreviews")?.outcome)
        assertTrue(result.output.contains("Accessibility check requires rendered snapshots"), result.output)
        assertFalse(accessibilityReport().exists())
    }

    @Test
    fun `filtered accessibility report includes only current run bundles and assets`() {
        writeSettings()
        writeBuildFile()
        writePreviewIndex(loginPreviewJson(), settingsPreviewJson())
        projectDir.resolve("build/agentPreviewReports/accessibility-assets").apply {
            mkdirs()
            resolve("stale.png").writeText("stale")
        }
        projectDir.resolve("build/agentPreviewSnapshots/app-commonMain-SettingsPreview/android-preview").apply {
            mkdirs()
            resolve("snapshot.json").writeText("{}")
            resolve("screenshot.png").writeText("stale")
        }

        val result =
            runner(
                "captureComposePreviews",
                "-PagentPreview.fakeRenderer=true",
                "-PagentPreview.accessibilityCheck=true",
                "-PagentPreview.previewNameFilter=LoginPreview",
            ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureComposePreviews")?.outcome)
        val html = accessibilityReport().readText()
        assertTrue(html.contains(":app:commonMain:LoginPreview"), html)
        assertFalse(html.contains(":app:commonMain:SettingsPreview"), html)
        assertFalse(html.contains("SettingsPreview"), html)
        assertFalse(html.contains("stale.png"), html)
        assertFalse(projectDir.resolve("build/agentPreviewReports/accessibility-assets/stale.png").exists())
    }

    @Test
    fun `invalid accessibility check property fails with actionable message`() {
        writeSettings()
        writeBuildFile()
        writePreviewIndex(loginPreviewJson())

        val result =
            runner(
                "captureComposePreviews",
                "-PagentPreview.fakeRenderer=true",
                "-PagentPreview.accessibilityCheck=maybe",
            ).buildAndFail()

        assertTrue(result.output.contains("agentPreview.accessibilityCheck must be true or false"), result.output)
        assertTrue(result.output.contains("-PagentPreview.accessibilityCheck=true|false"), result.output)
    }

    @Test
    fun `DSL accessibility check enables report and CLI false overrides DSL true`() {
        writeSettings()
        writeBuildFile(
            """
            agentPreview {
                accessibilityCheck.set(true)
            }
            """.trimIndent(),
        )
        writePreviewIndex(loginPreviewJson())

        val dslResult =
            runner("captureComposePreviews", "-PagentPreview.fakeRenderer=true")
                .build()

        assertEquals(TaskOutcome.SUCCESS, dslResult.task(":captureComposePreviews")?.outcome)
        assertTrue(accessibilityReport().isFile)

        accessibilityReport().delete()
        val cliOverrideResult =
            runner(
                "captureComposePreviews",
                "-PagentPreview.fakeRenderer=true",
                "-PagentPreview.accessibilityCheck=false",
            ).build()

        assertEquals(TaskOutcome.SUCCESS, cliOverrideResult.task(":captureComposePreviews")?.outcome)
        assertFalse(accessibilityReport().exists())
        assertTrue(cliOverrideResult.output.contains("Tip: run with -PagentPreview.accessibilityCheck=true"), cliOverrideResult.output)
    }

    private fun runner(vararg arguments: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments(*arguments)
            .withPluginClasspath()

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

    private fun writePreviewIndex(vararg previews: String) {
        projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
            parentFile.mkdirs()
            writeText(previews.joinToString(prefix = "[", separator = ",", postfix = "]"))
        }
    }

    private fun loginPreviewJson(): String =
        """
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
        """.trimIndent()

    private fun settingsPreviewJson(): String =
        """
        {
          "id": ":app:commonMain:SettingsPreview",
          "name": "Settings",
          "group": "Settings",
          "sourceSet": "commonMain",
          "fullyQualifiedFunctionName": "dev.staticvar.SettingsPreviewKt.SettingsPreview",
          "sourceFile": "SettingsPreview.kt",
          "sourceLine": 24,
          "widthDp": 393,
          "heightDp": 852
        }
        """.trimIndent()

    private fun accessibilityReport(): File = projectDir.resolve("build/agentPreviewReports/accessibility-report.html")
}
