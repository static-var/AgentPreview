/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.discovery.JsonIndexPreviewDiscovery
import dev.staticvar.agentpreview.discovery.PreviewDiscovery
import dev.staticvar.agentpreview.export.SnapshotExporter
import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.PreviewMetadata
import dev.staticvar.agentpreview.model.PreviewSnapshot
import dev.staticvar.agentpreview.render.FakePreviewRenderer
import dev.staticvar.agentpreview.semantics.EmptySemanticsExtractor
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class CaptureComposePreviewsTask : DefaultTask() {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val previewIndexFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val includeUnmergedSemantics: Property<Boolean>

    @get:Input
    abstract val previewNameFilter: ListProperty<String>

    @get:Input
    abstract val fakeRenderer: Property<Boolean>

    @get:Classpath
    abstract val previewClassesDirs: ConfigurableFileCollection

    @get:Classpath
    abstract val previewRuntimeClasspath: ConfigurableFileCollection

    @TaskAction
    fun capture() {
        val indexFile = previewIndexFile.get().asFile
        val filters = previewNameFilter.get().toSet()
        val previews =
            discoverPreviews(indexFile)
                .filter { filters.isEmpty() || it.id in filters || it.name in filters }
        val outputRoot = outputDirectory.get().asFile
        if (outputRoot.exists()) {
            outputRoot.deleteRecursively()
        }

        if (previews.isEmpty()) {
            logger.lifecycle("No Compose previews discovered.")
            return
        }

        if (!fakeRenderer.get()) {
            throw GradleException(
                "Production preview rendering is not implemented in phase 1. " +
                    "Use -PagentPreview.fakeRenderer=true for scaffold testing, or implement the production renderer in the next phase.",
            )
        }

        outputRoot.mkdirs()

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

    private fun discoverPreviews(indexFile: java.io.File): List<PreviewDescriptor> =
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

    private fun sourceLabel(preview: PreviewDescriptor): String =
        if (preview.sourceLine == null) preview.sourceFile else "${preview.sourceFile}:${preview.sourceLine}"
}
