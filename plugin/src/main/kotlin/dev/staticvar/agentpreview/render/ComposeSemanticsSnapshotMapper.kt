/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.model.Bounds
import dev.staticvar.agentpreview.model.SnapshotNode
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

internal class ComposeSemanticsSnapshotMapper {
    fun write(
        contentRoot: Any,
        outputFile: File,
        includeUnmergedSemantics: Boolean,
    ) {
        val composeView =
            findComposeView(contentRoot) ?: run {
                outputFile.writeText("[]")
                return
            }
        val semanticsOwner = ComposeReflection.requiredNoArgValue<Any>(composeView, "getSemanticsOwner")
        val nodes = listOf(toSnapshotNode(rootSemanticsNode(semanticsOwner, includeUnmergedSemantics), includeUnmergedSemantics))
        outputFile.writeText(Json.encodeToString(ListSerializer(SnapshotNode.serializer()), nodes))
    }

    fun rootSemanticsNode(
        semanticsOwner: Any,
        includeUnmergedSemantics: Boolean,
    ): Any {
        val methodName =
            if (includeUnmergedSemantics) {
                "getUnmergedRootSemanticsNode"
            } else {
                "getRootSemanticsNode"
            }
        return ComposeReflection.requiredNoArgValue<Any>(semanticsOwner, methodName)
    }

    fun semanticsChildren(
        node: Any,
        includeUnmergedSemantics: Boolean,
    ): List<Any> {
        val method =
            ComposeReflection.optionalMethodMatching(node.javaClass, "getChildren") { method ->
                method.parameterTypes.size in 0..3
            } ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return when (method.parameterTypes.size) {
            3 -> method.invoke(node, includeUnmergedSemantics, true, false)
            2 -> method.invoke(node, includeUnmergedSemantics, true)
            1 -> method.invoke(node, includeUnmergedSemantics)
            else -> method.invoke(node)
        } as List<Any>
    }

    private fun findComposeView(view: Any): Any? {
        if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
        val childCountMethod = ComposeReflection.optionalNoArgMethod(view, "getChildCount") ?: return null
        val getChildAtMethod = ComposeReflection.optionalMethod(view.javaClass, "getChildAt", Int::class.javaPrimitiveType) ?: return null
        val childCount = childCountMethod.invoke(view) as Int
        for (index in 0 until childCount) {
            val match = findComposeView(getChildAtMethod.invoke(view, index))
            if (match != null) return match
        }
        return null
    }

    private fun toSnapshotNode(
        node: Any,
        includeUnmergedSemantics: Boolean,
    ): SnapshotNode {
        val id = ComposeReflection.requiredNoArgValue<Any>(node, "getId").toString()
        val config = ComposeReflection.requiredNoArgValue<Any>(node, "getConfig")
        val properties = semanticsProperties(config)
        val children =
            semanticsChildren(node, includeUnmergedSemantics)
                .map { child -> toSnapshotNode(child, includeUnmergedSemantics) }
        return SnapshotNode(
            id = id,
            role = properties["Role"]?.toString(),
            text = properties["Text"]?.toSnapshotText(),
            contentDescription = properties["ContentDescription"]?.toSnapshotText(),
            bounds = bounds(node),
            actions = properties.filterKeys { it.startsWith("On") }.keys.sorted(),
            tag = properties["TestTag"]?.toString(),
            children = children,
        )
    }

    private fun semanticsProperties(config: Any?): Map<String, Any?> {
        val nonNullConfig = config ?: return emptyMap()

        @Suppress("UNCHECKED_CAST")
        val iterator =
            ComposeReflection.optionalNoArgMethod(nonNullConfig, "iterator")?.invoke(nonNullConfig) as? Iterator<Map.Entry<Any, Any?>>
                ?: return emptyMap()
        return buildMap {
            iterator.forEach { entry ->
                val key = entry.key
                val name = ComposeReflection.optionalNoArgMethod(key, "getName")?.invoke(key)?.toString() ?: key.toString()
                put(name, entry.value)
            }
        }
    }

    private fun bounds(node: Any): Bounds {
        val rect = ComposeReflection.requiredNoArgMethod(node, "getBoundsInRoot").invoke(node)
        val left = ComposeReflection.requiredNoArgValue<Float>(rect, "getLeft")
        val top = ComposeReflection.requiredNoArgValue<Float>(rect, "getTop")
        val right = ComposeReflection.requiredNoArgValue<Float>(rect, "getRight")
        val bottom = ComposeReflection.requiredNoArgValue<Float>(rect, "getBottom")
        return Bounds(
            left.roundToInt(),
            top.roundToInt(),
            (right - left).roundToInt().coerceAtLeast(0),
            (bottom - top).roundToInt().coerceAtLeast(0),
        )
    }

    private fun Any.toSnapshotText(): String = if (this is Iterable<*>) joinToString(" ") { it.toString() } else toString()
}
