/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.export

import dev.staticvar.agentpreview.model.PreviewSnapshot
import dev.staticvar.agentpreview.sanitize
import kotlinx.serialization.json.Json
import java.io.File

class SnapshotExporter {
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
    ): File {
        val destination = outputRoot.resolve(previewId.sanitize())
        destination.mkdirs()
        screenshotFile.copyTo(destination.resolve("screenshot.png"), overwrite = true)
        destination.resolve("snapshot.json").writeText(
            json.encodeToString(PreviewSnapshot.serializer(), snapshot),
        )
        return destination
    }
}
