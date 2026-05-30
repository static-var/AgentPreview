/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.export

import dev.staticvar.agentpreview.model.Bounds
import dev.staticvar.agentpreview.model.PreviewMetadata
import dev.staticvar.agentpreview.model.PreviewSnapshot
import dev.staticvar.agentpreview.model.SnapshotNode
import dev.staticvar.agentpreview.model.Viewport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SnapshotExporterTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `parallel exports fail one colliding sanitized destination before overwrite`() {
        val outputRoot = dir.resolve("out")
        val screenshotA = dir.resolve("a.png").apply { writeBytes(byteArrayOf(1)) }
        val screenshotB = dir.resolve("b.png").apply { writeBytes(byteArrayOf(2)) }
        val snapshotA = snapshot(":app:Login Preview")
        val snapshotB = snapshot(":app:Login/Preview")
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)

        try {
            val futures =
                listOf(
                    executor.submit<File> {
                        start.await(1, TimeUnit.SECONDS)
                        SnapshotExporter().export(snapshotA.preview.id, screenshotA, snapshotA, outputRoot)
                    },
                    executor.submit<File> {
                        start.await(1, TimeUnit.SECONDS)
                        SnapshotExporter().export(snapshotB.preview.id, screenshotB, snapshotB, outputRoot)
                    },
                )

            start.countDown()
            val results =
                futures.map { future ->
                    runCatching { future.get(1, TimeUnit.SECONDS) }
                }

            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, results.count { it.exceptionOrNull()?.cause is IllegalStateException })
            assertTrue(outputRoot.resolve("app-Login-Preview/screenshot.png").isFile)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `exports screenshot and snapshot json`() {
        val screenshot = dir.resolve("source.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val snapshot =
            PreviewSnapshot(
                schemaVersion = 2,
                preview = PreviewMetadata(id = ":app:LoginPreview", name = "Login"),
                viewport = Viewport(width = 100, height = 200, density = 1.0f),
                nodes =
                    listOf(
                        SnapshotNode(
                            id = "n1",
                            role = "text",
                            text = "Hello",
                            bounds = Bounds(x = 0, y = 0, width = 50, height = 20),
                        ),
                    ),
            )

        SnapshotExporter().export(
            previewId = ":app:LoginPreview",
            screenshotFile = screenshot,
            snapshot = snapshot,
            outputRoot = dir.resolve("out"),
        )

        assertTrue(dir.resolve("out/app-LoginPreview/screenshot.png").isFile)
        val snapshotJson = dir.resolve("out/app-LoginPreview/snapshot.json").readText()
        assertTrue(snapshotJson.contains("\"schemaVersion\": 2"))
        assertTrue(snapshotJson.contains("\"Hello\""))
    }

    private fun snapshot(previewId: String): PreviewSnapshot =
        PreviewSnapshot(
            schemaVersion = 2,
            preview = PreviewMetadata(id = previewId, name = "Login"),
            viewport = Viewport(width = 100, height = 200, density = 1.0f),
            nodes =
                listOf(
                    SnapshotNode(
                        id = "n1",
                        role = "text",
                        text = "Hello",
                        bounds = Bounds(x = 0, y = 0, width = 50, height = 20),
                    ),
                ),
        )
}
