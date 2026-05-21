/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery

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
