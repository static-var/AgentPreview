/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.config

import kotlinx.serialization.Serializable

@Serializable
data class ConfiguredViewport(
    val platform: String,
    val name: String,
    val width: Int,
    val height: Int,
    val density: Float = 1.0f,
)
