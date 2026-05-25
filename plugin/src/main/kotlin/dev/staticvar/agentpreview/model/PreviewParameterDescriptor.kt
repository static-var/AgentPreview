/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.model

import kotlinx.serialization.Serializable

@Serializable
data class PreviewParameterDescriptor(
    val providerClassName: String,
    val parameterType: String,
    val limit: Int? = null,
    val index: Int? = null,
)
