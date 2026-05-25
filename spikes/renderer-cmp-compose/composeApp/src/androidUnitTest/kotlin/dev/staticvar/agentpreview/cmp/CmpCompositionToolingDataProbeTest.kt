/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.staticvar.agentpreview.cmp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.tooling.CompositionDataRecord
import androidx.compose.ui.tooling.Inspectable
import androidx.compose.ui.tooling.data.Group
import androidx.compose.ui.tooling.data.NodeGroup
import androidx.compose.ui.tooling.data.UiToolingDataApi
import androidx.compose.ui.tooling.data.asTree
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
@OptIn(UiToolingDataApi::class)
class CmpCompositionToolingDataProbeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dumpsCmpAndroidTargetCompositionToolingGroups() {
        val record = CompositionDataRecord.create()
        composeRule.setContent {
            Inspectable(record) {
                MaterialTheme {
                    CmpSourceNamesProbeScreen()
                }
            }
        }
        composeRule.waitForIdle()

        val composeView = findComposeView(composeRule.activity.window.decorView.rootView) ?: error("No AndroidComposeView")
        val rootLayoutNode = composeView.javaClass.methods.single { it.name == "getRoot" && it.parameterTypes.isEmpty() }.invoke(composeView)
        val layoutIdentities = layoutNodes(rootLayoutNode).map { System.identityHashCode(it) }.toSet()
        val groups = record.store.map { it.asTree().toCmpProbeGroup(layoutIdentities) }
        val json = groups.joinToString(prefix = "[\n", separator = ",\n", postfix = "\n]") { it.toJson("  ") }
        println(json)

        val flat = groups.flatMap { it.flatten() }
        assertTrue(flat.any { it.name == "CmpSourceNamesProbeScreen" })
        assertTrue(flat.any { it.name == "Column" })
        assertTrue(flat.any { it.name == "Row" })
        assertTrue(flat.any { it.name == "Button" })
        assertTrue(flat.any { it.sourceFile?.endsWith("CmpCompositionToolingDataProbeTest.kt") == true && it.lineNumber > 0 })
        assertTrue(flat.any { it.layoutNodeIdentity != null && it.layoutNodeIdentity in layoutIdentities })
    }

    private fun findComposeView(view: Any): Any? {
        if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
        val count = view.javaClass.methods.firstOrNull { it.name == "getChildCount" && it.parameterTypes.isEmpty() }?.invoke(view) as? Int ?: return null
        val childAt = view.javaClass.methods.first { it.name == "getChildAt" && it.parameterTypes.size == 1 }
        for (index in 0 until count) findComposeView(childAt.invoke(view, index))?.let { return it }
        return null
    }

    private fun layoutNodes(root: Any): List<Any> {
        fun children(node: Any): List<Any> {
            val vector = node.javaClass.methods.firstOrNull { it.name == "getZSortedChildren" && it.parameterTypes.isEmpty() }?.invoke(node)
                ?: node.javaClass.methods.firstOrNull { it.name == "get_children\$ui" && it.parameterTypes.isEmpty() }?.invoke(node)
                ?: return emptyList()
            if (vector is Iterable<*>) return vector.filterNotNull()
            val asList = vector.javaClass.methods.firstOrNull { it.name == "asMutableList" && it.parameterTypes.isEmpty() }?.invoke(vector)
            if (asList is List<*>) return asList.filterNotNull()
            val size = vector.javaClass.methods.firstOrNull { it.name == "getSize" && it.parameterTypes.isEmpty() }?.invoke(vector) as? Int ?: return emptyList()
            val content = vector.javaClass.methods.firstOrNull { it.name == "getContent" && it.parameterTypes.isEmpty() }?.invoke(vector) as? Array<*> ?: return emptyList()
            return content.take(size).filterNotNull()
        }
        return listOf(root) + children(root).flatMap(::layoutNodes)
    }
}

@Composable
private fun CmpSourceNamesProbeScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        CmpSourceNamesProbeCard()
        Box(Modifier.size(24.dp).background(Color.Red))
    }
}

@Composable
private fun CmpSourceNamesProbeCard() {
    Card(Modifier.padding(8.dp)) {
        Row(Modifier.padding(12.dp)) {
            Text("CMP Tooling Welcome")
            Spacer(Modifier.size(8.dp))
            Button(onClick = {}) { Text("Continue") }
        }
    }
}

private data class CmpToolingProbeGroup(
    val name: String?,
    val sourceFile: String?,
    val lineNumber: Int,
    val box: String,
    val identity: String?,
    val layoutNodeIdentity: Int?,
    val nodeClass: String?,
    val children: List<CmpToolingProbeGroup>,
) {
    fun flatten(): List<CmpToolingProbeGroup> = listOf(this) + children.flatMap { it.flatten() }

    fun toJson(indent: String = ""): String = buildString {
        append(indent).append("{\n")
        append(indent).append("  \"name\": ").append(name.json()).append(",\n")
        append(indent).append("  \"sourceFile\": ").append(sourceFile.json()).append(",\n")
        append(indent).append("  \"lineNumber\": ").append(lineNumber).append(",\n")
        append(indent).append("  \"box\": ").append(box.json()).append(",\n")
        append(indent).append("  \"identity\": ").append(identity.json()).append(",\n")
        append(indent).append("  \"layoutNodeIdentity\": ").append(layoutNodeIdentity?.toString() ?: "null").append(",\n")
        append(indent).append("  \"nodeClass\": ").append(nodeClass.json()).append(",\n")
        append(indent).append("  \"children\": [")
        if (children.isNotEmpty()) append('\n').append(children.joinToString(",\n") { it.toJson("$indent    ") }).append('\n').append(indent).append("  ")
        append("]\n")
        append(indent).append("}")
    }
}

@OptIn(UiToolingDataApi::class)
private fun Group.toCmpProbeGroup(layoutIdentities: Set<Int>): CmpToolingProbeGroup {
    val node = (this as? NodeGroup)?.node
    val nodeIdentity = node?.let(System::identityHashCode)
    return CmpToolingProbeGroup(
        name = name,
        sourceFile = location?.sourceFile,
        lineNumber = location?.lineNumber ?: -1,
        box = box.toString(),
        identity = identity?.let { "${it.javaClass.name}@${System.identityHashCode(it)}" },
        layoutNodeIdentity = nodeIdentity?.takeIf { it in layoutIdentities },
        nodeClass = node?.javaClass?.name,
        children = children.map { it.toCmpProbeGroup(layoutIdentities) },
    )
}

private fun String?.json(): String = this?.replace("\\", "\\\\")?.replace("\"", "\\\"")?.let { "\"$it\"" } ?: "null"
