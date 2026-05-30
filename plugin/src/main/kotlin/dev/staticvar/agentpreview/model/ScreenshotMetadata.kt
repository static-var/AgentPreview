/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.model

import kotlinx.serialization.Serializable

@Serializable
data class ScreenshotMetadata(
    val width: Int,
    val height: Int,
    val crop: ScreenshotCropMetadata,
)

@Serializable
data class ScreenshotCropMetadata(
    val enabled: Boolean,
    val fallback: Boolean,
    val x: Int? = null,
    val y: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val paddingDp: Int,
    val reason: String? = null,
)
