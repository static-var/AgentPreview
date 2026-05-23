/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.Viewport
import java.io.File

class PreviewRendererImpl(
    private val robolectricSdk: Int = DEFAULT_ROBOLECTRIC_SDK,
) : PreviewRenderer {
    fun render(
        preview: PreviewDescriptor,
        viewport: Viewport,
        outputDirectory: File,
    ): RenderResult {
        outputDirectory.mkdirs()
        error(
            "PreviewRendererImpl selected for ${preview.id} (${viewport.platform}-${viewport.name ?: "preview"}) " +
                "with Robolectric SDK $robolectricSdk, but Roborazzi-backed preview rendering is not wired into " +
                "the Gradle plugin yet. Use -PagentPreview.fakeRenderer=true for scaffold captures until the " +
                "Roborazzi renderer bridge is completed.",
        )
    }

    override fun render(
        preview: PreviewDescriptor,
        outputDirectory: File,
    ): RenderResult =
        render(
            preview = preview,
            viewport =
                Viewport(
                    platform = "android",
                    name = "preview",
                    width = preview.widthDp ?: DEFAULT_WIDTH_DP,
                    height = preview.heightDp ?: DEFAULT_HEIGHT_DP,
                    density = DEFAULT_DENSITY,
                ),
            outputDirectory = outputDirectory,
        )

    private companion object {
        const val DEFAULT_ROBOLECTRIC_SDK = 35
        const val DEFAULT_WIDTH_DP = 393
        const val DEFAULT_HEIGHT_DP = 852
        const val DEFAULT_DENSITY = 1.0f
    }
}
