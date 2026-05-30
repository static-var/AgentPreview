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
import java.io.File

internal class AndroidKmpPreviewWiring(
    private val project: Project,
    private val extension: AgentPreviewExtension,
) : AndroidPreviewWiringStrategy {
    override fun canWire(): Boolean {
        val hasRuntimeClasspath = project.configurations.findByName(ANDROID_KMP_RUNTIME_CLASSPATH) != null
        val hasCompileTask = project.tasks.findByName(ANDROID_KMP_COMPILE_TASK_NAME) != null
        val classesDir = androidKmpClassesDir().get().asFile
        val hasClassesDir = classesDir.isDirectory
        if (hasClassesDir && !hasRuntimeClasspath && !hasCompileTask) {
            project.logger.warn(
                "AgentPreview detected Android KMP previews from existing build output at ${classesDir.absolutePath}. " +
                    "This may be stale; run a clean build if preview discovery looks incorrect.",
            )
        }
        return hasRuntimeClasspath || hasCompileTask || hasClassesDir
    }

    override fun wire() {
        val classesDir = androidKmpClassesDir().get().asFile
        if (!classesDir.isDirectory) {
            project.logger.info(
                "AgentPreview inferred Android KMP class directory is absent: ${classesDir.absolutePath}",
            )
        }
        // Future pass: replace this hard-coded KMP output path with provider-backed Kotlin/Android Components APIs when available.
        extension.previewClassesDirs.from(androidKmpClassesDir().map { it.asFile })
        extension.previewRuntimeClasspath.from(androidKmpRuntimeClasspath())
        project.tasks.findByName(ANDROID_KMP_COMPILE_TASK_NAME)?.let(::wireDiscoveryTasksTo)
    }

    private fun androidKmpClassesDir() = project.layout.buildDirectory.dir(ANDROID_KMP_CLASSES_DIR)

    private fun androidKmpRuntimeClasspath(): FileCollection =
        project.files(project.configurations.findByName(ANDROID_KMP_RUNTIME_CLASSPATH))

    private fun wireDiscoveryTasksTo(task: Task) {
        AGENT_PREVIEW_TASK_NAMES.forEach { taskName ->
            project.tasks.named(taskName).configure { previewTask ->
                previewTask.dependsOn(task)
            }
        }
    }

    private companion object {
        const val ANDROID_KMP_CLASSES_DIR = "classes/kotlin/android/main"
        const val ANDROID_KMP_COMPILE_TASK_NAME = "compileAndroidMain"
        const val ANDROID_KMP_RUNTIME_CLASSPATH = "androidRuntimeClasspath"
        val AGENT_PREVIEW_TASK_NAMES = setOf("listComposePreviews", "captureComposePreviews")
    }
}
