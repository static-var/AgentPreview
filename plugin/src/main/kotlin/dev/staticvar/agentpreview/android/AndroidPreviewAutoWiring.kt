/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.android

import dev.staticvar.agentpreview.AgentPreviewExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import java.io.File

class AndroidPreviewAutoWiring(
    private val project: Project,
    private val extension: AgentPreviewExtension,
) {
    fun configure() {
        project.afterEvaluate {
            val variantName = extension.android.variant.get()
            when {
                hasAndroidBackedVariant() -> configureAndroidBackedVariant(variantName)
                hasAndroidKmpLibraryShape() -> configureAndroidKmpLibrary()
            }
        }
    }

    private fun hasAndroidKmpLibraryShape(): Boolean =
        project.configurations.findByName(ANDROID_KMP_RUNTIME_CLASSPATH) != null ||
            project.tasks.findByName(ANDROID_KMP_COMPILE_TASK_NAME) != null ||
            androidKmpClassesDir().get().asFile.isDirectory

    private fun hasAndroidBackedVariant(): Boolean {
        val variantName = extension.android.variant.get()
        return ANDROID_PLUGIN_IDS.any(project.plugins::hasPlugin) ||
            project.configurations.findByName("${variantName}RuntimeClasspath") != null
    }

    private fun configureAndroidKmpLibrary() {
        extension.previewClassesDirs.from(androidKmpClassesDir().map { it.asFile })
        extension.previewRuntimeClasspath.from(androidKmpRuntimeClasspath())
        project.tasks.findByName(ANDROID_KMP_COMPILE_TASK_NAME)?.let(::wireDiscoveryTasksTo)
    }

    private fun configureAndroidBackedVariant(variantName: String) {
        extension.previewClassesDirs.from(classDirsFor(variantName))
        extension.previewRuntimeClasspath.from(runtimeClasspathFor(variantName))
        configureTaskDependencies(variantName)
    }

    private fun classDirsFor(variantName: String): List<Provider<File>> =
        listOf(
            project.layout.buildDirectory
                .dir("tmp/kotlin-classes/$variantName")
                .map { it.asFile },
            project.layout.buildDirectory
                .dir("intermediates/javac/$variantName/classes")
                .map { it.asFile },
            project.layout.buildDirectory
                .file(
                    "intermediates/compile_and_runtime_not_namespaced_r_class_jar/$variantName/process${variantName.replaceFirstChar {
                        it
                            .uppercaseChar()
                    }}Resources/R.jar",
                ).map { it.asFile },
        )

    private fun androidKmpClassesDir() = project.layout.buildDirectory.dir(ANDROID_KMP_CLASSES_DIR)

    private fun runtimeClasspathFor(variantName: String): FileCollection =
        project.files(
            project.configurations.findByName("${variantName}RuntimeClasspath"),
        )

    private fun androidKmpRuntimeClasspath(): FileCollection =
        project.files(project.configurations.findByName(ANDROID_KMP_RUNTIME_CLASSPATH))

    private fun configureTaskDependencies(variantName: String) {
        compileTaskNames(variantName).forEach { taskName ->
            project.tasks.findByName(taskName)?.let(::wireDiscoveryTasksTo)
        }
    }

    private fun wireDiscoveryTasksTo(task: Task) {
        AGENT_PREVIEW_TASK_NAMES.forEach { taskName ->
            project.tasks.named(taskName).configure { previewTask ->
                previewTask.dependsOn(task)
            }
        }
    }

    private fun compileTaskNames(variantName: String): Set<String> {
        val capitalized = variantName.replaceFirstChar { char -> char.uppercaseChar() }
        return setOf(
            "compile${capitalized}Kotlin",
            "compile${capitalized}KotlinAndroid",
            "compile${capitalized}JavaWithJavac",
        )
    }

    private companion object {
        const val ANDROID_KMP_CLASSES_DIR = "classes/kotlin/android/main"
        const val ANDROID_KMP_COMPILE_TASK_NAME = "compileAndroidMain"
        const val ANDROID_KMP_RUNTIME_CLASSPATH = "androidRuntimeClasspath"
        val ANDROID_PLUGIN_IDS = setOf("com.android.application", "com.android.library")
        val AGENT_PREVIEW_TASK_NAMES = setOf("listComposePreviews", "captureComposePreviews")
    }
}
