/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.export.SnapshotExportMetadata
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
    val capturedExports: List<CapturedSnapshotExport> = emptyList(),
)

internal data class CapturedSnapshotExport(
    val previewId: String,
    val viewportLabel: String,
    val renderModeLabel: String,
    val export: SnapshotExportMetadata,
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
        val export: SnapshotExportMetadata,
    ) : SingleCaptureResult

    data class Failed(
        val failure: CaptureFailure,
    ) : SingleCaptureResult
}

internal fun SingleCaptureResult.Captured.toCapturedSnapshotExport(): CapturedSnapshotExport =
    CapturedSnapshotExport(
        previewId = previewId,
        viewportLabel = viewport.label(),
        renderModeLabel = renderModeLabel,
        export = export,
    )

private fun Viewport.label(): String = "$platform-$name"
