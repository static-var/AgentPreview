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
