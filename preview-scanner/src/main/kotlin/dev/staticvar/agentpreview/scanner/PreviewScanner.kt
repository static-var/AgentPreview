/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner

import java.io.File

interface PreviewScanner {
    fun scan(input: PreviewScanInput): PreviewScanResult
}

data class PreviewScanInput(
    val projectPath: String,
    val sourceSetName: String,
    val classesDirs: List<File>,
    val runtimeClasspath: List<File>,
)

data class PreviewScanResult(
    val previews: List<ScannedPreview>,
    val diagnostics: List<PreviewScanDiagnostic> = emptyList(),
)

data class ScannedPreview(
    val id: String,
    val name: String?,
    val group: String?,
    val sourceSet: String,
    val declaringClassName: String,
    val methodName: String,
    val fullyQualifiedFunctionName: String,
    val annotations: List<PreviewAnnotation>,
)

data class PreviewAnnotation(
    val name: String?,
    val group: String?,
    val widthDp: Int,
    val heightDp: Int,
    val showBackground: Boolean,
    val backgroundColor: Long,
    val fontScale: Float,
    val locale: String?,
    val device: String?,
    val uiMode: Int,
)

data class PreviewScanDiagnostic(
    val severity: Severity,
    val message: String,
    val className: String? = null,
    val methodName: String? = null,
) {
    enum class Severity {
        WARNING,
        ERROR,
    }
}
