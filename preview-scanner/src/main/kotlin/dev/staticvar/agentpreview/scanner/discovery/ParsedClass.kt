/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery

import dev.staticvar.agentpreview.scanner.preview.PreviewAnnotation

internal data class ParsedClass(
    val name: String,
    val previewAnnotations: List<PreviewAnnotation>,
    val methods: List<ParsedMethod>,
)
