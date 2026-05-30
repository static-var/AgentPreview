/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.model.CaptureFailure
import dev.staticvar.agentpreview.model.CaptureReport
import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.Viewport

internal data class PlannedPreviewCapture(
    val preview: PreviewDescriptor,
    val viewports: List<Viewport>,
    val skippedByViewportFilter: Int,
)

internal data class CapturePlan(
    val discoveredPreviewCount: Int,
    val expandedPreviewCount: Int,
    val selectedPreviewCount: Int,
    val plannedCaptures: List<PlannedPreviewCapture>,
    val skippedByPreviewFilterCount: Int,
    val dryRun: Boolean,
    val continueOnError: Boolean,
    val maxCaptures: Int?,
    val maxParallelRenders: Int,
    val previewFilters: List<String>,
    val viewportFilters: List<String>,
) {
    val plannedViewportCaptureCount: Int = plannedCaptures.sumOf { it.viewports.size }
    val skippedByViewportFilterCount: Int = plannedCaptures.sumOf { it.skippedByViewportFilter }

    fun report(
        capturedViewportCount: Int = 0,
        failures: List<CaptureFailure> = emptyList(),
    ) = CaptureReport(
        discoveredPreviewCount = discoveredPreviewCount,
        expandedPreviewCount = expandedPreviewCount,
        selectedPreviewCount = selectedPreviewCount,
        plannedViewportCaptureCount = plannedViewportCaptureCount,
        capturedViewportCaptureCount = capturedViewportCount,
        failedViewportCaptureCount = failures.size,
        skippedByPreviewFilterCount = skippedByPreviewFilterCount,
        skippedByViewportFilterCount = skippedByViewportFilterCount,
        dryRun = dryRun,
        continueOnError = continueOnError,
        maxCaptures = maxCaptures,
        maxParallelRenders = maxParallelRenders,
        previewFilters = previewFilters,
        viewportFilters = viewportFilters,
        failures = failures,
    )
}
