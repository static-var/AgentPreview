package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.Viewport
import java.io.File

class FakePreviewRenderer : PreviewRenderer {
    override fun render(preview: PreviewDescriptor, outputDirectory: File): RenderResult {
        outputDirectory.mkdirs()
        val screenshot = outputDirectory.resolve(sanitize(preview.id) + ".png")
        screenshot.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        return RenderResult(
            screenshotFile = screenshot,
            viewport = Viewport(
                width = preview.widthDp ?: 393,
                height = preview.heightDp ?: 852,
                density = 1.0f,
            ),
            rawSemantics = null,
        )
    }

    private fun sanitize(value: String): String {
        return value
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-')
            .ifEmpty { "preview" }
    }
}
