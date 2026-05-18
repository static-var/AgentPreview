package dev.staticvar.agentpreview.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class CaptureComposePreviewsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val includeUnmergedSemantics: Property<Boolean>

    @get:Input
    abstract val previewNameFilter: ListProperty<String>

    @TaskAction
    fun capture() {
        outputDirectory.get().asFile.mkdirs()
        logger.lifecycle("No Compose previews captured.")
    }
}
