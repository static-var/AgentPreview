package dev.staticvar.agentpreview.dependencies

internal object RendererDependencyPolicy {
    const val FALLBACK_COMPOSE_VERSION = "1.11.2"
    const val FALLBACK_ANDROIDX_TEST_CORE_VERSION = "1.7.0"
    const val FALLBACK_ANDROIDX_TEST_MONITOR_VERSION = "1.8.0"

    val composeToolingModules = setOf("ui-tooling", "ui-tooling-data")

    fun fallbackRendererCoordinates(composeVersion: String): List<String> =
        listOf(
            "androidx.compose.ui:ui-tooling:$composeVersion",
            "androidx.compose.ui:ui-tooling-data:$composeVersion",
            "androidx.test:core:$FALLBACK_ANDROIDX_TEST_CORE_VERSION",
            "androidx.test:monitor:$FALLBACK_ANDROIDX_TEST_MONITOR_VERSION",
        )

    fun releaseVariantMessage(variant: String): String =
        "AgentPreview: agentPreview.android.variant=$variant is not supported for rendering. " +
            "AgentPreview reads the selected variant runtime classpath for rendering and keeps renderer support " +
            "dependencies out of the app production graph. Use a debug/preview-style variant instead."
}
