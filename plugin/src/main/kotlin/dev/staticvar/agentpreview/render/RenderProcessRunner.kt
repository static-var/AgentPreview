/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.io.File

interface RenderProcessRunner {
    fun run(
        request: AndroidComposeRenderRequest,
        previewClasspath: List<File>,
    )
}
