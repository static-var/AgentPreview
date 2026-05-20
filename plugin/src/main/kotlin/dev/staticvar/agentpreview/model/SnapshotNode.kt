/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.model

import kotlinx.serialization.Serializable

@Serializable
data class SnapshotNode(
    val id: String,
    val role: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val bounds: Bounds,
    val actions: List<String> = emptyList(),
    val tag: String? = null,
    val source: String? = null,
    val children: List<SnapshotNode> = emptyList(),
)
