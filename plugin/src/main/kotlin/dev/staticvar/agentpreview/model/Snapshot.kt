package dev.staticvar.agentpreview.model

import kotlinx.serialization.Serializable

@Serializable
data class PreviewSnapshot(
    val schemaVersion: Int,
    val preview: PreviewMetadata,
    val viewport: Viewport,
    val nodes: List<SnapshotNode>,
)

@Serializable
data class PreviewMetadata(
    val id: String,
    val name: String? = null,
    val group: String? = null,
    val source: String? = null,
    val sourceSet: String? = null,
)

@Serializable
data class Viewport(
    val width: Int,
    val height: Int,
    val density: Float,
)

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

@Serializable
data class Bounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)
