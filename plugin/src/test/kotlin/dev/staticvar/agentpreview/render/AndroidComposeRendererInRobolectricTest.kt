/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.Locale

class AndroidComposeRendererInRobolectricTest {
    @TempDir
    lateinit var tempDir: File

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
    fun `extracts layout tree hints semantics summary and px dp bounds without test tags`() {
        val child =
            FakeLayoutNode(
                semanticsId = 7,
                coordinates = FakeCoordinates(width = 40, height = 20, rootX = 10.0f, rootY = 6.0f),
                measurePolicy = FakeRowMeasurePolicy(),
                modifier = FakeModifier("padding"),
                semanticsConfiguration =
                    FakeSemanticsConfiguration(
                        "Text" to listOf("Welcome"),
                        "ContentDescription" to listOf("Greeting"),
                        "Role" to "Button",
                        "OnClick" to "action",
                    ),
            )
        val root =
            FakeLayoutNode(
                semanticsId = 1,
                coordinates = FakeCoordinates(width = 100, height = 50, rootX = 0.0f, rootY = 0.0f),
                measurePolicy = FakeColumnMeasurePolicy(),
                modifier = FakeModifier("fillMaxSize"),
                semanticsConfiguration = FakeSemanticsConfiguration(),
                children = listOf(child),
            )

        val tree = AndroidComposeRendererInRobolectric.extractLayoutTree(root, density = 2.0f)

        assertEquals("layout-1", tree.id)
        assertTrue(tree.componentHint.orEmpty().contains("FakeColumnMeasurePolicy"))
        assertEquals(100, tree.boundsPx.width)
        assertEquals(50.0f, tree.boundsDp.width)
        assertEquals("7", tree.children.single().semanticsId)
        assertTrue(
            tree.children
                .single()
                .componentHint
                .orEmpty()
                .contains("FakeRowMeasurePolicy"),
        )
        assertEquals(
            "Welcome",
            tree.children
                .single()
                .semantics
                ?.text,
        )
        assertEquals(
            "Greeting",
            tree.children
                .single()
                .semantics
                ?.contentDescription,
        )
        assertEquals(
            "Button",
            tree.children
                .single()
                .semantics
                ?.role,
        )
        assertEquals(
            listOf("OnClick"),
            tree.children
                .single()
                .semantics
                ?.actions,
        )
        assertEquals(
            null,
            tree.children
                .single()
                .semantics
                ?.tag,
        )
    }

    @Test
    fun `extracts layout tree source hints when tooling identity matches runtime node`() {
        val child =
            FakeLayoutNode(
                semanticsId = 7,
                coordinates = FakeCoordinates(width = 40, height = 20, rootX = 10.0f, rootY = 6.0f),
                measurePolicy = FakeRowMeasurePolicy(),
                modifier = FakeModifier("padding"),
                semanticsConfiguration = FakeSemanticsConfiguration(),
            )
        val root =
            FakeLayoutNode(
                semanticsId = 1,
                coordinates = FakeCoordinates(width = 100, height = 50, rootX = 0.0f, rootY = 0.0f),
                measurePolicy = FakeColumnMeasurePolicy(),
                modifier = FakeModifier("fillMaxSize"),
                semanticsConfiguration = FakeSemanticsConfiguration(),
                children = listOf(child),
            )
        val sourceHints =
            mapOf(
                System.identityHashCode(child) to
                    AndroidComposeRendererInRobolectric.LayoutTreeSourceHint(
                        sourceName = "LoginButton",
                        sourceFile = "LoginPreview.kt",
                        sourceLine = 42,
                        sourceHintKind = "tooling-ancestor-node-identity",
                    ),
            )

        val tree = AndroidComposeRendererInRobolectric.extractLayoutTree(root, density = 2.0f, sourceHints = sourceHints)

        val enriched = tree.children.single()
        assertEquals("LoginButton", enriched.sourceName)
        assertEquals("LoginPreview.kt", enriched.sourceFile)
        assertEquals(42, enriched.sourceLine)
        assertEquals("tooling-ancestor-node-identity", enriched.sourceHintKind)
        assertTrue(enriched.componentHint.orEmpty().contains("FakeRowMeasurePolicy"))
    }

