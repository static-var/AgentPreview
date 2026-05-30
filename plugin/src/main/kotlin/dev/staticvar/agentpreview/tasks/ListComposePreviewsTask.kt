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
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
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
    /** Gradle project path used as the stable prefix for discovered preview ids. */
    @get:Input
    abstract val projectPath: Property<String>

    /** Preview id/name/function filters after DSL and CLI additive filters have been combined. */
    @get:Input
    abstract val previewNameFilter: ListProperty<String>

    /** Default cap for enumerating `@PreviewParameter` values when listing parameterized previews. */
    @get:Input
    abstract val maxPreviewParameterValues: Property<Int>

    /** CLI scalar override for [maxPreviewParameterValues]; unlike list filters, this replaces the DSL value. */
    @get:Input
    @get:Optional
    abstract val cliMaxPreviewParameterValues: Property<String>

    /** Primary bytecode scan roots; when empty, listing falls back to the generated preview index input. */
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

    /** Consumer runtime classpath used for scanner metadata and preview-parameter provider counting. */
    @get:Classpath
    abstract val previewRuntimeClasspath: ConfigurableFileCollection

    /** Renderer-owned scanner support artifacts appended only for Android-backed bytecode discovery. */
    @get:Classpath
    abstract val previewSupportClasspath: ConfigurableFileCollection

    /** Android variant name used for compatibility diagnostics and renderer-support classpath reporting. */
    @get:Input
    abstract val selectedVariant: Property<String>

    /** Robolectric SDK configured for real rendering; list uses it only for compatibility warnings. */
    @get:Input
    abstract val robolectricSdk: Property<Int>

    /** Java major version of the current Gradle JVM, used to warn about unsupported render configurations. */
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
        if (effectivePreviewClasses().isEmpty()) emptySet() else previewSupportClasspath.files

    private fun logEffectiveRenderingClasspath() {
        if (effectivePreviewClasses().isEmpty()) return
        logger.debug(
            "AgentPreview list variant ${selectedVariant.get()} runtime artifacts: " +
                materializedDiscoveryClasspath().joinToString(", ") { it.name },
        )
    }

    private fun materializedDiscoveryClasspath(): List<File> =
        AarClasspathMaterializer().materialize(
            previewRuntimeClasspath.files + androidRuntimeClasses() + previewSupportClasspathIfAndroidBacked(),
        )

    private fun selectionService(): PreviewSelectionService =
        PreviewSelectionService(
            projectPath = projectPath.get(),
            classesDirs = effectivePreviewClasses().toList(),
            discoveryClasspath = materializedDiscoveryClasspath(),
        )

    private fun effectivePreviewClasses(): Set<File> =
        previewClassesDirs.files +
            androidProjectClassDirs.get().map { directory -> directory.asFile } +
            androidProjectClassJars.get().map { jar -> jar.asFile }

    private fun androidRuntimeClasses(): Set<File> =
        androidRuntimeClassDirs.get().map { directory -> directory.asFile }.toSet() +
            androidRuntimeClassJars.get().map { jar -> jar.asFile }
}
