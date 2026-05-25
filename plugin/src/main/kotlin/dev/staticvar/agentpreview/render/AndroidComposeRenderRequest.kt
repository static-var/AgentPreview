/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.io.File

data class AndroidComposeRenderRequest(
    val className: String,
    val methodName: String,
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val robolectricSdk: Int,
    val outputFile: File,
    val semanticsOutputFile: File,
    val layoutTreeOutputFile: File,
    val includeUnmergedSemantics: Boolean = false,
    val locale: String? = null,
    val uiMode: Int? = null,
    val fontScale: Float? = null,
    val showBackground: Boolean = false,
    val backgroundColor: Long? = null,
    val previewParameterProviderClassName: String? = null,
    val previewParameterIndex: Int? = null,
)
