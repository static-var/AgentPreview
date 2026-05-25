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
import dev.staticvar.agentpreview.model.SnapshotNode
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.robolectric.Robolectric
import java.io.File
import java.io.OutputStream
import java.util.Locale
import kotlin.math.roundToInt

object AndroidComposeRendererInRobolectric {
    fun render(
        className: String,
        methodName: String,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        outputFile: File,
        semanticsOutputFile: File,
        layoutTreeOutputFile: File,
        includeUnmergedSemantics: Boolean = false,
        locale: String? = null,
        uiMode: Int? = null,
        fontScale: Float? = null,
        showBackground: Boolean = false,
        backgroundColor: Long? = null,
        previewParameterProviderClassName: String? = null,
        previewParameterIndex: Int? = null,
    ) {
        outputFile.parentFile.mkdirs()
        semanticsOutputFile.parentFile.mkdirs()
        layoutTreeOutputFile.parentFile.mkdirs()
        val activityClass = Class.forName("androidx.activity.ComponentActivity")
        val controller =
            Robolectric::class.java
                .getMethod("buildActivity", Class::class.java)
                .invoke(null, activityClass)
        val activity = controller.javaClass.getMethod("get").invoke(controller)
        setNoActionBarTheme(activity)
        controller.javaClass.getMethod("setup").invoke(controller)
        applyConfiguration(activity, density, fontScale ?: DEFAULT_FONT_SCALE, locale, uiMode)
        setContent(activity, className, methodName, previewParameterProviderClassName, previewParameterIndex)
        val view = draw(activity, widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), outputFile, showBackground, backgroundColor)
        writeSemantics(view, semanticsOutputFile, includeUnmergedSemantics)
        writeLayoutTree(view, layoutTreeOutputFile, density)
    }

    private fun applyConfiguration(
        activity: Any,
        density: Float,
        fontScale: Float,
        localeTag: String?,
        uiMode: Int?,
    ) {
        val resources = activity.javaClass.getMethod("getResources").invoke(activity)
        val metrics = resources.javaClass.getMethod("getDisplayMetrics").invoke(resources)
        setField(metrics, "density", density)
        setField(metrics, "scaledDensity", scaledDensity(density, fontScale))
        setField(metrics, "densityDpi", (density * DENSITY_DEFAULT).toInt().coerceAtLeast(1))
        val configuration = resources.javaClass.getMethod("getConfiguration").invoke(resources)
        setField(configuration, "fontScale", fontScale.coerceAtLeast(MIN_FONT_SCALE))
        localeTag?.let { tag ->
            val locale = localeForPreviewQualifier(tag)
            configuration.javaClass.getMethod("setLocale", Locale::class.java).invoke(configuration, locale)
        }
        applyUiMode(configuration, uiMode)
        resources.javaClass
            .getMethod(
                "updateConfiguration",
                configuration.javaClass,
                metrics.javaClass,
            ).invoke(resources, configuration, metrics)
    }

    internal fun scaledDensity(
        density: Float,
        fontScale: Float,
    ): Float = density * fontScale.coerceAtLeast(MIN_FONT_SCALE)

    internal fun localeForPreviewQualifier(localeQualifier: String): Locale {
        val parts = localeQualifier.replace('_', '-').split('-').filter(String::isNotBlank)
        val language = parts.firstOrNull().orEmpty()
        val region =
            parts
                .drop(1)
                .firstOrNull()
                ?.removePrefix("r")
                .orEmpty()
        return if (region.isBlank()) Locale(language) else Locale(language, region)
    }

    internal fun applyUiMode(
        configuration: Any,
        uiMode: Int?,
    ) {
        if (uiMode == null || uiMode == 0) return

        val requestedTypeMode = uiMode.and(UI_MODE_TYPE_MASK)
        val requestedNightMode = uiMode.and(UI_MODE_NIGHT_MASK)
        val field = configuration.javaClass.getField("uiMode")
        var updatedUiMode = field.getInt(configuration)

        if (requestedTypeMode != 0) {
            updatedUiMode = updatedUiMode.and(UI_MODE_TYPE_MASK.inv()).or(requestedTypeMode)
        }
        if (requestedNightMode == UI_MODE_NIGHT_YES || requestedNightMode == UI_MODE_NIGHT_NO) {
            updatedUiMode = updatedUiMode.and(UI_MODE_NIGHT_MASK.inv()).or(requestedNightMode)
        }

        field.setInt(configuration, updatedUiMode)
    }

    private fun setContent(
        activity: Any,
        className: String,
        methodName: String,
        previewParameterProviderClassName: String?,
        previewParameterIndex: Int?,
    ) {
        val content = PreviewComposable(className, methodName, previewParameterProviderClassName, previewParameterIndex)
        val ownerClass = Class.forName("androidx.activity.ComponentActivity")
        val setContent =
            Class
                .forName("androidx.activity.compose.ComponentActivityKt")
                .methods
                .single { method ->
                    method.name == "setContent" && method.parameterTypes.size == 3 && method.parameterTypes[0] == ownerClass
                }
        setContent.invoke(null, activity, null, content)
    }

    private fun draw(
        activity: Any,
        widthPx: Int,
        heightPx: Int,
        outputFile: File,
        showBackground: Boolean,
        backgroundColor: Long?,
    ): Any {
        val view = contentRoot(activity)
        if (showBackground) {
            view.javaClass
                .getMethod("setBackgroundColor", Int::class.javaPrimitiveType)
                .invoke(view, effectiveBackgroundColor(backgroundColor))
        }
        val measureSpecClass = Class.forName("android.view.View\$MeasureSpec")
        val exactly = measureSpecClass.getField("EXACTLY").getInt(null)
        val makeMeasureSpec = measureSpecClass.getMethod("makeMeasureSpec", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val widthSpec = makeMeasureSpec.invoke(null, widthPx, exactly) as Int
        val heightSpec = makeMeasureSpec.invoke(null, heightPx, exactly) as Int
        view.javaClass.getMethod("measure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(view, widthSpec, heightSpec)
        view.javaClass
            .getMethod(
                "layout",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).invoke(view, 0, 0, widthPx, heightPx)

        val bitmapClass = Class.forName("android.graphics.Bitmap")
        val configClass = Class.forName("android.graphics.Bitmap\$Config")
        val argb8888 = configClass.getField("ARGB_8888").get(null)
        val bitmap =
            bitmapClass
                .getMethod("createBitmap", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, configClass)
                .invoke(null, widthPx, heightPx, argb8888)
        val canvas = Class.forName("android.graphics.Canvas").getConstructor(bitmapClass).newInstance(bitmap)
        view.javaClass.getMethod("draw", Class.forName("android.graphics.Canvas")).invoke(view, canvas)
        val compressFormatClass = Class.forName("android.graphics.Bitmap\$CompressFormat")
        val png = compressFormatClass.getField("PNG").get(null)
        outputFile.outputStream().use { stream ->
            val wrote =
                bitmapClass
                    .getMethod("compress", compressFormatClass, Int::class.javaPrimitiveType, OutputStream::class.java)
                    .invoke(bitmap, png, PNG_QUALITY, stream) as Boolean
            check(wrote) { "Failed to write PNG to ${outputFile.absolutePath}" }
        }
        return view
    }

    private fun writeSemantics(
        contentRoot: Any,
        outputFile: File,
        includeUnmergedSemantics: Boolean,
    ) {
        val composeView =
            findComposeView(contentRoot) ?: run {
                outputFile.writeText("[]")
                return
            }
        val semanticsOwner = composeView.javaClass.getMethod("getSemanticsOwner").invoke(composeView)
        val nodes = listOf(toSnapshotNode(rootSemanticsNode(semanticsOwner, includeUnmergedSemantics), includeUnmergedSemantics))
        outputFile.writeText(Json.encodeToString(ListSerializer(SnapshotNode.serializer()), nodes))
    }

    private fun writeLayoutTree(
        contentRoot: Any,
        outputFile: File,
        density: Float,
    ) {
        runCatching {
            val composeView = checkNotNull(findComposeView(contentRoot)) { "Compose root view was not found." }
            writeLayoutTreeFromComposeView(composeView, outputFile, density)
        }.getOrElse { throwable ->
            writeEmptyLayoutTreeWarning(outputFile, throwable)
        }
    }

    internal fun writeLayoutTreeFromComposeView(
        composeView: Any,
        outputFile: File,
        density: Float,
    ) {
        runCatching {
            val getRoot =
                composeView
                    .javaClass
                    .methods
                    .firstOrNull {
                        it.name == "getRoot" && it.parameterTypes.isEmpty()
                    }
                    ?: error("Compose root view ${composeView.javaClass.name} does not expose getRoot().")
            val rootLayoutNode = getRoot.invoke(composeView)
            val nodes = listOf(extractLayoutTree(rootLayoutNode, density))
            outputFile.writeText(Json.encodeToString(ListSerializer(SnapshotLayoutNode.serializer()), nodes))
        }.getOrElse { throwable ->
            writeEmptyLayoutTreeWarning(outputFile, throwable)
        }
    }

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

    internal fun extractLayoutTree(
        rootLayoutNode: Any,
        density: Float,
    ): SnapshotLayoutNode = toSnapshotLayoutNode(rootLayoutNode, density, nextLayoutId())

    private fun nextLayoutId(): Iterator<String> = generateSequence(1) { it + 1 }.map { id -> "layout-$id" }.iterator()

    internal fun rootSemanticsNode(
        semanticsOwner: Any,
        includeUnmergedSemantics: Boolean,
    ): Any {
        val methodName =
            if (includeUnmergedSemantics) {
                "getUnmergedRootSemanticsNode"
            } else {
                "getRootSemanticsNode"
            }
        return semanticsOwner.javaClass.methods
            .first { it.name == methodName }
            .invoke(semanticsOwner)
    }

    private fun findComposeView(view: Any): Any? {
        if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
        val childCountMethod =
            view.javaClass.methods.firstOrNull { it.name == "getChildCount" && it.parameterTypes.isEmpty() } ?: return null
        val getChildAtMethod =
            view.javaClass.methods.firstOrNull { method ->
                method.name == "getChildAt" && method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
            } ?: return null
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
        val id =
            node.javaClass.methods
                .first { it.name == "getId" }
                .invoke(node)
                .toString()
        val config =
            node.javaClass.methods
                .first { it.name == "getConfig" }
                .invoke(node)
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

    private fun toSnapshotLayoutNode(
        node: Any,
        density: Float,
        ids: Iterator<String>,
    ): SnapshotLayoutNode {
        val id = ids.next()
        val boundsPx = layoutBounds(node)
        val semantics = semanticsProperties(method(node, "getSemanticsConfiguration")?.invoke(node))
        val children = layoutChildren(node).map { child -> toSnapshotLayoutNode(child, density, ids) }
        val measurePolicy = method(node, "getMeasurePolicy")?.invoke(node)
        val modifier = method(node, "getModifier")?.invoke(node)
        return SnapshotLayoutNode(
            id = id,
            boundsPx = boundsPx,
            boundsDp = boundsPx.toDpBounds(density),
            componentHint = measurePolicy?.javaClass?.name ?: node.javaClass.name,
            modifierHint = modifier?.javaClass?.name ?: modifier?.toString(),
            classHint = node.javaClass.name,
            semanticsId = method(node, "getSemanticsId")?.invoke(node)?.toString(),
            semantics = semantics.toLayoutSummary(),
            children = children,
        )
    }

    private fun layoutChildren(node: Any): List<Any> {
        val vector =
            method(node, "getZSortedChildren")?.invoke(node) ?: method(node, "get_children\$ui")?.invoke(node) ?: return emptyList()
        if (vector is Iterable<*>) return vector.filterNotNull()
        val asList = method(vector, "asMutableList")?.invoke(vector)
        if (asList is List<*>) return asList.filterNotNull()
        val size = method(vector, "getSize")?.invoke(vector) as? Int ?: return emptyList()
        val content = method(vector, "getContent")?.invoke(vector) as? Array<*> ?: return emptyList()
        return content.take(size).filterNotNull()
    }

    private fun layoutBounds(node: Any): Bounds {
        val coordinates = method(node, "getCoordinates")?.invoke(node) ?: return Bounds(0, 0, 0, 0)
        val width = method(coordinates, "getWidth")?.invoke(coordinates) as? Int ?: 0
        val height = method(coordinates, "getHeight")?.invoke(coordinates) as? Int ?: 0
        val packedOffset =
            coordinates.javaClass.methods
                .firstOrNull { method ->
                    method.name.startsWith("localToRoot-") && method.parameterTypes.contentEquals(arrayOf(Long::class.javaPrimitiveType))
                }?.invoke(coordinates, 0L) as? Long ?: 0L
        return Bounds(
            x = packedOffset.unpackFloat1().roundToInt(),
            y = packedOffset.unpackFloat2().roundToInt(),
            width = width.coerceAtLeast(0),
            height = height.coerceAtLeast(0),
        )
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

    private fun method(
        target: Any,
        name: String,
    ) = target.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }

    private fun semanticsProperties(config: Any?): Map<String, Any?> {
        val nonNullConfig = config ?: return emptyMap()

        @Suppress("UNCHECKED_CAST")
        val iterator =
            nonNullConfig.javaClass.methods
                .firstOrNull { it.name == "iterator" && it.parameterTypes.isEmpty() }
                ?.invoke(nonNullConfig)
                as? Iterator<Map.Entry<Any, Any?>>
                ?: return emptyMap()
        return buildMap {
            iterator.forEach { entry ->
                val key = entry.key
                val name =
                    key.javaClass.methods
                        .firstOrNull { it.name == "getName" }
                        ?.invoke(key)
                        ?.toString() ?: key.toString()
                put(name, entry.value)
            }
        }
    }

    internal fun semanticsChildren(
        node: Any,
        includeUnmergedSemantics: Boolean,
    ): List<Any> {
        val method = node.javaClass.methods.firstOrNull { method -> method.name == "getChildren" } ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return when (method.parameterTypes.size) {
            3 -> method.invoke(node, includeUnmergedSemantics, true, false)
            2 -> method.invoke(node, includeUnmergedSemantics, true)
            1 -> method.invoke(node, includeUnmergedSemantics)
            else -> method.invoke(node)
        } as List<Any>
    }

    private fun bounds(node: Any): Bounds {
        val rect =
            node.javaClass.methods
                .first { it.name == "getBoundsInRoot" }
                .invoke(node)
        val left =
            rect.javaClass.methods
                .first { it.name == "getLeft" }
                .invoke(rect) as Float
        val top =
            rect.javaClass.methods
                .first { it.name == "getTop" }
                .invoke(rect) as Float
        val right =
            rect.javaClass.methods
                .first { it.name == "getRight" }
                .invoke(rect) as Float
        val bottom =
            rect.javaClass.methods
                .first { it.name == "getBottom" }
                .invoke(rect) as Float
        return Bounds(
            x = left.roundToInt(),
            y = top.roundToInt(),
            width = (right - left).roundToInt().coerceAtLeast(0),
            height = (bottom - top).roundToInt().coerceAtLeast(0),
        )
    }

    private fun Any.toSnapshotText(): String =
        when (this) {
            is Iterable<*> -> joinToString(" ") { item -> item.toString() }
            else -> toString()
        }

    private fun Long.unpackFloat1(): Float = Float.fromBits((this shr 32).toInt())

    private fun Long.unpackFloat2(): Float = Float.fromBits((this and 0xffffffffL).toInt())

    private fun setNoActionBarTheme(activity: Any) {
        val styleClass = Class.forName("android.R\$style")
        val themeId = styleClass.getField("Theme_Material_NoActionBar").getInt(null)
        activity.javaClass.getMethod("setTheme", Int::class.javaPrimitiveType).invoke(activity, themeId)
    }

    private fun contentRoot(activity: Any): Any {
        val idClass = Class.forName("android.R\$id")
        val contentId = idClass.getField("content").getInt(null)
        return activity.javaClass.getMethod("findViewById", Int::class.javaPrimitiveType).invoke(activity, contentId)
    }

    internal fun effectiveBackgroundColor(backgroundColor: Long?): Int =
        if (backgroundColor == null || backgroundColor == 0L) {
            DEFAULT_BACKGROUND_COLOR
        } else {
            backgroundColor.toInt()
        }

    private fun setField(
        target: Any,
        name: String,
        value: Any,
    ) {
        val field = target.javaClass.getField(name)
        field.set(target, value)
    }

    private const val DENSITY_DEFAULT = 160
    private const val PNG_QUALITY = 100
    private const val DEFAULT_FONT_SCALE = 1.0f
    private const val MIN_FONT_SCALE = 0.01f
    private const val UI_MODE_TYPE_MASK = 0x0f
    private const val UI_MODE_NIGHT_MASK = 0x30
    private const val UI_MODE_NIGHT_NO = 0x10
    private const val UI_MODE_NIGHT_YES = 0x20
    private const val DEFAULT_BACKGROUND_COLOR = -0x1
}
