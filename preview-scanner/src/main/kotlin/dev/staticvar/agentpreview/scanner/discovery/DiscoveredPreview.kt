/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery

import dev.staticvar.agentpreview.scanner.preview.ScannedPreview

internal data class DiscoveredPreview(
    val preview: ScannedPreview,
) : Discovery
