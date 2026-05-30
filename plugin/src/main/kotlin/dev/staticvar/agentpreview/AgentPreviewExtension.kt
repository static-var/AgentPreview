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
 * `maxPreviewParameterValues` is 50, `maxParallelRenders` is 1, and `continueOnError` is false.
 * Scalar command-line properties such as `-PagentPreview.maxPreviewParameterValues=n`,
 * `-PagentPreview.maxCaptures=n`, `-PagentPreview.maxParallelRenders=n`, and
 * `-PagentPreview.continueOnError=true|false` take precedence over the matching DSL defaults for that
 * invocation. List filters are additive: DSL `previewNameFilter`/`viewportNameFilter` entries are combined
 * with CSV command-line filters from `-PagentPreview.previewNameFilter=...` and
 * `-PagentPreview.viewportFilter=...`.
 */
abstract class AgentPreviewExtension(
    project: Project,
) {
    val outputDirectory: Provider<Directory> =
        project.layout.buildDirectory.dir("agentPreviewSnapshots")

    val previewClassesDirs: ConfigurableFileCollection = project.objects.fileCollection()
    val previewRuntimeClasspath: ConfigurableFileCollection = project.objects.fileCollection()

    /**
     * AgentPreview renders against the selected Android variant runtime classpath but keeps renderer-only
     * support dependencies out of the app's packaged dependency graph.
     */
    val android: AndroidPreviewConfig = project.objects.newInstance(AndroidPreviewConfig::class.java)

    abstract val includeUnmergedSemantics: Property<Boolean>
    abstract val previewNameFilter: ListProperty<String>
    abstract val viewportNameFilter: ListProperty<String>
    abstract val maxPreviewParameterValues: Property<Int>
    abstract val maxCaptures: Property<Int>
    abstract val maxParallelRenders: Property<Int>
    abstract val continueOnError: Property<Boolean>

    init {
        includeUnmergedSemantics.convention(false)
        previewNameFilter.convention(emptyList())
        viewportNameFilter.convention(emptyList())
        maxPreviewParameterValues.convention(50)
        maxParallelRenders.convention(1)
        continueOnError.convention(false)
    }

    fun android(action: Action<AndroidPreviewConfig>) {
        action.execute(android)
    }
}
