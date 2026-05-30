/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.config.ConfiguredViewport
import dev.staticvar.agentpreview.model.Viewport

internal class CapturePlanBuilder(
    configuredViewports: List<ConfiguredViewport>,
) {
    private val viewportResolver = ViewportResolver(configuredViewports)

    fun build(
        selection: PreviewSelectionService.Result,
        viewportFilters: Set<String>,
        dryRun: Boolean,
        continueOnError: Boolean,
        maxCaptures: Int?,
        maxParallelRenders: Int,
        previewFilters: Set<String>,
    ): CapturePlan {
        val plannedCaptures =
            selection.selectedPreviews.map { preview ->
                val resolved = viewportResolver.resolve(preview)
                val selected = resolved.filter { viewport -> viewportFilters.isEmpty() || viewport.matches(viewportFilters) }
                PlannedPreviewCapture(preview, selected, resolved.size - selected.size)
            }
        return CapturePlan(
            discoveredPreviewCount = selection.discoveredPreviewCount,
            expandedPreviewCount = selection.expandedPreviewCount,
            selectedPreviewCount = selection.selectedPreviews.size,
            plannedCaptures = plannedCaptures,
            skippedByPreviewFilterCount = selection.skippedByPreviewFilterCount,
            dryRun = dryRun,
            continueOnError = continueOnError,
            maxCaptures = maxCaptures,
            maxParallelRenders = maxParallelRenders,
            previewFilters = previewFilters.toList().sorted(),
            viewportFilters = viewportFilters.toList().sorted(),
        )
    }

    private fun Viewport.matches(filters: Set<String>): Boolean =
        filters.any { filter -> name.matchesPreviewFilter(filter) || "$platform-$name" == filter }
}
