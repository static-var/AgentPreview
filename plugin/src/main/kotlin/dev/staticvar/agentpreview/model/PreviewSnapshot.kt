/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.model

import kotlinx.serialization.Serializable

const val CURRENT_SNAPSHOT_SCHEMA_VERSION = 2

@Serializable
data class PreviewSnapshot(
    val schemaVersion: Int,
    val preview: PreviewMetadata,
    val viewport: Viewport,
    val nodes: List<SnapshotNode>,
    val layoutTree: List<SnapshotLayoutNode> = emptyList(),
    val render: SnapshotRenderMetadata? = null,
)
