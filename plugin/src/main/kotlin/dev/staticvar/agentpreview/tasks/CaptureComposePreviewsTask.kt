/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.accessibility.AccessibilityAuditor
import dev.staticvar.agentpreview.accessibility.AccessibilityHtmlReportWriter
import dev.staticvar.agentpreview.accessibility.AccessibilityReportAssetWriter
import dev.staticvar.agentpreview.accessibility.AuditedSnapshotBundle
import dev.staticvar.agentpreview.config.AndroidPreviewConfigValidator
import dev.staticvar.agentpreview.config.ConfiguredViewport
import dev.staticvar.agentpreview.dependencies.AarClasspathMaterializer
import dev.staticvar.agentpreview.export.PreviewSnapshotMapper
import dev.staticvar.agentpreview.export.ScreenshotCropPlanner
import dev.staticvar.agentpreview.export.SnapshotExportMetadata
import dev.staticvar.agentpreview.export.SnapshotExporter
import dev.staticvar.agentpreview.export.SnapshotOutputPath
import dev.staticvar.agentpreview.model.CaptureFailure
import dev.staticvar.agentpreview.model.CaptureReport
import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.SnapshotNode
import dev.staticvar.agentpreview.model.Viewport
import dev.staticvar.agentpreview.render.AndroidAssetMaterializer
import dev.staticvar.agentpreview.render.FakePreviewRenderer
import dev.staticvar.agentpreview.render.PreviewRendererImpl
import dev.staticvar.agentpreview.render.RenderResult
import dev.staticvar.agentpreview.scanner.discovery.PreviewScanDiagnostic
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Collections
import javax.imageio.ImageIO

