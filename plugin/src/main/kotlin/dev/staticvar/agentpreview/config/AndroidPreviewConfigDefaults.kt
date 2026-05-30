/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.config

internal object AndroidPreviewConfigDefaults {
    const val ROBOLECTRIC_SDK = 35
    const val VARIANT = "debug"

    val viewports: List<ConfiguredViewport> =
        listOf(
            ConfiguredViewport(platform = "android", name = "phone", width = 393, height = 852),
        )
}
