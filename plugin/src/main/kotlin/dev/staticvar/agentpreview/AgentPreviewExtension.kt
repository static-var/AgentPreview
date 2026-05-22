/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview

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

    abstract val includeUnmergedSemantics: Property<Boolean>
    abstract val previewNameFilter: ListProperty<String>

    init {
        includeUnmergedSemantics.convention(false)
        previewNameFilter.convention(emptyList())
    }
}
