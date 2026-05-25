/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.PreviewParameterDescriptor
import dev.staticvar.agentpreview.scanner.discovery.PreviewScanDiagnostic

data class PreviewParameterExpansionResult(
    val previews: List<PreviewDescriptor>,
    val diagnostics: List<PreviewScanDiagnostic> = emptyList(),
)

data class PreviewParameterCount(
    val count: Int,
    val diagnostics: List<String> = emptyList(),
)

interface PreviewParameterCountResolver {
    fun count(parameter: PreviewParameterDescriptor): PreviewParameterCount
}

class PreviewParameterExpander(
    private val resolver: PreviewParameterCountResolver,
) {
    fun expand(previews: List<PreviewDescriptor>): PreviewParameterExpansionResult {
        val expanded = previews.map(::expand)
        return PreviewParameterExpansionResult(
            previews = expanded.flatMap { it.previews },
            diagnostics = expanded.flatMap { it.diagnostics },
        )
    }

    private fun expand(preview: PreviewDescriptor): PreviewParameterExpansionResult {
        val parameter = preview.previewParameter ?: return PreviewParameterExpansionResult(listOf(preview))
        if (parameter.index != null) return PreviewParameterExpansionResult(listOf(preview))

        val count = resolver.count(parameter)
        val diagnostics = count.diagnostics.map { message -> warning(message) }
        if (count.count <= 0) return PreviewParameterExpansionResult(previews = emptyList(), diagnostics = diagnostics)

        return PreviewParameterExpansionResult(
            previews =
                (0 until count.count).map { index ->
                    preview.copy(
                        id = "${preview.id}:previewParam-$index",
                        previewParameter = parameter.copy(index = index),
                    )
                },
            diagnostics = diagnostics,
        )
    }

    private fun warning(message: String): PreviewScanDiagnostic =
        PreviewScanDiagnostic(
            severity = PreviewScanDiagnostic.Severity.WARNING,
            message = message,
        )
}
