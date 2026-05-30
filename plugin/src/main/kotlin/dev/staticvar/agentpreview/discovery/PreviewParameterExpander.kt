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

internal interface PreviewParameterCountResolver {
    fun count(parameter: PreviewParameterDescriptor): PreviewParameterCount
}

internal class PreviewParameterExpander(
    private val resolver: PreviewParameterCountResolver? = null,
    private val defaultCap: Int = Int.MAX_VALUE,
    private val requestedIndexes: Set<Int> = emptySet(),
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

        val resolvedCount = resolver?.count(parameter)
        if (resolvedCount == null && parameter.limit == null && requestedIndexes.isEmpty()) {
            return PreviewParameterExpansionResult(listOf(preview))
        }
        val diagnostics = resolvedCount?.diagnostics.orEmpty().map { message -> warning(message) }
        val indexes =
            PreviewParameterExpansionSpec(
                parameter = parameter,
                resolvedCount = resolvedCount?.count,
                defaultCap = defaultCap,
                requestedIndexes = requestedIndexes,
            ).indexesToExpand()
        if (indexes.isEmpty()) return PreviewParameterExpansionResult(previews = emptyList(), diagnostics = diagnostics)

        return PreviewParameterExpansionResult(
            previews =
                indexes.map { index ->
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
