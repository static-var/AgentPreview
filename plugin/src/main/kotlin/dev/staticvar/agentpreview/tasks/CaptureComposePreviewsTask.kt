/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.discovery.JsonIndexPreviewDiscovery
import dev.staticvar.agentpreview.export.SnapshotExporter
import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.PreviewMetadata
import dev.staticvar.agentpreview.model.PreviewSnapshot
import dev.staticvar.agentpreview.render.FakePreviewRenderer
import dev.staticvar.agentpreview.semantics.EmptySemanticsExtractor
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
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
        val indexFile =
            project.layout.buildDirectory
                .file("agentPreview/discovered-previews.json")
                .get()
                .asFile
        val filters = previewNameFilter.get().toSet()
        val previews =
            JsonIndexPreviewDiscovery(indexFile)
                .discover()
                .filter { filters.isEmpty() || it.id in filters || it.name in filters }

        if (previews.isEmpty()) {
            logger.lifecycle("No Compose previews discovered.")
            return
        }

        val fakeRendererEnabled =
            project.providers
                .gradleProperty("agentPreview.fakeRenderer")
                .map(String::toBoolean)
                .getOrElse(false)

        if (!fakeRendererEnabled) {
            throw GradleException(
                "Production preview rendering is not implemented in phase 1. " +
                    "Use -PagentPreview.fakeRenderer=true for scaffold testing, or implement the production renderer in the next phase.",
            )
        }

        val outputRoot = outputDirectory.get().asFile
        val renderOutput =
            project.layout.buildDirectory
                .dir("agentPreview/fakeRender")
                .get()
                .asFile
        val renderer = FakePreviewRenderer()
        val semanticsExtractor = EmptySemanticsExtractor()
        val exporter = SnapshotExporter()

        previews.forEach { preview ->
            val renderResult = renderer.render(preview, renderOutput)
            val snapshot =
                PreviewSnapshot(
                    schemaVersion = 1,
                    preview =
                        PreviewMetadata(
                            id = preview.id,
                            name = preview.name,
                            group = preview.group,
                            source = sourceLabel(preview),
                            sourceSet = preview.sourceSet,
                        ),
                    viewport = renderResult.viewport,
                    nodes = semanticsExtractor.extract(renderResult.rawSemantics),
                )

            exporter.export(
                previewId = preview.id,
                screenshotFile = renderResult.screenshotFile,
                snapshot = snapshot,
                outputRoot = outputRoot,
            )
            logger.lifecycle("Captured ${preview.id}")
        }
    }

    private fun sourceLabel(preview: PreviewDescriptor): String =
        if (preview.sourceLine == null) preview.sourceFile else "${preview.sourceFile}:${preview.sourceLine}"
}
