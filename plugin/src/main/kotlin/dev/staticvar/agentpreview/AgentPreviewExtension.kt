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

abstract class AgentPreviewExtension(
    project: Project,
) {
    val outputDirectory: Provider<Directory> =
        project.layout.buildDirectory.dir("agentPreviewSnapshots")

    val previewClassesDirs: ConfigurableFileCollection = project.objects.fileCollection()
    val previewRuntimeClasspath: ConfigurableFileCollection = project.objects.fileCollection()
    val android: AndroidPreviewConfig = project.objects.newInstance(AndroidPreviewConfig::class.java)

    abstract val includeUnmergedSemantics: Property<Boolean>
    abstract val previewNameFilter: ListProperty<String>
    abstract val viewportNameFilter: ListProperty<String>
    abstract val maxPreviewParameterValues: Property<Int>
    abstract val maxCaptures: Property<Int>
    abstract val continueOnError: Property<Boolean>

    init {
        includeUnmergedSemantics.convention(false)
        previewNameFilter.convention(emptyList())
        viewportNameFilter.convention(emptyList())
        maxPreviewParameterValues.convention(50)
        continueOnError.convention(false)
    }

    fun android(action: Action<AndroidPreviewConfig>) {
        action.execute(android)
    }
}
