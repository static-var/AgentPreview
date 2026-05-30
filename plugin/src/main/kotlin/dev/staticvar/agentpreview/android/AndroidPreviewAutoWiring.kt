/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.android

import dev.staticvar.agentpreview.AgentPreviewExtension
import org.gradle.api.Project

internal class AndroidPreviewAutoWiring(
    private val project: Project,
    private val extension: AgentPreviewExtension,
) {
    fun configure() {
        project.afterEvaluate {
            val variantName = extension.android.variant.get()
            // Strategy order is intentional: Android-backed variants win over the Android KMP fallback.
            // Each strategy appends inferred classpaths to any user-configured classpaths on the extension.
            // Future pass: replace afterEvaluate and hard-coded output paths with provider-backed Android Components/KMP APIs safely.
            strategies(variantName).firstOrNull { it.canWire() }?.wire()
        }
    }

    private fun strategies(variantName: String): List<AndroidPreviewWiringStrategy> =
        listOf(
            AndroidVariantPreviewWiring(project, extension, variantName),
            AndroidKmpPreviewWiring(project, extension),
        )
}
