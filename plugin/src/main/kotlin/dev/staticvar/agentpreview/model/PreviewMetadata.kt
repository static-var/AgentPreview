/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.model

import kotlinx.serialization.Serializable

@Serializable
data class PreviewMetadata(
    val id: String,
    val name: String? = null,
    val group: String? = null,
    val source: String? = null,
    val sourceSet: String? = null,
    val previewParameter: PreviewParameterDescriptor? = null,
)
