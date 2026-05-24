/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CmpLayoutTreeReflectionProbeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun extractsAndroidTargetLayoutTreeWithoutTestTags() {
        var runtimeDensity = 0f
        composeRule.setContent {
            runtimeDensity = LocalDensity.current.density
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Card(Modifier.padding(8.dp)) {
                        Row(Modifier.padding(12.dp)) {
                            Text("CMP Welcome")
                            Spacer(Modifier.size(8.dp))
                            Button(onClick = {}) { Text("Continue") }
                        }
                    }
                    Box(Modifier.size(24.dp).background(Color.Red))
                }
            }
        }
        composeRule.waitForIdle()

        val composeView = findComposeView(composeRule.activity.window.decorView.rootView) ?: error("No AndroidComposeView")
        val rootLayoutNode = composeView.javaClass.methods.single { it.name == "getRoot" && it.parameterTypes.isEmpty() }.invoke(composeView)
        val tree = CmpLayoutNodeProbe(runtimeDensity).extract(rootLayoutNode)
        val json = tree.toJson()
        println(json)

        assertTrue(json.contains("ColumnMeasurePolicy"))
        assertTrue(json.contains("RowMeasurePolicy"))
        assertTrue(json.contains("CMP Welcome"))
        assertTrue(json.contains("Continue"))
        assertTrue(tree.flatten().any { it.boundsPx.width > 0 && it.boundsDp.width > 0 })
    }

    private fun findComposeView(view: Any): Any? {
        if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
        val count = view.javaClass.methods.firstOrNull { it.name == "getChildCount" && it.parameterTypes.isEmpty() }?.invoke(view) as? Int ?: return null
        val childAt = view.javaClass.methods.first { it.name == "getChildAt" && it.parameterTypes.size == 1 }
        for (index in 0 until count) findComposeView(childAt.invoke(view, index))?.let { return it }
        return null
    }
}

private class CmpLayoutNodeProbe(
    private val density: Float,
) {
    fun extract(node: Any): CmpProbeNode {
        val coordinates = method(node, "getCoordinates")?.invoke(node)
        val boundsPx = boundsPx(coordinates)
        val semantics = semanticsProperties(method(node, "getSemanticsConfiguration")?.invoke(node))
        val measurePolicy = method(node, "getMeasurePolicy")?.invoke(node)
        val modifier = method(node, "getModifier")?.invoke(node)
        return CmpProbeNode(
            semanticsId = method(node, "getSemanticsId")?.invoke(node)?.toString(),
            componentHint = measurePolicy?.javaClass?.name ?: node.javaClass.name,
            modifierHint = modifier?.javaClass?.name,
            text = semantics["Text"]?.toString(),
            role = semantics["Role"]?.toString(),
            boundsPx = boundsPx,
            boundsDp = boundsPx.toDp(density),
            children = children(node).map(::extract),
        )
    }

    private fun children(node: Any): List<Any> {
        val vector = method(node, "getZSortedChildren")?.invoke(node) ?: method(node, "get_children\$ui")?.invoke(node) ?: return emptyList()
        if (vector is Iterable<*>) return vector.filterNotNull()
        val asList = method(vector, "asMutableList")?.invoke(vector)
        if (asList is List<*>) return asList.filterNotNull()
        val size = method(vector, "getSize")?.invoke(vector) as? Int ?: return emptyList()
        val content = method(vector, "getContent")?.invoke(vector) as? Array<*> ?: return emptyList()
        return content.take(size).filterNotNull()
    }

    private fun boundsPx(coordinates: Any?): CmpProbeBounds {
        if (coordinates == null) return CmpProbeBounds.Zero
        val width = method(coordinates, "getWidth")?.invoke(coordinates) as? Int ?: 0
        val height = method(coordinates, "getHeight")?.invoke(coordinates) as? Int ?: 0
        val packedOffset = coordinates.javaClass.methods.firstOrNull { it.name.startsWith("localToRoot-") && it.parameterTypes.contentEquals(arrayOf(Long::class.javaPrimitiveType)) }?.invoke(coordinates, 0L) as? Long ?: 0L
        return CmpProbeBounds(packedOffset.unpackFloat1().roundToInt(), packedOffset.unpackFloat2().roundToInt(), width, height)
    }

    private fun semanticsProperties(config: Any?): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val iterator = method(config ?: return emptyMap(), "iterator")?.invoke(config) as? Iterator<Map.Entry<Any, Any?>> ?: return emptyMap()
        return buildMap {
            iterator.forEach { entry -> put(method(entry.key, "getName")?.invoke(entry.key)?.toString() ?: entry.key.toString(), entry.value) }
        }
    }

    private fun method(target: Any, name: String) = target.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
}

private data class CmpProbeNode(
    val semanticsId: String?,
    val componentHint: String,
    val modifierHint: String?,
    val text: String?,
    val role: String?,
    val boundsPx: CmpProbeBounds,
    val boundsDp: CmpProbeBounds,
    val children: List<CmpProbeNode>,
) {
    fun flatten(): List<CmpProbeNode> = listOf(this) + children.flatMap { it.flatten() }

    fun toJson(indent: String = ""): String = buildString {
        append(indent).append("{\n")
        append(indent).append("  \"semanticsId\": ").append(semanticsId.json()).append(",\n")
        append(indent).append("  \"componentHint\": ").append(componentHint.json()).append(",\n")
        append(indent).append("  \"modifierHint\": ").append(modifierHint.json()).append(",\n")
        append(indent).append("  \"text\": ").append(text.json()).append(",\n")
        append(indent).append("  \"role\": ").append(role.json()).append(",\n")
        append(indent).append("  \"boundsPx\": ").append(boundsPx.toJson()).append(",\n")
        append(indent).append("  \"boundsDp\": ").append(boundsDp.toJson()).append(",\n")
        append(indent).append("  \"children\": [")
        if (children.isNotEmpty()) append('\n').append(children.joinToString(",\n") { it.toJson("$indent    ") }).append('\n').append(indent).append("  ")
        append("]\n")
        append(indent).append("}")
    }
}

private data class CmpProbeBounds(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun toDp(density: Float): CmpProbeBounds = if (density <= 0f) Zero else CmpProbeBounds((x / density).roundToInt(), (y / density).roundToInt(), (width / density).roundToInt(), (height / density).roundToInt())

    fun toJson(): String = "{\"x\":$x,\"y\":$y,\"width\":$width,\"height\":$height}"

    companion object {
        val Zero = CmpProbeBounds(0, 0, 0, 0)
    }
}

private fun Long.unpackFloat1(): Float = Float.fromBits((this shr 32).toInt())

private fun Long.unpackFloat2(): Float = Float.fromBits((this and 0xffffffffL).toInt())

private fun String?.json(): String = this?.replace("\\", "\\\\")?.replace("\"", "\\\"")?.let { "\"$it\"" } ?: "null"
