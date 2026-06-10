/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.accessibility

import dev.staticvar.agentpreview.model.Bounds
import dev.staticvar.agentpreview.model.PreviewMetadata
import dev.staticvar.agentpreview.model.PreviewSnapshot
import dev.staticvar.agentpreview.model.SnapshotNode
import dev.staticvar.agentpreview.model.SnapshotRenderMetadata
import dev.staticvar.agentpreview.model.Viewport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccessibilityRulesTest {
    @Test
    fun `rules flag first pass accessibility findings from snapshot semantics`() {
        val snapshot =
            snapshot(
                viewport = Viewport(name = "phone", width = 360, height = 640, density = 2.0f),
                nodes =
                    listOf(
                        node(
                            id = "missing-name",
                            role = "Button",
                            actions = listOf("OnClick"),
                            bounds = Bounds(x = 16, y = 20, width = 96, height = 96),
                        ),
                        node(
                            id = "placeholder",
                            role = "Button",
                            text = "Button",
                            actions = listOf("OnClick"),
                            bounds = Bounds(x = 16, y = 140, width = 96, height = 96),
                        ),
                        node(
                            id = "duplicate-a",
                            role = "Button",
                            text = "Edit",
                            actions = listOf("OnClick"),
                            bounds = Bounds(x = 16, y = 260, width = 96, height = 96),
                        ),
                        node(
                            id = "duplicate-b",
                            role = "Button",
                            text = "Edit",
                            actions = listOf("OnClick"),
                            bounds = Bounds(x = 128, y = 260, width = 96, height = 96),
                        ),
                        node(
                            id = "missing-role",
                            text = "Save",
                            actions = listOf("OnClick"),
                            bounds = Bounds(x = 16, y = 380, width = 96, height = 96),
                        ),
                        node(
                            id = "small-target",
                            role = "Button",
                            text = "Close",
                            actions = listOf("OnClick"),
                            bounds = Bounds(x = 16, y = 500, width = 80, height = 96),
                        ),
                    ),
            )

        val findings = AccessibilityRules.evaluate(snapshot, viewportLabel = "phone")

        assertFinding(
            findings = findings,
            category = AccessibilityCategory.MISSING_ACCESSIBLE_NAME,
            severity = AccessibilitySeverity.ERROR,
            nodeId = "missing-name",
        )
        assertFinding(
            findings = findings,
            category = AccessibilityCategory.PLACEHOLDER_LABEL,
            severity = AccessibilitySeverity.WARNING,
            nodeId = "placeholder",
        )
        assertFinding(
            findings = findings,
            category = AccessibilityCategory.DUPLICATE_ACCESSIBLE_NAME,
            severity = AccessibilitySeverity.WARNING,
            nodeId = "duplicate-b",
        )
        assertFinding(
            findings = findings,
            category = AccessibilityCategory.MISSING_ROLE,
            severity = AccessibilitySeverity.WARNING,
            nodeId = "missing-role",
        )
        assertFinding(
            findings = findings,
            category = AccessibilityCategory.SMALL_TOUCH_TARGET,
            severity = AccessibilitySeverity.WARNING,
            nodeId = "small-target",
        )
        assertEquals(setOf("AccessibilityRulesTestPreview"), findings.map { it.previewId }.toSet())
        assertEquals(setOf("phone"), findings.map { it.viewportLabel }.toSet())
    }

    @Test
    fun `rules flag traversal order suspicion when actionable semantics order diverges from visual order`() {
        val snapshot =
            snapshot(
                nodes =
                    listOf(
                        node(
                            id = "second-visually",
                            role = "Button",
                            text = "Second",
                            actions = listOf("OnClick"),
                            bounds = Bounds(x = 16, y = 180, width = 96, height = 96),
                        ),
                        node(
                            id = "first-visually",
                            role = "Button",
                            text = "First",
                            actions = listOf("OnClick"),
                            bounds = Bounds(x = 16, y = 20, width = 96, height = 96),
                        ),
                    ),
            )

        val findings = AccessibilityRules.evaluate(snapshot, viewportLabel = "default")

        assertFinding(
            findings = findings,
            category = AccessibilityCategory.TRAVERSAL_ORDER_SUSPICION,
            severity = AccessibilitySeverity.INFO,
            nodeId = "second-visually",
        )
    }

    @Test
    fun `rules audit role only interactive nodes`() {
        val snapshot =
            snapshot(
                viewport = Viewport(name = "zero-density", width = 360, height = 640, density = 0.0f),
                nodes =
                    listOf(
                        node(
                            id = "role-only-missing-name-small",
                            role = "Button",
                            bounds = Bounds(x = 16, y = 20, width = 44, height = 56),
                        ),
                    ),
            )

        val findings = AccessibilityRules.evaluate(snapshot, viewportLabel = "zero-density")

        assertFinding(
            findings = findings,
            category = AccessibilityCategory.MISSING_ACCESSIBLE_NAME,
            severity = AccessibilitySeverity.ERROR,
            nodeId = "role-only-missing-name-small",
        )
        assertFinding(
            findings = findings,
            category = AccessibilityCategory.SMALL_TOUCH_TARGET,
            severity = AccessibilitySeverity.WARNING,
            nodeId = "role-only-missing-name-small",
        )
    }

    @Test
    fun `missing role applies to any nonblank action`() {
        val snapshot =
            snapshot(
                nodes =
                    listOf(
                        node(
                            id = "click-action-no-role",
                            text = "Open",
                            actions = listOf("click"),
                            bounds = Bounds(x = 16, y = 20, width = 96, height = 96),
                        ),
                    ),
            )

        val findings = AccessibilityRules.evaluate(snapshot, viewportLabel = "default")

        assertFinding(
            findings = findings,
            category = AccessibilityCategory.MISSING_ROLE,
            severity = AccessibilitySeverity.WARNING,
            nodeId = "click-action-no-role",
        )
    }

    @Test
    fun `flattener preserves hierarchy and merges child accessible names`() {
        val snapshot =
            snapshot(
                nodes =
                    listOf(
                        node(
                            id = "parent",
                            role = "Button",
                            actions = listOf("OnClick"),
                            bounds = Bounds(x = 16, y = 20, width = 120, height = 56),
                            children =
                                listOf(
                                    node(
                                        id = "child-text",
                                        text = "Continue",
                                        bounds = Bounds(x = 24, y = 28, width = 80, height = 24),
                                    ),
                                    node(
                                        id = "child-icon",
                                        contentDescription = "securely",
                                        bounds = Bounds(x = 108, y = 28, width = 24, height = 24),
                                    ),
                                ),
                        ),
                    ),
            )

        val flattened = AccessibilityNodeFlattener.flatten(snapshot)

        val parent = flattened.single { it.id == "parent" }
        val childText = flattened.single { it.id == "child-text" }
        val childIcon = flattened.single { it.id == "child-icon" }
        assertEquals("Continue securely", parent.accessibleName)
        assertEquals(null, parent.parentId)
        assertEquals(0, parent.siblingIndex)
        assertTrue(parent.isActionable)
        assertEquals("parent", childText.parentId)
        assertEquals(0, childText.siblingIndex)
        assertEquals("parent", childIcon.parentId)
        assertEquals(1, childIcon.siblingIndex)
        assertFalse(childText.isActionable)
    }

    @Test
    fun `default report not checked notes document known audit limits`() {
        val report =
            AccessibilityAuditReport(
                auditedBundleCount = 1,
                skippedBundleCount = 0,
                findings = emptyList(),
                warnings = emptyList(),
            )

        assertEquals(defaultNotCheckedNotes, report.notChecked)
        assertTrue(report.notChecked.any { it.contains("TalkBack") && it.contains("Switch Access") })
        assertTrue(report.notChecked.any { it.contains("contrast") })
        assertTrue(report.notChecked.any { it.contains("large text") && it.contains("orientation") })
        assertTrue(report.notChecked.any { it.contains("end-to-end") && it.contains("Play") })
    }

    private fun assertFinding(
        findings: List<AccessibilityFinding>,
        category: AccessibilityCategory,
        severity: AccessibilitySeverity,
        nodeId: String,
    ) {
        val finding = findings.single { it.category == category && it.node.id == nodeId }
        assertEquals(severity, finding.severity)
        assertTrue(finding.message.isNotBlank())
        assertTrue(finding.recommendation.isNotBlank())
        assertTrue(finding.guideline.isNotBlank())
    }

    private fun snapshot(
        viewport: Viewport = Viewport(name = "default", width = 360, height = 640, density = 1.0f),
        nodes: List<SnapshotNode>,
    ) = PreviewSnapshot(
        schemaVersion = 1,
        preview =
            PreviewMetadata(
                id = "AccessibilityRulesTestPreview",
                name = "AccessibilityRulesTest",
            ),
        viewport = viewport,
        nodes = nodes,
        render = SnapshotRenderMetadata(mode = "robolectric"),
    )

    private fun node(
        id: String,
        role: String? = null,
        text: String? = null,
        contentDescription: String? = null,
        bounds: Bounds,
        actions: List<String> = emptyList(),
        tag: String? = null,
        source: String? = null,
        children: List<SnapshotNode> = emptyList(),
    ) = SnapshotNode(
        id = id,
        role = role,
        text = text,
        contentDescription = contentDescription,
        bounds = bounds,
        actions = actions,
        tag = tag,
        source = source,
        children = children,
    )
}
