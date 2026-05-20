/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner

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
