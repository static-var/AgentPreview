/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview

import dev.staticvar.agentpreview.android.AndroidPreviewAutoWiring
import dev.staticvar.agentpreview.config.ConfiguredViewport
import dev.staticvar.agentpreview.dependencies.AarClasspathMaterializer
import dev.staticvar.agentpreview.dependencies.RendererDependencyPolicy
import dev.staticvar.agentpreview.dependencies.RendererSupportClasspathResolver
import dev.staticvar.agentpreview.dependencies.RendererSupportConfigurationFactory
import dev.staticvar.agentpreview.dependencies.ResolvedArtifactCoordinate
import dev.staticvar.agentpreview.dependencies.VariantRuntimeClasspathProvider
import dev.staticvar.agentpreview.tasks.CaptureComposePreviewsTask
import dev.staticvar.agentpreview.tasks.ListComposePreviewsTask
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import java.io.File

class AgentPreviewPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension =
            project.extensions.create(
                "agentPreview",
                AgentPreviewExtension::class.java,
                project,
            )

        val previewIndexFile = project.layout.buildDirectory.file("agentPreview/discovered-previews.json")
        val selectedVariant = extension.android.variant
        val variantRuntimeConfigurations = mutableMapOf<String, Configuration>()

        val listComposePreviews =
            project.tasks.register("listComposePreviews", ListComposePreviewsTask::class.java) {
                it.group = "agent preview"
                it.description = "Lists Compose previews discoverable by Preview For Agents."
                it.previewIndexFile.set(
                    project.provider { previewIndexFile.get().takeIf { file -> file.asFile.isFile } },
                )
                it.projectPath.set(project.path)
                it.previewNameFilter.set(extension.previewNameFilter)
                it.previewNameFilter.addAll(csvGradleProperty(project, "agentPreview.previewNameFilter"))
                it.maxPreviewParameterValues.set(extension.maxPreviewParameterValues)
                it.cliMaxPreviewParameterValues.set(project.providers.gradleProperty("agentPreview.maxPreviewParameterValues"))
                it.previewClassesDirs.from(extension.previewClassesDirs)
                it.androidProjectClassDirs.convention(emptyList())
                it.androidProjectClassJars.convention(emptyList())
                it.androidRuntimeClassDirs.convention(emptyList())
                it.androidRuntimeClassJars.convention(emptyList())
                it.previewRuntimeClasspath.from(extension.previewRuntimeClasspath)
                it.previewSupportClasspath.from(
                    project.provider {
                        resolveRendererClasspath(
                            project = project,
                            variant = selectedVariant.get(),
                            providerBackedRuntimeConfiguration = variantRuntimeConfigurations[selectedVariant.get()],
                        ).previewSupportFiles
                    },
                )
                it.selectedVariant.set(selectedVariant)
                it.robolectricSdk.set(extension.android.robolectricSdk)
                it.javaMajorVersion.set(javaMajorVersion(project))
            }

        val captureComposePreviews =
            project.tasks.register("captureComposePreviews", CaptureComposePreviewsTask::class.java) {
                it.group = "agent preview"
                it.description = "Captures Compose previews into screenshot.png and snapshot.json bundles."
                it.previewIndexFile.set(
                    project.provider { previewIndexFile.get().takeIf { file -> file.asFile.isFile } },
                )
                it.projectPath.set(project.path)
                it.outputDirectory.set(extension.outputDirectory)
                it.reportDirectory.set(project.layout.buildDirectory.dir("agentPreviewReports"))
                it.renderOutputDirectory.set(project.layout.buildDirectory.dir("agentPreview/render"))
                it.includeUnmergedSemantics.set(extension.includeUnmergedSemantics)
                it.previewNameFilter.set(extension.previewNameFilter)
                it.previewNameFilter.addAll(csvGradleProperty(project, "agentPreview.previewNameFilter"))
                it.viewportNameFilter.set(extension.viewportNameFilter)
                it.viewportNameFilter.addAll(csvGradleProperty(project, "agentPreview.viewportFilter"))
                it.maxPreviewParameterValues.set(extension.maxPreviewParameterValues)
                it.cliMaxPreviewParameterValues.set(project.providers.gradleProperty("agentPreview.maxPreviewParameterValues"))
                it.maxCaptures.set(extension.maxCaptures)
                it.cliMaxCaptures.set(project.providers.gradleProperty("agentPreview.maxCaptures"))
                it.maxParallelRenders.set(extension.maxParallelRenders)
                it.cliMaxParallelRenders.set(project.providers.gradleProperty("agentPreview.maxParallelRenders"))
                it.dryRun.set(false)
                it.cliDryRun.set(project.providers.gradleProperty("agentPreview.dryRun"))
                it.continueOnError.set(extension.continueOnError)
                it.cliContinueOnError.set(project.providers.gradleProperty("agentPreview.continueOnError"))
                it.cropToContent.set(extension.android.screenshot.cropToContent)
                it.cliCropToContent.set(project.providers.gradleProperty("agentPreview.cropToContent"))
                it.cropPaddingDp.set(extension.android.screenshot.cropPaddingDp)
                it.cliCropPaddingDp.set(project.providers.gradleProperty("agentPreview.cropPaddingDp"))
                it.previewClassesDirs.from(extension.previewClassesDirs)
                it.androidProjectClassDirs.convention(emptyList())
                it.androidProjectClassJars.convention(emptyList())
                it.androidRuntimeClassDirs.convention(emptyList())
                it.androidRuntimeClassJars.convention(emptyList())
                it.previewRuntimeClasspath.from(extension.previewRuntimeClasspath)
                it.rendererRuntimeClasspath.from(
                    project.provider {
                        resolveRendererClasspath(
                            project = project,
                            variant = selectedVariant.get(),
                            providerBackedRuntimeConfiguration = variantRuntimeConfigurations[selectedVariant.get()],
                        ).rendererRuntimeFiles
                    },
                )
                it.selectedVariant.set(selectedVariant)
                it.androidViewportsJson.set(
                    project.provider {
                        Json.encodeToString(
                            ListSerializer(ConfiguredViewport.serializer()),
                            extension.android.viewports.get(),
                        )
                    },
                )
                it.robolectricSdk.set(extension.android.robolectricSdk)
                it.javaMajorVersion.set(javaMajorVersion(project))
                it.fakeRenderer.set(
                    project.providers
                        .gradleProperty("agentPreview.fakeRenderer")
                        .map(String::toBoolean)
                        .orElse(false),
                )
            }

        AndroidPreviewAutoWiring(
            project = project,
            extension = extension,
            listComposePreviews = listComposePreviews,
            captureComposePreviews = captureComposePreviews,
            registerRuntimeConfiguration = { variant, configuration -> variantRuntimeConfigurations[variant] = configuration },
        ).configure()
    }

    private fun resolveRendererClasspath(
        project: Project,
        variant: String,
        providerBackedRuntimeConfiguration: Configuration?,
    ): ResolvedRendererClasspath {
        RendererDependencyPolicy.requireSupportedVariant(variant)
        val runtimeProvider = VariantRuntimeClasspathProvider(project)
        val inspectedConfigurations = runtimeProvider.inspectedConfigurationNames(variant)
        val variantRuntimeConfiguration = providerBackedRuntimeConfiguration ?: runtimeProvider.configurationFor(variant)
        val runtimeArtifacts = variantRuntimeConfiguration.resolvedArtifacts()
        val resolution =
            RendererSupportClasspathResolver().resolve(
                selectedVariant = variant,
                inspectedConfigurations = inspectedConfigurations,
                runtimeArtifacts = runtimeArtifacts,
            )
        resolution.warnings.forEach(project.logger::warn)
        project.logger.debug(
            "AgentPreview renderer variant '$variant' compose=${resolution.composeVersion}; consumer tooling=" +
                resolution.consumerArtifactFiles.joinToString(", ").ifBlank { "<none>" } +
                "; plugin renderer support=" +
                resolution.pluginArtifactCoordinates.joinToString(", ").ifBlank { "<none>" },
        )
        val pluginFiles = pluginOwnedRendererFiles(project, resolution.pluginArtifactCoordinates, variantRuntimeConfiguration)
        val consumerFiles = AarClasspathMaterializer().materialize(resolution.consumerArtifactFiles.map(::File))
        return ResolvedRendererClasspath(
            previewSupportFiles = (consumerFiles + pluginFiles.filter { it.name.contains("ui-tooling") }).toSet(),
            rendererRuntimeFiles = (consumerFiles + pluginFiles).toSet(),
        )
    }

    private fun pluginOwnedRendererFiles(
        project: Project,
        coordinates: List<String>,
        variantRuntimeConfiguration: Configuration?,
    ): Set<File> {
        if (coordinates.isEmpty()) return emptySet()
        val configuration =
            RendererSupportConfigurationFactory().create(
                project = project,
                coordinates = coordinates,
                variantRuntimeConfiguration = variantRuntimeConfiguration,
            )
        return lenientFiles(project, configuration).files
    }

    private fun lenientFiles(
        project: Project,
        configuration: Configuration,
    ) = project.files(
        project.provider<Set<File>> {
            configuration.resolvedConfiguration.lenientConfiguration.artifacts
                .map { artifact -> artifact.file }
                .toSet()
        },
    )

    private fun Configuration?.resolvedArtifacts(): List<ResolvedArtifactCoordinate> {
        if (this == null) return emptyList()
        return resolvedConfiguration.lenientConfiguration.artifacts.mapNotNull { artifact ->
            val id = artifact.moduleVersion.id
            val version = id.version ?: return@mapNotNull null
            ResolvedArtifactCoordinate(
                group = id.group,
                module = artifact.name,
                version = version,
                filePath = artifact.file.absolutePath,
            )
        }
    }

    private fun javaMajorVersion(project: Project) =
        project.providers
            .gradleProperty("agentPreview.javaMajorVersion")
            .map(String::toInt)
            .orElse(Runtime.version().feature())

    private fun csvGradleProperty(
        project: Project,
        name: String,
    ) = project.providers
        .gradleProperty(name)
        .map { raw -> raw.splitCsv() }
        .orElse(emptyList())

    private fun String.splitCsv(): List<String> =
        split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
}

private data class ResolvedRendererClasspath(
    val previewSupportFiles: Set<File>,
    val rendererRuntimeFiles: Set<File>,
)
