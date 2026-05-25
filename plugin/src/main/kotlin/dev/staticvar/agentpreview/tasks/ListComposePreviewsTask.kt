/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.config.AndroidPreviewConfigValidator
import dev.staticvar.agentpreview.discovery.JsonIndexPreviewDiscovery
import dev.staticvar.agentpreview.discovery.PreviewDiscovery
import dev.staticvar.agentpreview.discovery.PreviewDiscoveryResult
import dev.staticvar.agentpreview.model.PreviewParameterDescriptor
import dev.staticvar.agentpreview.scanner.discovery.PreviewScanDiagnostic
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class ListComposePreviewsTask : DefaultTask() {
    @get:Input
    abstract val previewIndexFilePath: Property<String>

    @get:Input
    abstract val previewIndexContent: Property<String>

    @get:Input
    abstract val previewNameFilter: ListProperty<String>

    @get:Classpath
    abstract val previewClassesDirs: ConfigurableFileCollection

    @get:Classpath
    abstract val previewRuntimeClasspath: ConfigurableFileCollection

    @get:Input
    abstract val robolectricSdk: Property<Int>

    @get:Input
    abstract val javaMajorVersion: Property<Int>

    @TaskAction
    fun list() {
        val indexFile = File(previewIndexFilePath.get())
        warnIfConfigurationIsIncompatible()
        val filters = previewNameFilter.get().toSet()
        val discoveryResult = discoverPreviews(indexFile)
        logDiagnostics(discoveryResult.diagnostics)
        val previews =
            discoveryResult.previews
                .filter { filters.isEmpty() || it.id in filters || it.name in filters }

        if (previews.isEmpty()) {
            logger.lifecycle("No Compose previews discovered.")
            return
        }

        previews.forEach { preview ->
            val label = preview.name ?: preview.fullyQualifiedFunctionName
            val parameterNote = previewParameterNote(preview.previewParameter)
            logger.lifecycle("${preview.id}  $label$parameterNote")
        }
    }

    private fun warnIfConfigurationIsIncompatible() {
        AndroidPreviewConfigValidator
            .warning(
                robolectricSdk = robolectricSdk.get(),
                javaMajorVersion = javaMajorVersion.get(),
            )?.let { warning -> logger.warn(warning) }
    }

    private fun previewParameterNote(parameter: PreviewParameterDescriptor?): String =
        parameter
            ?.let {
                val limit = it.limit?.toString() ?: "default cap 50"
                "  [@PreviewParameter provider=${it.providerClassName}, limit=$limit; values expand during capture]"
            }.orEmpty()

    private fun logDiagnostics(diagnostics: List<PreviewScanDiagnostic>) {
        diagnostics.forEach { diagnostic ->
            val message = "AgentPreview scanner: ${diagnostic.message}"
            when (diagnostic.severity) {
                PreviewScanDiagnostic.Severity.WARNING -> logger.warn(message)
                PreviewScanDiagnostic.Severity.ERROR -> logger.error(message)
            }
        }
    }

    private fun previewSupportClasspathIfAndroidBacked(): Set<File> =
        if (previewClassesDirs.files.isEmpty()) {
            emptySet()
        } else {
            runCatching {
                project.configurations
                    .detachedConfiguration(
                        project.dependencies.create("androidx.compose.ui:ui-tooling:1.11.2"),
                    ).resolve()
            }.getOrDefault(emptySet())
        }

    private fun discoverPreviews(indexFile: File): PreviewDiscoveryResult =
        if (previewClassesDirs.files.isNotEmpty()) {
            PreviewDiscovery(
                projectPath = project.path,
                sourceSetName = "main",
                classesDirs = previewClassesDirs.files.toList(),
                runtimeClasspath = (previewRuntimeClasspath.files + previewSupportClasspathIfAndroidBacked()).toList(),
            ).discoverWithDiagnostics()
        } else {
            PreviewDiscoveryResult(
                previews = JsonIndexPreviewDiscovery(indexFile).discover(),
                diagnostics = emptyList(),
            )
        }
}
