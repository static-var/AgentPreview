/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery

interface PreviewScanner {
    fun scan(input: PreviewScanInput): PreviewScanResult
}
