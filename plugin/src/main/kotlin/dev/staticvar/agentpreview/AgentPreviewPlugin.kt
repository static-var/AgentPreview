/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview

import dev.staticvar.agentpreview.android.AndroidPreviewAutoWiring
import dev.staticvar.agentpreview.config.ConfiguredViewport
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

        AndroidPreviewAutoWiring(project, extension).configure()
        val previewIndexFile = project.layout.buildDirectory.file("agentPreview/discovered-previews.json")
        val previewSupportClasspath = previewSupportClasspath(project)
        val rendererRuntimeClasspath = rendererRuntimeClasspath(project)

        project.tasks.register("listComposePreviews", ListComposePreviewsTask::class.java) {
            it.group = "agent preview"
            it.description = "Lists Compose previews discoverable by Preview For Agents."
            it.previewIndexFilePath.set(previewIndexFile.map { file -> file.asFile.path })
            it.previewIndexContent.set(
                project.provider {
                    previewIndexFile
                        .get()
                        .asFile
                        .takeIf { file ->
                            file.isFile
                        }?.readText()
                        .orEmpty()
                },
            )
            it.projectPath.set(project.path)
            it.previewNameFilter.set(extension.previewNameFilter)
            it.previewNameFilter.addAll(csvGradleProperty(project, "agentPreview.previewNameFilter"))
            it.maxPreviewParameterValues.set(extension.maxPreviewParameterValues)
            it.cliMaxPreviewParameterValues.set(project.providers.gradleProperty("agentPreview.maxPreviewParameterValues"))
            it.previewClassesDirs.from(extension.previewClassesDirs)
            it.previewRuntimeClasspath.from(extension.previewRuntimeClasspath)
            it.previewSupportClasspath.from(previewSupportClasspath)
            it.robolectricSdk.set(extension.android.robolectricSdk)
            it.javaMajorVersion.set(javaMajorVersion(project))
        }

        project.tasks.register("captureComposePreviews", CaptureComposePreviewsTask::class.java) {
            it.group = "agent preview"
            it.description = "Captures Compose previews into screenshot.png and snapshot.json bundles."
            it.previewIndexFilePath.set(previewIndexFile.map { file -> file.asFile.path })
            it.previewIndexContent.set(
                project.provider {
                    previewIndexFile
                        .get()
                        .asFile
                        .takeIf { file ->
                            file.isFile
                        }?.readText()
                        .orEmpty()
                },
            )
            it.projectPath.set(project.path)
            it.outputDirectory.set(extension.outputDirectory)
            it.renderOutputDirectory.set(project.layout.buildDirectory.dir("agentPreview/render"))
            it.includeUnmergedSemantics.set(extension.includeUnmergedSemantics)
            it.previewNameFilter.set(extension.previewNameFilter)
            it.previewNameFilter.addAll(csvGradleProperty(project, "agentPreview.previewNameFilter"))
            it.viewportNameFilter.set(extension.viewportNameFilter)
            it.viewportNameFilter.addAll(csvGradleProperty(project, "agentPreview.viewportFilter"))
            it.maxPreviewParameterValues.set(extension.maxPreviewParameterValues)
            it.cliMaxPreviewParameterValues.set(project.providers.gradleProperty("agentPreview.maxPreviewParameterValues"))
            it.previewClassesDirs.from(extension.previewClassesDirs)
            it.previewRuntimeClasspath.from(extension.previewRuntimeClasspath)
            it.rendererRuntimeClasspath.from(rendererRuntimeClasspath)
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
    }

    private fun previewSupportClasspath(project: Project) =
        lenientFiles(
            project,
            project.configurations.detachedConfiguration(
                project.dependencies.create("androidx.compose.ui:ui-tooling:1.11.2"),
            ),
        )

    private fun rendererRuntimeClasspath(project: Project) =
        lenientFiles(
            project,
            project.configurations.detachedConfiguration(
                project.dependencies.create("androidx.compose.ui:ui-tooling:1.11.2"),
                project.dependencies.create("androidx.test:core:1.7.0"),
                project.dependencies.create("androidx.test:monitor:1.8.0"),
            ),
        )

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
