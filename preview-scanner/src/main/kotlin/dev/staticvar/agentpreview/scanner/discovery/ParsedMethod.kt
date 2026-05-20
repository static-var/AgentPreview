/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery

import dev.staticvar.agentpreview.scanner.preview.PreviewAnnotation

internal data class ParsedMethod(
    val name: String,
    val argumentCount: Int,
    val previewAnnotations: List<PreviewAnnotation>,
    val metaAnnotationNames: List<String>,
)
