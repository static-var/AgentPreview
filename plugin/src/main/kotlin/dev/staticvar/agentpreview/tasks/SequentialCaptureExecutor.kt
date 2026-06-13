/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

internal class SequentialCaptureExecutor : CaptureExecutor {
    override fun execute(
        requests: List<CaptureRequest>,
        continueOnError: Boolean,
        render: (CaptureRequest) -> SingleCaptureResult,
        record: (SingleCaptureResult) -> Unit,
    ): CaptureResult {
        val failures = mutableListOf<dev.staticvar.agentpreview.model.CaptureFailure>()
        val capturedExports = mutableListOf<CapturedSnapshotExport>()
        var capturedViewportCount = 0
        requests.forEach { request ->
            val result = render(request)
            record(result)
            if (result is SingleCaptureResult.Captured) {
                capturedViewportCount++
                capturedExports += result.toCapturedSnapshotExport()
            }
            if (result is SingleCaptureResult.Failed) {
                failures += result.failure
                if (!continueOnError) return CaptureResult(capturedViewportCount, failures, capturedExports)
            }
        }
        return CaptureResult(capturedViewportCount, failures, capturedExports)
    }
}
