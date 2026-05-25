/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.model

data class PreviewParameter(
    val providerClassName: String,
    val limit: Int? = null,
    val parameterIndex: Int,
    val parameterType: String,
)
