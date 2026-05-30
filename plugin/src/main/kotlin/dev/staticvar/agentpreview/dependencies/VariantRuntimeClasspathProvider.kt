/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.dependencies

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * Resolves the consuming project's selected variant runtime classpath for preview rendering without mutating
 * consumer dependency buckets.
 */
internal class VariantRuntimeClasspathProvider(
    private val project: Project,
) {
    fun configurationFor(variant: String): Configuration? =
        project.configurations.findByName("${variant}RuntimeClasspath")
            ?: project.configurations.findByName(ANDROID_KMP_RUNTIME_CLASSPATH)

    fun inspectedConfigurationNames(variant: String): List<String> =
        listOfNotNull(
            "${variant}RuntimeClasspath",
            "${variant}CompileClasspath",
            ANDROID_KMP_RUNTIME_CLASSPATH.takeIf { project.configurations.findByName(it) != null },
        )

    private companion object {
        const val ANDROID_KMP_RUNTIME_CLASSPATH = "androidRuntimeClasspath"
    }
}
