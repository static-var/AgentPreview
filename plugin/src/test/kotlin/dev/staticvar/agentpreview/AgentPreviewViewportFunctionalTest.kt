/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AgentPreviewViewportFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `capture task renders configured Android viewports when preview has no explicit size`() {
        writeBasicSettings()
        writeBuildFile()
        writePreviewIndex(
            id = ":app:main:ResponsivePreview",
            name = "Responsive",
            functionName = "dev.staticvar.ResponsivePreviewKt.ResponsivePreview",
            sourceFile = "ResponsivePreview.kt",
            widthDp = -1,
            heightDp = -1,
        )

        runCapture()

        val phoneSnapshot = projectDir.resolve("build/agentPreviewSnapshots/app-main-ResponsivePreview/android-phone/snapshot.json")
        val tabletSnapshot = projectDir.resolve("build/agentPreviewSnapshots/app-main-ResponsivePreview/android-tablet/snapshot.json")
        assertTrue(phoneSnapshot.isFile)
        assertTrue(tabletSnapshot.isFile)
        assertTrue(phoneSnapshot.readText().contains("\"platform\": \"android\""))
        assertTrue(phoneSnapshot.readText().contains("\"name\": \"phone\""))
        assertTrue(tabletSnapshot.readText().contains("\"width\": 800"))
    }

    @Test
    fun `capture task filters configured viewports from CLI`() {
        writeBasicSettings()
        writeBuildFile()
        writePreviewIndex(
            id = ":app:main:ResponsivePreview",
            name = "Responsive",
            functionName = "dev.staticvar.ResponsivePreviewKt.ResponsivePreview",
            sourceFile = "ResponsivePreview.kt",
            widthDp = -1,
            heightDp = -1,
        )

        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments("captureComposePreviews", "-PagentPreview.fakeRenderer=true", "-PagentPreview.viewportFilter=phone")
            .withPluginClasspath()
            .build()

        assertTrue(projectDir.resolve("build/agentPreviewSnapshots/app-main-ResponsivePreview/android-phone/snapshot.json").isFile)
        assertFalse(projectDir.resolve("build/agentPreviewSnapshots/app-main-ResponsivePreview/android-tablet").exists())
    }

    @Test
    fun `capture task uses explicit preview dimensions instead of configured Android viewports`() {
        writeBasicSettings()
        writeBuildFile()
        writePreviewIndex(
            id = ":app:main:FixedPreview",
            name = "Fixed",
            functionName = "dev.staticvar.FixedPreviewKt.FixedPreview",
            sourceFile = "FixedPreview.kt",
            widthDp = 320,
            heightDp = 640,
        )

        runCapture()

        val snapshot = projectDir.resolve("build/agentPreviewSnapshots/app-main-FixedPreview/android-preview/snapshot.json")
        assertTrue(snapshot.isFile)
        assertTrue(snapshot.readText().contains("\"name\": \"preview\""))
        assertTrue(snapshot.readText().contains("\"width\": 320"))
        assertFalse(projectDir.resolve("build/agentPreviewSnapshots/app-main-FixedPreview/android-phone").exists())
    }

    @Test
    fun `capture task applies partial preview width to configured Android viewports`() {
        writeBasicSettings()
        writeBuildFile()
        writePreviewIndex(
            id = ":app:main:WidthOnlyPreview",
            name = "WidthOnly",
            functionName = "dev.staticvar.WidthOnlyPreviewKt.WidthOnlyPreview",
            sourceFile = "WidthOnlyPreview.kt",
            widthDp = 320,
            heightDp = -1,
        )

        runCapture()

        val phoneSnapshot = projectDir.resolve("build/agentPreviewSnapshots/app-main-WidthOnlyPreview/android-phone/snapshot.json")
        val tabletSnapshot = projectDir.resolve("build/agentPreviewSnapshots/app-main-WidthOnlyPreview/android-tablet/snapshot.json")
        assertTrue(phoneSnapshot.readText().contains("\"width\": 320"))
        assertTrue(phoneSnapshot.readText().contains("\"height\": 852"))
        assertTrue(tabletSnapshot.readText().contains("\"width\": 320"))
        assertTrue(tabletSnapshot.readText().contains("\"height\": 1280"))
    }

    @Test
    fun `capture task applies partial preview height to configured Android viewports`() {
        writeBasicSettings()
        writeBuildFile()
        writePreviewIndex(
            id = ":app:main:HeightOnlyPreview",
            name = "HeightOnly",
            functionName = "dev.staticvar.HeightOnlyPreviewKt.HeightOnlyPreview",
            sourceFile = "HeightOnlyPreview.kt",
            widthDp = -1,
            heightDp = 640,
        )

        runCapture()

        val phoneSnapshot = projectDir.resolve("build/agentPreviewSnapshots/app-main-HeightOnlyPreview/android-phone/snapshot.json")
        val tabletSnapshot = projectDir.resolve("build/agentPreviewSnapshots/app-main-HeightOnlyPreview/android-tablet/snapshot.json")
        assertTrue(phoneSnapshot.readText().contains("\"width\": 393"))
        assertTrue(phoneSnapshot.readText().contains("\"height\": 640"))
        assertTrue(tabletSnapshot.readText().contains("\"width\": 800"))
        assertTrue(tabletSnapshot.readText().contains("\"height\": 640"))
    }

    private fun writeBasicSettings() {
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

    private fun writeBuildFile() {
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
    }

    private fun writePreviewIndex(
        id: String,
        name: String,
        functionName: String,
        sourceFile: String,
        widthDp: Int,
        heightDp: Int,
    ) {
        projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
            parentFile.mkdirs()
            writeText(
                """
                [
                  {
                    "id": "$id",
                    "name": "$name",
                    "group": "Cards",
                    "sourceSet": "main",
                    "fullyQualifiedFunctionName": "$functionName",
                    "sourceFile": "$sourceFile",
                    "widthDp": $widthDp,
                    "heightDp": $heightDp
                  }
                ]
                """.trimIndent(),
            )
        }
    }

    private fun runCapture() {
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments("captureComposePreviews", "-PagentPreview.fakeRenderer=true")
            .withPluginClasspath()
            .build()
    }
}
