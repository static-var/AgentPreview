/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner

data class PreviewScanResult(
    val previews: List<ScannedPreview>,
    val diagnostics: List<PreviewScanDiagnostic> = emptyList(),
)
