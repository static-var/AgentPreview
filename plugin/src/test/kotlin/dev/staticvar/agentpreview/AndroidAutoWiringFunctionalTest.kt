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

class AndroidAutoWiringFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `wires Android KMP library compile classes and runtime classpath into preview tasks`() {
        writeSettings()
        projectDir.resolve("android-runtime.jar").writeText("runtime")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import dev.staticvar.agentpreview.tasks.ListComposePreviewsTask

            plugins {
                id("dev.staticvar.agentpreview")
            }

            configurations.create("androidRuntimeClasspath")
            dependencies.add("androidRuntimeClasspath", files("android-runtime.jar"))

            tasks.register("compileAndroidMain") {
                doLast {
                    layout.buildDirectory.dir("classes/kotlin/android/main").get().asFile.mkdirs()
                }
            }

            tasks.named<ListComposePreviewsTask>("listComposePreviews") {
                doFirst {
                    println("previewClassesDirs=" + previewClassesDirs.files.joinToString("|") { it.invariantSeparatorsPath })
                    println("previewRuntimeClasspath=" + previewRuntimeClasspath.files.joinToString("|") { it.name })
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

        assertTrue(result.output.contains(":compileAndroidMain"), result.output)
        assertTrue(result.output.contains("build/classes/kotlin/android/main"), result.output)
        assertTrue(result.output.contains("android-runtime.jar"), result.output)
        assertTrue(result.output.contains("No Compose previews discovered."), result.output)
    }

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
    fun `non-preview tasks still run when release variant is configured`() {
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

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("help")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":help")?.outcome)
    }

    @Test
    fun `fails when release variant is configured for auto wiring`() {
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
                .buildAndFail()

        assertTrue(result.output.contains("agentPreview.android.variant=release is not supported"), result.output)
    }

    @Test
    fun `normal Android variant auto wiring wins when KMP-shaped signals also exist`() {
        writeFakeAndroidPlugin()
        writeSettings()
        projectDir.resolve("android-runtime.jar").writeText("kmp-runtime")
        projectDir.resolve("debug-runtime.jar").writeText("debug-runtime")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import dev.staticvar.agentpreview.tasks.ListComposePreviewsTask

            plugins {
                id("com.android.library")
                id("dev.staticvar.agentpreview")
            }

            configurations.create("androidRuntimeClasspath")
            dependencies.add("androidRuntimeClasspath", files("android-runtime.jar"))
            dependencies.add("debugRuntimeClasspath", files("debug-runtime.jar"))

            tasks.register("compileAndroidMain") {
                doLast {
                    layout.buildDirectory.dir("classes/kotlin/android/main").get().asFile.mkdirs()
                }
            }

            tasks.named<ListComposePreviewsTask>("listComposePreviews") {
                doFirst {
                    println("previewClassesDirs=" + previewClassesDirs.files.joinToString("|") { it.invariantSeparatorsPath })
                    println("previewRuntimeClasspath=" + previewRuntimeClasspath.files.joinToString("|") { it.name })
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

        assertTrue(result.output.contains(":compileDebugKotlin"), result.output)
        assertTrue(!result.output.contains(":compileAndroidMain"), result.output)
        assertTrue(result.output.contains("build/tmp/kotlin-classes/debug"), result.output)
        assertTrue(result.output.contains("debug-runtime.jar"), result.output)
        assertTrue(!result.output.contains("build/classes/kotlin/android/main"), result.output)
        assertTrue(!result.output.contains("android-runtime.jar"), result.output)
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
