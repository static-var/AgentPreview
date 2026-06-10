/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview

import dev.staticvar.agentpreview.config.AndroidPreviewConfig
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Gradle DSL for AgentPreview task defaults.
 *
 * Defaults: snapshots are written under `build/agentPreviewSnapshots`, reports under
 * `build/agentPreviewReports`, Android rendering uses the `debug` variant, preview filters are empty,
 * `maxPreviewParameterValues` is 50, `maxParallelRenders` is 1, `continueOnError` is false, and
 * `accessibilityCheck` is false.
 * Scalar command-line properties such as `-PagentPreview.maxPreviewParameterValues=n`,
 * `-PagentPreview.maxCaptures=n`, `-PagentPreview.maxParallelRenders=n`, and
 * `-PagentPreview.continueOnError=true|false`, and `-PagentPreview.accessibilityCheck=true|false` take
 * precedence over the matching DSL defaults for that invocation. List filters are additive: DSL
 * `previewNameFilter`/`viewportNameFilter` entries are combined with CSV command-line filters from
 * `-PagentPreview.previewNameFilter=...` and `-PagentPreview.viewportFilter=...`.
 */
abstract class AgentPreviewExtension(
    project: Project,
) {
    /** Directory where `captureComposePreviews` writes per-preview screenshot and snapshot bundles. */
    val outputDirectory: Provider<Directory> =
        project.layout.buildDirectory.dir("agentPreviewSnapshots")

    /** Primary compiled class directories scanned for `@Preview` composables; the generated index is used only when these are empty. */
    val previewClassesDirs: ConfigurableFileCollection = project.objects.fileCollection()

    /** Runtime classpath used for bytecode scanning, preview-parameter counting, and child-JVM rendering. */
    val previewRuntimeClasspath: ConfigurableFileCollection = project.objects.fileCollection()

    /**
     * AgentPreview renders against the selected Android variant runtime classpath but keeps renderer-only
     * support dependencies out of the app's packaged dependency graph.
     */
    val android: AndroidPreviewConfig = project.objects.newInstance(AndroidPreviewConfig::class.java)

    /** Include unmerged Compose semantics nodes in `snapshot.json` when the renderer can extract them. */
    abstract val includeUnmergedSemantics: Property<Boolean>

    /** Preview id/name/function filters used by list and capture tasks; CLI filters are appended to this list. */
    abstract val previewNameFilter: ListProperty<String>

    /** Viewport-name filters used by capture tasks; CLI viewport filters are appended to this list. */
    abstract val viewportNameFilter: ListProperty<String>

    /** Maximum values to enumerate from each `@PreviewParameter` provider before diagnostics cap expansion. */
    abstract val maxPreviewParameterValues: Property<Int>

    /** Optional cap on total planned captures after preview and viewport expansion. */
    abstract val maxCaptures: Property<Int>

    /** Maximum number of render child JVMs scheduled concurrently by `captureComposePreviews`. */
    abstract val maxParallelRenders: Property<Int>

    /** Continue writing successful captures and reports when an individual preview render fails. */
    abstract val continueOnError: Property<Boolean>

    /** Generate a non-failing accessibility HTML report from rendered snapshot bundles. */
    abstract val accessibilityCheck: Property<Boolean>

    init {
        includeUnmergedSemantics.convention(false)
        previewNameFilter.convention(emptyList())
        viewportNameFilter.convention(emptyList())
        maxPreviewParameterValues.convention(50)
        maxParallelRenders.convention(1)
        continueOnError.convention(false)
        accessibilityCheck.convention(false)
    }

    fun android(action: Action<AndroidPreviewConfig>) {
        action.execute(android)
    }
}
