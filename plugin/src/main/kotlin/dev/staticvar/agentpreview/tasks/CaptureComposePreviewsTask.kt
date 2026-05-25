/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.config.AndroidPreviewConfigValidator
import dev.staticvar.agentpreview.config.ConfiguredViewport
import dev.staticvar.agentpreview.discovery.JsonIndexPreviewDiscovery
import dev.staticvar.agentpreview.discovery.PreviewDiscovery
import dev.staticvar.agentpreview.discovery.PreviewDiscoveryResult
import dev.staticvar.agentpreview.export.SnapshotExporter
import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.PreviewMetadata
import dev.staticvar.agentpreview.model.PreviewSnapshot
import dev.staticvar.agentpreview.model.SnapshotRenderMetadata
import dev.staticvar.agentpreview.model.Viewport
import dev.staticvar.agentpreview.render.FakePreviewRenderer
import dev.staticvar.agentpreview.render.PreviewRendererImpl
import dev.staticvar.agentpreview.scanner.discovery.PreviewScanDiagnostic
import dev.staticvar.agentpreview.semantics.EmptySemanticsExtractor
import dev.staticvar.agentpreview.semantics.RenderedSemanticsExtractor
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class CaptureComposePreviewsTask : DefaultTask() {
    @get:Input
    abstract val previewIndexFilePath: Property<String>

    @get:Input
    abstract val previewIndexContent: Property<String>

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

    @get:Input
    abstract val androidViewportsJson: Property<String>

    @get:Input
    abstract val robolectricSdk: Property<Int>

    @get:Input
    abstract val javaMajorVersion: Property<Int>

    @TaskAction
    fun capture() {
        val indexFile = File(previewIndexFilePath.get())
        warnIfConfigurationIsIncompatible()
        val filters = previewNameFilter.get().toSet()
        val discoveryResult = discoverPreviews(indexFile)
        logDiagnostics(discoveryResult.diagnostics)
        val previews =
            discoveryResult.previews
                .filter { filters.isEmpty() || it.id in filters || it.name in filters }
        val outputRoot = outputDirectory.get().asFile
        if (outputRoot.exists()) {
            outputRoot.deleteRecursively()
        }

        if (previews.isEmpty()) {
            logger.lifecycle("No Compose previews discovered.")
            return
        }

        outputRoot.mkdirs()

        val useFakeRenderer = fakeRenderer.get()
        val renderOutput =
            project.layout.buildDirectory
                .dir("agentPreview/render")
                .get()
                .asFile
        val fakePreviewRenderer = FakePreviewRenderer()
        val previewRenderer by lazy {
            PreviewRendererImpl(
                robolectricSdk = robolectricSdk.get(),
                previewClasspath =
                    (previewClassesDirs.files + previewRuntimeClasspath.files + rendererRuntimeClasspathIfAndroidBacked()).toList(),
                includeUnmergedSemantics = includeUnmergedSemantics.get(),
            )
        }
        val emptySemanticsExtractor = EmptySemanticsExtractor()
        val renderedSemanticsExtractor = RenderedSemanticsExtractor()
        val exporter = SnapshotExporter()

        previews.forEach { preview ->
            viewportsFor(preview).forEach { viewport ->
                val renderResult =
                    if (useFakeRenderer) {
                        fakePreviewRenderer.render(preview, viewport, renderOutput)
                    } else {
                        previewRenderer.render(preview, viewport, renderOutput)
                    }
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
                        nodes =
                            if (useFakeRenderer) {
                                emptySemanticsExtractor.extract(renderResult.rawSemantics)
                            } else {
                                renderedSemanticsExtractor.extract(renderResult.rawSemantics)
                            },
                        layoutTree = renderResult.layoutTree.takeUnless { useFakeRenderer }.orEmpty(),
                        render = SnapshotRenderMetadata(mode = renderResult.renderMode.logLabel),
                    )

                exporter.export(
                    previewId = preview.id,
                    screenshotFile = renderResult.screenshotFile,
                    snapshot = snapshot,
                    outputRoot = outputRoot,
                    viewport = viewport,
                )
                logger.lifecycle("Captured ${preview.id} (${viewport.platform}-${viewport.name}) via ${renderResult.renderMode.logLabel}")
            }
        }
    }

    private fun viewportsFor(preview: PreviewDescriptor): List<Viewport> =
        if (preview.hasExplicitWidth() && preview.hasExplicitHeight()) {
            listOf(
                Viewport(
                    platform = "android",
                    name = "preview",
                    width = requireNotNull(preview.widthDp),
                    height = requireNotNull(preview.heightDp),
                    density = 1.0f,
                ),
            )
        } else {
            androidViewports().map { configured ->
                Viewport(
                    platform = configured.platform,
                    name = configured.name,
                    width = preview.widthDp.takeIf { preview.hasExplicitWidth() } ?: configured.width,
                    height = preview.heightDp.takeIf { preview.hasExplicitHeight() } ?: configured.height,
                    density = configured.density,
                )
            }
        }

    private fun PreviewDescriptor.hasExplicitWidth(): Boolean = widthDp != null && widthDp > 0

    private fun PreviewDescriptor.hasExplicitHeight(): Boolean = heightDp != null && heightDp > 0

    private fun androidViewports(): List<ConfiguredViewport> =
        Json.decodeFromString(
            ListSerializer(ConfiguredViewport.serializer()),
            androidViewportsJson.get(),
        )

    private fun warnIfConfigurationIsIncompatible() {
        AndroidPreviewConfigValidator
            .warning(
                robolectricSdk = robolectricSdk.get(),
                javaMajorVersion = javaMajorVersion.get(),
            )?.let { warning -> logger.warn(warning) }
    }

    private fun rendererRuntimeClasspathIfAndroidBacked(): Set<File> =
        if (previewClassesDirs.files.isEmpty()) {
            emptySet()
        } else {
            project.configurations
                .detachedConfiguration(
                    project.dependencies.create("androidx.compose.ui:ui-tooling:1.11.2"),
                    project.dependencies.create("androidx.test:core:1.7.0"),
                    project.dependencies.create("androidx.test:monitor:1.8.0"),
                ).resolve()
        }

    private fun logDiagnostics(diagnostics: List<PreviewScanDiagnostic>) {
        diagnostics.forEach { diagnostic ->
            val message = "AgentPreview scanner: ${diagnostic.message}"
            when (diagnostic.severity) {
                PreviewScanDiagnostic.Severity.WARNING -> logger.warn(message)
                PreviewScanDiagnostic.Severity.ERROR -> logger.error(message)
            }
        }
    }

    private fun discoverPreviews(indexFile: File): PreviewDiscoveryResult =
        if (previewClassesDirs.files.isNotEmpty()) {
            PreviewDiscovery(
                projectPath = project.path,
                sourceSetName = "main",
                classesDirs = previewClassesDirs.files.toList(),
                runtimeClasspath = previewRuntimeClasspath.files.toList(),
            ).discoverWithDiagnostics()
        } else {
            PreviewDiscoveryResult(
                previews = JsonIndexPreviewDiscovery(indexFile).discover(),
                diagnostics = emptyList(),
            )
        }

    private fun sourceLabel(preview: PreviewDescriptor): String =
        if (preview.sourceLine == null) preview.sourceFile else "${preview.sourceFile}:${preview.sourceLine}"
}
