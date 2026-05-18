package dev.staticvar.agentpreview.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class ListComposePreviewsTask : DefaultTask() {
    @get:Input
    abstract val previewNameFilter: ListProperty<String>

    @TaskAction
    fun list() {
        logger.lifecycle("No Compose previews discovered.")
    }
}
