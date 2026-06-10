/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.accessibility

import dev.staticvar.agentpreview.sanitize
import java.io.File

internal data class AccessibilityReportAssetResult(
    val bundles: List<AuditedSnapshotBundle>,
    val warnings: List<String>,
)

internal object AccessibilityReportAssetWriter {
    fun write(
        bundles: List<AuditedSnapshotBundle>,
        assetsDir: File,
    ): AccessibilityReportAssetResult {
        val warnings = mutableListOf<String>()
        if (assetsDir.exists()) {
            val deleted = assetsDir.deleteRecursively()
            if (!deleted) {
                warnings += "Failed to clean accessibility report assets directory ${assetsDir.path}; stale assets may remain."
            }
        }
        if (!assetsDir.mkdirs() && !assetsDir.isDirectory) {
            warnings += "Failed to create accessibility report assets directory ${assetsDir.path}; screenshots will be omitted."
            return AccessibilityReportAssetResult(
                bundles = bundles.map { it.copy(reportScreenshotFile = null) },
                warnings = warnings,
            )
        }

        val usedAssetNames = mutableSetOf<String>()
        val updatedBundles =
            bundles.map { bundle ->
                val screenshotFile = bundle.screenshotFile
                if (screenshotFile == null) {
                    warnings +=
                        "No screenshot available for ${bundle.previewId}/${bundle.viewportLabel}; report section will omit the image."
                    return@map bundle.copy(reportScreenshotFile = null)
                }
                if (!screenshotFile.isFile || !screenshotFile.canRead()) {
                    warnings +=
                        "Screenshot missing or unreadable for ${bundle.previewId}/${bundle.viewportLabel}: ${screenshotFile.path}."
                    return@map bundle.copy(reportScreenshotFile = null)
                }

                val extension = screenshotFile.extension.takeIf { it.isNotBlank() }?.sanitize() ?: "png"
                val baseName = "${bundle.previewId.sanitize()}-${bundle.viewportLabel.sanitize()}"
                val assetName = uniqueAssetName(baseName, extension, usedAssetNames)
                val destination = assetsDir.resolve(assetName)
                runCatching {
                    screenshotFile.copyTo(destination, overwrite = true)
                }.fold(
                    onSuccess = { copied -> bundle.copy(reportScreenshotFile = copied) },
                    onFailure = { throwable ->
                        warnings +=
                            "Failed to copy screenshot for ${bundle.previewId}/${bundle.viewportLabel}: ${throwable.message}."
                        bundle.copy(reportScreenshotFile = null)
                    },
                )
            }

        return AccessibilityReportAssetResult(
            bundles = updatedBundles,
            warnings = warnings,
        )
    }

    private fun uniqueAssetName(
        baseName: String,
        extension: String,
        usedAssetNames: MutableSet<String>,
    ): String {
        var suffix = 1
        var assetName = "$baseName.$extension"
        while (!usedAssetNames.add(assetName)) {
            suffix++
            assetName = "$baseName-$suffix.$extension"
        }
        return assetName
    }
}
