/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.dependencies

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute

internal class RendererSupportConfigurationFactory {
    fun create(
        project: Project,
        coordinates: List<String>,
        variantRuntimeConfiguration: Configuration?,
    ): Configuration =
        project.configurations.detachedConfiguration().apply {
            coordinates
                .map(project.dependencies::create)
                .forEach(dependencies::add)
            isCanBeConsumed = false
            isCanBeResolved = true
            isVisible = false
            description = "AgentPreview renderer-only support for isolated preview rendering"
            copyAttributesFrom(variantRuntimeConfiguration)
        }

    private fun Configuration.copyAttributesFrom(source: Configuration?) {
        source?.attributes?.keySet()?.forEach { attribute ->
            copyAttributeFrom(source, attribute)
        }
    }

    private fun <T : Any> Configuration.copyAttributeFrom(
        source: Configuration,
        attribute: Attribute<T>,
    ) {
        val value = source.attributes.getAttribute(attribute) ?: return
        attributes.attribute(attribute, value)
    }
}
