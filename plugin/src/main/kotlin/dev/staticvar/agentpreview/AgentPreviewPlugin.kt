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
            it.previewIndexFile.set(project.layout.buildDirectory.file("agentPreview/discovered-previews.json"))
            it.previewNameFilter.set(extension.previewNameFilter)
            it.previewClassesDirs.from(extension.previewClassesDirs)
            it.previewRuntimeClasspath.from(extension.previewRuntimeClasspath)
        }

        project.tasks.register("captureComposePreviews", CaptureComposePreviewsTask::class.java) {
            it.group = "agent preview"
            it.description = "Captures Compose previews into screenshot.png and snapshot.json bundles."
            it.previewIndexFile.set(project.layout.buildDirectory.file("agentPreview/discovered-previews.json"))
            it.outputDirectory.set(extension.outputDirectory)
            it.includeUnmergedSemantics.set(extension.includeUnmergedSemantics)
            it.previewNameFilter.set(extension.previewNameFilter)
            it.previewClassesDirs.from(extension.previewClassesDirs)
            it.previewRuntimeClasspath.from(extension.previewRuntimeClasspath)
            it.fakeRenderer.set(
                project.providers
                    .gradleProperty("agentPreview.fakeRenderer")
                    .map(String::toBoolean)
                    .orElse(false),
            )
        }
    }
}
