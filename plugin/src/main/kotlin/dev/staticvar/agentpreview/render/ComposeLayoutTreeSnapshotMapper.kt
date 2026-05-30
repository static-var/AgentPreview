/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.model.Bounds
import dev.staticvar.agentpreview.model.DpBounds
import dev.staticvar.agentpreview.model.SnapshotLayoutNode
import dev.staticvar.agentpreview.model.SnapshotLayoutSemanticsSummary
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

internal class ComposeLayoutTreeSnapshotMapper {
    fun write(
        contentRoot: Any,
        outputFile: File,
        density: Float,
        toolingRecord: AndroidComposeRendererInRobolectric.ToolingCompositionRecord?,
        previewSourceFallback: PreviewSourceFallback?,
    ) {
        runCatching {
            val composeView = checkNotNull(findComposeView(contentRoot)) { "Compose root view was not found." }
            writeFromComposeView(composeView, outputFile, density, toolingRecord, previewSourceFallback)
        }.getOrElse { throwable -> writeEmptyLayoutTreeWarning(outputFile, throwable) }
    }

    fun writeFromComposeView(
        composeView: Any,
        outputFile: File,
        density: Float,
        toolingRecord: AndroidComposeRendererInRobolectric.ToolingCompositionRecord? = null,
        previewSourceFallback: PreviewSourceFallback? = null,
    ) {
        runCatching {
            val getRoot =
                ComposeReflection.optionalNoArgMethod(composeView, "getRoot")
                    ?: error("Compose root view ${composeView.javaClass.name} does not expose getRoot().")
            val rootLayoutNode = getRoot.invoke(composeView)
            val fallbackSourceHint = previewSourceFallback?.toLayoutTreeSourceHint()
            val sourceHints = toolingRecord?.sourceHintsOrEmpty(preferredAppSourceFile = fallbackSourceHint?.sourceFile).orEmpty()
            val fallbackHints =
                if (sourceHints.isEmpty() && fallbackSourceHint != null) {
                    mapOf(System.identityHashCode(rootLayoutNode) to fallbackSourceHint)
                } else {
                    emptyMap()
                }
            val nodes = listOf(extract(rootLayoutNode, density, sourceHints + fallbackHints))
            outputFile.writeText(Json.encodeToString(ListSerializer(SnapshotLayoutNode.serializer()), nodes))
        }.getOrElse { throwable -> writeEmptyLayoutTreeWarning(outputFile, throwable) }
    }

    fun extract(
        rootLayoutNode: Any,
        density: Float,
        sourceHints: Map<Int, LayoutTreeSourceHint> = emptyMap(),
    ): SnapshotLayoutNode = toSnapshotLayoutNode(rootLayoutNode, density, nextLayoutId(), sourceHints)

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

    private fun toSnapshotLayoutNode(
        node: Any,
        density: Float,
        ids: Iterator<String>,
        sourceHints: Map<Int, LayoutTreeSourceHint>,
    ): SnapshotLayoutNode {
        val id = ids.next()
        val boundsPx = layoutBounds(node)
        val semantics = semanticsProperties(ComposeReflection.optionalNoArgValue<Any>(node, "getSemanticsConfiguration"))
        val children = layoutChildren(node).map { child -> toSnapshotLayoutNode(child, density, ids, sourceHints) }
        val measurePolicy = ComposeReflection.optionalNoArgValue<Any>(node, "getMeasurePolicy")
        val modifier = ComposeReflection.optionalNoArgValue<Any>(node, "getModifier")
        val sourceHint = sourceHints[System.identityHashCode(node)]
        return SnapshotLayoutNode(
            id = id,
            boundsPx = boundsPx,
            boundsDp = boundsPx.toDpBounds(density),
            componentHint = measurePolicy?.javaClass?.name ?: node.javaClass.name,
            sourceName = sourceHint?.sourceName,
            sourceFile = sourceHint?.sourceFile,
            sourceLine = sourceHint?.sourceLine,
            sourceHintKind = sourceHint?.sourceHintKind,
            modifierHint = modifier?.javaClass?.name ?: modifier?.toString(),
            classHint = node.javaClass.name,
            semanticsId = ComposeReflection.optionalNoArgValue<Any>(node, "getSemanticsId")?.toString(),
            semantics = semantics.toLayoutSummary(),
            children = children,
        )
    }

