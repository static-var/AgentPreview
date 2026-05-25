/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.config.AndroidPreviewConfigValidator
import dev.staticvar.agentpreview.config.ConfiguredViewport
import dev.staticvar.agentpreview.discovery.IsolatedPreviewParameterCountResolver
import dev.staticvar.agentpreview.discovery.JsonIndexPreviewDiscovery
import dev.staticvar.agentpreview.discovery.PreviewDiscovery
import dev.staticvar.agentpreview.discovery.PreviewDiscoveryResult
import dev.staticvar.agentpreview.discovery.PreviewParameterExpander
import dev.staticvar.agentpreview.export.SnapshotExporter
import dev.staticvar.agentpreview.model.CURRENT_SNAPSHOT_SCHEMA_VERSION
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
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class CaptureComposePreviewsTask : DefaultTask() {
    @get:Input
    abstract val previewIndexFilePath: Property<String>

    @get:Input
    abstract val previewIndexContent: Property<String>

    @get:Input
    abstract val projectPath: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:LocalState
    abstract val renderOutputDirectory: DirectoryProperty

    @get:Input
    abstract val includeUnmergedSemantics: Property<Boolean>

    @get:Input
    abstract val previewNameFilter: ListProperty<String>

    @get:Input
    abstract val viewportNameFilter: ListProperty<String>

    @get:Input
    abstract val maxPreviewParameterValues: Property<Int>

    @get:Input
    @get:Optional
    abstract val cliMaxPreviewParameterValues: Property<String>

    @get:Input
    abstract val fakeRenderer: Property<Boolean>

    @get:Classpath
    abstract val previewClassesDirs: ConfigurableFileCollection

    @get:Classpath
    abstract val previewRuntimeClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val rendererRuntimeClasspath: ConfigurableFileCollection

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
        val maxPreviewParameterValues = effectiveMaxPreviewParameterValues()
        val filters = previewNameFilter.get().toSet()
        val viewportFilters = viewportNameFilter.get().toSet()
        val discoveryResult = discoverPreviews(indexFile)
        val expansionCandidates =
            discoveryResult.previews
                .filter { preview -> filters.isEmpty() || preview.matchesBeforePreviewParameterExpansion(filters) }
        val expansionResult = expandPreviewParameters(expansionCandidates, maxPreviewParameterValues)
        logDiagnostics(discoveryResult.diagnostics + expansionResult.diagnostics)
        val previews =
            expansionResult.previews
                .filter { preview -> filters.isEmpty() || preview.matchesAfterPreviewParameterExpansion(filters) }
        val previewFilterSkipped =
            (discoveryResult.previews.size - expansionCandidates.size) +
                (expansionResult.previews.size - previews.size)
        val outputRoot = outputDirectory.get().asFile
        if (outputRoot.exists()) {
            outputRoot.deleteRecursively()
        }

        if (previews.isEmpty()) {
            logger.lifecycle(
                captureSummary(
                    discoveredCount = discoveryResult.previews.size,
                    expandedCount = expansionResult.previews.size,
                    selectedPreviewCount = 0,
                    capturedViewportCount = 0,
                    previewFilterSkipped = previewFilterSkipped,
                    viewportFilterSkipped = 0,
                ),
            )
            logger.lifecycle("No Compose previews selected for capture.")
            return
        }

        outputRoot.mkdirs()

        val useFakeRenderer = fakeRenderer.get()
        val renderOutput = renderOutputDirectory.get().asFile
        val fakePreviewRenderer = FakePreviewRenderer()
        val previewRenderer by lazy {
            PreviewRendererImpl(
                robolectricSdk = robolectricSdk.get(),
                previewClasspath = previewClasspath(),
                includeUnmergedSemantics = includeUnmergedSemantics.get(),
            )
        }
        val emptySemanticsExtractor = EmptySemanticsExtractor()
        val renderedSemanticsExtractor = RenderedSemanticsExtractor()
        val exporter = SnapshotExporter()

        var capturedViewportCount = 0
        var viewportFilterSkipped = 0
        previews.forEach { preview ->
            val resolvedViewports = viewportsFor(preview)
            val selectedViewports = resolvedViewports.filter { viewport -> viewportFilters.isEmpty() || viewport.matches(viewportFilters) }
            viewportFilterSkipped += resolvedViewports.size - selectedViewports.size
            selectedViewports.forEach { viewport ->
                val renderResult =
                    if (useFakeRenderer) {
                        fakePreviewRenderer.render(preview, viewport, renderOutput)
                    } else {
                        previewRenderer.render(preview, viewport, renderOutput)
                    }
                val snapshot =
                    PreviewSnapshot(
                        schemaVersion = CURRENT_SNAPSHOT_SCHEMA_VERSION,
                        preview =
                            PreviewMetadata(
                                id = preview.id,
                                name = preview.name,
                                group = preview.group,
                                source = sourceLabel(preview),
                                sourceSet = preview.sourceSet,
                                previewParameter = preview.previewParameter,
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
                capturedViewportCount++
                logger.lifecycle("Captured ${preview.id} (${viewport.platform}-${viewport.name}) via ${renderResult.renderMode.logLabel}")
            }
        }
        logger.lifecycle(
            captureSummary(
                discoveredCount = discoveryResult.previews.size,
                expandedCount = expansionResult.previews.size,
                selectedPreviewCount = previews.size,
                capturedViewportCount = capturedViewportCount,
                previewFilterSkipped = previewFilterSkipped,
                viewportFilterSkipped = viewportFilterSkipped,
            ),
        )
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

    private fun Viewport.matches(filters: Set<String>): Boolean =
        filters.any { filter ->
            name.matchesPreviewFilter(filter) ||
                "$platform-$name" == filter
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

    private fun previewClasspath(): List<File> =
        (previewClassesDirs.files + previewRuntimeClasspath.files + rendererRuntimeClasspathIfAndroidBacked()).toList()

    private fun expandPreviewParameters(
        previews: List<PreviewDescriptor>,
        maxPreviewParameterValues: Int,
    ) = PreviewParameterExpander(
        resolver =
            if (previewClassesDirs.files.isEmpty()) {
                null
            } else {
                IsolatedPreviewParameterCountResolver(
                    previewClasspath = previewClasspath(),
                    defaultCap = maxPreviewParameterValues,
                )
            },
        defaultCap = maxPreviewParameterValues,
        requestedIndexes = previewNameFilter.get().toSet().previewParameterFilterIndexes(),
    ).expand(previews)

    private fun effectiveMaxPreviewParameterValues(): Int {
        val raw = cliMaxPreviewParameterValues.orNull
        val value = raw?.toIntOrNull() ?: maxPreviewParameterValues.get()
        require(raw == null || raw.toIntOrNull() != null) { maxPreviewParameterValuesError() }
        require(value > 0) { maxPreviewParameterValuesError() }
        return value
    }

    private fun maxPreviewParameterValuesError(): String =
        "agentPreview.maxPreviewParameterValues must be a positive integer. " +
            "Configure agentPreview { maxPreviewParameterValues.set(n) } or pass -PagentPreview.maxPreviewParameterValues=n."

    private fun captureSummary(
        discoveredCount: Int,
        expandedCount: Int,
        selectedPreviewCount: Int,
        capturedViewportCount: Int,
        previewFilterSkipped: Int,
        viewportFilterSkipped: Int,
    ): String =
        "AgentPreview capture: discovered $discoveredCount, expanded $expandedCount, selected $selectedPreviewCount, " +
            "captured $capturedViewportCount viewport(s), skipped preview filters $previewFilterSkipped, " +
            "viewport filters $viewportFilterSkipped."

    private fun rendererRuntimeClasspathIfAndroidBacked(): Set<File> =
        if (previewClassesDirs.files.isEmpty()) emptySet() else rendererRuntimeClasspath.files

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
                projectPath = projectPath.get(),
                sourceSetName = "main",
                classesDirs = previewClassesDirs.files.toList(),
                runtimeClasspath = (previewRuntimeClasspath.files + rendererRuntimeClasspathIfAndroidBacked()).toList(),
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
