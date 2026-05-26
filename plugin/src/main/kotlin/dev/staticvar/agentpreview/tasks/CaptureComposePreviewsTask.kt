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
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors

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
    abstract val maxParallelRenders: Property<Int>

    @get:Input
    @get:Optional
    abstract val cliMaxParallelRenders: Property<String>

    @get:Input
    abstract val dryRun: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val cliDryRun: Property<String>

    @get:Input
    abstract val continueOnError: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val cliContinueOnError: Property<String>

    @get:Input
    abstract val fakeRenderer: Property<Boolean>

    @get:Input
    abstract val selectedVariant: Property<String>

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
        logEffectiveRenderingClasspath()
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
        if (result.failures.isNotEmpty() && !plan.continueOnError && plan.maxParallelRenders > 1) {
            logger.lifecycle(
                "AgentPreview stopped scheduling new captures after the first parallel failure; " +
                    "already-started captures were allowed to finish.",
            )
        }
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
            dryRun = effectiveDryRun(),
            continueOnError = effectiveContinueOnError(),
            maxCaptures = effectiveMaxCaptures(),
            maxParallelRenders = effectiveMaxParallelRenders(),
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
        val renderSettings = renderSettings()
        return if (plan.maxParallelRenders == 1) {
            renderCapturesSequentially(plan, renderSettings)
        } else {
            renderCapturesInParallel(plan, renderSettings)
        }
    }

    private fun renderCapturesSequentially(
        plan: CapturePlan,
        renderSettings: RenderSettings,
    ): CaptureResult {
        val failures = mutableListOf<CaptureFailure>()
        var capturedViewportCount = 0
        captureRequests(plan).forEach { request ->
            val result = renderCapture(request, renderSettings)
            if (recordCompletedCapture(result, failures)) capturedViewportCount++
            if (result is SingleCaptureResult.Failed && !plan.continueOnError) {
                return CaptureResult(capturedViewportCount, failures)
            }
        }
        return CaptureResult(capturedViewportCount, failures)
    }

    private fun renderCapturesInParallel(
        plan: CapturePlan,
        renderSettings: RenderSettings,
    ): CaptureResult {
        logger.lifecycle("AgentPreview parallel render workers: ${plan.maxParallelRenders}")
        val requests = captureRequests(plan)
        val executor = Executors.newFixedThreadPool(plan.maxParallelRenders)
        val completions: CompletionService<SingleCaptureResult> = ExecutorCompletionService(executor)
        val failures = mutableListOf<CaptureFailure>()
        var capturedViewportCount = 0
        var submitted = 0
        var completed = 0
        var acceptingWork = true

        fun submitNext() {
            if (submitted < requests.size && acceptingWork) {
                val request = requests[submitted++]
                completions.submit { renderCapture(request, renderSettings) }
            }
        }

        try {
            repeat(plan.maxParallelRenders.coerceAtMost(requests.size)) { submitNext() }
            while (completed < submitted) {
                val result = completions.take().get()
                if (recordCompletedCapture(result, failures)) capturedViewportCount++
                if (result is SingleCaptureResult.Failed && !plan.continueOnError) acceptingWork = false
                completed++
                submitNext()
            }
        } finally {
            executor.shutdownNow()
        }

        return CaptureResult(capturedViewportCount, failures)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun renderCapture(
        request: CaptureRequest,
        renderSettings: RenderSettings,
    ): SingleCaptureResult =
        try {
            val renderer = CaptureRenderer(renderSettings)
            val renderResult = renderer.render(request.preview, request.viewport)
            SnapshotExporter().export(
                previewId = request.preview.id,
                screenshotFile = renderResult.screenshotFile,
                snapshot = snapshot(request.preview, renderResult, renderSettings.useFakeRenderer),
                outputRoot = renderSettings.outputRoot,
                viewport = request.viewport,
            )
            SingleCaptureResult.Captured(request.preview.id, request.viewport, renderResult.renderMode.logLabel)
        } catch (exception: Exception) {
            SingleCaptureResult.Failed(
                CaptureFailure(request.preview.id, request.viewport.label(), exception.message ?: exception.javaClass.name),
            )
        }

    private fun captureRequests(plan: CapturePlan): List<CaptureRequest> =
        plan.plannedCaptures.flatMap { plannedPreview ->
            plannedPreview.viewports.map { viewport -> CaptureRequest(plannedPreview.preview, viewport) }
        }

    private fun recordCompletedCapture(
        result: SingleCaptureResult,
        failures: MutableList<CaptureFailure>,
    ): Boolean =
        when (result) {
            is SingleCaptureResult.Captured -> {
                logCaptured(result)
                true
            }

            is SingleCaptureResult.Failed -> {
                failures += result.failure
                logFailure(result.failure)
                false
            }
        }

    private fun logCaptured(result: SingleCaptureResult.Captured) {
        logger.lifecycle("Captured ${result.previewId} (${result.viewport.platform}-${result.viewport.name}) via ${result.renderModeLabel}")
    }

    private fun logFailure(failure: CaptureFailure) {
        logger.error("Failed ${failure.previewId} (${failure.viewport}): ${failure.message}")
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

    private data class CaptureRequest(
        val preview: PreviewDescriptor,
        val viewport: Viewport,
    )

    private data class RenderSettings(
        val useFakeRenderer: Boolean,
        val outputRoot: File,
        val renderOutput: File,
        val robolectricSdk: Int,
        val previewClasspath: List<File>,
        val includeUnmergedSemantics: Boolean,
    )

    private sealed interface SingleCaptureResult {
        data class Captured(
            val previewId: String,
            val viewport: Viewport,
            val renderModeLabel: String,
        ) : SingleCaptureResult

        data class Failed(
            val failure: CaptureFailure,
        ) : SingleCaptureResult
    }

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
        val maxParallelRenders: Int,
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
            maxParallelRenders = maxParallelRenders,
            previewFilters = previewFilters,
            viewportFilters = viewportFilters,
            failures = failures,
        )
    }

    private fun renderSettings() =
        RenderSettings(
            useFakeRenderer = fakeRenderer.get(),
            outputRoot = outputDirectory.get().asFile,
            renderOutput = renderOutputDirectory.get().asFile,
            robolectricSdk = robolectricSdk.get(),
            previewClasspath = previewClasspath(),
            includeUnmergedSemantics = includeUnmergedSemantics.get(),
        )

    private class CaptureRenderer(
        private val settings: RenderSettings,
    ) {
        private val fakePreviewRenderer = FakePreviewRenderer()
        private val previewRenderer by lazy {
            PreviewRendererImpl(
                robolectricSdk = settings.robolectricSdk,
                previewClasspath = settings.previewClasspath,
                includeUnmergedSemantics = settings.includeUnmergedSemantics,
            )
        }

        fun render(
            preview: PreviewDescriptor,
            viewport: Viewport,
        ): RenderResult =
            if (settings.useFakeRenderer) {
                fakePreviewRenderer.render(preview, viewport, settings.renderOutput)
            } else {
                previewRenderer.render(preview, viewport, settings.renderOutput)
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

    private fun effectiveMaxParallelRenders(): Int {
        val raw = cliMaxParallelRenders.orNull
        val value = raw?.toIntOrNull() ?: maxParallelRenders.get()
        require(raw == null || raw.toIntOrNull() != null) { maxParallelRendersError() }
        require(value > 0) { maxParallelRendersError() }
        return value
    }

    private fun effectiveDryRun(): Boolean {
        val raw = cliDryRun.orNull
        val value = raw?.toBooleanStrictOrNull()
        require(raw == null || value != null) { dryRunError() }
        return value ?: dryRun.get()
    }

    private fun effectiveContinueOnError(): Boolean {
        val raw = cliContinueOnError.orNull
        val value = raw?.toBooleanStrictOrNull()
        require(raw == null || value != null) { continueOnErrorError() }
        return value ?: continueOnError.get()
    }

    private fun maxPreviewParameterValuesError(): String =
        "agentPreview.maxPreviewParameterValues must be a positive integer. " +
            "Configure agentPreview { maxPreviewParameterValues.set(n) } or pass -PagentPreview.maxPreviewParameterValues=n."

    private fun maxCapturesError(): String =
        "agentPreview.maxCaptures must be a non-negative integer. " +
            "Configure agentPreview { maxCaptures.set(n) } or pass -PagentPreview.maxCaptures=n."

    private fun maxParallelRendersError(): String =
        "agentPreview.maxParallelRenders must be a positive integer. " +
            "Configure agentPreview { maxParallelRenders.set(n) } or pass -PagentPreview.maxParallelRenders=n."

    private fun dryRunError(): String =
        "agentPreview.dryRun must be true or false. " +
            "Pass -PagentPreview.dryRun=true|false."

    private fun continueOnErrorError(): String =
        "agentPreview.continueOnError must be true or false. " +
            "Configure agentPreview { continueOnError.set(true|false) } or pass -PagentPreview.continueOnError=true|false."

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

    private fun logEffectiveRenderingClasspath() {
        if (previewClassesDirs.files.isEmpty()) return
        logger.lifecycle(
            "AgentPreview capture variant ${selectedVariant.get()} runtime artifacts: " +
                (previewRuntimeClasspath.files + rendererRuntimeClasspathIfAndroidBacked())
                    .joinToString(", ") { it.name },
        )
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
