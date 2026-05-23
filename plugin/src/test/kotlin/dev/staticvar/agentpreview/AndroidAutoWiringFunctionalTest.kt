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

class AndroidAutoWiringFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `wires Android library debug compile task into preview task graph`() {
        writeFakeAndroidPlugin()
        writeSettings()
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.library")
                id("dev.staticvar.agentpreview")
            }
            """.trimIndent(),
        )
        writeEmptyPreviewIndex()

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("listComposePreviews")
                .withPluginClasspath()
                .build()

        assertTrue(result.output.contains(":compileDebugKotlin"), result.output)
        assertTrue(result.output.contains("No Compose previews discovered."), result.output)
    }

    @Test
    fun `wires Compose Multiplatform Android compile task into preview task graph`() {
        writeFakeAndroidPlugin()
        writeSettings()
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.library")
                id("dev.staticvar.agentpreview")
            }
            """.trimIndent(),
        )
        writeEmptyPreviewIndex()

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("listComposePreviews")
                .withPluginClasspath()
                .build()

        assertTrue(result.output.contains(":compileDebugKotlinAndroid"), result.output)
        assertTrue(result.output.contains("No Compose previews discovered."), result.output)
    }

    @Test
    fun `uses configured Android variant for auto wiring`() {
        writeFakeAndroidPlugin()
        writeSettings()
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.library")
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
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("listComposePreviews")
                .withPluginClasspath()
                .build()

        assertTrue(result.output.contains(":compileReleaseKotlin"), result.output)
        assertTrue(result.output.contains("No Compose previews discovered."), result.output)
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

    private fun writeFakeAndroidPlugin() {
        projectDir.resolve("buildSrc/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(
                """
                plugins {
                    `java-gradle-plugin`
                }

                gradlePlugin {
                    plugins {
                        create("fakeAndroidLibrary") {
                            id = "com.android.library"
                            implementationClass = "FakeAndroidLibraryPlugin"
                        }
                    }
                }
                """.trimIndent(),
            )
        }
        projectDir.resolve("buildSrc/src/main/java/FakeAndroidLibraryPlugin.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class FakeAndroidLibraryPlugin implements Plugin<Project> {
                    @Override
                    public void apply(Project project) {
                        project.getConfigurations().create("debugRuntimeClasspath");
                        project.getConfigurations().create("releaseRuntimeClasspath");
                        project.getTasks().register("compileDebugKotlin");
                        project.getTasks().register("compileDebugKotlinAndroid");
                        project.getTasks().register("compileReleaseKotlin");
                        project.getTasks().register("compileReleaseKotlinAndroid");
                    }
                }
                """.trimIndent(),
            )
        }
    }
}
