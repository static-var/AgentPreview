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

@Suppress("LargeClass")
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
        val toolingRecord = ToolingCompositionRecord.createOrNull()
        setContent(activity, className, methodName, previewParameterProviderClassName, previewParameterIndex, toolingRecord)
        val view = draw(activity, widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), outputFile, showBackground, backgroundColor)
        writeSemantics(view, semanticsOutputFile, includeUnmergedSemantics)
        writeLayoutTree(
            view,
            layoutTreeOutputFile,
            density,
            toolingRecord,
            PreviewSourceFallback(className = className, methodName = methodName),
        )
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
        toolingRecord: ToolingCompositionRecord?,
    ) {
        val previewContent = PreviewComposable(className, methodName, previewParameterProviderClassName, previewParameterIndex)
        val content = toolingRecord?.wrap(previewContent) ?: previewContent
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
        toolingRecord: ToolingCompositionRecord? = null,
        previewSourceFallback: PreviewSourceFallback? = null,
    ) {
        runCatching {
            val composeView = checkNotNull(findComposeView(contentRoot)) { "Compose root view was not found." }
            writeLayoutTreeFromComposeView(composeView, outputFile, density, toolingRecord, previewSourceFallback)
        }.getOrElse { throwable ->
            writeEmptyLayoutTreeWarning(outputFile, throwable)
        }
    }

    internal fun writeLayoutTreeFromComposeView(
        composeView: Any,
        outputFile: File,
        density: Float,
        toolingRecord: ToolingCompositionRecord? = null,
        previewSourceFallback: PreviewSourceFallback? = null,
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
            val fallbackSourceHint = previewSourceFallback?.toLayoutTreeSourceHint()
            val sourceHints = toolingRecord?.sourceHintsOrEmpty(preferredAppSourceFile = fallbackSourceHint?.sourceFile).orEmpty()
            val fallbackHints =
                if (sourceHints.isEmpty() && fallbackSourceHint != null) {
                    mapOf(System.identityHashCode(rootLayoutNode) to fallbackSourceHint)
                } else {
                    emptyMap()
                }
            val nodes = listOf(extractLayoutTree(rootLayoutNode, density, sourceHints + fallbackHints))
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
        sourceHints: Map<Int, LayoutTreeSourceHint> = emptyMap(),
    ): SnapshotLayoutNode = toSnapshotLayoutNode(rootLayoutNode, density, nextLayoutId(), sourceHints)

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
        sourceHints: Map<Int, LayoutTreeSourceHint>,
    ): SnapshotLayoutNode {
        val id = ids.next()
        val boundsPx = layoutBounds(node)
        val semantics = semanticsProperties(method(node, "getSemanticsConfiguration")?.invoke(node))
        val children = layoutChildren(node).map { child -> toSnapshotLayoutNode(child, density, ids, sourceHints) }
        val measurePolicy = method(node, "getMeasurePolicy")?.invoke(node)
        val modifier = method(node, "getModifier")?.invoke(node)
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

    internal fun accessibleNoArgMethod(
        target: Any,
        name: String,
    ) = target.javaClass.methods
        .firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
        ?.apply { isAccessible = true }

    private fun method(
        target: Any,
        name: String,
    ) = accessibleNoArgMethod(target, name)

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

    internal class ToolingCompositionRecord private constructor(
        private val record: Any,
        private val inspectableMethod: java.lang.reflect.Method,
    ) {
        fun wrap(content: Function2<Any?, Int, Unit>): Function2<Any?, Int, Unit> =
            object : Function2<Any?, Int, Unit> {
                private var warningLogged = false

                override fun invoke(
                    composer: Any?,
                    changed: Int,
                ) {
                    runCatching {
                        inspectableMethod.invoke(null, record, content, composer, changed)
                    }.getOrElse { throwable ->
                        if (!warningLogged) {
                            warningLogged = true
                            warnSourceHintsDisabled("failed to invoke Compose tooling Inspectable wrapper", throwable)
                        }
                        content.invoke(composer, changed)
                    }
                }
            }

        fun sourceHintsOrEmpty(preferredAppSourceFile: String? = null): Map<Int, LayoutTreeSourceHint> =
            runCatching { sourceHints(preferredAppSourceFile) }.getOrElse { throwable ->
                warnSourceHintsDisabled("failed to read Compose tooling composition data", throwable)
                emptyMap()
            }

        private fun sourceHints(preferredAppSourceFile: String?): Map<Int, LayoutTreeSourceHint> {
            @Suppress("UNCHECKED_CAST")
            val store = method(record, "getStore")?.invoke(record) as? Iterable<*> ?: return emptyMap()
            val asTree = Class.forName("androidx.compose.ui.tooling.data.SlotTreeKt").getMethod("asTree", compositionDataClass())
            return buildMap {
                store.filterNotNull().forEach { compositionData ->
                    val rootGroup = asTree.invoke(null, compositionData)
                    collectGroupSourceHints(rootGroup, hints = this, preferredAppSourceFile = preferredAppSourceFile)
                }
            }
        }

        companion object {
            fun createOrNull(): ToolingCompositionRecord? =
                runCatching {
                    val recordClass = Class.forName("androidx.compose.ui.tooling.CompositionDataRecord")
                    val companion = recordClass.getField("Companion").get(null)
                    val record = companion.javaClass.getMethod("create").invoke(companion)
                    val inspectableMethod =
                        Class
                            .forName("androidx.compose.ui.tooling.InspectableKt")
                            .getMethod(
                                "Inspectable",
                                recordClass,
                                Function2::class.java,
                                compositionComposerClass(),
                                Int::class.javaPrimitiveType,
                            )
                    ToolingCompositionRecord(record, inspectableMethod)
                }.getOrElse { throwable ->
                    warnSourceHintsDisabled("Compose tooling APIs are unavailable", throwable)
                    null
                }

            private fun compositionDataClass(): Class<*> = Class.forName("androidx.compose.runtime.tooling.CompositionData")

            private fun compositionComposerClass(): Class<*> = Class.forName("androidx.compose.runtime.Composer")
        }
    }

    @Suppress("CyclomaticComplexMethod")
    internal fun collectGroupSourceHints(
        group: Any,
        hints: MutableMap<Int, LayoutTreeSourceHint>,
        preferredAppSourceFile: String? = null,
    ) {
        val entries = mutableListOf<ToolingGroupEntry>()
        collectToolingGroupEntries(group, parentPreorder = null, depth = 0, entries = entries)
        val sourceEntries = entries.filter { it.hint != null }
        entries.filter { it.node != null }.forEach { nodeEntry ->
            val node = nodeEntry.node ?: return@forEach
            val ancestorEntries =
                sourceEntries
                    .filter { sourceEntry -> sourceEntry.preorder in nodeEntry.ancestorPreorders }
            val siblingParentPreorders =
                (nodeEntry.ancestorPreorders + nodeEntry.preorder)
                    .mapNotNull { preorder -> entries.getOrNull(preorder)?.parentPreorder }
                    .toSet()
            val siblingEntries =
                sourceEntries
                    .filter { sourceEntry ->
                        sourceEntry.preorder < nodeEntry.preorder &&
                            sourceEntry.parentPreorder in siblingParentPreorders &&
                            sourceEntry.box != null &&
                            nodeEntry.box != null &&
                            sourceEntry.box.contains(nodeEntry.box)
                    }
            val hint =
                nodeEntry.hint?.takeIf { it.isAppSourceHint() }?.copy(sourceHintKind = "tooling-node-identity")
                    ?: ancestorEntries.nearestAppSourceHint("tooling-nearest-app-ancestor", preferredAppSourceFile)
                    ?: siblingEntries.nearestAppSourceHint("tooling-sibling-preorder-app", preferredAppSourceFile)
                    ?: nodeEntry.hint?.takeIf { it.isUsefulFrameworkSourceHint() }?.copy(sourceHintKind = "tooling-framework-node-identity")
                    ?: ancestorEntries.nearestUsefulFrameworkSourceHint("tooling-useful-framework-ancestor")
                    ?: siblingEntries.nearestUsefulFrameworkSourceHint("tooling-sibling-preorder-framework")
                    ?: nodeEntry.hint?.copy(sourceHintKind = "tooling-framework-node-identity")
                    ?: ancestorEntries.nearestSourceHint("tooling-framework-ancestor")
                    ?: siblingEntries.nearestSourceHint("tooling-sibling-preorder-framework")
            if (hint != null) hints[System.identityHashCode(node)] = hint
        }
    }

    private fun List<ToolingGroupEntry>.nearestAppSourceHint(
        sourceHintKind: String,
        preferredAppSourceFile: String?,
    ): LayoutTreeSourceHint? =
        filter { it.hint?.isAppSourceHint() == true }
            .maxWithOrNull(
                compareBy<ToolingGroupEntry> { it.depth }
                    .thenBy { entry -> entry.hint?.preferredAppSourceScore(preferredAppSourceFile) ?: 0 }
                    .thenBy { it.preorder },
            )?.hint
            ?.copy(sourceHintKind = sourceHintKind)

    private fun List<ToolingGroupEntry>.nearestUsefulFrameworkSourceHint(sourceHintKind: String): LayoutTreeSourceHint? =
        filter { it.hint?.isUsefulFrameworkSourceHint() == true }
            .maxWithOrNull(compareBy<ToolingGroupEntry> { it.depth }.thenBy { it.preorder })
            ?.hint
            ?.copy(sourceHintKind = sourceHintKind)

    private fun List<ToolingGroupEntry>.nearestSourceHint(sourceHintKind: String): LayoutTreeSourceHint? =
        maxWithOrNull(compareBy<ToolingGroupEntry> { it.depth }.thenBy { it.preorder })
            ?.hint
            ?.copy(sourceHintKind = sourceHintKind)

    private fun LayoutTreeSourceHint.isAppSourceHint(): Boolean {
        val file = sourceFile.orEmpty()
        val name = sourceName.orEmpty()
        return (sourceFile != null || sourceName != null) &&
            !file.isFrameworkSourceFile() &&
            !file.isGeneratedSourceFile() &&
            !name.isFrameworkSourceName()
    }

    private fun LayoutTreeSourceHint.preferredAppSourceScore(preferredAppSourceFile: String?): Int =
        if (preferredAppSourceFile != null && sourceFile == preferredAppSourceFile) 1 else 0

    private fun String.isFrameworkSourceFile(): Boolean = this in COMPOSE_INTERNAL_SOURCE_FILES || this in COMPOSE_PUBLIC_SOURCE_FILES

    private fun String.isGeneratedSourceFile(): Boolean =
        this in GENERATED_SOURCE_FILES ||
            endsWith(".generated.kt") ||
            endsWith(".Generated.kt") ||
            contains("/build/generated/") ||
            contains("\\build\\generated\\")

    private fun String.isFrameworkSourceName(): Boolean =
        this in COMPOSE_INTERNAL_SOURCE_NAMES ||
            FRAMEWORK_SOURCE_NAME_PREFIXES.any { startsWith(it) }

    private fun LayoutTreeSourceHint.isUsefulFrameworkSourceHint(): Boolean = sourceName in USEFUL_COMPOSE_SOURCE_NAMES

    private fun collectToolingGroupEntries(
        group: Any,
        parentPreorder: Int?,
        depth: Int,
        entries: MutableList<ToolingGroupEntry>,
        ancestorPreorders: List<Int> = emptyList(),
    ) {
        val preorder = entries.size
        val ownName = method(group, "getName")?.invoke(group) as? String
        val ownLocation = method(group, "getLocation")?.invoke(group)
        val ownHint = sourceHint(ownName, ownLocation, "tooling-node-identity")
        val node = method(group, "getNode")?.invoke(group)
        entries +=
            ToolingGroupEntry(
                preorder = preorder,
                parentPreorder = parentPreorder,
                depth = depth,
                ancestorPreorders = ancestorPreorders,
                hint = ownHint,
                node = node,
                box = toolingGroupBox(method(group, "getBox")?.invoke(group)),
            )
        @Suppress("UNCHECKED_CAST")
        val children = method(group, "getChildren")?.invoke(group) as? Iterable<*> ?: return
        children.filterNotNull().forEach { child ->
            collectToolingGroupEntries(
                group = child,
                parentPreorder = preorder,
                depth = depth + 1,
                entries = entries,
                ancestorPreorders = ancestorPreorders + preorder,
            )
        }
    }

    private data class ToolingGroupEntry(
        val preorder: Int,
        val parentPreorder: Int?,
        val depth: Int,
        val ancestorPreorders: List<Int>,
        val hint: LayoutTreeSourceHint?,
        val node: Any?,
        val box: ToolingIntRect?,
    )

    private data class ToolingIntRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        fun contains(other: ToolingIntRect): Boolean =
            left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
    }

    private fun toolingGroupBox(box: Any?): ToolingIntRect? {
        box ?: return null
        val reflected =
            ToolingIntRect(
                left = method(box, "getLeft")?.invoke(box) as? Int ?: Int.MIN_VALUE,
                top = method(box, "getTop")?.invoke(box) as? Int ?: Int.MIN_VALUE,
                right = method(box, "getRight")?.invoke(box) as? Int ?: Int.MIN_VALUE,
                bottom = method(box, "getBottom")?.invoke(box) as? Int ?: Int.MIN_VALUE,
            ).takeIf { rect ->
                rect.left != Int.MIN_VALUE && rect.top != Int.MIN_VALUE && rect.right != Int.MIN_VALUE && rect.bottom != Int.MIN_VALUE
            }
        if (reflected != null) return reflected
        val values = INT_RECT_PATTERN.findAll(box.toString()).mapNotNull { it.value.toIntOrNull() }.toList()
        return values.takeIf { it.size >= 4 }?.let { ToolingIntRect(it[0], it[1], it[2], it[3]) }
    }

    private fun sourceHint(
        sourceName: String?,
        location: Any?,
        sourceHintKind: String,
    ): LayoutTreeSourceHint? {
        val sourceFile = location?.let { method(it, "getSourceFile")?.invoke(it) as? String }
        val sourceLine = location?.let { method(it, "getLineNumber")?.invoke(it) as? Int }?.takeIf { it > 0 }
        return LayoutTreeSourceHint(
            sourceName = sourceName,
            sourceFile = sourceFile,
            sourceLine = sourceLine,
            sourceHintKind = sourceHintKind,
        ).takeIf { it.sourceName != null || it.sourceFile != null || it.sourceLine != null }
    }

    private fun warnSourceHintsDisabled(
        message: String,
        throwable: Throwable,
    ) {
        System.err.println(
            "AgentPreview: optional Compose layout source hints disabled; $message. " +
                "Layout tree extraction will continue without source hints. " +
                "Cause: ${throwable.javaClass.name}: ${throwable.message}",
        )
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
    private val INT_RECT_PATTERN = Regex("-?\\d+")
    private val COMPOSE_INTERNAL_SOURCE_FILES =
        setOf(
            "Layout.kt",
            "Composer.kt",
            "Composables.kt",
            "Effects.kt",
            "Updater.kt",
        )
    private val COMPOSE_PUBLIC_SOURCE_FILES =
        setOf(
            "BasicText.kt",
            "Box.kt",
            "Button.kt",
            "Card.kt",
            "Column.kt",
            "Row.kt",
            "Spacer.kt",
            "Surface.kt",
            "Text.kt",
            "ProvideContentColorTextStyle.kt",
        )
    private val COMPOSE_INTERNAL_SOURCE_NAMES =
        setOf(
            "ReusableComposeNode",
            "ComposeNode",
            "ReusableNode",
            "Layout",
            "CompositionLocalProvider",
            "startRestartGroup",
            "startReplaceableGroup",
            "startReusableGroup",
            "Updater",
        )
    private val GENERATED_SOURCE_FILES =
        setOf(
            "R.kt",
            "BuildConfig.kt",
        )
    private val FRAMEWORK_SOURCE_NAME_PREFIXES =
        listOf(
            "android.",
            "androidx.",
            "com.android.",
            "java.",
            "javax.",
            "kotlin.",
            "kotlinx.",
            "org.jetbrains.compose.",
        )
    private val USEFUL_COMPOSE_SOURCE_NAMES =
        setOf(
            "BasicText",
            "Box",
            "Button",
            "Card",
            "Column",
            "Row",
            "Spacer",
            "Text",
        )
}
