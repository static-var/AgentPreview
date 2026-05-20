/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.model

import kotlinx.serialization.Serializable

@Serializable
data class Bounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)
