/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.config

object AndroidPreviewConfigDefaults {
    const val ROBOLECTRIC_SDK = 35

    val viewports: List<ConfiguredViewport> =
        listOf(
            ConfiguredViewport(platform = "android", name = "phone", width = 393, height = 852),
        )
}