    private fun layoutChildren(node: Any): List<Any> {
        val vector =
            ComposeReflection.optionalNoArgMethod(node, "getZSortedChildren")?.invoke(node)
                ?: ComposeReflection.optionalNoArgMethod(node, "get_children\$ui")?.invoke(node)
                ?: return emptyList()
        if (vector is Iterable<*>) return vector.filterNotNull()
        val asList = ComposeReflection.optionalNoArgMethod(vector, "asMutableList")?.invoke(vector)
        if (asList is List<*>) return asList.filterNotNull()
        val size = ComposeReflection.optionalNoArgMethod(vector, "getSize")?.invoke(vector) as? Int ?: return emptyList()
        val content = ComposeReflection.optionalNoArgMethod(vector, "getContent")?.invoke(vector) as? Array<*> ?: return emptyList()
        return content.take(size).filterNotNull()
    }

    private fun layoutBounds(node: Any): Bounds {
        val coordinates = ComposeReflection.optionalNoArgValue<Any>(node, "getCoordinates") ?: return Bounds(0, 0, 0, 0)
        val width = ComposeReflection.optionalNoArgValue<Int>(coordinates, "getWidth") ?: 0
        val height = ComposeReflection.optionalNoArgValue<Int>(coordinates, "getHeight") ?: 0
        val packedOffset =
            coordinates.javaClass.methods
                .firstOrNull { method ->
                    method.name.startsWith("localToRoot-") && method.parameterTypes.contentEquals(arrayOf(Long::class.javaPrimitiveType))
                }?.apply { isAccessible = true }
                ?.invoke(coordinates, 0L) as? Long ?: 0L
        return Bounds(
            x = packedOffset.unpackFloat1().roundToInt(),
            y = packedOffset.unpackFloat2().roundToInt(),
            width = width.coerceAtLeast(0),
            height = height.coerceAtLeast(0),
        )
    }

    private fun semanticsProperties(config: Any?): Map<String, Any?> {
        val nonNullConfig = config ?: return emptyMap()

        @Suppress("UNCHECKED_CAST")
        val iterator =
            ComposeReflection.optionalNoArgMethod(nonNullConfig, "iterator")?.invoke(nonNullConfig)
                as? Iterator<Map.Entry<Any, Any?>>
                ?: return emptyMap()
        return buildMap {
            iterator.forEach { entry ->
                val key = entry.key
                val name = ComposeReflection.optionalNoArgMethod(key, "getName")?.invoke(key)?.toString() ?: key.toString()
                put(name, entry.value)
            }
        }
    }

    private fun Bounds.toDpBounds(density: Float): DpBounds =
        if (density <= 0f) {
            DpBounds(0.0f, 0.0f, 0.0f, 0.0f)
        } else {
            DpBounds(x / density, y / density, width / density, height / density)
        }

    private fun Map<String, Any?>.toLayoutSummary(): SnapshotLayoutSemanticsSummary? {
        if (isEmpty()) return null
        val actions = filterKeys { it.startsWith("On") }.keys.sorted()
        val summary =
            SnapshotLayoutSemanticsSummary(
                text = get("Text")?.toSnapshotText(),
                contentDescription = get("ContentDescription")?.toSnapshotText(),
                role = get("Role")?.toString(),
                actions = actions,
                tag = get("TestTag")?.toString(),
            )
        return summary.takeIf {
            it.text != null || it.contentDescription != null || it.role != null || it.actions.isNotEmpty() || it.tag != null
        }
    }

    private fun Any.toSnapshotText(): String =
        when (this) {
            is Iterable<*> -> joinToString(" ") { item -> item.toString() }
            else -> toString()
        }

    private fun Long.unpackFloat1(): Float = Float.fromBits((this shr 32).toInt())

    private fun Long.unpackFloat2(): Float = Float.fromBits((this and 0xffffffffL).toInt())

    private fun nextLayoutId(): Iterator<String> = generateSequence(1) { it + 1 }.map { id -> "layout-$id" }.iterator()

    private fun writeEmptyLayoutTreeWarning(
        outputFile: File,
        throwable: Throwable,
    ) {
        outputFile.writeText("[]")
        System.err.println(
            "AgentPreview: failed to extract Compose layout tree for ${outputFile.absolutePath}; " +
                "wrote an empty layout tree sidecar. Screenshot and semantics output are preserved. " +
                "Cause: ${throwable.javaClass.name}: ${throwable.message}",
        )
    }
}

internal data class LayoutTreeSourceHint(
    val sourceName: String? = null,
    val sourceFile: String? = null,
    val sourceLine: Int? = null,
    val sourceHintKind: String? = null,
)

internal data class PreviewSourceFallback(
    val className: String,
    val methodName: String,
) {
    fun toLayoutTreeSourceHint(): LayoutTreeSourceHint =
        LayoutTreeSourceHint(
            sourceName = methodName,
            sourceFile = "${className.substringAfterLast('$').substringAfterLast('.').removeSuffix("Kt")}.kt",
            sourceLine = null,
            sourceHintKind = "preview-entrypoint-fallback",
        )
}
