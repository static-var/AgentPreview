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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CaptureExecutorTest {
    @Test
    fun `sequential fail-fast stops after first failure`() {
        val rendered = mutableListOf<String>()
        val result =
            SequentialCaptureExecutor().execute(
                requests = requests(3),
                continueOnError = false,
                render = { request ->
                    rendered += request.preview.id
                    if (request.preview.id == "preview-1") failure(request) else captured(request)
                },
                record = {},
            )

        assertEquals(listOf("preview-0", "preview-1"), rendered)
        assertEquals(1, result.capturedViewportCount)
        assertEquals(1, result.failures.size)
    }

    @Test
    fun `parallel fail-fast lets submitted captures finish but stops queued work`() {
        val firstTwoStarted = CountDownLatch(2)
        val releaseFirstTwo = CountDownLatch(1)
        val rendered = mutableListOf<String>()

        val result =
            ParallelCaptureExecutor(maxParallelRenders = 2).execute(
                requests = requests(4),
                continueOnError = false,
                render = { request ->
                    synchronized(rendered) { rendered += request.preview.id }
                    if (request.preview.id in setOf("preview-0", "preview-1")) {
                        firstTwoStarted.countDown()
                        firstTwoStarted.await(1, TimeUnit.SECONDS)
                    }
                    if (request.preview.id == "preview-1") releaseFirstTwo.await(1, TimeUnit.SECONDS)
                    if (request.preview.id == "preview-0") failure(request) else captured(request)
                },
                record = { result ->
                    if (result is SingleCaptureResult.Failed) releaseFirstTwo.countDown()
                },
            )

        assertEquals(setOf("preview-0", "preview-1"), rendered.toSet())
        assertEquals(1, result.capturedViewportCount)
        assertEquals(1, result.failures.size)
    }

    @Test
    fun `capture counts are based on results not record callback`() {
        val result =
            SequentialCaptureExecutor().execute(
                requests = requests(2),
                continueOnError = true,
                render = { request -> captured(request) },
                record = {},
            )

        assertEquals(2, result.capturedViewportCount)
        assertEquals(listOf("preview-0", "preview-1"), result.capturedExports.map { it.previewId })
    }

    private fun requests(count: Int): List<CaptureRequest> =
        (0 until count).map { index ->
            CaptureRequest(
                preview =
                    PreviewDescriptor(
                        id = "preview-$index",
                        name = "Preview $index",
                        sourceSet = "main",
                        fullyQualifiedFunctionName = "example.Preview$index",
                        sourceFile = "Example.kt",
                    ),
                viewport = Viewport(width = 1, height = 1, density = 1f, platform = "android", name = "phone"),
                scratchDirectory = File("scratch-$index"),
            )
        }

    private fun captured(request: CaptureRequest) =
        SingleCaptureResult.Captured(
            previewId = request.preview.id,
            viewport = request.viewport,
            renderModeLabel = "fake",
            export =
                SnapshotExportMetadata(
                    directory = File("out/${request.preview.id}"),
                    snapshotFile = File("out/${request.preview.id}/snapshot.json"),
                    screenshotFile = File("out/${request.preview.id}/screenshot.png"),
                ),
        )

    private fun failure(request: CaptureRequest) =
        SingleCaptureResult.Failed(CaptureFailure(request.preview.id, request.viewport.label(), "boom"))

    private fun Viewport.label(): String = "$platform-$name"
}
