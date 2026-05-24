/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndroidComposeRendererInRobolectricTest {
    @Test
    fun `scaled density multiplies viewport density by preview font scale`() {
        val scaledDensity = AndroidComposeRendererInRobolectric.scaledDensity(density = 2.0f, fontScale = 1.3f)

        assertEquals(2.6f, scaledDensity)
    }

    @Test
    fun `night ui mode replaces only night bits`() {
        val configuration = FakeConfiguration(uiMode = 0x03 or 0x10)

        AndroidComposeRendererInRobolectric.applyNightMode(configuration, uiMode = 0x20)

        assertEquals(0x03 or 0x20, configuration.uiMode)
    }

    @Test
    fun `show background without explicit color uses white preview background`() {
        val backgroundColor = AndroidComposeRendererInRobolectric.effectiveBackgroundColor(backgroundColor = 0L)

        assertEquals(-0x1, backgroundColor)
    }

    @Test
    fun `show background uses explicit ARGB preview color`() {
        val backgroundColor = AndroidComposeRendererInRobolectric.effectiveBackgroundColor(backgroundColor = 0xFF112233)

        assertEquals(0xFF112233.toInt(), backgroundColor)
    }

    @Test
    fun `default semantics mode omits replaced semantics children`() {
        val node = FakeSemanticsNode()

        val children = AndroidComposeRendererInRobolectric.semanticsChildren(node, includeUnmergedSemantics = false)

        assertEquals(listOf("merged-child"), children)
    }

    @Test
    fun `include unmerged semantics mode includes replaced semantics children`() {
        val node = FakeSemanticsNode()

        val children = AndroidComposeRendererInRobolectric.semanticsChildren(node, includeUnmergedSemantics = true)

        assertEquals(listOf("unmerged-child"), children)
    }

    @Test
    fun `default semantics mode selects merged root`() {
        val owner = FakeSemanticsOwner()

        val root = AndroidComposeRendererInRobolectric.rootSemanticsNode(owner, includeUnmergedSemantics = false)

        assertEquals("merged-root", root)
    }

    @Test
    fun `include unmerged semantics mode selects unmerged root`() {
        val owner = FakeSemanticsOwner()

        val root = AndroidComposeRendererInRobolectric.rootSemanticsNode(owner, includeUnmergedSemantics = true)

        assertEquals("unmerged-root", root)
    }

    class FakeConfiguration(
        @JvmField
        var uiMode: Int,
    )

    private class FakeSemanticsOwner {
        private var calls = 0

        fun getRootSemanticsNode(): String {
            calls += 1
            return "merged-root"
        }

        fun getUnmergedRootSemanticsNode(): String {
            calls += 1
            return "unmerged-root"
        }
    }

    private class FakeSemanticsNode {
        fun getChildren(
            includeReplacedSemantics: Boolean,
            includeFakeNodes: Boolean,
            includeDeactivatedNodes: Boolean,
        ): List<String> =
            if (includeReplacedSemantics && includeFakeNodes && !includeDeactivatedNodes) {
                listOf("unmerged-child")
            } else {
                listOf("merged-child")
            }
    }
}
