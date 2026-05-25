/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.scanner.discovery.BytecodePreviewScanner
import dev.staticvar.agentpreview.scanner.discovery.PreviewScanDiagnostic
import dev.staticvar.agentpreview.scanner.discovery.PreviewScanInput
import dev.staticvar.agentpreview.scanner.model.PreviewAnnotation
import dev.staticvar.agentpreview.scanner.model.ScannedPreview
import java.io.File

data class PreviewDiscoveryResult(
    val previews: List<PreviewDescriptor>,
    val diagnostics: List<PreviewScanDiagnostic>,
)

class PreviewDiscovery(
    private val projectPath: String,
    private val sourceSetName: String,
    private val classesDirs: List<File>,
    private val runtimeClasspath: List<File>,
) {
    fun discover(): List<PreviewDescriptor> = discoverWithDiagnostics().previews

    fun discoverWithDiagnostics(): PreviewDiscoveryResult {
        if (classesDirs.isEmpty()) return PreviewDiscoveryResult(previews = emptyList(), diagnostics = emptyList())

        val scanResult =
            BytecodePreviewScanner()
                .scan(
                    PreviewScanInput(
                        projectPath = projectPath,
                        sourceSetName = sourceSetName,
                        classesDirs = classesDirs,
                        runtimeClasspath = runtimeClasspath,
                    ),
                )

        return PreviewDiscoveryResult(
            previews = scanResult.previews.flatMap(::toPreviewDescriptors),
            diagnostics = scanResult.diagnostics,
        )
    }

    private fun toPreviewDescriptors(scannedPreview: ScannedPreview): List<PreviewDescriptor> =
        scannedPreview.annotations.mapIndexed { index, annotation ->
            PreviewDescriptor(
                id = variantId(scannedPreview, annotation, index),
                name = annotation.name ?: scannedPreview.name,
                group = annotation.group ?: scannedPreview.group,
                sourceSet = scannedPreview.sourceSet,
                fullyQualifiedFunctionName = scannedPreview.fullyQualifiedFunctionName,
                fullyQualifiedClassName = scannedPreview.fullyQualifiedClassName,
                sourceFile = scannedPreview.sourceFile ?: scannedPreview.declaringClassName,
                sourceLine = null,
                widthDp = annotation.widthDp,
                heightDp = annotation.heightDp,
                locale = annotation.locale,
                uiMode = annotation.uiMode,
                fontScale = annotation.fontScale,
                showBackground = annotation.showBackground,
                backgroundColor = annotation.backgroundColor,
            )
        }

    private fun variantId(
        scannedPreview: ScannedPreview,
        annotation: PreviewAnnotation,
        index: Int,
    ): String =
        if (scannedPreview.annotations.size == 1) {
            scannedPreview.id
        } else {
            "${scannedPreview.id}:${index + 1}-${variantName(annotation, index)}"
        }

    private fun variantName(
        annotation: PreviewAnnotation,
        index: Int,
    ): String =
        annotation.name
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]+"), "-")
            ?.trim('-')
            ?.takeIf { it.isNotBlank() }
            ?: "variant-${index + 1}"
}
