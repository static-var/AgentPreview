/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner

data class PreviewAnnotation(
    val name: String?,
    val group: String?,
    val widthDp: Int,
    val heightDp: Int,
    val showBackground: Boolean,
    val backgroundColor: Long,
    val fontScale: Float,
    val locale: String?,
    val device: String?,
    val uiMode: Int,
)
