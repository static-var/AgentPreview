/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.PreviewParameterDescriptor
import dev.staticvar.agentpreview.scanner.discovery.BytecodePreviewScanner
import dev.staticvar.agentpreview.scanner.discovery.PreviewScanDiagnostic
import dev.staticvar.agentpreview.scanner.discovery.PreviewScanInput
import dev.staticvar.agentpreview.scanner.model.PreviewAnnotation
import dev.staticvar.agentpreview.scanner.model.ScannedPreview
import java.io.File
import java.net.URLClassLoader

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

        val mapped = scanResult.previews.flatMap(::toPreviewDescriptors)
        val expanded = mapped.map(::expandPreviewParameter).flatten()
        return PreviewDiscoveryResult(
            previews = expanded.previews,
            diagnostics = scanResult.diagnostics + expanded.diagnostics,
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
                previewParameter =
                    scannedPreview.previewParameter?.let { parameter ->
                        PreviewParameterDescriptor(
                            providerClassName = parameter.providerClassName,
                            parameterType = parameter.parameterType,
                            limit = parameter.limit,
                        )
                    },
            )
        }

    private fun expandPreviewParameter(preview: PreviewDescriptor): PreviewParameterExpansion {
        val parameter = preview.previewParameter ?: return PreviewParameterExpansion(previews = listOf(preview))
        val countResult = previewParameterValueCount(parameter)
        val count = countResult.count
        val diagnostics = countResult.diagnostic?.let(::listOf).orEmpty()
        if (count <= 0) return PreviewParameterExpansion(previews = emptyList(), diagnostics = diagnostics)
        return PreviewParameterExpansion(
            previews =
                (0 until count).map { index ->
                    preview.copy(
                        id = "${preview.id}:previewParam-$index",
                        previewParameter = parameter.copy(index = index),
                    )
                },
            diagnostics = diagnostics,
        )
    }

    private fun previewParameterValueCount(parameter: PreviewParameterDescriptor): PreviewParameterCountResult {
        val classpath = classesDirs + runtimeClasspath
        return runCatching {
            URLClassLoader(classpath.map { it.toURI().toURL() }.toTypedArray(), javaClass.classLoader).use { loader ->
                val providerClass = Class.forName(parameter.providerClassName, true, loader)
                val constructor = providerClass.getDeclaredConstructor()
                if (!constructor.canAccess(null)) constructor.isAccessible = true
                val provider = constructor.newInstance()
                val values = providerClass.methods.first { it.name == "getValues" && it.parameterTypes.isEmpty() }.invoke(provider)
                val iterator =
                    values.javaClass.methods.first { it.name == "iterator" && it.parameterTypes.isEmpty() }.invoke(
                        values,
                    ) as Iterator<*>
                var count = 0
                val limit = parameter.limit ?: Int.MAX_VALUE
                while (count < limit && iterator.hasNext()) {
                    iterator.next()
                    count++
                }
                val diagnostic =
                    if (count == 0) {
                        PreviewScanDiagnostic(
                            severity = PreviewScanDiagnostic.Severity.WARNING,
                            message = "Skipping parameterized preview because provider ${parameter.providerClassName} produced no values.",
                        )
                    } else {
                        null
                    }
                PreviewParameterCountResult(count = count, diagnostic = diagnostic)
            }
        }.getOrElse { throwable ->
            PreviewParameterCountResult(
                count = 0,
                diagnostic =
                    PreviewScanDiagnostic(
                        severity = PreviewScanDiagnostic.Severity.WARNING,
                        message =
                            "Skipping parameterized preview because provider ${parameter.providerClassName} could not be instantiated. " +
                                "Ensure it implements AndroidX PreviewParameterProvider and has an accessible no-arg constructor. " +
                                "Cause: ${throwable.javaClass.name}: ${throwable.message}",
                    ),
            )
        }
    }

    private fun List<PreviewParameterExpansion>.flatten(): PreviewParameterExpansion =
        PreviewParameterExpansion(
            previews = flatMap { it.previews },
            diagnostics = flatMap { it.diagnostics },
        )

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

    private data class PreviewParameterExpansion(
        val previews: List<PreviewDescriptor>,
        val diagnostics: List<PreviewScanDiagnostic> = emptyList(),
    )

    private data class PreviewParameterCountResult(
        val count: Int,
        val diagnostic: PreviewScanDiagnostic? = null,
    )

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
