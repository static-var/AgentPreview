/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class AndroidComposeRendererInRobolectricTest {
    @Test
    fun `scaled density multiplies viewport density by preview font scale`() {
        val scaledDensity = AndroidComposeRendererInRobolectric.scaledDensity(density = 2.0f, fontScale = 1.3f)

        assertEquals(2.6f, scaledDensity)
    }

    @Test
    fun `android resource locale qualifier maps language and region`() {
        val locale = AndroidComposeRendererInRobolectric.localeForPreviewQualifier("en-rUS")

        assertEquals(Locale("en", "US"), locale)
    }

    @Test
    fun `language tag locale maps language and region`() {
        val locale = AndroidComposeRendererInRobolectric.localeForPreviewQualifier("fr-FR")

        assertEquals(Locale("fr", "FR"), locale)
    }

    @Test
    fun `bare language locale maps language only`() {
        val locale = AndroidComposeRendererInRobolectric.localeForPreviewQualifier("de")

        assertEquals(Locale("de"), locale)
    }

    @Test
    fun `ui mode replaces requested type and night bits while preserving unrelated bits`() {
        val unrelatedBits = 0x100
        val configuration =
            FakeConfiguration(
                uiMode = unrelatedBits or UI_MODE_TYPE_DESK or UI_MODE_NIGHT_NO,
            )

        AndroidComposeRendererInRobolectric.applyUiMode(
            configuration,
            uiMode = UI_MODE_TYPE_CAR or UI_MODE_NIGHT_YES,
        )

        assertEquals(unrelatedBits or UI_MODE_TYPE_CAR or UI_MODE_NIGHT_YES, configuration.uiMode)
    }

    @Test
    fun `ui mode type-only request preserves current night bits`() {
        val configuration = FakeConfiguration(uiMode = UI_MODE_TYPE_DESK or UI_MODE_NIGHT_YES)

        AndroidComposeRendererInRobolectric.applyUiMode(configuration, uiMode = UI_MODE_TYPE_TELEVISION)

        assertEquals(UI_MODE_TYPE_TELEVISION or UI_MODE_NIGHT_YES, configuration.uiMode)
    }

    @Test
    fun `empty ui mode request preserves current ui mode`() {
        val initialUiMode = UI_MODE_TYPE_DESK or UI_MODE_NIGHT_NO
        val configuration = FakeConfiguration(uiMode = initialUiMode)

        AndroidComposeRendererInRobolectric.applyUiMode(configuration, uiMode = 0)
        AndroidComposeRendererInRobolectric.applyUiMode(configuration, uiMode = null)

        assertEquals(initialUiMode, configuration.uiMode)
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

private const val UI_MODE_TYPE_DESK = 0x02
private const val UI_MODE_TYPE_CAR = 0x03
private const val UI_MODE_TYPE_TELEVISION = 0x04
private const val UI_MODE_NIGHT_NO = 0x10
private const val UI_MODE_NIGHT_YES = 0x20
