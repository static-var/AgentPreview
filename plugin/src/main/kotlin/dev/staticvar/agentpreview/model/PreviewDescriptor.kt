/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.model

import kotlinx.serialization.Serializable

@Serializable
data class PreviewDescriptor(
    val id: String,
    val name: String? = null,
    val group: String? = null,
    val sourceSet: String,
    val fullyQualifiedFunctionName: String,
    val fullyQualifiedClassName: String? = null,
    val sourceFile: String,
    val sourceLine: Int? = null,
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    val locale: String? = null,
    val uiMode: Int? = null,
    val fontScale: Float? = null,
)
