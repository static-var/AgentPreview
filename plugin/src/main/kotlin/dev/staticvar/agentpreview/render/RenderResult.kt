package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.model.Viewport
import java.io.File

data class RenderResult(
    val screenshotFile: File,
    val viewport: Viewport,
    val rawSemantics: Any?,
)
