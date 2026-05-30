/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.android

import dev.staticvar.agentpreview.AgentPreviewExtension
import dev.staticvar.agentpreview.tasks.CaptureComposePreviewsTask
import dev.staticvar.agentpreview.tasks.ListComposePreviewsTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskProvider

internal class AndroidPreviewAutoWiring(
    private val project: Project,
    private val extension: AgentPreviewExtension,
    private val listComposePreviews: TaskProvider<ListComposePreviewsTask>,
    private val captureComposePreviews: TaskProvider<CaptureComposePreviewsTask>,
    private val registerRuntimeConfiguration: (String, Configuration) -> Unit,
) {
    fun configure() {
        AndroidVariantPreviewWiring(
            project = project,
            extension = extension,
            listComposePreviews = listComposePreviews,
            captureComposePreviews = captureComposePreviews,
            registerRuntimeConfiguration = registerRuntimeConfiguration,
        ).configure()
        AndroidKmpPreviewWiring(
            project = project,
            listComposePreviews = listComposePreviews,
            captureComposePreviews = captureComposePreviews,
        ).configure()
    }
}