    @Test
    fun `accessible no arg reflection invokes public method on non-public implementation`() {
        val record = FakePackagePrivateRecord(setOf("composition-data"))

        val store = AndroidComposeRendererInRobolectric.accessibleNoArgMethod(record, "getStore")?.invoke(record)

        assertEquals(setOf("composition-data"), store)
    }

    @Test
    fun `correlates sibling source call group to following node group by preorder and bounds`() {
        val node = Any()
        val rootGroup =
            FakeToolingGroup(
                children =
                    listOf(
                        FakeToolingGroup(
                            name = "LoginCard",
                            location = FakeSourceLocation("LoginPreview.kt", 42),
                            box = FakeIntRect(16, 16, 304, 454),
                        ),
                        FakeToolingGroup(
                            node = node,
                            box = FakeIntRect(16, 16, 304, 454),
                        ),
                    ),
            )
        val hints = mutableMapOf<Int, AndroidComposeRendererInRobolectric.LayoutTreeSourceHint>()

        AndroidComposeRendererInRobolectric.collectGroupSourceHints(rootGroup, hints)

        val hint = hints.getValue(System.identityHashCode(node))
        assertEquals("LoginCard", hint.sourceName)
        assertEquals("LoginPreview.kt", hint.sourceFile)
        assertEquals(42, hint.sourceLine)
        assertEquals("tooling-sibling-preorder-app", hint.sourceHintKind)
    }

    @Test
    fun `does not correlate sibling source call group when bounds disagree`() {
        val node = Any()
        val rootGroup =
            FakeToolingGroup(
                children =
                    listOf(
                        FakeToolingGroup(
                            name = "UnrelatedHeader",
                            location = FakeSourceLocation("LoginPreview.kt", 12),
                            box = FakeIntRect(0, 0, 100, 40),
                        ),
                        FakeToolingGroup(
                            node = node,
                            box = FakeIntRect(16, 80, 304, 454),
                        ),
                    ),
            )
        val hints = mutableMapOf<Int, AndroidComposeRendererInRobolectric.LayoutTreeSourceHint>()

        AndroidComposeRendererInRobolectric.collectGroupSourceHints(rootGroup, hints)

        assertEquals(null, hints[System.identityHashCode(node)])
    }

    @Test
    fun `prefers app source ancestor over nearer compose runtime ancestor`() {
        val node = Any()
        val rootGroup =
            FakeToolingGroup(
                name = "LoginPreview",
                location = FakeSourceLocation("LoginPreview.kt", 18),
                box = FakeIntRect(0, 0, 393, 852),
                children =
                    listOf(
                        FakeToolingGroup(
                            name = "ReusableComposeNode",
                            location = FakeSourceLocation("Layout.kt", 83),
                            box = FakeIntRect(0, 0, 393, 852),
                            children =
                                listOf(
                                    FakeToolingGroup(
                                        node = node,
                                        box = FakeIntRect(0, 0, 393, 852),
                                    ),
                                ),
                        ),
                    ),
            )
        val hints = mutableMapOf<Int, AndroidComposeRendererInRobolectric.LayoutTreeSourceHint>()

        AndroidComposeRendererInRobolectric.collectGroupSourceHints(rootGroup, hints)

        val hint = hints.getValue(System.identityHashCode(node))
        assertEquals("LoginPreview", hint.sourceName)
        assertEquals("LoginPreview.kt", hint.sourceFile)
        assertEquals(18, hint.sourceLine)
        assertEquals("tooling-nearest-app-ancestor", hint.sourceHintKind)
    }

