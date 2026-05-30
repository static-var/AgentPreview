/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.export

import dev.staticvar.agentpreview.model.Bounds
import dev.staticvar.agentpreview.model.SnapshotLayoutNode
import dev.staticvar.agentpreview.model.SnapshotNode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ScreenshotCropRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

internal data class ScreenshotCropPlan(
    val enabled: Boolean,
    val fallback: Boolean,
    val reason: String?,
    val rect: ScreenshotCropRect,
    val paddingDp: Int,
) {
    val screenshotWidth: Int get() = rect.width
    val screenshotHeight: Int get() = rect.height
}

internal class ScreenshotCropPlanner {
    fun plan(
        bitmapWidth: Int,
        bitmapHeight: Int,
        density: Float,
        cropToContent: Boolean,
        cropPaddingDp: Int,
        layoutTree: List<SnapshotLayoutNode>,
        semanticsNodes: List<SnapshotNode>,
    ): ScreenshotCropPlan {
        val fullViewport = ScreenshotCropRect(0, 0, bitmapWidth, bitmapHeight)
        if (!cropToContent) {
            return ScreenshotCropPlan(
                enabled = false,
                fallback = true,
                reason = "disabled",
                rect = fullViewport,
                paddingDp = cropPaddingDp,
            )
        }

        val contentBounds =
            layoutBounds(layoutTree, bitmapWidth, bitmapHeight)
                ?: semanticsBounds(semanticsNodes, bitmapWidth, bitmapHeight)
                ?: return ambiguous(fullViewport, cropPaddingDp)
        val padded = contentBounds.expand((cropPaddingDp * density).roundToInt()).clamp(bitmapWidth, bitmapHeight)
        if (!padded.isPositive() || padded.effectivelyFullViewport(bitmapWidth, bitmapHeight)) return ambiguous(fullViewport, cropPaddingDp)
        return ScreenshotCropPlan(
            enabled = true,
            fallback = false,
            reason = null,
            rect = padded,
            paddingDp = cropPaddingDp,
        )
    }

    private fun ambiguous(
        fullViewport: ScreenshotCropRect,
        paddingDp: Int,
    ) = ScreenshotCropPlan(
        enabled = true,
        fallback = true,
        reason = "ambiguous-content-bounds",
        rect = fullViewport,
        paddingDp = paddingDp,
    )

    private fun layoutBounds(
        nodes: List<SnapshotLayoutNode>,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): ScreenshotCropRect? {
        val roots = nodes.toSet()
        val candidates =
            nodes
                .flatMap { it.flatten() }
                .filter { node -> node.boundsPx.isPositive() }
                .filterNot { node -> node in roots && node.boundsPx.effectivelyFullViewport(bitmapWidth, bitmapHeight) }
                .map { node -> node.boundsPx.toRect() }
        return candidates.unionOrNull()?.takeUnless { it.effectivelyFullViewport(bitmapWidth, bitmapHeight) }
    }

    private fun semanticsBounds(
        nodes: List<SnapshotNode>,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): ScreenshotCropRect? {
        val roots = nodes.toSet()
        val candidates =
            nodes
                .flatMap { it.flatten() }
                .filter { node -> node.bounds.isPositive() }
                .filterNot { node -> node in roots && node.bounds.effectivelyFullViewport(bitmapWidth, bitmapHeight) }
                .map { node -> node.bounds.toRect() }
        return candidates.unionOrNull()?.takeUnless { it.effectivelyFullViewport(bitmapWidth, bitmapHeight) }
    }

    private fun SnapshotLayoutNode.flatten(): List<SnapshotLayoutNode> = listOf(this) + children.flatMap { it.flatten() }

    private fun SnapshotNode.flatten(): List<SnapshotNode> = listOf(this) + children.flatMap { it.flatten() }

    private fun List<ScreenshotCropRect>.unionOrNull(): ScreenshotCropRect? =
        takeIf { it.isNotEmpty() }?.let { rects ->
            val left = rects.minOf { it.x }
            val top = rects.minOf { it.y }
            val right = rects.maxOf { it.x + it.width }
            val bottom = rects.maxOf { it.y + it.height }
            ScreenshotCropRect(left, top, right - left, bottom - top)
        }

    private fun ScreenshotCropRect.expand(paddingPx: Int): ScreenshotCropRect =
        ScreenshotCropRect(x - paddingPx, y - paddingPx, width + paddingPx * 2, height + paddingPx * 2)

    private fun ScreenshotCropRect.clamp(
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): ScreenshotCropRect {
        val left = max(0, x)
        val top = max(0, y)
        val right = min(bitmapWidth, x + width)
        val bottom = min(bitmapHeight, y + height)
        return ScreenshotCropRect(left, top, max(0, right - left), max(0, bottom - top))
    }

    private fun ScreenshotCropRect.isPositive(): Boolean = width > 0 && height > 0

    private fun Bounds.toRect(): ScreenshotCropRect = ScreenshotCropRect(x, y, width, height)

    private fun Bounds.isPositive(): Boolean = width > 0 && height > 0

    private fun Bounds.effectivelyFullViewport(
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): Boolean = toRect().effectivelyFullViewport(bitmapWidth, bitmapHeight)

    private fun ScreenshotCropRect.effectivelyFullViewport(
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): Boolean = x <= 1 && y <= 1 && (x + width) >= bitmapWidth - 1 && (y + height) >= bitmapHeight - 1
}
