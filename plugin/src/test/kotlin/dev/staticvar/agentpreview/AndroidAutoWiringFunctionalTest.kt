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
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AndroidAutoWiringFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `does not wire stale Android KMP build output without Kotlin compilation signals`() {
        writeSettings()
        projectDir.resolve("build/classes/kotlin/android/main").mkdirs()
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import dev.staticvar.agentpreview.tasks.ListComposePreviewsTask

            plugins {
                id("dev.staticvar.agentpreview")
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

        assertTrue(!result.output.contains("build/classes/kotlin/android/main"), result.output)
        assertTrue(result.output.contains("No Compose previews discovered."), result.output)
    }

    @Test
    fun `wires Android Components classes and selected debug runtime classpath into preview tasks`() {
        writeSettings(requireAndroidSdk = true)
        projectDir.resolve("debug-runtime.jar").writeText("debug runtime")
        projectDir.resolve("release-runtime.jar").writeText("release runtime")
        projectDir.resolve("manual-runtime.jar").writeText("manual runtime")
        projectDir.resolve("manual-classes").mkdirs()
        projectDir.resolve("build.gradle.kts").writeText(
            androidLibraryBuildScript(
                """
                agentPreview {
                    previewClassesDirs.from(files("manual-classes"))
                    previewRuntimeClasspath.from(files("manual-runtime.jar"))
                }

                dependencies {
                    add("debugRuntimeOnly", files("debug-runtime.jar"))
                    add("releaseRuntimeOnly", files("release-runtime.jar"))
                }

                tasks.named<ListComposePreviewsTask>("listComposePreviews") {
                    doFirst {
                        println("manualPreviewClassesDirs=" + previewClassesDirs.files.joinToString("|") { it.invariantSeparatorsPath })
                        println("androidProjectClassDirs=" + androidProjectClassDirs.get().joinToString("|") { it.asFile.invariantSeparatorsPath })
                        println("androidProjectClassJars=" + androidProjectClassJars.get().joinToString("|") { it.asFile.invariantSeparatorsPath })
                        println("previewRuntimeClasspath=" + previewRuntimeClasspath.files.joinToString("|") { it.name })
                    }
                }
                """.trimIndent(),
            ),
        )
        writeEmptyPreviewIndex()

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("listComposePreviews")
                .withPluginClasspath()
                .build()

        assertTrue(result.output.contains(":compileDebugJavaWithJavac"), result.output)
        assertTrue(result.output.contains("manualPreviewClassesDirs="), result.output)
        assertTrue(result.output.contains("manual-classes"), result.output)
        assertTrue(result.output.contains("androidProjectClassDirs="), result.output)
        assertTrue(result.output.contains("debug-runtime.jar"), result.output)
        assertTrue(result.output.contains("manual-runtime.jar"), result.output)
        assertTrue(!result.output.contains("release-runtime.jar"), result.output)
        assertTrue(result.output.contains("No Compose previews discovered."), result.output)
    }

    @Test
    fun `does not treat plain JVM target named android as Android KMP fallback`() {
        writeSettings()
        projectDir.resolve("android-runtime.jar").writeText("runtime")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import dev.staticvar.agentpreview.tasks.ListComposePreviewsTask

            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "2.3.21"
                id("dev.staticvar.agentpreview")
            }

            kotlin {
                jvm("android")
            }

            dependencies {
                add("androidMainRuntimeOnly", files("android-runtime.jar"))
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

        assertTrue(!result.output.contains(":compileKotlinAndroid"), result.output)
        assertTrue(!result.output.contains("build/classes/kotlin/android/main"), result.output)
        assertTrue(!result.output.contains("android-runtime.jar"), result.output)
        assertTrue(result.output.contains("No Compose previews discovered."), result.output)
    }

    @Test
    fun `wires Android KMP components that expose selector backed onVariants`() {
        writeSettings(includeAndroidKmpStub = true)
        projectDir.resolve("android-kmp-runtime.jar").writeText("runtime")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import dev.staticvar.agentpreview.tasks.ListComposePreviewsTask

            plugins {
                id("com.android.kotlin.multiplatform.library")
                id("dev.staticvar.agentpreview")
            }

            dependencies {
                add("androidMainRuntimeClasspath", files("android-kmp-runtime.jar"))
            }

            tasks.named<ListComposePreviewsTask>("listComposePreviews") {
                doFirst {
                    println("previewRuntimeClasspath=" + previewRuntimeClasspath.files.joinToString("|") { it.name })
                }
            }
            """.trimIndent(),
        )
        writeEmptyPreviewIndex()
        writeAndroidKmpStubPlugin()

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("listComposePreviews")
                .withPluginClasspath()
                .build()

        assertTrue(result.output.contains("android-kmp-runtime.jar"), result.output)
        assertTrue(result.output.contains("No Compose previews discovered."), result.output)
    }

    @Test
    fun `non-preview tasks still run when release variant is configured`() {
        writeSettings(requireAndroidSdk = true)
        projectDir.resolve("build.gradle.kts").writeText(
            androidLibraryBuildScript(
                """
                agentPreview {
                    android {
                        variant.set("release")
                    }
                }
                """.trimIndent(),
            ),
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
    fun `fails at preview task execution when release variant is configured for auto wiring`() {
        writeSettings(requireAndroidSdk = true)
        projectDir.resolve("debug-runtime.jar").writeText("debug runtime")
        projectDir.resolve("release-runtime.jar").writeText("release runtime")
        projectDir.resolve("build.gradle.kts").writeText(
            androidLibraryBuildScript(
                """
                agentPreview {
                    android {
                        variant.set("release")
                    }
                }

                dependencies {
                    add("debugRuntimeOnly", files("debug-runtime.jar"))
                    add("releaseRuntimeOnly", files("release-runtime.jar"))
                }

                tasks.named<ListComposePreviewsTask>("listComposePreviews") {
                    doFirst {
                        println("previewRuntimeClasspath=" + previewRuntimeClasspath.files.joinToString("|") { it.name })
                    }
                }
                """.trimIndent(),
            ),
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

    private fun writeSettings(
        requireAndroidSdk: Boolean = false,
        includeAndroidKmpStub: Boolean = false,
    ) {
        val sdkDir = androidSdkDir()
        if (requireAndroidSdk) {
            assumeTrue(sdkDir != null, "Android SDK not configured; set ANDROID_HOME or ANDROID_SDK_ROOT")
        }
        sdkDir?.let { projectDir.resolve("local.properties").writeText("sdk.dir=${it.invariantSeparatorsPath}\n") }
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                ${if (includeAndroidKmpStub) """includeBuild("android-kmp-stub-plugin")""" else ""}
                repositories {
                    gradlePluginPortal()
                    google()
                    mavenCentral()
                }
            }

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }
            """.trimIndent(),
        )
    }

    private fun writeAndroidKmpStubPlugin() {
        val stubDir = projectDir.resolve("android-kmp-stub-plugin")
        stubDir.mkdirs()
        stubDir.resolve("settings.gradle.kts").writeText("""rootProject.name = "android-kmp-stub-plugin"""")
        stubDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                `java-gradle-plugin`
            }

            gradlePlugin {
                plugins {
                    create("androidKmpStub") {
                        id = "com.android.kotlin.multiplatform.library"
                        implementationClass = "stub.AndroidKmpStubPlugin"
                    }
                }
            }
            """.trimIndent(),
        )
        stubDir.resolve("src/main/java/com/android/build/api/variant/ScopedArtifacts.java").writeTextCreatingParents(
            """
            package com.android.build.api.variant;

            public final class ScopedArtifacts {
                public enum Scope {
                    PROJECT,
                    ALL
                }
            }
            """.trimIndent(),
        )
        stubDir.resolve("src/main/java/com/android/build/api/artifact/ScopedArtifact.java").writeTextCreatingParents(
            """
            package com.android.build.api.artifact;

            public final class ScopedArtifact {
                public static final class CLASSES {
                    public static final CLASSES INSTANCE = new CLASSES();

                    private CLASSES() {
                    }
                }
            }
            """.trimIndent(),
        )
        stubDir.resolve("src/main/java/stub/AndroidKmpStubPlugin.java").writeTextCreatingParents(
            """
            package stub;

            import com.android.build.api.variant.ScopedArtifacts;
            import org.gradle.api.Action;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.artifacts.Configuration;
            import org.gradle.api.tasks.TaskProvider;

            public final class AndroidKmpStubPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    Configuration runtimeClasspath = project.getConfigurations().create("androidMainRuntimeClasspath");
                    project.getExtensions().add("androidComponents", new AndroidComponents(runtimeClasspath));
                }

                public static final class AndroidComponents {
                    private final Configuration runtimeClasspath;

                    AndroidComponents(Configuration runtimeClasspath) {
                        this.runtimeClasspath = runtimeClasspath;
                    }

                    public Selector selector() {
                        return new Selector();
                    }

                    public void onVariants(Selector selector, Action<Object> action) {
                        action.execute(new Variant(runtimeClasspath));
                    }
                }

                public static final class Selector {
                    public Selector all() {
                        return this;
                    }
                }

                public static final class Variant {
                    private final Configuration runtimeClasspath;

                    Variant(Configuration runtimeClasspath) {
                        this.runtimeClasspath = runtimeClasspath;
                    }

                    public String getName() {
                        return "android";
                    }

                    public Configuration getRuntimeConfiguration() {
                        return runtimeClasspath;
                    }

                    public Artifacts getArtifacts() {
                        return new Artifacts();
                    }
                }

                public static final class Artifacts {
                    public Artifacts forScope(ScopedArtifacts.Scope scope) {
                        return this;
                    }

                    public Artifacts use(TaskProvider<?> taskProvider) {
                        return this;
                    }

                    public void toGet(Object classesArtifact, Object jarsProperty, Object dirsProperty) {
                    }
                }
            }
            """.trimIndent(),
        )
    }

    private fun File.writeTextCreatingParents(text: String) {
        parentFile.mkdirs()
        writeText(text)
    }

    private fun androidSdkDir(): File? =
        listOfNotNull(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
        ).map(::File)
            .firstOrNull { it.isDirectory }
            ?: File(System.getProperty("user.home"), "Library/Android/sdk").takeIf { it.isDirectory }

    private fun writeEmptyPreviewIndex() {
        projectDir.resolve("build/agentPreview/discovered-previews.json").apply {
            parentFile.mkdirs()
            writeText("[]")
        }
    }

    private fun androidLibraryBuildScript(body: String): String =
        """
        import dev.staticvar.agentpreview.tasks.ListComposePreviewsTask

        plugins {
            id("com.android.library") version "8.13.2"
            id("dev.staticvar.agentpreview")
        }

        android {
            namespace = "dev.staticvar.agentpreview.test"
            compileSdk = 36
        }

        $body
        """.trimIndent()
}