    @Test
    fun `prefers app sibling over nearer compose runtime ancestor when bounds contain node`() {
        val node = Any()
        val rootGroup =
            FakeToolingGroup(
                children =
                    listOf(
                        FakeToolingGroup(
                            name = "LoginCard",
                            location = FakeSourceLocation("LoginPreview.kt", 42),
                            box = FakeIntRect(16, 16, 304, 454),
                        ),
                        FakeToolingGroup(
                            name = "ReusableComposeNode",
                            location = FakeSourceLocation("Layout.kt", 85),
                            box = FakeIntRect(16, 16, 304, 454),
                            children =
                                listOf(
                                    FakeToolingGroup(
                                        node = node,
                                        box = FakeIntRect(16, 16, 304, 454),
                                    ),
                                ),
                        ),
                    ),
            )
        val hints = mutableMapOf<Int, AndroidComposeRendererInRobolectric.LayoutTreeSourceHint>()

        AndroidComposeRendererInRobolectric.collectGroupSourceHints(rootGroup, hints)

        val hint = hints.getValue(System.identityHashCode(node))
        assertEquals("LoginCard", hint.sourceName)
        assertEquals("LoginPreview.kt", hint.sourceFile)
        assertEquals(42, hint.sourceLine)
        assertEquals("tooling-sibling-preorder-app", hint.sourceHintKind)
    }

    @Test
    fun `preview source fallback can enrich root layout node when tooling hints are absent`() {
        val outputFile = tempDir.resolve("layout-tree.json")
        val root =
            FakeLayoutNode(
                semanticsId = 1,
                coordinates = FakeCoordinates(width = 100, height = 50, rootX = 0.0f, rootY = 0.0f),
                measurePolicy = FakeColumnMeasurePolicy(),
                modifier = FakeModifier("fillMaxSize"),
                semanticsConfiguration = FakeSemanticsConfiguration(),
            )

        AndroidComposeRendererInRobolectric.writeLayoutTreeFromComposeView(
            ComposeRoot(root),
            outputFile,
            density = 2.0f,
            previewSourceFallback =
                AndroidComposeRendererInRobolectric.PreviewSourceFallback(
                    className = "dev.example.LoginPreviewKt",
                    methodName = "LoginPreview",
                ),
        )

        val tree = outputFile.readText()
        assertTrue(tree.contains("\"sourceName\":\"LoginPreview\""), tree)
        assertTrue(tree.contains("\"sourceFile\":\"LoginPreview.kt\""), tree)
        assertTrue(!tree.contains("\"sourceLine\""), tree)
        assertTrue(tree.contains("\"sourceHintKind\":\"preview-entrypoint-fallback\""), tree)
    }

    @Test
    fun `missing compose root writes empty layout tree sidecar and warns`() {
        val outputFile = tempDir.resolve("layout-tree.json")

        val warning =
            captureStderr {
                AndroidComposeRendererInRobolectric::class.java
                    .getDeclaredMethod(
                        "writeLayoutTree",
                        Any::class.java,
                        File::class.java,
                        Float::class.javaPrimitiveType,
                        AndroidComposeRendererInRobolectric.ToolingCompositionRecord::class.java,
                        AndroidComposeRendererInRobolectric.PreviewSourceFallback::class.java,
                    ).apply { isAccessible = true }
                    .invoke(AndroidComposeRendererInRobolectric, ViewWithoutComposeRoot(), outputFile, 2.0f, null, null)
            }

        assertEquals("[]", outputFile.readText())
        assertTrue(warning.contains("failed to extract Compose layout tree"), warning)
        assertTrue(warning.contains("wrote an empty layout tree sidecar"), warning)
    }

