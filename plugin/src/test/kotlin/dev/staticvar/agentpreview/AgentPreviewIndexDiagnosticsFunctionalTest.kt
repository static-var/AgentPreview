/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AgentPreviewIndexDiagnosticsFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `list task reports no previews when default preview index is absent`() {
        writeBasicProject()

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("listComposePreviews")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":listComposePreviews")?.outcome)
        assertTrue(result.output.contains("No Compose previews discovered."), result.output)
    }

    @Test
    fun `capture task reports no selected previews when default preview index is absent`() {
        writeBasicProject()

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("captureComposePreviews", "-PagentPreview.fakeRenderer=true")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureComposePreviews")?.outcome)
        assertTrue(result.output.contains("No Compose previews selected for capture."), result.output)
    }

    @Test
    fun `malformed preview index fails with index file diagnostics`() {
        writeBasicProject()
        projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
            parentFile.mkdirs()
            writeText("not-json")
        }

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("listComposePreviews")
                .withPluginClasspath()
                .buildAndFail()

        assertTrue(result.output.contains("discovered-previews.json"), result.output)
        assertTrue(result.output.contains("Failed to parse preview index"), result.output)
    }

    private fun writeBasicProject() {
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
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.staticvar.agentpreview")
            }
            """.trimIndent(),
        )
    }
}
