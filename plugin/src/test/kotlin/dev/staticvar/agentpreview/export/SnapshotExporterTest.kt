package dev.staticvar.agentpreview.export

import dev.staticvar.agentpreview.model.Bounds
import dev.staticvar.agentpreview.model.PreviewMetadata
import dev.staticvar.agentpreview.model.PreviewSnapshot
import dev.staticvar.agentpreview.model.SnapshotNode
import dev.staticvar.agentpreview.model.Viewport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SnapshotExporterTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `exports screenshot and snapshot json`() {
        val screenshot = dir.resolve("source.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val snapshot = PreviewSnapshot(
            schemaVersion = 1,
            preview = PreviewMetadata(id = ":app:LoginPreview", name = "Login"),
            viewport = Viewport(width = 100, height = 200, density = 1.0f),
            nodes = listOf(
                SnapshotNode(
                    id = "n1",
                    role = "text",
                    text = "Hello",
                    bounds = Bounds(x = 0, y = 0, width = 50, height = 20),
                )
            ),
        )

        SnapshotExporter().export(
            previewId = ":app:LoginPreview",
            screenshotFile = screenshot,
            snapshot = snapshot,
            outputRoot = dir.resolve("out"),
        )

        assertTrue(dir.resolve("out/app-LoginPreview/screenshot.png").isFile)
        assertTrue(dir.resolve("out/app-LoginPreview/snapshot.json").readText().contains("\"Hello\""))
    }
}
