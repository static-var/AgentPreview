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
import java.util.jar.JarOutputStream

class AgentPreviewDependencyIsolationFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `capture task resolves renderer support in isolated configuration without leaking into release runtime classpath`() {
        writeSettings()
        writeLocalModule("androidx.compose.ui", "ui", "1.8.1")
        writeLocalModule("androidx.compose.ui", "ui-tooling", "1.8.1")
        writeLocalModule("androidx.compose.ui", "ui-tooling-data", "1.8.1")
        writeLocalModule("androidx.test", "core", "1.7.0")
        writeLocalModule("androidx.test", "monitor", "1.8.0")
        writeLocalModule("com.example", "release-ui", "1.0")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.staticvar.agentpreview")
            }

            repositories {
                maven(url = uri("${mavenRepoDir().invariantSeparatorsPath}"))
            }

            configurations.create("debugRuntimeClasspath")
            configurations.create("releaseRuntimeClasspath")

            dependencies {
                add("debugRuntimeClasspath", "androidx.compose.ui:ui:1.8.1")
                add("debugRuntimeClasspath", "androidx.compose.ui:ui-tooling:1.8.1")
                add("releaseRuntimeClasspath", "com.example:release-ui:1.0")
            }

            agentPreview {
                previewClassesDirs.from(files("${testClassesDir().invariantSeparatorsPath}"))
            }
            """.trimIndent(),
        )

        val captureResult = runner("captureComposePreviews", "-PagentPreview.fakeRenderer=true").build()

        assertEquals(TaskOutcome.SUCCESS, captureResult.task(":captureComposePreviews")?.outcome)
        assertTrue(captureResult.output.contains("ui-tooling-1.8.1.jar"), captureResult.output)
        assertTrue(captureResult.output.contains("ui-tooling-data-1.8.1.jar"), captureResult.output)
        assertTrue(captureResult.output.contains("core-1.7.0.jar"), captureResult.output)
        assertTrue(captureResult.output.contains("monitor-1.8.0.jar"), captureResult.output)

        val dependenciesResult = runner("dependencies", "--configuration", "releaseRuntimeClasspath").build()

        assertEquals(TaskOutcome.SUCCESS, dependenciesResult.task(":dependencies")?.outcome)
        assertTrue(dependenciesResult.output.contains("com.example:release-ui:1.0"), dependenciesResult.output)
        assertFalse(dependenciesResult.output.contains("ui-tooling"), dependenciesResult.output)
        assertFalse(dependenciesResult.output.contains("ui-tooling-data"), dependenciesResult.output)
        assertFalse(dependenciesResult.output.contains("androidx.test:core"), dependenciesResult.output)
        assertFalse(dependenciesResult.output.contains("androidx.test:monitor"), dependenciesResult.output)
        assertFalse(dependenciesResult.output.contains("agentpreview"), dependenciesResult.output)
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

        val result = runner("listComposePreviews").buildAndFail()

        assertTrue(result.output.contains("agentPreview.android.variant=release is not supported"), result.output)
    }

    private fun runner(vararg arguments: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments(arguments.toList())
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

    private fun writeEmptyPreviewIndex() {
        projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
            parentFile.mkdirs()
            writeText("[]")
        }
    }

    private fun writeLocalModule(
        group: String,
        module: String,
        version: String,
    ) {
        val moduleDir =
            mavenRepoDir().resolve("${group.replace('.', '/')}/$module/$version").apply {
                mkdirs()
            }
        moduleDir.resolve("$module-$version.pom").writeText(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>$group</groupId>
              <artifactId>$module</artifactId>
              <version>$version</version>
              <packaging>jar</packaging>
            </project>
            """.trimIndent(),
        )
        JarOutputStream(moduleDir.resolve("$module-$version.jar").outputStream()).use { }
    }

    private fun mavenRepoDir(): File = projectDir.resolve("test-repo")

    private fun testClassesDir(): File =
        javaClass.protectionDomain.codeSource.location
            .toURI()
            .let(::File)
}
