/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.model

import kotlinx.serialization.Serializable

@Serializable
data class SnapshotLayoutNode(
    val id: String,
    val boundsPx: Bounds,
    val boundsDp: DpBounds,
    val componentHint: String? = null,
    val sourceName: String? = null,
    val sourceFile: String? = null,
    val sourceLine: Int? = null,
    val sourceHintKind: String? = null,
    val modifierHint: String? = null,
    val classHint: String? = null,
    val semanticsId: String? = null,
    val semantics: SnapshotLayoutSemanticsSummary? = null,
    val children: List<SnapshotLayoutNode> = emptyList(),
)

@Serializable
data class DpBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

@Serializable
data class SnapshotLayoutSemanticsSummary(
    val text: String? = null,
    val contentDescription: String? = null,
    val role: String? = null,
    val actions: List<String> = emptyList(),
    val tag: String? = null,
)
