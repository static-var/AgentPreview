/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.model

import dev.staticvar.agentpreview.scanner.discovery.PreviewScanDiagnostic

internal data class DiscoveryDiagnostic(
    val diagnostic: PreviewScanDiagnostic,
) : Discovery
