/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.model.SnapshotLayoutNode
import dev.staticvar.agentpreview.model.Viewport
import java.io.File

data class RenderResult(
    val screenshotFile: File,
    val viewport: Viewport,
    val rawSemantics: Any?,
    val layoutTree: List<SnapshotLayoutNode> = emptyList(),
    val renderMode: RenderMode,
)
