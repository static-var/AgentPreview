/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.config.AndroidPreviewConfigValidator
import dev.staticvar.agentpreview.discovery.JsonIndexPreviewDiscovery
import dev.staticvar.agentpreview.discovery.PreviewDiscovery
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class ListComposePreviewsTask : DefaultTask() {
    @get:Input
    abstract val previewIndexFilePath: Property<String>

    @get:Input
    abstract val previewIndexContent: Property<String>

    @get:Input
    abstract val previewNameFilter: ListProperty<String>

    @get:Classpath
    abstract val previewClassesDirs: ConfigurableFileCollection

    @get:Classpath
    abstract val previewRuntimeClasspath: ConfigurableFileCollection

    @get:Input
    abstract val robolectricSdk: Property<Int>

    @get:Input
    abstract val javaMajorVersion: Property<Int>

    @TaskAction
    fun list() {
        val indexFile = File(previewIndexFilePath.get())
        warnIfConfigurationIsIncompatible()
        val filters = previewNameFilter.get().toSet()
        val previews =
            discoverPreviews(indexFile)
                .filter { filters.isEmpty() || it.id in filters || it.name in filters }

        if (previews.isEmpty()) {
            logger.lifecycle("No Compose previews discovered.")
            return
        }

        previews.forEach { preview ->
            val label = preview.name ?: preview.fullyQualifiedFunctionName
            logger.lifecycle("${preview.id}  $label")
        }
    }

    private fun warnIfConfigurationIsIncompatible() {
        AndroidPreviewConfigValidator
            .warning(
                robolectricSdk = robolectricSdk.get(),
                javaMajorVersion = javaMajorVersion.get(),
            )?.let { warning -> logger.warn(warning) }
    }

    private fun discoverPreviews(indexFile: File) =
        if (previewClassesDirs.files.isNotEmpty()) {
            PreviewDiscovery(
                projectPath = project.path,
                sourceSetName = "main",
                classesDirs = previewClassesDirs.files.toList(),
                runtimeClasspath = previewRuntimeClasspath.files.toList(),
            ).discover()
        } else {
            JsonIndexPreviewDiscovery(indexFile).discover()
        }
}
