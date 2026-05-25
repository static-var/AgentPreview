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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import javax.imageio.ImageIO

class AgentPreviewPluginFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `list task shows preview parameter metadata without expanding values`() {
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

            agentPreview {
                previewClassesDirs.from(files("${testClassesDir().invariantSeparatorsPath}"))
            }
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("listComposePreviews", "--warning-mode", "all")
                .withPluginClasspath()
                .build()

        assertTrue(result.output.contains("parameterizedPreview  Parameterized"), result.output)
        assertTrue(result.output.contains("values expand during capture"), result.output)
        assertFalse(result.output.contains("parameterizedPreview:previewParam-0"), result.output)
        assertTrue(result.output.contains(":listComposePreviews"), result.output)
    }

    @Test
    fun `fake capture expands preview parameter values through isolated resolver`() {
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

            agentPreview {
                previewClassesDirs.from(files("${testClassesDir().invariantSeparatorsPath}"))
            }
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("captureComposePreviews", "-PagentPreview.fakeRenderer=true")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureComposePreviews")?.outcome)
        assertTrue(result.output.contains("parameterizedPreview:previewParam-0"), result.output)
        assertTrue(result.output.contains("parameterizedPreview:previewParam-1"), result.output)
    }

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
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.staticvar.agentpreview")
            }
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
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
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.staticvar.agentpreview")
            }
            """.trimIndent(),
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
                """.trimIndent(),
            )
        }

        val result =
            GradleRunner
                .create()
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
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.staticvar.agentpreview")
            }
            """.trimIndent(),
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
                """.trimIndent(),
            )
        }
        val staleBundle =
            projectDir.resolve("build/agentPreviewSnapshots/stale-preview").apply {
                mkdirs()
                resolve("snapshot.json").writeText("{}")
            }

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("captureComposePreviews", "-PagentPreview.fakeRenderer=true")
                .withPluginClasspath()
                .build()

        assertTrue(result.output.contains("Captured :app:commonMain:LoginPreview"))
        val screenshot = projectDir.resolve("build/agentPreviewSnapshots/app-commonMain-LoginPreview/android-preview/screenshot.png")
        assertTrue(screenshot.isFile)
        val image = ImageIO.read(screenshot)
        assertNotNull(image)
        assertEquals(393, image.width)
        assertEquals(852, image.height)
        assertFalse(staleBundle.exists())
        val snapshotJson =
            projectDir
                .resolve(
                    "build/agentPreviewSnapshots/app-commonMain-LoginPreview/android-preview/snapshot.json",
                ).readText()
        assertTrue(snapshotJson.contains("\"Login\""))
        assertTrue(snapshotJson.contains("\"render\""))
        assertTrue(snapshotJson.contains("\"mode\": \"fake\""))
    }

    @Test
    fun `capture task warns when Robolectric SDK requires newer Java`() {
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

            agentPreview {
                android {
                    robolectricSdk.set(36)
                }
            }
            """.trimIndent(),
        )
        projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
            parentFile.mkdirs()
            writeText("[]")
        }

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("captureComposePreviews", "-PagentPreview.fakeRenderer=true", "-PagentPreview.javaMajorVersion=17")
                .withPluginClasspath()
                .build()

        assertTrue(result.output.contains("android.robolectricSdk=36 is not supported by the Android renderer yet"))
    }

    @Test
    fun `capture task uses PreviewRendererImpl without fake mode`() {
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
                """.trimIndent(),
            )
        }

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("captureComposePreviews")
                .withPluginClasspath()
                .buildAndFail()

        assertTrue(result.output.contains("PreviewRendererImpl"))
        assertTrue(result.output.contains("requires Android compiled classes and runtime classpath"))
        assertFalse(result.output.contains("Production preview rendering is not implemented in phase 1"))
    }

    @Test
    fun `capture task reruns when fake renderer property changes`() {
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
                    "sourceLine": 12
                  }
                ]
                """.trimIndent(),
            )
        }

        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments("captureComposePreviews", "-PagentPreview.fakeRenderer=true")
            .withPluginClasspath()
            .build()

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("captureComposePreviews")
                .withPluginClasspath()
                .buildAndFail()

        assertTrue(result.output.contains("PreviewRendererImpl"))
        assertTrue(result.output.contains("requires Android compiled classes and runtime classpath"))
    }

    @Test
    fun `capture task reruns when discovered preview index changes`() {
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
        val indexFile =
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
                        "sourceLine": 12
                      }
                    ]
                    """.trimIndent(),
                )
            }

        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments("captureComposePreviews", "-PagentPreview.fakeRenderer=true")
            .withPluginClasspath()
            .build()

        indexFile.writeText(
            """
            [
              {
                "id": ":app:commonMain:SettingsPreview",
                "name": "Settings",
                "group": "Settings",
                "sourceSet": "commonMain",
                "fullyQualifiedFunctionName": "dev.staticvar.SettingsPreviewKt.SettingsPreview",
                "sourceFile": "SettingsPreview.kt",
                "sourceLine": 24
              }
            ]
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("captureComposePreviews", "-PagentPreview.fakeRenderer=true")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureComposePreviews")?.outcome)
        assertTrue(projectDir.resolve("build/agentPreviewSnapshots/app-commonMain-SettingsPreview/android-phone/snapshot.json").isFile)
        assertFalse(projectDir.resolve("build/agentPreviewSnapshots/app-commonMain-LoginPreview").exists())

        indexFile.writeText("[]")

        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments("captureComposePreviews", "-PagentPreview.fakeRenderer=true")
            .withPluginClasspath()
            .build()

        assertFalse(projectDir.resolve("build/agentPreviewSnapshots/app-commonMain-SettingsPreview").exists())
    }

    private fun testClassesDir(): File =
        javaClass.protectionDomain.codeSource.location
            .toURI()
            .let(::File)
}
