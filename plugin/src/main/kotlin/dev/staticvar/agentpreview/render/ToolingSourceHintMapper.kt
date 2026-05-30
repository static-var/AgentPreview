/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

internal object ToolingSourceHintMapper {
    @Suppress("CyclomaticComplexMethod")
    fun collectGroupSourceHints(
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
        val ownName = ComposeReflection.optionalNoArgValue<String>(group, "getName")
        val ownLocation = ComposeReflection.optionalNoArgValue<Any>(group, "getLocation")
        val ownHint = sourceHint(ownName, ownLocation, "tooling-node-identity")
        val node = ComposeReflection.optionalNoArgValue<Any>(group, "getNode")
        entries +=
            ToolingGroupEntry(
                preorder = preorder,
                parentPreorder = parentPreorder,
                depth = depth,
                ancestorPreorders = ancestorPreorders,
                hint = ownHint,
                node = node,
                box = toolingGroupBox(ComposeReflection.optionalNoArgValue<Any>(group, "getBox")),
            )
        @Suppress("UNCHECKED_CAST")
        val children = ComposeReflection.optionalNoArgValue<Iterable<*>>(group, "getChildren") ?: return
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
                left = ComposeReflection.optionalNoArgValue<Int>(box, "getLeft") ?: Int.MIN_VALUE,
                top = ComposeReflection.optionalNoArgValue<Int>(box, "getTop") ?: Int.MIN_VALUE,
                right = ComposeReflection.optionalNoArgValue<Int>(box, "getRight") ?: Int.MIN_VALUE,
                bottom = ComposeReflection.optionalNoArgValue<Int>(box, "getBottom") ?: Int.MIN_VALUE,
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
        val sourceFile = location?.let { ComposeReflection.optionalNoArgValue<String>(it, "getSourceFile") }
        val sourceLine = location?.let { ComposeReflection.optionalNoArgValue<Int>(it, "getLineNumber") }?.takeIf { it > 0 }
        return LayoutTreeSourceHint(
            sourceName = sourceName,
            sourceFile = sourceFile,
            sourceLine = sourceLine,
            sourceHintKind = sourceHintKind,
        ).takeIf { it.sourceName != null || it.sourceFile != null || it.sourceLine != null }
    }

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
