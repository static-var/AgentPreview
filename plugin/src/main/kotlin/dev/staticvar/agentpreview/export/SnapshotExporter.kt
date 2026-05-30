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
import javax.imageio.ImageIO

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
        cropPlan: ScreenshotCropPlan? = null,
    ): File {
        // Sanitized directories are path labels; logical snapshot identity remains preview.id in snapshot.json.
        val destination = outputPath.resolve(outputRoot, previewId, viewport)
        outputPath.validateAvailable(destination)
        writeScreenshot(screenshotFile, destination.resolve("screenshot.png"), cropPlan)
        destination.resolve("snapshot.json").writeText(
            json.encodeToString(PreviewSnapshot.serializer(), snapshot),
        )
        return destination
    }

    private fun writeScreenshot(
        source: File,
        destination: File,
        cropPlan: ScreenshotCropPlan?,
    ) {
        if (cropPlan == null || cropPlan.fallback) {
            source.copyTo(destination, overwrite = true)
            return
        }
        val image = ImageIO.read(source) ?: error("Unable to read screenshot PNG at ${source.absolutePath}.")
        val rect = cropPlan.rect
        if (rect.matches(image)) {
            source.copyTo(destination, overwrite = true)
            return
        }
        val cropped = image.getSubimage(rect.x, rect.y, rect.width, rect.height)
        ImageIO.write(cropped, "png", destination)
    }

    private fun ScreenshotCropRect.matches(image: java.awt.image.BufferedImage): Boolean =
        x == 0 && y == 0 && width == image.width && height == image.height
}
