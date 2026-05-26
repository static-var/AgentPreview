package dev.staticvar.agentpreview

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AgentPreviewDependencyIsolationFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `plugin does not leak renderer support dependencies into release runtime classpath`() {
        writeSettings()
        projectDir.resolve("debug-ui.jar").writeText("debug-ui")
        projectDir.resolve("release-ui.jar").writeText("release-ui")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.staticvar.agentpreview")
            }

            configurations.create("debugRuntimeClasspath")
            configurations.create("releaseRuntimeClasspath")
            dependencies {
                add("debugRuntimeClasspath", files("debug-ui.jar"))
                add("releaseRuntimeClasspath", files("release-ui.jar"))
            }
            """.trimIndent(),
        )

        val result =
            GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments("dependencies", "--configuration", "releaseRuntimeClasspath")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":dependencies")?.outcome)
        assertTrue(result.output.contains("releaseRuntimeClasspath"), result.output)
        assertFalse(result.output.contains("ui-tooling"), result.output)
        assertFalse(result.output.contains("ui-tooling-data"), result.output)
        assertFalse(result.output.contains("robolectric"), result.output)
        assertFalse(result.output.contains("androidx.test:core"), result.output)
        assertFalse(result.output.contains("androidx.test:monitor"), result.output)
        assertFalse(result.output.contains("agentpreview"), result.output)
    }

    @Test
    fun `list task fails fast when release variant is selected`() {
        writeSettings()
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.staticvar.agentpreview")
            }

            agentPreview {
                android {
                    variant.set("release")
                }
            }
            """.trimIndent(),
        )
        writeEmptyPreviewIndex()

        val result =
            GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments("listComposePreviews")
                .withPluginClasspath()
                .buildAndFail()

        assertTrue(result.output.contains("agentPreview.android.variant=release is not supported"), result.output)
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

    private fun writeEmptyPreviewIndex() {
        projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
            parentFile.mkdirs()
            writeText("[]")
        }
    }
}
