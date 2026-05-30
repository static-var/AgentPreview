/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.android

import dev.staticvar.agentpreview.tasks.CaptureComposePreviewsTask
import dev.staticvar.agentpreview.tasks.ListComposePreviewsTask
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskProvider

internal class AndroidKmpPreviewWiring(
    private val project: Project,
    private val listComposePreviews: TaskProvider<ListComposePreviewsTask>,
    private val captureComposePreviews: TaskProvider<CaptureComposePreviewsTask>,
) {
    fun configure() {
        project.plugins.withId(KOTLIN_MULTIPLATFORM_PLUGIN_ID) {
            val kotlin = project.extensions.findByName("kotlin") ?: return@withId
            val targets = kotlin.getNamedCollection("getTargets") ?: return@withId
            targets.configureEach { target ->
                if (target.isAndroidKmpFallbackCandidate()) {
                    target.getNamedCollection("getCompilations")?.configureEach { compilation ->
                        if (compilation.name == MAIN_COMPILATION_NAME) {
                            wireCompilation(compilation)
                        }
                    }
                }
            }
        }
    }

    private fun Named.isAndroidKmpFallbackCandidate(): Boolean =
        !AndroidVariantPreviewWiring.hasStandardAndroidPlugin(project) &&
            !AndroidVariantPreviewWiring.hasAndroidKmpComponents(project) &&
            platformTypeName() == ANDROID_PLATFORM_TYPE

    private fun wireCompilation(compilation: Named) {
        val classesDirs = compilation.outputClassesDirs() ?: return
        val runtimeDependencyFiles = compilation.runtimeDependencyFiles()
        val compileTaskProvider = compilation.compileTaskProvider()
        val fallbackClassesDirs = fallbackOnly(classesDirs)
        val fallbackRuntimeDependencyFiles = runtimeDependencyFiles?.let(::fallbackOnly)
        val fallbackCompileTask =
            project.provider {
                if (AndroidVariantPreviewWiring.hasAndroidKmpComponents(project)) emptyList() else listOfNotNull(compileTaskProvider)
            }
        listComposePreviews.configure { task ->
            task.previewClassesDirs.from(fallbackClassesDirs)
            fallbackRuntimeDependencyFiles?.let { files -> task.previewRuntimeClasspath.from(files) }
            task.dependsOn(fallbackCompileTask)
        }
        captureComposePreviews.configure { task ->
            task.previewClassesDirs.from(fallbackClassesDirs)
            fallbackRuntimeDependencyFiles?.let { files -> task.previewRuntimeClasspath.from(files) }
            task.dependsOn(fallbackCompileTask)
        }
    }

    private fun fallbackOnly(files: FileCollection): FileCollection =
        project.files(
            project.provider {
                if (AndroidVariantPreviewWiring.hasAndroidKmpComponents(project)) emptyList() else files.files
            },
        )

    private fun Any.getNamedCollection(methodName: String): NamedDomainObjectCollection<Named>? {
        val collection = invoke(methodName) as? NamedDomainObjectCollection<*> ?: return null
        @Suppress("UNCHECKED_CAST")
        return collection as NamedDomainObjectCollection<Named>
    }

    private fun Any.platformTypeName(): String? = invoke("getPlatformType")?.toString()

    private fun Any.runtimeDependencyFiles(): FileCollection? = invoke("getRuntimeDependencyFiles") as? FileCollection

    private fun Any.compileTaskProvider(): TaskProvider<*>? = invoke("getCompileTaskProvider") as? TaskProvider<*>

    private fun Any.outputClassesDirs(): FileCollection? =
        invoke("getOutput")?.let { output -> output.invoke("getClassesDirs") as? FileCollection }

    private fun Any.invoke(methodName: String): Any? =
        runCatching {
            javaClass.methods
                .firstOrNull { method -> method.name == methodName && method.parameterCount == 0 }
                ?.invoke(this)
        }.getOrNull()

    private companion object {
        const val KOTLIN_MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"
        const val MAIN_COMPILATION_NAME = "main"
        const val ANDROID_PLATFORM_TYPE = "androidJvm"
    }
}
