/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.model

import kotlinx.serialization.Serializable

@Serializable
data class Viewport(
    val width: Int,
    val height: Int,
    val density: Float,
)
