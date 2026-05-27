/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.dependencies

internal data class ResolvedModuleCoordinate(
    val group: String,
    val module: String,
    val version: String,
) {
    val notation: String = "$group:$module:$version"
}

internal data class ComposeVersionDetectionResult(
    val version: String?,
    val matchedModules: List<String>,
)

/**
 * Infers the Compose UI version from the selected variant's resolved dependency graph.
 */
internal class ComposeVersionDetector {
    fun detect(modules: List<ResolvedModuleCoordinate>): ComposeVersionDetectionResult {
        val matches =
            modules.filter {
                it.group == "androidx.compose.ui" && it.module in composeVersionSourceModules
            }

        return ComposeVersionDetectionResult(
            version = matches.firstOrNull()?.version,
            matchedModules = matches.map { it.notation },
        )
    }

    private companion object {
        val composeVersionSourceModules =
            setOf(
                "ui",
                "ui-android",
                "ui-tooling-preview",
                "ui-tooling-preview-android",
            )
    }
}
