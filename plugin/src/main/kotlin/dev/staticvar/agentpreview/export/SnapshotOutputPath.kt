/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.export

import dev.staticvar.agentpreview.model.Viewport
import dev.staticvar.agentpreview.sanitize
import java.io.File

internal class SnapshotOutputPath {
    fun resolve(
        outputRoot: File,
        previewId: String,
        viewport: Viewport? = null,
    ): File =
        outputRoot.resolve(previewId.sanitize()).let { previewRoot ->
            if (viewport?.platform != null && viewport.name != null) {
                previewRoot.resolve("${viewport.platform}-${viewport.name}".sanitize())
            } else {
                previewRoot
            }
        }

    fun validateAvailable(destination: File) {
        if (!destination.mkdirs()) {
            throw IllegalStateException(
                "Snapshot output path collision at ${destination.absolutePath}; " +
                    "multiple previews/viewports sanitize to the same directory label.",
            )
        }
    }

    fun duplicateDestinations(destinations: List<File>): List<File> =
        destinations
            .groupingBy { it.absoluteFile.normalize() }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .toList()
}