abstract class CaptureComposePreviewsTask :
    DefaultTask(),
    PreviewIndexInput {
    /** Gradle project path used as the stable prefix for discovered preview ids. */
    @get:Input
    abstract val projectPath: Property<String>

    /** Root directory for snapshot bundles; each successful capture writes `screenshot.png` and `snapshot.json`. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /** Directory for `capture-report.json`, including dry-run plans and failure details. */
    @get:OutputDirectory
    abstract val reportDirectory: DirectoryProperty

    /** Scratch space for renderer child-JVM sidecars and PNGs; safe to delete between task executions. */
    @get:LocalState
    abstract val renderOutputDirectory: DirectoryProperty

    /** Include unmerged Compose semantics when the real renderer can extract them. */
    @get:Input
    abstract val includeUnmergedSemantics: Property<Boolean>

    /** Preview id/name/function filters after DSL and CLI additive filters have been combined. */
    @get:Input
    abstract val previewNameFilter: ListProperty<String>

    /** Viewport filters after DSL and CLI additive filters have been combined. */
    @get:Input
    abstract val viewportNameFilter: ListProperty<String>

    /** Default cap for enumerating `@PreviewParameter` values before capture expansion. */
    @get:Input
    abstract val maxPreviewParameterValues: Property<Int>

    /** CLI scalar override for [maxPreviewParameterValues]; unlike list filters, this replaces the DSL value. */
    @get:Input
    @get:Optional
    abstract val cliMaxPreviewParameterValues: Property<String>

    /** Optional DSL cap on planned viewport captures after preview and viewport filtering. */
    @get:Input
    @get:Optional
    abstract val maxCaptures: Property<Int>

    /** CLI scalar override for [maxCaptures]. */
    @get:Input
    @get:Optional
    abstract val cliMaxCaptures: Property<String>

    /** Maximum number of renderer child JVMs scheduled at once. */
    @get:Input
    abstract val maxParallelRenders: Property<Int>

    /** CLI scalar override for [maxParallelRenders]. */
    @get:Input
    @get:Optional
    abstract val cliMaxParallelRenders: Property<String>

    /** When true, write the capture plan/report without rendering or snapshot bundles. */
    @get:Input
    abstract val dryRun: Property<Boolean>

    /** CLI scalar override for [dryRun]. */
    @get:Input
    @get:Optional
    abstract val cliDryRun: Property<String>

    /** Continue scheduling remaining captures after failures; the task still fails if any capture fails. */
    @get:Input
    abstract val continueOnError: Property<Boolean>

    /** CLI scalar override for [continueOnError]. */
    @get:Input
    @get:Optional
    abstract val cliContinueOnError: Property<String>

    /** Generate a non-failing accessibility HTML report from rendered snapshot bundles. */
    @get:Input
    abstract val accessibilityCheck: Property<Boolean>

    /** CLI scalar override for [accessibilityCheck]. */
    @get:Input
    @get:Optional
    abstract val cliAccessibilityCheck: Property<String>

    /** Crop screenshots to detected content bounds when reliable bounds exist. */
    @get:Input
    abstract val cropToContent: Property<Boolean>

    /** CLI scalar override for [cropToContent]. */
    @get:Input
    @get:Optional
    abstract val cliCropToContent: Property<String>

    /** Padding added around detected content before cropping, in dp. */
    @get:Input
    abstract val cropPaddingDp: Property<Int>

    /** CLI scalar override for [cropPaddingDp]. */
    @get:Input
    @get:Optional
    abstract val cliCropPaddingDp: Property<String>

    /** Use the deterministic fake renderer for wiring tests instead of launching the real Android renderer. */
    @get:Input
    abstract val fakeRenderer: Property<Boolean>

    /** Android variant name used for renderer classpath resolution and diagnostics. */
    @get:Input
    abstract val selectedVariant: Property<String>

    /** Primary bytecode scan roots; when empty, capture falls back to the generated preview index input. */
    @get:Classpath
    abstract val previewClassesDirs: ConfigurableFileCollection

    /** Android Components project class directories from ScopedArtifact.CLASSES. */
    @get:Classpath
    abstract val androidProjectClassDirs: ListProperty<Directory>

    /** Android Components project class jars from ScopedArtifact.CLASSES. */
    @get:Classpath
    abstract val androidProjectClassJars: ListProperty<RegularFile>

    /** Android Components all-scope class directories used only as runtime support. */
    @get:Classpath
    abstract val androidRuntimeClassDirs: ListProperty<Directory>

    /** Android Components all-scope class jars used only as runtime support. */
    @get:Classpath
    abstract val androidRuntimeClassJars: ListProperty<RegularFile>

    /** Consumer runtime classpath used for scanner metadata, preview-parameter providers, and target preview classes. */
    @get:Classpath
    abstract val previewRuntimeClasspath: ConfigurableFileCollection

    /** Renderer-owned Android/Robolectric support artifacts used only by real Android-backed captures. */
    @get:Classpath
    abstract val rendererRuntimeClasspath: ConfigurableFileCollection

    /** Android asset source directories configured by the DSL and merged once for real Android-backed captures. */
    @get:Internal
    abstract val androidAssetsDirs: ConfigurableFileCollection

    /** Android compiled resource APK from AGP, used by Robolectric for app resources. */
    @get:Internal
    abstract val androidResourceApk: RegularFileProperty

    /** Android linked binary resource artifact directories from AGP, used when no local-test APK exists. */
    @get:Internal
    abstract val androidLinkedResourceApkDirs: ConfigurableFileCollection

    /** Android merged manifest from AGP, used by Robolectric for app package metadata. */
    @get:Internal
    abstract val androidMergedManifest: RegularFileProperty

    /** Android namespace/custom package from AGP, used by Robolectric for resource lookup. */
    @get:Internal
    abstract val androidCustomPackage: Property<String>

    /** Effective Android asset source directories; fake captures must not snapshot or materialize assets. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    val effectiveAndroidAssetsDirs: FileCollection =
        project.objects.fileCollection().from(
            fakeRenderer.map { isFake ->
                if (isFake) project.files() else androidAssetsDirs
            },
        )

    private val emptyAndroidResourceInputs: FileCollection = project.files()
    private val realAndroidResourceApkInputs: FileCollection =
        project.files(providerBackedFileCollection(androidResourceApk), androidLinkedResourceApkDirs)
    private val realAndroidMergedManifestInputs: FileCollection = project.files(providerBackedFileCollection(androidMergedManifest))

    /** Effective Android resource APK artifacts; fake captures must not snapshot or materialize Android resources. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val effectiveAndroidResourceApk: FileCollection
        get() = if (fakeRenderer.get()) emptyAndroidResourceInputs else realAndroidResourceApkInputs

    /** Effective Android merged manifest; fake captures must not snapshot or materialize Android resources. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val effectiveAndroidMergedManifest: FileCollection
        get() = if (fakeRenderer.get()) emptyAndroidResourceInputs else realAndroidMergedManifestInputs

    /** Effective Android namespace/custom package; fake captures must not snapshot Android resources. */
    @get:Input
    val effectiveAndroidCustomPackage =
        fakeRenderer.flatMap { isFake ->
            if (isFake) project.providers.provider { "" } else androidCustomPackage.orElse("")
        }

    /** Serialized Android viewport DSL used as a task input for viewport planning. */
    @get:Input
    abstract val androidViewportsJson: Property<String>

    /** Robolectric SDK configured for real rendering and compatibility warnings. */
    @get:Input
    abstract val robolectricSdk: Property<Int>

    /** Gradle root/project directory used to locate local.properties sdk.dir for Android SDK lookup. */
    @get:Input
    abstract val sdkLookupBaseDir: Property<String>

    /** Java major version of the current Gradle JVM, used to warn about unsupported render configurations. */
    @get:Input
    abstract val javaMajorVersion: Property<Int>

    @TaskAction
    fun capture() {
        warnIfConfigurationIsIncompatible()
        logEffectiveRenderingClasspath()
        val accessibilityCheck = effectiveAccessibilityCheck()
        if (!accessibilityCheck) {
            clearAccessibilityReport()
        }
        val plan = capturePlan()

        if (plan.dryRun) {
            writeReport(plan.report())
            logSummary(plan, capturedViewportCount = 0, failedViewportCount = 0, accessibilityCheck = accessibilityCheck)
            logArtifactLocations(plan, wroteSnapshots = false)
            logNoSelection(plan)
            if (accessibilityCheck) {
                clearAccessibilityReport()
                logger.lifecycle("Accessibility check requires rendered snapshots; skipping accessibility report for dry run.")
            }
            return
        }

        if (accessibilityCheck) {
            clearAccessibilityReport()
        }
        enforceMaxCaptures(plan)
        prepareOutputDirectory()

        if (plan.selectedPreviewCount == 0) {
            writeReport(plan.report())
            logSummary(plan, capturedViewportCount = 0, failedViewportCount = 0, accessibilityCheck = accessibilityCheck)
            logArtifactLocations(plan, wroteSnapshots = false)
            logger.lifecycle("No Compose previews selected for capture.")
            if (accessibilityCheck) {
                writeAccessibilityReport(emptyList())
            }
            return
        }

        val result = renderCaptures(plan)
        writeReport(plan.report(result.capturedViewportCount, result.failures))
        logSummary(plan, result.capturedViewportCount, result.failures.size, accessibilityCheck)
        logArtifactLocations(plan, wroteSnapshots = result.capturedViewportCount > 0)
        if (accessibilityCheck) {
            writeAccessibilityReport(result.exportedBundles)
        }
        if (result.failures.isNotEmpty() && !plan.continueOnError && plan.maxParallelRenders > 1) {
            logger.lifecycle(
                "AgentPreview stopped scheduling new captures after the first parallel failure; " +
                    "already-submitted captures were allowed to finish.",
            )
        }
        if (result.failures.isNotEmpty()) {
            error("AgentPreview capture failed for ${result.failures.size} viewport(s); see ${reportFile().absolutePath} for details.")
        }
    }

    private fun capturePlan(): CapturePlan {
        val filters = previewNameFilter.get().toSet()
        val viewportFilters = viewportNameFilter.get().toSet()
        val selection =
            selectionService().select(
                indexFile = previewIndexFileOrNull(),
                filters = filters,
                maxPreviewParameterValues = effectiveMaxPreviewParameterValues(),
                mode = PreviewSelectionService.Mode.CAPTURE,
            )
        logDiagnostics(selection.diagnostics)
        return CapturePlanBuilder(androidViewports()).build(
            selection = selection,
            viewportFilters = viewportFilters,
            dryRun = effectiveDryRun(),
            continueOnError = effectiveContinueOnError(),
            maxCaptures = effectiveMaxCaptures(),
            maxParallelRenders = effectiveMaxParallelRenders(),
            previewFilters = filters,
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

    private fun renderCaptures(plan: CapturePlan): CaptureTaskResult {
        val exportedBundles = Collections.synchronizedList(mutableListOf<CurrentRunExport>())
        val renderSettings = renderSettings(recordExport = exportedBundles::add)
        val requests = captureRequests(plan)
        preflightDestinationCollisions(requests, renderSettings.outputRoot).takeIf { it.isNotEmpty() }?.let { failures ->
            failures.forEach(::logFailure)
            return CaptureTaskResult(capturedViewportCount = 0, failures = failures, exportedBundles = emptyList())
        }
        val executor =
            if (plan.maxParallelRenders == 1) {
                SequentialCaptureExecutor()
            } else {
                logger.lifecycle("AgentPreview parallel render workers: ${plan.maxParallelRenders}")
                ParallelCaptureExecutor(plan.maxParallelRenders)
            }
        val result =
            executor.execute(
                requests = requests,
                continueOnError = plan.continueOnError,
                render = { request -> renderCapture(request, renderSettings) },
                record = ::recordCompletedCapture,
            )
        return CaptureTaskResult(
            capturedViewportCount = result.capturedViewportCount,
            failures = result.failures,
            exportedBundles = exportedBundles.toList(),
        )
    }

    private fun preflightDestinationCollisions(
        requests: List<CaptureRequest>,
        outputRoot: File,
    ): List<CaptureFailure> {
        val outputPath = SnapshotOutputPath()
        val destinations = requests.map { request -> outputPath.resolve(outputRoot, request.preview.id, request.viewport) }
        val duplicateDestinations = outputPath.duplicateDestinations(destinations).map { it.absolutePath }.toSet()
        if (duplicateDestinations.isEmpty()) return emptyList()

        return requests
            .filter { request ->
                outputPath.resolve(outputRoot, request.preview.id, request.viewport).absolutePath in duplicateDestinations
            }.map { request ->
                CaptureFailure(
                    request.preview.id,
                    request.viewport.label(),
                    "Snapshot output path collision at ${outputPath.resolve(
                        outputRoot,
                        request.preview.id,
                        request.viewport,
                    ).absolutePath}; " +
                        "multiple previews/viewports sanitize to the same directory label.",
                )
            }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun renderCapture(
        request: CaptureRequest,
        renderSettings: RenderSettings,
    ): SingleCaptureResult =
        try {
            val renderer = CaptureRenderer(renderSettings)
            val renderResult = renderer.render(request.preview, request.viewport, request.scratchDirectory)
            val screenshot =
                ImageIO.read(renderResult.screenshotFile)
                    ?: error("AgentPreview renderer did not produce a readable PNG at ${renderResult.screenshotFile.absolutePath}.")
            val semanticsNodes = renderResult.rawSemantics as? List<*> ?: emptyList<Any>()
            val cropSemanticsNodes =
                semanticsNodes
                    .filterIsInstance<SnapshotNode>()
                    .takeUnless { renderSettings.useFakeRenderer }
                    .orEmpty()
            val cropPlan =
                ScreenshotCropPlanner().plan(
                    bitmapWidth = screenshot.width,
                    bitmapHeight = screenshot.height,
                    density = renderResult.viewport.density,
                    cropToContent = renderSettings.cropToContent,
                    cropPaddingDp = renderSettings.cropPaddingDp,
                    layoutTree = renderResult.layoutTree.takeUnless { renderSettings.useFakeRenderer }.orEmpty(),
                    semanticsNodes = cropSemanticsNodes,
                )
            val exportedBundle =
                SnapshotExporter().export(
                    previewId = request.preview.id,
                    screenshotFile = renderResult.screenshotFile,
                    snapshot =
                        PreviewSnapshotMapper().map(
                            preview = request.preview,
                            renderResult = renderResult,
                            useFakeRenderer = renderSettings.useFakeRenderer,
                            cropPlan = cropPlan,
                        ),
                    outputRoot = renderSettings.outputRoot,
                    viewport = request.viewport,
                    cropPlan = cropPlan,
                )
            renderSettings.recordExport(
                CurrentRunExport(
                    previewId = request.preview.id,
                    viewportLabel = request.viewport.label(),
                    renderMode = renderResult.renderMode.logLabel,
                    export = exportedBundle,
                ),
            )
            SingleCaptureResult.Captured(request.preview.id, request.viewport, renderResult.renderMode.logLabel)
        } catch (exception: Exception) {
            SingleCaptureResult.Failed(
                CaptureFailure(request.preview.id, request.viewport.label(), exception.message ?: exception.javaClass.name),
            )
        }

    private fun captureRequests(plan: CapturePlan): List<CaptureRequest> =
        plan.plannedCaptures
            .flatMap { plannedPreview -> plannedPreview.viewports.map { viewport -> plannedPreview.preview to viewport } }
            .mapIndexed { index, (preview, viewport) ->
                CaptureRequest(
                    preview = preview,
                    viewport = viewport,
                    scratchDirectory = renderOutputDirectory.get().asFile.resolve("capture-$index"),
                )
            }

    private fun recordCompletedCapture(result: SingleCaptureResult) {
        when (result) {
            is SingleCaptureResult.Captured -> logCaptured(result)
            is SingleCaptureResult.Failed -> logFailure(result.failure)
        }
    }

    private fun logCaptured(result: SingleCaptureResult.Captured) {
        logger.lifecycle("Captured ${result.previewId} (${result.viewport.platform}-${result.viewport.name}) via ${result.renderModeLabel}")
    }

    private fun logFailure(failure: CaptureFailure) {
        logger.error("Failed ${failure.previewId} (${failure.viewport}): ${failure.message}")
    }

    private fun logSummary(
        plan: CapturePlan,
        capturedViewportCount: Int,
        failedViewportCount: Int,
        accessibilityCheck: Boolean,
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
        if (!plan.dryRun && !accessibilityCheck) {
            logger.lifecycle(
                "Tip: run with -PagentPreview.accessibilityCheck=true to generate an accessibility report for rendered snapshots.",
            )
        }
    }

    private fun logArtifactLocations(
        plan: CapturePlan,
        wroteSnapshots: Boolean,
    ) {
        if (wroteSnapshots) {
            logger.lifecycle("AgentPreview snapshots written to: ${outputDirectory.get().asFile.absolutePath}")
        }
        val reportLabel = if (plan.dryRun) "dry-run report" else "report"
        logger.lifecycle("AgentPreview $reportLabel written to: ${reportFile().absolutePath}")
    }

    private fun logNoSelection(plan: CapturePlan) {
        if (plan.selectedPreviewCount == 0) {
            logger.lifecycle("No Compose previews selected for capture.")
        }
    }

    private fun writeReport(report: CaptureReport) {
        val reportFile = reportFile()
        reportFile.parentFile.mkdirs()
        reportFile.writeText(reportJson.encodeToString(CaptureReport.serializer(), report))
    }

    private fun reportFile(): File = reportDirectory.get().asFile.resolve("capture-report.json")

    private fun clearAccessibilityReport() {
        val reportRoot = reportDirectory.get().asFile
        reportRoot.resolve("accessibility-report.html").deleteIfExists()
        reportRoot.resolve("accessibility-assets").deleteRecursivelyIfExists()
    }

    private fun File.deleteIfExists() {
        if (isFile && !delete()) {
            logger.warn("Failed to delete stale accessibility report $absolutePath.")
        }
    }

    private fun File.deleteRecursivelyIfExists() {
        if (exists() && !deleteRecursively()) {
            logger.warn("Failed to delete stale accessibility report assets $absolutePath.")
        }
    }

    private fun writeAccessibilityReport(exports: List<CurrentRunExport>) {
        val bundles =
            exports.map { exported ->
                AuditedSnapshotBundle(
                    previewId = exported.previewId,
                    viewportLabel = exported.viewportLabel,
                    snapshotFile = exported.export.snapshotFile,
                    screenshotFile = exported.export.screenshotFile,
                    reportScreenshotFile = null,
                    renderMode = exported.renderMode,
                )
            }
        val assetsResult =
            AccessibilityReportAssetWriter.write(
                bundles = bundles,
                assetsDir = reportDirectory.get().asFile.resolve("accessibility-assets"),
            )
        val auditReport = AccessibilityAuditor.audit(assetsResult.bundles)
        val reportWithAssetWarnings = auditReport.copy(warnings = assetsResult.warnings + auditReport.warnings)
        val reportFile =
            AccessibilityHtmlReportWriter.write(
                report = reportWithAssetWarnings,
                bundles = assetsResult.bundles,
                outputDir = reportDirectory.get().asFile,
            )
        logger.lifecycle("AgentPreview accessibility report written to: ${reportFile.absolutePath}")
    }

    private fun Viewport.label(): String = "$platform-$name"

    private data class RenderSettings(
        val useFakeRenderer: Boolean,
        val outputRoot: File,
        val renderOutput: File,
        val robolectricSdk: Int,
        val previewClasspath: List<File>,
        val includeUnmergedSemantics: Boolean,
        val cropToContent: Boolean,
        val cropPaddingDp: Int,
        val androidAssetsDir: File?,
        val androidResourceApk: File?,
        val androidMergedManifest: File?,
        val androidCustomPackage: String?,
        val sdkLookupBaseDir: File,
        val recordExport: (CurrentRunExport) -> Unit,
    )

    private fun renderSettings(recordExport: (CurrentRunExport) -> Unit) =
        RenderSettings(
            useFakeRenderer = fakeRenderer.get(),
            outputRoot = outputDirectory.get().asFile,
            renderOutput = renderOutputDirectory.get().asFile,
            robolectricSdk = robolectricSdk.get(),
            previewClasspath = previewClasspath(),
            includeUnmergedSemantics = includeUnmergedSemantics.get(),
            cropToContent = effectiveCropToContent(),
            cropPaddingDp = effectiveCropPaddingDp(),
            androidAssetsDir = materializedAndroidAssetsDir(),
            androidResourceApk = materializedAndroidResourceApk(),
            androidMergedManifest = materializedAndroidMergedManifest(),
            androidCustomPackage = effectiveAndroidCustomPackage.get().ifBlank { null },
            sdkLookupBaseDir = File(sdkLookupBaseDir.get()),
            recordExport = recordExport,
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
                androidAssetsDir = settings.androidAssetsDir,
                androidResourceApk = settings.androidResourceApk,
                androidMergedManifest = settings.androidMergedManifest,
                androidCustomPackage = settings.androidCustomPackage,
                sdkLookupBaseDir = settings.sdkLookupBaseDir,
            )
        }

        fun render(
            preview: PreviewDescriptor,
            viewport: Viewport,
            scratchDirectory: File,
        ): RenderResult =
            if (settings.useFakeRenderer) {
                fakePreviewRenderer.render(preview, viewport, scratchDirectory)
            } else {
                previewRenderer.render(preview, viewport, scratchDirectory)
            }
    }

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
        AarClasspathMaterializer().materialize(
            effectivePreviewClasses() + androidRuntimeClasses() + previewRuntimeClasspath.files + rendererRuntimeClasspathIfAndroidBacked(),
        )

    private fun effectiveMaxPreviewParameterValues(): Int =
        AgentPreviewTaskOptions.maxPreviewParameterValues(
            defaultValue = maxPreviewParameterValues.get(),
            cliValue = cliMaxPreviewParameterValues.orNull,
        )

    private fun effectiveMaxCaptures(): Int? =
        AgentPreviewTaskOptions.maxCaptures(
            defaultValue = maxCaptures.orNull,
            cliValue = cliMaxCaptures.orNull,
        )

    private fun effectiveMaxParallelRenders(): Int =
        AgentPreviewTaskOptions.maxParallelRenders(
            defaultValue = maxParallelRenders.get(),
            cliValue = cliMaxParallelRenders.orNull,
        )

    private fun effectiveDryRun(): Boolean =
        AgentPreviewTaskOptions.dryRun(
            defaultValue = dryRun.get(),
            cliValue = cliDryRun.orNull,
        )

    private fun effectiveContinueOnError(): Boolean =
        AgentPreviewTaskOptions.continueOnError(
            defaultValue = continueOnError.get(),
            cliValue = cliContinueOnError.orNull,
        )

    private fun effectiveAccessibilityCheck(): Boolean {
        val cliValue = cliAccessibilityCheck.orNull
        val parsedValue = cliValue?.toBooleanStrictOrNull()
        require(cliValue == null || parsedValue != null) {
            "agentPreview.accessibilityCheck must be true or false. " +
                "Configure agentPreview { accessibilityCheck.set(true|false) } or pass -PagentPreview.accessibilityCheck=true|false."
        }
        return parsedValue ?: accessibilityCheck.get()
    }

    private fun effectiveCropToContent(): Boolean =
        AgentPreviewTaskOptions.cropToContent(
            defaultValue = cropToContent.get(),
            cliValue = cliCropToContent.orNull,
        )

    private fun effectiveCropPaddingDp(): Int =
        AgentPreviewTaskOptions.cropPaddingDp(
            defaultValue = cropPaddingDp.get(),
            cliValue = cliCropPaddingDp.orNull,
        )

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
        if (effectivePreviewClasses().isEmpty()) emptySet() else rendererRuntimeClasspath.files

    private fun materializedAndroidAssetsDir(): File? {
        if (fakeRenderer.get()) return null
        val assetRoots = effectiveAndroidAssetsDirs.files
        val hasAssets = assetRoots.any { root -> root.isDirectory && root.walkTopDown().any { it.isFile } }
        if (!hasAssets) return null
        return AndroidAssetMaterializer().materialize(
            inputRoots = assetRoots,
            outputRoot = renderOutputDirectory.get().asFile.resolve("merged-assets"),
        )
    }

    private fun materializedAndroidResourceApk(): File? {
        if (fakeRenderer.get()) return null
        return AndroidResourceApkResolver.resolve(
            directApk = optionalDirectAndroidResourceApk(),
            linkedResourceApkDirs = androidLinkedResourceApkDirs.files,
        )
    }

    private fun providerBackedFileCollection(file: RegularFileProperty): FileCollection =
        project.objects
            .fileCollection()
            .from(file.asFile.map { selectedFile -> listOf(selectedFile) }.orElse(emptyList()))

    private fun optionalDirectAndroidResourceApk(): File? =
        runCatching { androidResourceApk.orNull?.asFile }
            .getOrNull()

    private fun materializedAndroidMergedManifest(): File? {
        if (fakeRenderer.get()) return null
        return optionalAndroidMergedManifest()?.takeIf { it.isFile }
    }

    private fun optionalAndroidMergedManifest(): File? =
        runCatching { androidMergedManifest.orNull?.asFile }
            .getOrNull()

    private fun logEffectiveRenderingClasspath() {
        if (effectivePreviewClasses().isEmpty()) return
        logger.lifecycle(
            "AgentPreview capture variant ${selectedVariant.get()} runtime artifacts: " +
                (previewRuntimeClasspath.files + rendererRuntimeClasspathIfAndroidBacked())
                    .plus(androidRuntimeClasses())
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

    private fun selectionService(): PreviewSelectionService =
        PreviewSelectionService(
            projectPath = projectPath.get(),
            classesDirs = effectivePreviewClasses().toList(),
            discoveryClasspath =
                AarClasspathMaterializer().materialize(
                    previewRuntimeClasspath.files + androidRuntimeClasses() + rendererRuntimeClasspathIfAndroidBacked(),
                ),
            previewParameterClasspath = previewClasspath(),
        )

    private fun effectivePreviewClasses(): Set<File> =
        previewClassesDirs.files +
            androidProjectClassDirs.get().map { directory -> directory.asFile } +
            androidProjectClassJars.get().map { jar -> jar.asFile }

    private fun androidRuntimeClasses(): Set<File> =
        androidRuntimeClassDirs.get().map { directory -> directory.asFile }.toSet() +
            androidRuntimeClassJars.get().map { jar -> jar.asFile }
}

private data class CaptureTaskResult(
    val capturedViewportCount: Int,
    val failures: List<CaptureFailure>,
    val exportedBundles: List<CurrentRunExport>,
)

private data class CurrentRunExport(
    val previewId: String,
    val viewportLabel: String,
    val renderMode: String,
    val export: SnapshotExportMetadata,
)
