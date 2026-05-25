/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.model

import kotlinx.serialization.Serializable

@Serializable
data class CaptureReport(
    val discoveredPreviewCount: Int,
    val expandedPreviewCount: Int,
    val selectedPreviewCount: Int,
    val plannedViewportCaptureCount: Int,
    val capturedViewportCaptureCount: Int,
    val failedViewportCaptureCount: Int,
    val skippedByPreviewFilterCount: Int,
    val skippedByViewportFilterCount: Int,
    val dryRun: Boolean,
    val continueOnError: Boolean,
    val maxCaptures: Int? = null,
    val previewFilters: List<String> = emptyList(),
    val viewportFilters: List<String> = emptyList(),
    val failures: List<CaptureFailure> = emptyList(),
)

@Serializable
data class CaptureFailure(
    val previewId: String,
    val viewport: String,
    val message: String,
)
