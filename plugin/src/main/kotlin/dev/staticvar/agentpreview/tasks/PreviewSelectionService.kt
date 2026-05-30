/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.discovery.IsolatedPreviewParameterCountResolver
import dev.staticvar.agentpreview.discovery.JsonIndexPreviewDiscovery
import dev.staticvar.agentpreview.discovery.PreviewDiscovery
import dev.staticvar.agentpreview.discovery.PreviewDiscoveryResult
import dev.staticvar.agentpreview.discovery.PreviewParameterExpander
import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.scanner.discovery.PreviewScanDiagnostic
import java.io.File

internal class PreviewSelectionService(
    private val projectPath: String,
    private val classesDirs: List<File>,
    private val discoveryClasspath: List<File>,
    private val previewParameterClasspath: List<File> = discoveryClasspath,
    private val sourceSetName: String = "main",
) {
    enum class Mode { LIST, CAPTURE }

    data class Result(
        val discoveredPreviewCount: Int,
        val expandedPreviewCount: Int,
        val selectedPreviews: List<PreviewDescriptor>,
        val skippedByPreviewFilterCount: Int,
        val diagnostics: List<PreviewScanDiagnostic>,
    )

    fun select(
        indexFile: File?,
        filters: Set<String>,
        maxPreviewParameterValues: Int,
        mode: Mode,
    ): Result {
        val discovery = discover(indexFile)
        val expansionCandidates = discovery.previews.filter { filters.isEmpty() || it.matchesBeforePreviewParameterExpansion(filters) }
        val requestedIndexes = filters.previewParameterFilterIndexes()
        val expansion =
            when {
                mode == Mode.LIST && (requestedIndexes.isEmpty() || classesDirs.isNotEmpty()) -> {
                    dev.staticvar.agentpreview.discovery
                        .PreviewParameterExpansionResult(previews = expansionCandidates)
                }

                else -> {
                    PreviewParameterExpander(
                        resolver =
                            if (mode == Mode.CAPTURE && classesDirs.isNotEmpty()) {
                                IsolatedPreviewParameterCountResolver(
                                    previewClasspath = previewParameterClasspath,
                                    defaultCap = maxPreviewParameterValues,
                                )
                            } else {
                                null
                            },
                        defaultCap = maxPreviewParameterValues,
                        requestedIndexes = requestedIndexes,
                    ).expand(expansionCandidates)
                }
            }
        val selected = expansion.previews.filter { filters.isEmpty() || it.matchesAfterExpansion(filters, mode) }
        return Result(
            discoveredPreviewCount = discovery.previews.size,
            expandedPreviewCount = expansion.previews.size,
            selectedPreviews = selected,
            skippedByPreviewFilterCount = (discovery.previews.size - expansionCandidates.size) + (expansion.previews.size - selected.size),
            diagnostics = discovery.diagnostics + expansion.diagnostics,
        )
    }

    private fun PreviewDescriptor.matchesAfterExpansion(
        filters: Set<String>,
        mode: Mode,
    ): Boolean =
        if (mode == Mode.LIST && classesDirs.isNotEmpty() && previewParameter?.index == null) {
            matchesBeforePreviewParameterExpansion(filters)
        } else {
            matchesAfterPreviewParameterExpansion(filters)
        }

    private fun discover(indexFile: File?): PreviewDiscoveryResult =
        if (classesDirs.isNotEmpty()) {
            PreviewDiscovery(projectPath, sourceSetName, classesDirs, discoveryClasspath).discoverWithDiagnostics()
        } else {
            PreviewDiscoveryResult(indexFile?.let { JsonIndexPreviewDiscovery(it).discover() }.orEmpty(), emptyList())
        }
}
