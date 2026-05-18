package dev.staticvar.agentpreview

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AgentPreviewPluginFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `plugin registers list and capture tasks`() {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    google()
                    mavenCentral()
                }
            }
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.staticvar.agentpreview")
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("tasks", "--group", "agent preview")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":tasks")?.outcome)
        assertTrue(result.output.contains("listComposePreviews"))
        assertTrue(result.output.contains("captureComposePreviews"))
    }
    @Test
    fun `list task prints discovered preview ids from configured index file`() {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    google()
                    mavenCentral()
                }
            }
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.staticvar.agentpreview")
            }
            """.trimIndent()
        )
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
                    "heightDp": 852,
                    "locale": null,
                    "uiMode": null,
                    "fontScale": null
                  }
                ]
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("listComposePreviews")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains(":app:commonMain:LoginPreview"))
        assertTrue(result.output.contains("Login"))
    }
    @Test
    fun `capture task exports snapshot bundle for indexed preview in fake mode`() {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    google()
                    mavenCentral()
                }
            }
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.staticvar.agentpreview")
            }
            """.trimIndent()
        )
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
                    "heightDp": 852,
                    "locale": null,
                    "uiMode": null,
                    "fontScale": null
                  }
                ]
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("captureComposePreviews", "-PagentPreview.fakeRenderer=true")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("Captured :app:commonMain:LoginPreview"))
        assertTrue(projectDir.resolve("build/agentPreviewSnapshots/app-commonMain-LoginPreview/screenshot.png").isFile)
        assertTrue(projectDir.resolve("build/agentPreviewSnapshots/app-commonMain-LoginPreview/snapshot.json").readText().contains("\"Login\""))
    }

    @Test
    fun `capture task explains that production rendering is not available without fake mode`() {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    google()
                    mavenCentral()
                }
            }
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.staticvar.agentpreview")
            }
            """.trimIndent()
        )
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
                    "heightDp": 852,
                    "locale": null,
                    "uiMode": null,
                    "fontScale": null
                  }
                ]
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("captureComposePreviews")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(result.output.contains("Production preview rendering is not implemented in phase 1"))
        assertTrue(result.output.contains("-PagentPreview.fakeRenderer=true"))
    }
}
