/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AgentPreviewConfigurationCacheFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `list and fake capture tasks are configuration-cache compatible`() {
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

        val listArguments = listOf("listComposePreviews", "--configuration-cache", "--warning-mode", "all")
        val listResult =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments(listArguments)
                .withPluginClasspath()
                .build()
        assertTrue(listResult.output.contains("Configuration cache entry stored"), listResult.output)

        val listReuseResult =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments(listArguments)
                .withPluginClasspath()
                .build()
        assertTrue(listReuseResult.output.contains("Configuration cache entry reused"), listReuseResult.output)

        val captureArguments = listOf("captureComposePreviews", "--configuration-cache", "-PagentPreview.fakeRenderer=true")
        val captureResult =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments(captureArguments)
                .withPluginClasspath()
                .build()
        assertTrue(captureResult.output.contains("Configuration cache entry stored"), captureResult.output)

        val captureReuseResult =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments(captureArguments)
                .withPluginClasspath()
                .build()
        assertTrue(captureReuseResult.output.contains("Configuration cache entry reused"), captureReuseResult.output)
    }

    private fun testClassesDir(): File =
        javaClass.protectionDomain.codeSource.location
            .toURI()
            .let(::File)
}
