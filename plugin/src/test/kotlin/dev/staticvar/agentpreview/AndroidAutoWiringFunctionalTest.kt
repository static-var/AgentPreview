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

@Suppress("LargeClass")
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
    fun `wires real Android KMP components into preview tasks`() {
        writeSettings(requireAndroidSdk = true)
        projectDir.resolve("src/commonMain/kotlin/dev/staticvar/agentpreview/test/DesignToken.kt").writeTextCreatingParents(
            """
            package dev.staticvar.agentpreview.test

            object DesignToken {
                const val Radius = 8
            }
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import dev.staticvar.agentpreview.tasks.ListComposePreviewsTask

            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "2.3.21"
                id("com.android.kotlin.multiplatform.library") version "8.13.2"
                id("dev.staticvar.agentpreview")
            }

            kotlin {
                androidLibrary {
                    namespace = "dev.staticvar.agentpreview.test"
                    compileSdk = 36
                    minSdk = 23
                }
            }

            tasks.named<ListComposePreviewsTask>("listComposePreviews") {
                doFirst {
                    println("previewClassesDirs=" + previewClassesDirs.files.joinToString("|") { it.invariantSeparatorsPath })
                    println("androidProjectClassDirs=" + androidProjectClassDirs.get().joinToString("|") { it.asFile.invariantSeparatorsPath })
                    println("androidRuntimeClassDirs=" + androidRuntimeClassDirs.get().joinToString("|") { it.asFile.invariantSeparatorsPath })
                    println("previewRuntimeClasspath=" + previewRuntimeClasspath.files.joinToString("|") { it.invariantSeparatorsPath })
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

        assertEquals(TaskOutcome.SUCCESS, result.task(":listComposePreviews")?.outcome)
        assertTrue(result.output.contains(":compileAndroidMain"), result.output)
        assertTrue(result.output.contains("previewClassesDirs=\n"), result.output)
        assertTrue(result.output.contains("androidProjectClassDirs="), result.output)
        assertTrue(result.output.contains("build/classes/kotlin/android/main"), result.output)
        assertTrue(result.output.contains("androidRuntimeClassDirs="), result.output)
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
    fun `wires Android KMP components under configuration cache`() {
        writeSettings(includeAndroidKmpStub = true)
        projectDir.resolve("android-kmp-runtime.jar").writeText("runtime")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.kotlin.multiplatform.library")
                id("dev.staticvar.agentpreview")
            }

            dependencies {
                add("androidMainRuntimeClasspath", files("android-kmp-runtime.jar"))
            }

            tasks.named("listComposePreviews") {
                doFirst {
                    fun property(name: String) = javaClass.getMethod(name).invoke(this)
                    fun listProperty(name: String): Iterable<*> =
                        property(name).javaClass.getMethod("get").invoke(property(name)) as Iterable<*>

                    println("androidProjectClassDirs=" + listProperty("getAndroidProjectClassDirs").joinToString("|") { it.toString() })
                    println("androidRuntimeClassDirs=" + listProperty("getAndroidRuntimeClassDirs").joinToString("|") { it.toString() })
                    val runtimeClasspath = property("getPreviewRuntimeClasspath") as org.gradle.api.file.FileCollection
                    println("previewRuntimeClasspath=" + runtimeClasspath.files.joinToString("|") { it.name })
                }
            }
            """.trimIndent(),
        )
        writeEmptyPreviewIndex()
        writeAndroidKmpStubPlugin()

        val arguments = listOf("listComposePreviews", "--configuration-cache", "--warning-mode", "all")
        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments(arguments)
                .withPluginClasspath()
                .build()

        assertTrue(result.output.contains("android-kmp-runtime.jar"), result.output)
        assertTrue(result.output.contains("stub-classes/project"), result.output)
        assertTrue(result.output.contains("stub-classes/all"), result.output)
        assertTrue(result.output.contains("Configuration cache entry stored"), result.output)

        val reuseResult =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments(arguments)
                .withPluginClasspath()
                .build()

        assertTrue(reuseResult.output.contains("Configuration cache entry reused"), reuseResult.output)
    }

    @Test
    fun `does not duplicate Kotlin compilation fallback when Android KMP components are active`() {
        assertAndroidKmpComponentsDoNotUseKotlinFallback(
            """
            plugins {
                id("com.android.kotlin.multiplatform.library")
                id("org.jetbrains.kotlin.multiplatform")
                id("dev.staticvar.agentpreview")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `does not duplicate Kotlin fallback when Android KMP plugin is applied after AgentPreview`() {
        assertAndroidKmpComponentsDoNotUseKotlinFallback(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("dev.staticvar.agentpreview")
                id("com.android.kotlin.multiplatform.library")
            }
            """.trimIndent(),
        )
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

    private fun assertAndroidKmpComponentsDoNotUseKotlinFallback(pluginsBlock: String) {
        writeSettings(includeAndroidKmpStub = true)
        projectDir.resolve("android-kmp-runtime.jar").writeText("component runtime")
        projectDir.resolve("fallback-runtime.jar").writeText("fallback runtime")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import dev.staticvar.agentpreview.tasks.ListComposePreviewsTask

            $pluginsBlock

            dependencies {
                add("androidMainRuntimeClasspath", files("android-kmp-runtime.jar"))
                add("fallbackRuntimeClasspath", files("fallback-runtime.jar"))
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
        writeAndroidKmpStubPlugin()

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("listComposePreviews")
                .withPluginClasspath()
                .build()

        assertTrue(result.output.contains("android-kmp-runtime.jar"), result.output)
        assertTrue(!result.output.contains("fallback-runtime.jar"), result.output)
        assertTrue(!result.output.contains("fallback-classes"), result.output)
        assertTrue(!result.output.contains(":compileFallbackKotlinAndroid"), result.output)
        assertTrue(result.output.contains("No Compose previews discovered."), result.output)
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
                    create("kotlinMultiplatformStub") {
                        id = "org.jetbrains.kotlin.multiplatform"
                        implementationClass = "stub.KotlinMultiplatformStubPlugin"
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
            import org.gradle.api.Named;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.artifacts.Configuration;
            import org.gradle.api.file.FileCollection;
            import org.gradle.api.NamedDomainObjectContainer;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Provider;
            import org.gradle.api.tasks.TaskProvider;
            import java.util.Locale;
            import kotlin.jvm.functions.Function1;

            public final class AndroidKmpStubPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    Configuration runtimeClasspath = project.getConfigurations().create("androidMainRuntimeClasspath");
                    project.getExtensions().add("androidComponents", new AndroidComponents(project, runtimeClasspath));
                }

                public static final class AndroidComponents {
                    private final Project project;
                    private final Configuration runtimeClasspath;

                    AndroidComponents(Project project, Configuration runtimeClasspath) {
                        this.project = project;
                        this.runtimeClasspath = runtimeClasspath;
                    }

                    public Selector selector() {
                        return new Selector();
                    }

                    public void onVariants(Selector selector, Action<Object> action) {
                        action.execute(new Variant(project, runtimeClasspath));
                    }
                }

                public static final class Selector {
                    public Selector all() {
                        return this;
                    }
                }

                public static final class Variant {
                    private final Project project;
                    private final Configuration runtimeClasspath;

                    Variant(Project project, Configuration runtimeClasspath) {
                        this.project = project;
                        this.runtimeClasspath = runtimeClasspath;
                    }

                    public String getName() {
                        return "android";
                    }

                    public Configuration getRuntimeConfiguration() {
                        return runtimeClasspath;
                    }

                    public Artifacts getArtifacts() {
                        return new Artifacts(project);
                    }
                }

                public static final class Artifacts {
                    private final Project project;
                    private ScopedArtifacts.Scope scope;
                    private TaskProvider<?> taskProvider;

                    Artifacts(Project project) {
                        this.project = project;
                    }

                    public Artifacts forScope(ScopedArtifacts.Scope scope) {
                        this.scope = scope;
                        return this;
                    }

                    public Artifacts use(TaskProvider<?> taskProvider) {
                        this.taskProvider = taskProvider;
                        return this;
                    }

                    public void toGet(Object classesArtifact, Object jarsProperty, Object dirsProperty) {
                        @SuppressWarnings("unchecked")
                        ListProperty<Object> dirs = (ListProperty<Object>) ((Function1<Object, Object>) dirsProperty).invoke(taskProvider.get());
                        Provider<?> stubClasses = project.getLayout().getBuildDirectory().dir(
                            "stub-classes/" + scope.name().toLowerCase(Locale.ROOT)
                        );
                        dirs.add((Provider<Object>) stubClasses);
                    }
                }
            }
            """.trimIndent(),
        )
        stubDir.resolve("src/main/java/stub/KotlinMultiplatformStubPlugin.java").writeTextCreatingParents(
            """
            package stub;

            import org.gradle.api.Named;
            import org.gradle.api.NamedDomainObjectContainer;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.artifacts.Configuration;
            import org.gradle.api.file.FileCollection;
            import org.gradle.api.tasks.TaskProvider;

            public final class KotlinMultiplatformStubPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    Configuration runtimeClasspath = project.getConfigurations().create("fallbackRuntimeClasspath");
                    TaskProvider<?> compileTask = project.getTasks().register("compileFallbackKotlinAndroid");
                    project.getExtensions().add("kotlin", new KotlinExtension(project, runtimeClasspath, compileTask));
                }

                public static final class KotlinExtension {
                    private final NamedDomainObjectContainer<Target> targets;

                    KotlinExtension(Project project, Configuration runtimeClasspath, TaskProvider<?> compileTask) {
                        this.targets = project.container(Target.class, name -> new Target(name, project, runtimeClasspath, compileTask));
                        this.targets.add(new Target("android", project, runtimeClasspath, compileTask));
                    }

                    public NamedDomainObjectContainer<Target> getTargets() {
                        return targets;
                    }
                }

                public static final class Target implements Named {
                    private final String name;
                    private final NamedDomainObjectContainer<Compilation> compilations;

                    Target(String name, Project project, Configuration runtimeClasspath, TaskProvider<?> compileTask) {
                        this.name = name;
                        this.compilations = project.container(Compilation.class, compilationName -> new Compilation(compilationName, project, runtimeClasspath, compileTask));
                        this.compilations.add(new Compilation("main", project, runtimeClasspath, compileTask));
                    }

                    @Override
                    public String getName() {
                        return name;
                    }

                    public Object getPlatformType() {
                        return new Object() {
                            @Override
                            public String toString() {
                                return "androidJvm";
                            }
                        };
                    }

                    public NamedDomainObjectContainer<Compilation> getCompilations() {
                        return compilations;
                    }
                }

                public static final class Compilation implements Named {
                    private final String name;
                    private final Output output;
                    private final Configuration runtimeClasspath;
                    private final TaskProvider<?> compileTask;

                    Compilation(String name, Project project, Configuration runtimeClasspath, TaskProvider<?> compileTask) {
                        this.name = name;
                        this.output = new Output(project.files(project.getLayout().getBuildDirectory().dir("fallback-classes")));
                        this.runtimeClasspath = runtimeClasspath;
                        this.compileTask = compileTask;
                    }

                    @Override
                    public String getName() {
                        return name;
                    }

                    public Output getOutput() {
                        return output;
                    }

                    public FileCollection getRuntimeDependencyFiles() {
                        return runtimeClasspath;
                    }

                    public TaskProvider<?> getCompileTaskProvider() {
                        return compileTask;
                    }
                }

                public static final class Output {
                    private final FileCollection classesDirs;

                    Output(FileCollection classesDirs) {
                        this.classesDirs = classesDirs;
                    }

                    public FileCollection getClassesDirs() {
                        return classesDirs;
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
