/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.scanner.discovery.BytecodePreviewScanner
import dev.staticvar.agentpreview.scanner.discovery.PreviewScanInput
import dev.staticvar.agentpreview.scanner.model.ScannedPreview
import java.io.File

class PreviewDiscovery(
    private val projectPath: String,
    private val sourceSetName: String,
    private val classesDirs: List<File>,
    private val runtimeClasspath: List<File>,
) {
    fun discover(): List<PreviewDescriptor> {
        if (classesDirs.isEmpty()) return emptyList()

        return BytecodePreviewScanner()
            .scan(
                PreviewScanInput(
                    projectPath = projectPath,
                    sourceSetName = sourceSetName,
                    classesDirs = classesDirs,
                    runtimeClasspath = runtimeClasspath,
                ),
            ).previews
            .flatMap(::toPreviewDescriptors)
    }

    private fun toPreviewDescriptors(scannedPreview: ScannedPreview): List<PreviewDescriptor> =
        scannedPreview.annotations.map { annotation ->
            PreviewDescriptor(
                id = scannedPreview.id,
                name = annotation.name ?: scannedPreview.name,
                group = annotation.group ?: scannedPreview.group,
                sourceSet = scannedPreview.sourceSet,
                fullyQualifiedFunctionName = scannedPreview.fullyQualifiedFunctionName,
                sourceFile = "${scannedPreview.declaringClassName}.kt",
                sourceLine = null,
                widthDp = annotation.widthDp,
                heightDp = annotation.heightDp,
                locale = annotation.locale,
                uiMode = annotation.uiMode,
                fontScale = annotation.fontScale,
            )
        }
}
