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
        includeUnmergedSemantics: Boolean = false,
        locale: String? = null,
        uiMode: Int? = null,
        fontScale: Float? = null,
        showBackground: Boolean = false,
        backgroundColor: Long? = null,
    ) {
        outputFile.parentFile.mkdirs()
        semanticsOutputFile.parentFile.mkdirs()
        val activityClass = Class.forName("androidx.activity.ComponentActivity")
        val controller =
            Robolectric::class.java
                .getMethod("buildActivity", Class::class.java)
                .invoke(null, activityClass)
        val activity = controller.javaClass.getMethod("get").invoke(controller)
        setNoActionBarTheme(activity)
        controller.javaClass.getMethod("setup").invoke(controller)
        applyConfiguration(activity, density, fontScale ?: DEFAULT_FONT_SCALE, locale, uiMode)
        setContent(activity, className, methodName)
        val view = draw(activity, widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), outputFile, showBackground, backgroundColor)
        writeSemantics(view, semanticsOutputFile, includeUnmergedSemantics)
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
            val locale = Locale.forLanguageTag(tag.replace('_', '-'))
            configuration.javaClass.getMethod("setLocale", Locale::class.java).invoke(configuration, locale)
        }
        applyNightMode(configuration, uiMode)
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

    internal fun applyNightMode(
        configuration: Any,
        uiMode: Int?,
    ) {
        val requestedNightMode = uiMode?.and(UI_MODE_NIGHT_MASK) ?: return
        if (requestedNightMode != UI_MODE_NIGHT_YES && requestedNightMode != UI_MODE_NIGHT_NO) return
        val currentUiMode = configuration.javaClass.getField("uiMode").getInt(configuration)
        configuration.javaClass.getField("uiMode").setInt(
            configuration,
            currentUiMode.and(UI_MODE_NIGHT_MASK.inv()).or(requestedNightMode),
        )
    }

    private fun setContent(
        activity: Any,
        className: String,
        methodName: String,
    ) {
        val content = PreviewComposable(className, methodName)
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

    private fun semanticsProperties(config: Any): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val iterator =
            config.javaClass.methods
                .firstOrNull { it.name == "iterator" && it.parameterTypes.isEmpty() }
                ?.invoke(config)
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
    private const val UI_MODE_NIGHT_MASK = 0x30
    private const val UI_MODE_NIGHT_NO = 0x10
    private const val UI_MODE_NIGHT_YES = 0x20
    private const val DEFAULT_BACKGROUND_COLOR = -0x1
}
