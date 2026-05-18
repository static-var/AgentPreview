/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.model.PreviewDescriptor
import java.io.File

interface PreviewRenderer {
    fun render(
        preview: PreviewDescriptor,
        outputDirectory: File,
    ): RenderResult
}
