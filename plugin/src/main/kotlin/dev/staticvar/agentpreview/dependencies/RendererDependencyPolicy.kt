/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.dependencies

internal object RendererDependencyPolicy {
    const val FALLBACK_COMPOSE_VERSION = "1.11.2"
    const val FALLBACK_ANDROIDX_TEST_CORE_VERSION = "1.7.0"
    const val FALLBACK_ANDROIDX_TEST_MONITOR_VERSION = "1.8.0"

    private val rendererSupportModuleKeys =
        setOf(
            moduleKey("androidx.compose.ui", "ui-tooling"),
            moduleKey("androidx.compose.ui", "ui-tooling-data"),
            moduleKey("androidx.test", "core"),
            moduleKey("androidx.test", "monitor"),
        )

    fun isRendererSupportModule(
        group: String,
        module: String,
    ): Boolean = moduleKey(group, module) in rendererSupportModuleKeys

    fun moduleKey(coordinate: String): String = coordinate.substringBeforeLast(':')

    fun fallbackRendererCoordinates(composeVersion: String): List<String> =
        listOf(
            "androidx.compose.ui:ui-tooling:$composeVersion",
            "androidx.compose.ui:ui-tooling-data:$composeVersion",
            "androidx.test:core:$FALLBACK_ANDROIDX_TEST_CORE_VERSION",
            "androidx.test:monitor:$FALLBACK_ANDROIDX_TEST_MONITOR_VERSION",
        )

    fun requireSupportedVariant(variant: String) {
        require(!variant.equals("release", ignoreCase = true)) {
            releaseVariantMessage(variant)
        }
    }

    fun releaseVariantMessage(variant: String): String =
        "AgentPreview: agentPreview.android.variant=$variant is not supported for rendering. " +
            "AgentPreview reads the selected variant runtime classpath for rendering and keeps renderer support " +
            "dependencies out of the app production graph. Use a debug/preview-style variant instead."

    private fun moduleKey(
        group: String,
        module: String,
    ): String = "$group:$module"
}
