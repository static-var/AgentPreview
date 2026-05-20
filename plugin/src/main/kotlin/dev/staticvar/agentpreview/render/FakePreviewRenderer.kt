/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.Viewport
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class FakePreviewRenderer : PreviewRenderer {
    override fun render(
        preview: PreviewDescriptor,
        outputDirectory: File,
    ): RenderResult {
        outputDirectory.mkdirs()
        val screenshot = outputDirectory.resolve(sanitize(preview.id) + ".png")
        val viewport =
            Viewport(
                width = preview.widthDp ?: 393,
                height = preview.heightDp ?: 852,
                density = 1.0f,
            )
        val image = BufferedImage(viewport.width.coerceAtLeast(1), viewport.height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        ImageIO.write(image, "png", screenshot)
        return RenderResult(
            screenshotFile = screenshot,
            viewport = viewport,
            rawSemantics = null,
        )
    }

    private fun sanitize(value: String): String =
        value
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-')
            .ifEmpty { "preview" }
}
