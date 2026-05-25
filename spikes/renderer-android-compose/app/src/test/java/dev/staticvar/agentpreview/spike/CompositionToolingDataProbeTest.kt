/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.staticvar.agentpreview.spike

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
@OptIn(UiToolingDataApi::class)
class CompositionToolingDataProbeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dumpsCompositionToolingGroupsWithSourceNamesAndLayoutNodeCorrelation() {
        val record = CompositionDataRecord.create()
        composeRule.setContent {
            Inspectable(record) {
                MaterialTheme {
                    SourceNamesProbeScreen()
                }
            }
        }
        composeRule.waitForIdle()

        val composeView = findComposeView(composeRule.activity.window.decorView.rootView) ?: error("No AndroidComposeView")
        val rootLayoutNode = composeView.javaClass.methods.single { it.name == "getRoot" && it.parameterTypes.isEmpty() }.invoke(composeView)
        val layoutIdentities = layoutNodes(rootLayoutNode).map { System.identityHashCode(it) }.toSet()
        val groups = record.store.map { it.asTree().toProbeGroup(layoutIdentities) }
        println(prettyJson.encodeToString(buildJsonArray { groups.forEach { add(it.toJsonElement()) } }))

        val flat = groups.flatMap { it.flatten() }
        assertTrue(flat.any { it.name == "SourceNamesProbeScreen" })
        assertTrue(flat.any { it.name == "Column" })
        assertTrue(flat.any { it.name == "Row" })
        assertTrue(flat.any { it.name == "Button" })
        assertTrue(flat.any { it.sourceFile?.endsWith("CompositionToolingDataProbeTest.kt") == true && it.lineNumber > 0 })
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
private fun SourceNamesProbeScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SourceNamesProbeCard()
        Box(Modifier.size(24.dp).background(Color.Red))
    }
}

@Composable
private fun SourceNamesProbeCard() {
    Card(Modifier.padding(8.dp)) {
        Row(Modifier.padding(12.dp)) {
            Text("Tooling Welcome")
            Spacer(Modifier.size(8.dp))
            Button(onClick = {}) { Text("Continue") }
        }
    }
}

private data class ToolingProbeGroup(
    val name: String?,
    val sourceFile: String?,
    val lineNumber: Int,
    val box: String,
    val identity: String?,
    val layoutNodeIdentity: Int?,
    val nodeClass: String?,
    val children: List<ToolingProbeGroup>,
) {
    fun flatten(): List<ToolingProbeGroup> = listOf(this) + children.flatMap { it.flatten() }

    fun toJsonElement(): JsonObject = buildJsonObject {
        put("name", name)
        put("sourceFile", sourceFile)
        put("lineNumber", lineNumber)
        put("box", box)
        put("identity", identity)
        put("layoutNodeIdentity", layoutNodeIdentity)
        put("nodeClass", nodeClass)
        put("children", buildJsonArray { children.forEach { add(it.toJsonElement()) } })
    }
}

@OptIn(UiToolingDataApi::class)
private fun Group.toProbeGroup(layoutIdentities: Set<Int>): ToolingProbeGroup {
    val node = (this as? NodeGroup)?.node
    val nodeIdentity = node?.let(System::identityHashCode)
    return ToolingProbeGroup(
        name = name,
        sourceFile = location?.sourceFile,
        lineNumber = location?.lineNumber ?: -1,
        box = box.toString(),
        identity = identity?.let { "${it.javaClass.name}@${System.identityHashCode(it)}" },
        layoutNodeIdentity = nodeIdentity?.takeIf { it in layoutIdentities },
        nodeClass = node?.javaClass?.name,
        children = children.map { it.toProbeGroup(layoutIdentities) },
    )
}

private val prettyJson = Json { prettyPrint = true }
