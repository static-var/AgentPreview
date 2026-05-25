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
import dev.staticvar.agentpreview.model.CaptureFailure
import dev.staticvar.agentpreview.model.CaptureReport
import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.PreviewMetadata
import dev.staticvar.agentpreview.model.PreviewSnapshot
import dev.staticvar.agentpreview.model.SnapshotRenderMetadata
import dev.staticvar.agentpreview.model.Viewport
import dev.staticvar.agentpreview.render.FakePreviewRenderer
import dev.staticvar.agentpreview.render.PreviewRendererImpl
import dev.staticvar.agentpreview.render.RenderResult
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

    @get:OutputDirectory
    abstract val reportDirectory: DirectoryProperty

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
    @get:Optional
    abstract val maxCaptures: Property<Int>

    @get:Input
    @get:Optional
    abstract val cliMaxCaptures: Property<String>

    @get:Input
    abstract val dryRun: Property<Boolean>

    @get:Input
    abstract val continueOnError: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val cliContinueOnError: Property<String>

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
        warnIfConfigurationIsIncompatible()
        val plan = capturePlan()

        if (plan.dryRun) {
            writeReport(plan.report())
            logSummary(plan, capturedViewportCount = 0, failedViewportCount = 0)
            logNoSelection(plan)
            return
        }

        enforceMaxCaptures(plan)
        prepareOutputDirectory()

        if (plan.selectedPreviewCount == 0) {
            writeReport(plan.report())
            logSummary(plan, capturedViewportCount = 0, failedViewportCount = 0)
            logger.lifecycle("No Compose previews selected for capture.")
            return
        }

        val result = renderCaptures(plan)
        writeReport(plan.report(result.capturedViewportCount, result.failures))
        logSummary(plan, result.capturedViewportCount, result.failures.size)
        if (result.failures.isNotEmpty()) {
            error("AgentPreview capture failed for ${result.failures.size} viewport(s); see ${reportFile().absolutePath} for details.")
        }
    }

    private fun capturePlan(): CapturePlan {
        val filters = previewNameFilter.get().toSet()
        val viewportFilters = viewportNameFilter.get().toSet()
        val discoveryResult = discoverPreviews(File(previewIndexFilePath.get()))
        val expansionCandidates =
            discoveryResult.previews
                .filter { preview -> filters.isEmpty() || preview.matchesBeforePreviewParameterExpansion(filters) }
        val expansionResult = expandPreviewParameters(expansionCandidates, effectiveMaxPreviewParameterValues())
        logDiagnostics(discoveryResult.diagnostics + expansionResult.diagnostics)
        val previews =
            expansionResult.previews
                .filter { preview -> filters.isEmpty() || preview.matchesAfterPreviewParameterExpansion(filters) }
        val plannedCaptures = plannedCaptures(previews, viewportFilters)
        return CapturePlan(
            discoveredPreviewCount = discoveryResult.previews.size,
            expandedPreviewCount = expansionResult.previews.size,
            selectedPreviewCount = previews.size,
            plannedCaptures = plannedCaptures,
            skippedByPreviewFilterCount =
                (discoveryResult.previews.size - expansionCandidates.size) +
                    (expansionResult.previews.size - previews.size),
            skippedByViewportFilterCount = plannedCaptures.sumOf { it.skippedByViewportFilter },
            dryRun = dryRun.get(),
            continueOnError = effectiveContinueOnError(),
            maxCaptures = effectiveMaxCaptures(),
            previewFilters = filters.toList().sorted(),
            viewportFilters = viewportFilters.toList().sorted(),
        )
    }

    private fun enforceMaxCaptures(plan: CapturePlan) {
        val limit = plan.maxCaptures ?: return
        if (plan.plannedViewportCaptureCount <= limit) return

        writeReport(plan.report())
        error(
            "agentPreview.maxCaptures planned ${plan.plannedViewportCaptureCount} capture(s), " +
                "which exceeds the configured limit of $limit. " +
                "Narrow the run with -PagentPreview.previewNameFilter or -PagentPreview.viewportFilter, " +
                "increase -PagentPreview.maxCaptures, or run -PagentPreview.dryRun=true to inspect the plan.",
        )
    }

    private fun prepareOutputDirectory() {
        val outputRoot = outputDirectory.get().asFile
        if (outputRoot.exists()) {
            outputRoot.deleteRecursively()
        }
        outputRoot.mkdirs()
    }

    private fun renderCaptures(plan: CapturePlan): CaptureResult {
        val renderer = CaptureRenderer(fakeRenderer.get())
        val failures = mutableListOf<CaptureFailure>()
        var capturedViewportCount = 0
        plan.plannedCaptures.forEach { plannedPreview ->
            plannedPreview.viewports.forEach { viewport ->
                val captured = renderCapture(plannedPreview.preview, viewport, renderer, failures, plan, capturedViewportCount)
                if (captured) capturedViewportCount++
            }
        }
        return CaptureResult(capturedViewportCount, failures)
    }

    private fun renderCapture(
        preview: PreviewDescriptor,
        viewport: Viewport,
        renderer: CaptureRenderer,
        failures: MutableList<CaptureFailure>,
        plan: CapturePlan,
        capturedViewportCount: Int,
    ): Boolean =
        try {
            val renderResult = renderer.render(preview, viewport)
            SnapshotExporter().export(
                previewId = preview.id,
                screenshotFile = renderResult.screenshotFile,
                snapshot = snapshot(preview, renderResult, renderer.useFakeRenderer),
                outputRoot = outputDirectory.get().asFile,
                viewport = viewport,
            )
            logger.lifecycle("Captured ${preview.id} (${viewport.platform}-${viewport.name}) via ${renderResult.renderMode.logLabel}")
            true
        } catch (exception: IllegalArgumentException) {
            recordFailure(preview, viewport, exception, failures, plan, capturedViewportCount)
        } catch (exception: IllegalStateException) {
            recordFailure(preview, viewport, exception, failures, plan, capturedViewportCount)
        }

    private fun recordFailure(
        preview: PreviewDescriptor,
        viewport: Viewport,
        exception: RuntimeException,
        failures: MutableList<CaptureFailure>,
        plan: CapturePlan,
        capturedViewportCount: Int,
    ): Boolean {
        val failure = CaptureFailure(preview.id, viewport.label(), exception.message ?: exception.javaClass.name)
        failures += failure
        logger.error("Failed ${failure.previewId} (${failure.viewport}): ${failure.message}")
        if (!plan.continueOnError) {
            writeReport(plan.report(capturedViewportCount, failures))
            throw exception
        }
        return false
    }

    private fun snapshot(
        preview: PreviewDescriptor,
        renderResult: RenderResult,
        useFakeRenderer: Boolean,
    ) = PreviewSnapshot(
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
                EmptySemanticsExtractor().extract(renderResult.rawSemantics)
            } else {
                RenderedSemanticsExtractor().extract(renderResult.rawSemantics)
            },
        layoutTree = renderResult.layoutTree.takeUnless { useFakeRenderer }.orEmpty(),
        render = SnapshotRenderMetadata(mode = renderResult.renderMode.logLabel),
    )

    private fun logSummary(
        plan: CapturePlan,
        capturedViewportCount: Int,
        failedViewportCount: Int,
    ) {
        logger.lifecycle(
            captureSummary(
                discoveredCount = plan.discoveredPreviewCount,
                expandedCount = plan.expandedPreviewCount,
                selectedPreviewCount = plan.selectedPreviewCount,
                plannedViewportCount = plan.plannedViewportCaptureCount,
                capturedViewportCount = capturedViewportCount,
                failedViewportCount = failedViewportCount,
                previewFilterSkipped = plan.skippedByPreviewFilterCount,
                viewportFilterSkipped = plan.skippedByViewportFilterCount,
                dryRun = plan.dryRun,
            ),
        )
    }

    private fun logNoSelection(plan: CapturePlan) {
        if (plan.selectedPreviewCount == 0) {
            logger.lifecycle("No Compose previews selected for capture.")
        }
    }

    private fun plannedCaptures(
        previews: List<PreviewDescriptor>,
        viewportFilters: Set<String>,
    ): List<PlannedPreviewCapture> =
        previews.map { preview ->
            val resolvedViewports = viewportsFor(preview)
            val selectedViewports = resolvedViewports.filter { viewport -> viewportFilters.isEmpty() || viewport.matches(viewportFilters) }
            PlannedPreviewCapture(
                preview = preview,
                viewports = selectedViewports,
                skippedByViewportFilter = resolvedViewports.size - selectedViewports.size,
            )
        }

    private fun writeReport(report: CaptureReport) {
        val reportFile = reportFile()
        reportFile.parentFile.mkdirs()
        reportFile.writeText(reportJson.encodeToString(CaptureReport.serializer(), report))
    }

    private fun reportFile(): File = reportDirectory.get().asFile.resolve("capture-report.json")

    private fun Viewport.label(): String = "$platform-$name"

    private data class PlannedPreviewCapture(
        val preview: PreviewDescriptor,
        val viewports: List<Viewport>,
        val skippedByViewportFilter: Int,
    )

    private data class CaptureResult(
        val capturedViewportCount: Int,
        val failures: List<CaptureFailure>,
    )

    private data class CapturePlan(
        val discoveredPreviewCount: Int,
        val expandedPreviewCount: Int,
        val selectedPreviewCount: Int,
        val plannedCaptures: List<PlannedPreviewCapture>,
        val skippedByPreviewFilterCount: Int,
        val skippedByViewportFilterCount: Int,
        val dryRun: Boolean,
        val continueOnError: Boolean,
        val maxCaptures: Int?,
        val previewFilters: List<String>,
        val viewportFilters: List<String>,
    ) {
        val plannedViewportCaptureCount: Int = plannedCaptures.sumOf { it.viewports.size }

        fun report(
            capturedViewportCount: Int = 0,
            failures: List<CaptureFailure> = emptyList(),
        ) = CaptureReport(
            discoveredPreviewCount = discoveredPreviewCount,
            expandedPreviewCount = expandedPreviewCount,
            selectedPreviewCount = selectedPreviewCount,
            plannedViewportCaptureCount = plannedViewportCaptureCount,
            capturedViewportCaptureCount = capturedViewportCount,
            failedViewportCaptureCount = failures.size,
            skippedByPreviewFilterCount = skippedByPreviewFilterCount,
            skippedByViewportFilterCount = skippedByViewportFilterCount,
            dryRun = dryRun,
            continueOnError = continueOnError,
            maxCaptures = maxCaptures,
            previewFilters = previewFilters,
            viewportFilters = viewportFilters,
            failures = failures,
        )
    }

    private inner class CaptureRenderer(
        val useFakeRenderer: Boolean,
    ) {
        private val renderOutput = renderOutputDirectory.get().asFile
        private val fakePreviewRenderer = FakePreviewRenderer()
        private val previewRenderer by lazy {
            PreviewRendererImpl(
                robolectricSdk = robolectricSdk.get(),
                previewClasspath = previewClasspath(),
                includeUnmergedSemantics = includeUnmergedSemantics.get(),
            )
        }

        fun render(
            preview: PreviewDescriptor,
            viewport: Viewport,
        ): RenderResult =
            if (useFakeRenderer) {
                fakePreviewRenderer.render(preview, viewport, renderOutput)
            } else {
                previewRenderer.render(preview, viewport, renderOutput)
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

    private fun effectiveMaxCaptures(): Int? {
        val raw = cliMaxCaptures.orNull
        val value = raw?.toIntOrNull() ?: maxCaptures.orNull
        require(raw == null || raw.toIntOrNull() != null) { maxCapturesError() }
        require(value == null || value >= 0) { maxCapturesError() }
        return value
    }

    private fun effectiveContinueOnError(): Boolean {
        val raw = cliContinueOnError.orNull
        return raw?.toBooleanStrictOrNull() ?: continueOnError.get()
    }

    private fun maxPreviewParameterValuesError(): String =
        "agentPreview.maxPreviewParameterValues must be a positive integer. " +
            "Configure agentPreview { maxPreviewParameterValues.set(n) } or pass -PagentPreview.maxPreviewParameterValues=n."

    private fun maxCapturesError(): String =
        "agentPreview.maxCaptures must be a non-negative integer. " +
            "Configure agentPreview { maxCaptures.set(n) } or pass -PagentPreview.maxCaptures=n."

    private fun captureSummary(
        discoveredCount: Int,
        expandedCount: Int,
        selectedPreviewCount: Int,
        plannedViewportCount: Int,
        capturedViewportCount: Int,
        failedViewportCount: Int,
        previewFilterSkipped: Int,
        viewportFilterSkipped: Int,
        dryRun: Boolean,
    ): String =
        "AgentPreview capture${if (dryRun) " dry run" else ""}: discovered $discoveredCount, expanded $expandedCount, " +
            "selected $selectedPreviewCount, planned $plannedViewportCount viewport(s), " +
            "captured $capturedViewportCount viewport(s), failed $failedViewportCount viewport(s), " +
            "skipped preview filters $previewFilterSkipped, viewport filters $viewportFilterSkipped."

    private val reportJson: Json
        get() =
            Json {
                prettyPrint = true
                encodeDefaults = true
            }

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
