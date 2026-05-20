/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.discovery.JsonIndexPreviewDiscovery
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class ListComposePreviewsTask : DefaultTask() {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val previewIndexFile: RegularFileProperty

    @get:Input
    abstract val previewNameFilter: ListProperty<String>

    @TaskAction
    fun list() {
        val indexFile = previewIndexFile.get().asFile
        val filters = previewNameFilter.get().toSet()
        val previews =
            JsonIndexPreviewDiscovery(indexFile)
                .discover()
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
}
