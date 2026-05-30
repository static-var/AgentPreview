/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.model.CaptureFailure
import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.Viewport
import java.io.File

internal interface CaptureExecutor {
    fun execute(
        requests: List<CaptureRequest>,
        continueOnError: Boolean,
        render: (CaptureRequest) -> SingleCaptureResult,
        record: (SingleCaptureResult) -> Unit,
    ): CaptureResult
}

internal data class CaptureResult(
    val capturedViewportCount: Int,
    val failures: List<CaptureFailure>,
)

internal data class CaptureRequest(
    val preview: PreviewDescriptor,
    val viewport: Viewport,
    val scratchDirectory: File,
)

internal sealed interface SingleCaptureResult {
    data class Captured(
        val previewId: String,
        val viewport: Viewport,
        val renderModeLabel: String,
    ) : SingleCaptureResult

    data class Failed(
        val failure: CaptureFailure,
    ) : SingleCaptureResult
}
