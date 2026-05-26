package dev.staticvar.agentpreview.dependencies

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * Reads the selected variant runtime classpath for rendering without mutating consumer dependency buckets.
 */
/**
 * Resolves the consuming project's selected variant runtime classpath for preview rendering only.
 */
internal class VariantRuntimeClasspathProvider(
    private val project: Project,
) {
    fun configurationFor(variant: String): Configuration? =
        project.configurations.findByName("${variant}RuntimeClasspath")

    fun inspectedConfigurationNames(variant: String): List<String> =
        listOf("${variant}RuntimeClasspath", "${variant}CompileClasspath")
}
