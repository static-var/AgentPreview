/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.model

import dev.staticvar.agentpreview.scanner.model.PreviewAnnotation

internal data class ParsedClass(
    val name: String,
    val sourceFile: String?,
    val previewAnnotations: List<PreviewAnnotation>,
    val methods: List<ParsedMethod>,
)
