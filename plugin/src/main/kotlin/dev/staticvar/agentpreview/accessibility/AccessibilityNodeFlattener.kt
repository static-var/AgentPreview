/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.accessibility

import dev.staticvar.agentpreview.model.Bounds
import dev.staticvar.agentpreview.model.PreviewSnapshot
import dev.staticvar.agentpreview.model.SnapshotNode
import java.util.Locale

internal data class FlattenedAccessibilityNode(
    val id: String,
    val role: String?,
    val accessibleName: String?,
    val actions: List<String>,
    val bounds: Bounds,
    val tag: String?,
    val source: String?,
    val parentId: String?,
    val siblingIndex: Int,
    val isActionable: Boolean,
) {
    val summary: AccessibilityNodeSummary
        get() =
            AccessibilityNodeSummary(
                id = id,
                role = role,
                accessibleName = accessibleName,
                actions = actions,
                bounds = bounds,
                tag = tag,
                source = source,
            )
}

internal object AccessibilityNodeFlattener {
    private val interactiveRoles = setOf("button", "checkbox", "radio button", "radiobutton", "switch", "tab")

    fun flatten(snapshot: PreviewSnapshot): List<FlattenedAccessibilityNode> = flattenNodes(snapshot.nodes, parentId = null)

    private fun flattenNodes(
        nodes: List<SnapshotNode>,
        parentId: String?,
    ): List<FlattenedAccessibilityNode> =
        nodes.flatMapIndexed { index, node ->
            val flattened =
                FlattenedAccessibilityNode(
                    id = node.id,
                    role = node.role.trimToNull(),
                    accessibleName = accessibleNameFor(node),
                    actions = node.actions,
                    bounds = node.bounds,
                    tag = node.tag,
                    source = node.source,
                    parentId = parentId,
                    siblingIndex = index,
                    isActionable = node.hasActionableSemantics(),
                )
            listOf(flattened) + flattenNodes(node.children, parentId = node.id)
        }

    private fun accessibleNameFor(node: SnapshotNode): String? =
        node.text.trimToNull()
            ?: node.contentDescription.trimToNull()
            ?: node.children
                .mapNotNull { accessibleNameFor(it) }
                .joinToString(" ")
                .trimToNull()

    private fun SnapshotNode.hasActionableSemantics(): Boolean =
        actions.any { it.isNotBlank() } ||
            role.trimToNull()?.lowercase(Locale.US) in interactiveRoles

    private fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
