/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview

import dev.staticvar.agentpreview.tasks.CaptureComposePreviewsTask
import dev.staticvar.agentpreview.tasks.ListComposePreviewsTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class AgentPreviewPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension =
            project.extensions.create(
                "agentPreview",
                AgentPreviewExtension::class.java,
                project,
            )

        project.tasks.register("listComposePreviews", ListComposePreviewsTask::class.java) {
            it.group = "agent preview"
            it.description = "Lists Compose previews discoverable by Preview For Agents."
            it.previewNameFilter.set(extension.previewNameFilter)
        }

        project.tasks.register("captureComposePreviews", CaptureComposePreviewsTask::class.java) {
            it.group = "agent preview"
            it.description = "Captures Compose previews into screenshot.png and snapshot.json bundles."
            it.outputDirectory.set(extension.outputDirectory)
            it.includeUnmergedSemantics.set(extension.includeUnmergedSemantics)
            it.previewNameFilter.set(extension.previewNameFilter)
        }
    }
}
