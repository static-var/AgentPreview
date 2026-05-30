/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

internal object DiagnosticPngRenderer {
    fun render(
        outputFile: File,
        widthPx: Int,
        heightPx: Int,
        title: String,
        detail: String,
    ) {
        outputFile.parentFile.mkdirs()
        val image = BufferedImage(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = Color(0xFF, 0xF7, 0xED)
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.color = Color(0x1F, 0x3A, 0x5F)
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 24)
        graphics.drawString(title.take(MAX_CHARS), 24, 48)
        graphics.font = Font(Font.MONOSPACED, Font.PLAIN, 14)
        wrap(detail, 80).take(18).forEachIndexed { index, line ->
            graphics.drawString(line, 24, 88 + (index * 20))
        }
        graphics.dispose()
        ImageIO.write(image, "png", outputFile)
    }

    private fun wrap(
        text: String,
        width: Int,
    ): List<String> = text.chunked(width)

    private const val MAX_CHARS = 80
}
