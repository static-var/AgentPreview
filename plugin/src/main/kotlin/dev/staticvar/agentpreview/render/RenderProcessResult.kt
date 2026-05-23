/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

sealed interface RenderProcessResult {
    data object Success : RenderProcessResult

    data class Failure(
        val kind: RenderProcessFailureKind,
        val message: String,
    ) : RenderProcessResult
}