    @Test
    fun `layout tree reflection failure writes empty sidecar and warns without throwing`() {
        val outputFile = tempDir.resolve("layout-tree.json")

        val warning =
            captureStderr {
                AndroidComposeRendererInRobolectric.writeLayoutTreeFromComposeView(
                    ComposeRootWithBrokenLayoutNode(),
                    outputFile,
                    density = 2.0f,
                )
            }

        assertEquals("[]", outputFile.readText())
        assertTrue(warning.contains("failed to extract Compose layout tree"), warning)
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

    private fun captureStderr(block: () -> Unit): String {
        val originalErr = System.err
        val bytes = ByteArrayOutputStream()
        System.setErr(PrintStream(bytes))
        try {
            block()
        } finally {
            System.setErr(originalErr)
        }
        return bytes.toString()
    }

    private class ViewWithoutComposeRoot

    private class ComposeRoot(
        private val root: Any,
    ) {
        fun getRoot(): Any = root
    }

    private class ComposeRootWithBrokenLayoutNode {
        fun getRoot(): Any = error("boom")
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

    private class FakeLayoutNode(
        private val semanticsId: Int,
        private val coordinates: FakeCoordinates,
        private val measurePolicy: Any,
        private val modifier: FakeModifier,
        private val semanticsConfiguration: FakeSemanticsConfiguration,
        private val children: List<FakeLayoutNode> = emptyList(),
    ) {
        fun getSemanticsId(): Int = semanticsId

        fun getCoordinates(): FakeCoordinates = coordinates

        fun getMeasurePolicy(): Any = measurePolicy

        fun getModifier(): FakeModifier = modifier

        fun getSemanticsConfiguration(): FakeSemanticsConfiguration = semanticsConfiguration

        fun getZSortedChildren(): List<FakeLayoutNode> = children
    }

    private class FakeColumnMeasurePolicy

    private class FakeRowMeasurePolicy

    private class FakeModifier(
        private val label: String,
    ) {
        override fun toString(): String = label
    }

    private class FakeCoordinates(
        private val width: Int,
        private val height: Int,
        private val rootX: Float,
        private val rootY: Float,
    ) {
        fun getWidth(): Int = width

        fun getHeight(): Int = height

        fun `localToRoot-abc123`(offset: Long): Long {
            require(offset == 0L)
            return (rootX.toRawBits().toLong() shl 32) or (rootY.toRawBits().toLong() and 0xffffffffL)
        }
    }

    private class FakeSemanticsConfiguration(
        vararg entries: Pair<String, Any?>,
    ) : Iterable<Map.Entry<FakeSemanticsKey, Any?>> {
        private val values = entries.associate { (name, value) -> FakeSemanticsKey(name) to value }

        override fun iterator(): Iterator<Map.Entry<FakeSemanticsKey, Any?>> = values.entries.iterator()
    }

    private data class FakeSemanticsKey(
        private val name: String,
    ) {
        fun getName(): String = name
    }

    private class FakePackagePrivateRecord(
        private val store: Set<String>,
    ) {
        fun getStore(): Set<String> = store
    }

    private class FakeToolingGroup(
        private val name: String? = null,
        private val location: FakeSourceLocation? = null,
        private val node: Any? = null,
        private val box: FakeIntRect? = null,
        private val children: List<FakeToolingGroup> = emptyList(),
    ) {
        fun getName(): String? = name

        fun getLocation(): FakeSourceLocation? = location

        fun getNode(): Any? = node

        fun getBox(): FakeIntRect? = box

        fun getChildren(): List<FakeToolingGroup> = children
    }

    private class FakeSourceLocation(
        private val sourceFile: String,
        private val lineNumber: Int,
    ) {
        fun getSourceFile(): String = sourceFile

        fun getLineNumber(): Int = lineNumber
    }

    private class FakeIntRect(
        private val left: Int,
        private val top: Int,
        private val right: Int,
        private val bottom: Int,
    ) {
        fun getLeft(): Int = left

        fun getTop(): Int = top

        fun getRight(): Int = right

        fun getBottom(): Int = bottom
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
