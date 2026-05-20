/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.model

import dev.staticvar.agentpreview.scanner.model.ScannedPreview

internal data class DiscoveredPreview(
    val preview: ScannedPreview,
) : Discovery
