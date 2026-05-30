/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.export

import dev.staticvar.agentpreview.model.PreviewSnapshot
import dev.staticvar.agentpreview.model.Viewport
import kotlinx.serialization.json.Json
import java.io.File

internal class SnapshotExporter(
    private val outputPath: SnapshotOutputPath = SnapshotOutputPath(),
) {
    private val json =
        Json {
            prettyPrint = true
            explicitNulls = false
        }

    fun export(
        previewId: String,
        screenshotFile: File,
        snapshot: PreviewSnapshot,
        outputRoot: File,
        viewport: Viewport? = null,
    ): File {
        // Sanitized directories are path labels; logical snapshot identity remains preview.id in snapshot.json.
        val destination = outputPath.resolve(outputRoot, previewId, viewport)
        outputPath.validateAvailable(destination)
        screenshotFile.copyTo(destination.resolve("screenshot.png"), overwrite = true)
        destination.resolve("snapshot.json").writeText(
            json.encodeToString(PreviewSnapshot.serializer(), snapshot),
        )
        return destination
    }
}
