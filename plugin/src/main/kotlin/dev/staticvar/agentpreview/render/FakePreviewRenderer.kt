/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.Viewport
import dev.staticvar.agentpreview.sanitize
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class FakePreviewRenderer : PreviewRenderer {
    fun render(
        preview: PreviewDescriptor,
        viewport: Viewport,
        outputDirectory: File,
    ): RenderResult {
        outputDirectory.mkdirs()
        val screenshot = outputDirectory.resolve(preview.id.sanitize() + "-" + (viewport.name ?: "preview") + ".png")
        val image = BufferedImage(viewport.width.coerceAtLeast(1), viewport.height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        ImageIO.write(image, "png", screenshot)
        return RenderResult(
            screenshotFile = screenshot,
            viewport = viewport,
            rawSemantics = null,
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
                    width = preview.widthDp ?: 393,
                    height = preview.heightDp ?: 852,
                    density = 1.0f,
                ),
            outputDirectory = outputDirectory,
        )
}
