/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.config.AndroidPreviewConfigValidator
import dev.staticvar.agentpreview.dependencies.AarClasspathMaterializer
import dev.staticvar.agentpreview.model.PreviewParameterDescriptor
import dev.staticvar.agentpreview.scanner.discovery.PreviewScanDiagnostic
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class ListComposePreviewsTask :
    DefaultTask(),
    PreviewIndexInput {
    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val previewNameFilter: ListProperty<String>

    @get:Input
    abstract val maxPreviewParameterValues: Property<Int>

    @get:Input
    @get:Optional
    abstract val cliMaxPreviewParameterValues: Property<String>

    @get:Classpath
    abstract val previewClassesDirs: ConfigurableFileCollection

    @get:Classpath
    abstract val previewRuntimeClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val previewSupportClasspath: ConfigurableFileCollection

    @get:Input
    abstract val selectedVariant: Property<String>

    @get:Input
    abstract val robolectricSdk: Property<Int>

    @get:Input
    abstract val javaMajorVersion: Property<Int>

    @TaskAction
    fun list() {
        val indexFile = previewIndexFileOrNull()
        warnIfConfigurationIsIncompatible()
        logEffectiveRenderingClasspath()
        val maxPreviewParameterValues = effectiveMaxPreviewParameterValues()
        val filters = previewNameFilter.get().toSet()
        val selection =
            selectionService().select(
                indexFile = indexFile,
                filters = filters,
                maxPreviewParameterValues = maxPreviewParameterValues,
                mode = PreviewSelectionService.Mode.LIST,
            )
        logDiagnostics(selection.diagnostics)
        val previews = selection.selectedPreviews

        if (previews.isEmpty()) {
            logger.lifecycle("No Compose previews discovered.")
            return
        }

        previews.forEach { preview ->
            val label = preview.name ?: preview.fullyQualifiedFunctionName
            val parameterNote = previewParameterNote(preview.previewParameter, maxPreviewParameterValues)
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

    private fun previewParameterNote(
        parameter: PreviewParameterDescriptor?,
        maxPreviewParameterValues: Int,
    ): String =
        parameter
            ?.let {
                val limit = it.limit?.toString() ?: "default cap $maxPreviewParameterValues"
                "  [@PreviewParameter provider=${it.providerClassName}, limit=$limit; capture ids append :previewParam-N]"
            }.orEmpty()

    private fun effectiveMaxPreviewParameterValues(): Int =
        AgentPreviewTaskOptions.maxPreviewParameterValues(
            defaultValue = maxPreviewParameterValues.get(),
            cliValue = cliMaxPreviewParameterValues.orNull,
        )

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
        if (previewClassesDirs.files.isEmpty()) emptySet() else previewSupportClasspath.files

    private fun logEffectiveRenderingClasspath() {
        if (previewClassesDirs.files.isEmpty()) return
        logger.debug(
            "AgentPreview list variant ${selectedVariant.get()} runtime artifacts: " +
                materializedDiscoveryClasspath().joinToString(", ") { it.name },
        )
    }

    private fun materializedDiscoveryClasspath(): List<File> =
        AarClasspathMaterializer().materialize(previewRuntimeClasspath.files + previewSupportClasspathIfAndroidBacked())

    private fun selectionService(): PreviewSelectionService =
        PreviewSelectionService(
            projectPath = projectPath.get(),
            classesDirs = previewClassesDirs.files.toList(),
            discoveryClasspath = materializedDiscoveryClasspath(),
        )
}
