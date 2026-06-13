/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.accessibility

import dev.staticvar.agentpreview.model.PreviewSnapshot
import java.util.Locale

// Snapshot rules are deterministic heuristics for captured layout trees, not a replacement for device assistive-tech validation.
internal object AccessibilityRules {
    private val placeholderLabels = setOf("button", "icon", "image", "click", "tap", "submit button")

    fun evaluate(
        snapshot: PreviewSnapshot,
        viewportLabel: String,
    ): List<AccessibilityFinding> {
        val nodes = AccessibilityNodeFlattener.flatten(snapshot)
        val findings = mutableListOf<AccessibilityFinding>()

        nodes.filter { it.isActionable }.forEach { node ->
            if (node.accessibleName.isNullOrBlank()) {
                findings +=
                    finding(
                        category = AccessibilityCategory.MISSING_ACCESSIBLE_NAME,
                        severity = AccessibilitySeverity.ERROR,
                        node = node,
                        snapshot = snapshot,
                        viewportLabel = viewportLabel,
                        message = "Actionable node '${node.id}' does not expose an accessible name.",
                        recommendation = "Add visible text, a contentDescription, or merge meaningful child semantics.",
                        guideline = "WCAG 4.1.2 Name, Role, Value",
                    )
            }

            val normalizedName = node.accessibleName.normalizedLabel()
            if (normalizedName in placeholderLabels) {
                findings +=
                    finding(
                        category = AccessibilityCategory.PLACEHOLDER_LABEL,
                        severity = AccessibilitySeverity.WARNING,
                        node = node,
                        snapshot = snapshot,
                        viewportLabel = viewportLabel,
                        message = "Actionable node '${node.id}' uses a placeholder accessible name.",
                        recommendation = "Replace generic labels with a specific action or object name.",
                        guideline = "WCAG 2.4.6 Headings and Labels",
                    )
            }

            if (node.role.isNullOrBlank() && node.actions.any { it.isNotBlank() }) {
                findings +=
                    finding(
                        category = AccessibilityCategory.MISSING_ROLE,
                        severity = AccessibilitySeverity.WARNING,
                        node = node,
                        snapshot = snapshot,
                        viewportLabel = viewportLabel,
                        message = "Actionable node '${node.id}' has an event action but no role.",
                        recommendation = "Expose an appropriate semantic role for the actionable element.",
                        guideline = "WCAG 4.1.2 Name, Role, Value",
                    )
            }

            if (node.hasSmallTouchTarget(snapshot)) {
                findings +=
                    finding(
                        category = AccessibilityCategory.SMALL_TOUCH_TARGET,
                        severity = AccessibilitySeverity.WARNING,
                        node = node,
                        snapshot = snapshot,
                        viewportLabel = viewportLabel,
                        message = "Actionable node '${node.id}' is smaller than 48dp in at least one dimension.",
                        recommendation = "Increase the touch target to at least 48dp wide and 48dp tall.",
                        guideline = "Android accessibility touch target guidance",
                    )
            }
        }

        findings += duplicateAccessibleNames(nodes, snapshot, viewportLabel)
        traversalSuspicion(nodes, snapshot, viewportLabel)?.let { findings += it }

        return findings
    }

    private fun duplicateAccessibleNames(
        nodes: List<FlattenedAccessibilityNode>,
        snapshot: PreviewSnapshot,
        viewportLabel: String,
    ): List<AccessibilityFinding> =
        nodes
            .filter { it.isActionable && !it.accessibleName.isNullOrBlank() }
            .groupBy { it.parentId }
            .values
            .flatMap { siblings ->
                siblings
                    .groupBy { it.accessibleName.normalizedLabel() }
                    .values
                    .filter { duplicates -> duplicates.size > 1 }
                    .flatMap { duplicates ->
                        duplicates
                            .sortedBy { it.siblingIndex }
                            .drop(1)
                            .map { duplicate ->
                                finding(
                                    category = AccessibilityCategory.DUPLICATE_ACCESSIBLE_NAME,
                                    severity = AccessibilitySeverity.WARNING,
                                    node = duplicate,
                                    snapshot = snapshot,
                                    viewportLabel = viewportLabel,
                                    message = "Sibling actionable nodes share the accessible name '${duplicate.accessibleName}'.",
                                    recommendation = "Make nearby actionable labels unique enough to distinguish their purpose.",
                                    guideline = "WCAG 2.4.6 Headings and Labels",
                                )
                            }
                    }
            }

    private fun traversalSuspicion(
        nodes: List<FlattenedAccessibilityNode>,
        snapshot: PreviewSnapshot,
        viewportLabel: String,
    ): AccessibilityFinding? {
        val actionable = nodes.filter { it.isActionable }
        if (actionable.size < 2) return null

        val visualOrder = actionable.sortedWith(compareBy({ it.bounds.y }, { it.bounds.x }, { it.siblingIndex }))
        if (actionable.map { it.id } == visualOrder.map { it.id }) return null

        val firstUnexpected =
            actionable
                .zip(visualOrder)
                .firstOrNull { (semantic, visual) -> semantic.id != visual.id }
                ?.first
                ?: actionable.first()

        return finding(
            category = AccessibilityCategory.TRAVERSAL_ORDER_SUSPICION,
            severity = AccessibilitySeverity.INFO,
            node = firstUnexpected,
            snapshot = snapshot,
            viewportLabel = viewportLabel,
            message = "Actionable semantics order differs from top-to-bottom, left-to-right visual order.",
            recommendation = "Review traversal order and add explicit traversal semantics if the order is unintended.",
            guideline = "Android accessibility traversal order guidance",
        )
    }

    private fun FlattenedAccessibilityNode.hasSmallTouchTarget(snapshot: PreviewSnapshot): Boolean {
        val density = snapshot.viewport.density.takeIf { it > 0f } ?: 1.0f
        return bounds.width / density < 48.0f || bounds.height / density < 48.0f
    }

    private fun finding(
        category: AccessibilityCategory,
        severity: AccessibilitySeverity,
        node: FlattenedAccessibilityNode,
        snapshot: PreviewSnapshot,
        viewportLabel: String,
        message: String,
        recommendation: String,
        guideline: String,
    ) = AccessibilityFinding(
        id = "${category.name.lowercase(Locale.US)}:${snapshot.preview.id}:$viewportLabel:${node.id}",
        severity = severity,
        category = category,
        message = message,
        recommendation = recommendation,
        guideline = guideline,
        previewId = snapshot.preview.id,
        viewportLabel = viewportLabel,
        node = node.summary,
    )

    private fun String?.normalizedLabel(): String = this?.trim()?.lowercase(Locale.US).orEmpty()
}
