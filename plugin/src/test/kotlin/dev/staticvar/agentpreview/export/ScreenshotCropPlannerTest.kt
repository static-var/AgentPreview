/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.export

import dev.staticvar.agentpreview.model.Bounds
import dev.staticvar.agentpreview.model.DpBounds
import dev.staticvar.agentpreview.model.SnapshotLayoutNode
import dev.staticvar.agentpreview.model.SnapshotNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenshotCropPlannerTest {
    private val planner = ScreenshotCropPlanner()

    @Test
    fun `layout tree crop ignores viewport root and uses child union with padding`() {
        val plan =
            planner.plan(
                bitmapWidth = 400,
                bitmapHeight = 800,
                density = 2f,
                cropToContent = true,
                cropPaddingDp = 20,
                layoutTree =
                    listOf(
                        layout(
                            id = "root",
                            x = 0,
                            y = 0,
                            width = 400,
                            height = 800,
                            children = listOf(layout(id = "child", x = 100, y = 200, width = 80, height = 60)),
                        ),
                    ),
                semanticsNodes = emptyList(),
            )

        assertFalse(plan.fallback)
        assertEquals(ScreenshotCropRect(x = 60, y = 160, width = 160, height = 140), plan.rect)
        assertEquals(160, plan.screenshotWidth)
        assertEquals(140, plan.screenshotHeight)
    }

    @Test
    fun `non semantic container layout nodes are included in layout union`() {
        val plan =
            planner.plan(
                bitmapWidth = 393,
                bitmapHeight = 852,
                density = 1f,
                cropToContent = true,
                cropPaddingDp = 20,
                layoutTree =
                    listOf(
                        layout(
                            id = "root",
                            x = 0,
                            y = 0,
                            width = 393,
                            height = 852,
                            children =
                                listOf(
                                    layout(
                                        id = "column",
                                        x = 24,
                                        y = 24,
                                        width = 345,
                                        height = 804,
                                        children = listOf(layout(id = "text", x = 24, y = 340, width = 185, height = 37)),
                                    ),
                                ),
                        ),
                    ),
                semanticsNodes = emptyList(),
            )

        assertFalse(plan.fallback)
        assertEquals(ScreenshotCropRect(x = 4, y = 4, width = 385, height = 844), plan.rect)
    }

    @Test
    fun `non root full viewport layout node keeps layout ambiguous instead of cropping to grandchild`() {
        val plan =
            planner.plan(
                bitmapWidth = 400,
                bitmapHeight = 800,
                density = 1f,
                cropToContent = true,
                cropPaddingDp = 20,
                layoutTree =
                    listOf(
                        layout(
                            id = "root",
                            x = 0,
                            y = 0,
                            width = 400,
                            height = 800,
                            children =
                                listOf(
                                    layout(
                                        id = "full-size-box",
                                        x = 0,
                                        y = 0,
                                        width = 400,
                                        height = 800,
                                        children = listOf(layout(id = "small-grandchild", x = 100, y = 200, width = 80, height = 60)),
                                    ),
                                ),
                        ),
                    ),
                semanticsNodes = emptyList(),
            )

        assertTrue(plan.fallback)
        assertEquals("ambiguous-content-bounds", plan.reason)
        assertEquals(ScreenshotCropRect(x = 0, y = 0, width = 400, height = 800), plan.rect)
    }

    @Test
    fun `semantics bounds are used when layout tree is ambiguous`() {
        val plan =
            planner.plan(
                bitmapWidth = 400,
                bitmapHeight = 800,
                density = 1f,
                cropToContent = true,
                cropPaddingDp = 10,
                layoutTree = listOf(layout(id = "root", x = 0, y = 0, width = 400, height = 800)),
                semanticsNodes = listOf(node(id = "button", x = 50, y = 70, width = 40, height = 20)),
            )

        assertFalse(plan.fallback)
        assertEquals(ScreenshotCropRect(x = 40, y = 60, width = 60, height = 40), plan.rect)
    }

    @Test
    fun `ambiguous content falls back to full viewport`() {
        val plan =
            planner.plan(
                bitmapWidth = 400,
                bitmapHeight = 800,
                density = 1f,
                cropToContent = true,
                cropPaddingDp = 20,
                layoutTree = listOf(layout(id = "root", x = 0, y = 0, width = 400, height = 800)),
                semanticsNodes = emptyList(),
            )

        assertTrue(plan.fallback)
        assertEquals("ambiguous-content-bounds", plan.reason)
        assertEquals(ScreenshotCropRect(x = 0, y = 0, width = 400, height = 800), plan.rect)
    }

    @Test
    fun `disabled cropping falls back to full viewport metadata`() {
        val plan =
            planner.plan(
                bitmapWidth = 400,
                bitmapHeight = 800,
                density = 1f,
                cropToContent = false,
                cropPaddingDp = 20,
                layoutTree = listOf(layout(id = "child", x = 100, y = 200, width = 80, height = 60)),
                semanticsNodes = emptyList(),
            )

        assertFalse(plan.enabled)
        assertTrue(plan.fallback)
        assertEquals("disabled", plan.reason)
        assertEquals(ScreenshotCropRect(x = 0, y = 0, width = 400, height = 800), plan.rect)
    }

    @Test
    fun `padding that clamps content crop to full viewport reports fallback metadata`() {
        val plan =
            planner.plan(
                bitmapWidth = 400,
                bitmapHeight = 800,
                density = 1f,
                cropToContent = true,
                cropPaddingDp = 20,
                layoutTree = listOf(layout(id = "child", x = 10, y = 10, width = 380, height = 780)),
                semanticsNodes = emptyList(),
            )

        assertTrue(plan.fallback)
        assertEquals("ambiguous-content-bounds", plan.reason)
        assertEquals(ScreenshotCropRect(x = 0, y = 0, width = 400, height = 800), plan.rect)
        assertEquals(400, plan.screenshotWidth)
        assertEquals(800, plan.screenshotHeight)
    }

    @Test
    fun `padding is rounded and clamped to bitmap bounds`() {
        val plan =
            planner.plan(
                bitmapWidth = 100,
                bitmapHeight = 100,
                density = 1.5f,
                cropToContent = true,
                cropPaddingDp = 20,
                layoutTree = listOf(layout(id = "child", x = 5, y = 6, width = 20, height = 20)),
                semanticsNodes = emptyList(),
            )

        assertEquals(ScreenshotCropRect(x = 0, y = 0, width = 55, height = 56), plan.rect)
    }

    private fun layout(
        id: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        children: List<SnapshotLayoutNode> = emptyList(),
    ) = SnapshotLayoutNode(
        id = id,
        boundsPx = Bounds(x = x, y = y, width = width, height = height),
        boundsDp = DpBounds(x = x.toFloat(), y = y.toFloat(), width = width.toFloat(), height = height.toFloat()),
        children = children,
    )

    private fun node(
        id: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        children: List<SnapshotNode> = emptyList(),
    ) = SnapshotNode(id = id, bounds = Bounds(x = x, y = y, width = width, height = height), children = children)
}
