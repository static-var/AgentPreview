/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.model.CaptureFailure
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors

internal class ParallelCaptureExecutor(
    private val maxParallelRenders: Int,
) : CaptureExecutor {
    override fun execute(
        requests: List<CaptureRequest>,
        continueOnError: Boolean,
        render: (CaptureRequest) -> SingleCaptureResult,
        record: (SingleCaptureResult) -> Unit,
    ): CaptureResult {
        val executor = Executors.newFixedThreadPool(maxParallelRenders)
        val completions: CompletionService<SingleCaptureResult> = ExecutorCompletionService(executor)
        val failures = mutableListOf<CaptureFailure>()
        val capturedExports = mutableListOf<CapturedSnapshotExport>()
        var capturedViewportCount = 0
        var submitted = 0
        var completed = 0
        var acceptingWork = true

        fun submitNext() {
            if (submitted < requests.size && acceptingWork) {
                val request = requests[submitted++]
                completions.submit { render(request) }
            }
        }

        fun handleResult(result: SingleCaptureResult) {
            record(result)
            when (result) {
                is SingleCaptureResult.Captured -> {
                    capturedViewportCount++
                    capturedExports += result.toCapturedSnapshotExport()
                }

                is SingleCaptureResult.Failed -> {
                    failures += result.failure
                    if (!continueOnError) acceptingWork = false
                }
            }
        }

        try {
            repeat(maxParallelRenders.coerceAtMost(requests.size)) { submitNext() }
            while (completed < submitted) {
                handleResult(completions.take().get())
                completed++
                submitNext()
            }
        } finally {
            executor.shutdownNow()
        }

        return CaptureResult(capturedViewportCount, failures, capturedExports)
    }
}
