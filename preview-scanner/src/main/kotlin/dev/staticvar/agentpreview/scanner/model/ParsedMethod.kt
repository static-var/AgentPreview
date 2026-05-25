/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.model

import dev.staticvar.agentpreview.scanner.model.PreviewAnnotation

internal data class ParsedMethod(
    val name: String,
    val argumentTypes: List<String>,
    val previewAnnotations: List<PreviewAnnotation>,
    val metaAnnotationNames: List<String>,
    val previewParameters: List<PreviewParameter> = emptyList(),
)
