/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.io.File
import java.lang.reflect.Method
import java.util.Locale

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
        androidAssetsDir: File? = null,
        androidAssetApk: File? = null,
        fontProbe: Boolean = false,
    ) {
        outputFile.parentFile.mkdirs()
        semanticsOutputFile.parentFile.mkdirs()
        layoutTreeOutputFile.parentFile.mkdirs()

        val activity = RobolectricActivityHost().createActivity(density, fontScale, locale, uiMode)
        androidAssetApk?.let { addAssetPath(activity, it) }
        if (fontProbe) {
            androidAssetsDir?.let { AndroidFontAssetProbe.probe(activity, it) }
        }
        val toolingRecord = ToolingCompositionRecord.createOrNull()
        setContent(activity, className, methodName, previewParameterProviderClassName, previewParameterIndex, toolingRecord)
        val view =
            AndroidViewPngRenderer().render(
                activity,
                widthPx.coerceAtLeast(1),
                heightPx.coerceAtLeast(1),
                outputFile,
                showBackground,
                backgroundColor,
            )

        ComposeSemanticsSnapshotMapper().write(view, semanticsOutputFile, includeUnmergedSemantics)
        ComposeLayoutTreeSnapshotMapper().write(
            view,
            layoutTreeOutputFile,
            density,
            toolingRecord,
            PreviewSourceFallback(className = className, methodName = methodName),
        )
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

    internal fun effectiveBackgroundColor(backgroundColor: Long?): Int =
        if (backgroundColor == null || backgroundColor == 0L) {
            DEFAULT_BACKGROUND_COLOR
        } else {
            backgroundColor.toInt()
        }

    internal fun addAssetPath(
        activity: Any,
        assetApk: File,
    ): Boolean {
        val assets =
            runCatching { activity.javaClass.getMethod("getAssets").invoke(activity) }
                .getOrElse { throwable ->
                    System.err.println(
                        "AgentPreview asset apk: failed to read Activity.assets for ${assetApk.absolutePath}: ${throwable.javaClass.name}: ${throwable.message}",
                    )
                    return false
                }
        return runCatching {
            val cookie = assets.javaClass.getMethod("addAssetPath", String::class.java).invoke(assets, assetApk.absolutePath) as? Int ?: 0
            System.err.println("AgentPreview asset apk: addAssetPath(${assetApk.absolutePath}) returned $cookie")
            cookie != 0
        }.getOrElse { throwable ->
            System.err.println(
                "AgentPreview asset apk: failed to add ${assetApk.absolutePath}: ${throwable.javaClass.name}: ${throwable.message}",
            )
            false
        }
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
            ComposeReflection.requiredMethodMatching(
                Class.forName("androidx.activity.compose.ComponentActivityKt"),
                "setContent",
                "androidx.activity.ComponentActivity, *, *",
            ) { method ->
                method.parameterTypes.size == 3 && method.parameterTypes[0] == ownerClass
            }
        setContent.invoke(null, activity, null, content)
    }

    internal class ToolingCompositionRecord private constructor(
        private val record: Any,
        private val inspectableMethod: Method,
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
            val store = ComposeReflection.optionalNoArgValue<Iterable<*>>(record, "getStore") ?: return emptyMap()
            val asTree =
                ComposeReflection.requiredMethod(
                    Class.forName("androidx.compose.ui.tooling.data.SlotTreeKt"),
                    "asTree",
                    compositionDataClass(),
                )
            return buildMap {
                store.filterNotNull().forEach { compositionData ->
                    val rootGroup = asTree.invoke(null, compositionData)
                    ToolingSourceHintMapper.collectGroupSourceHints(
                        rootGroup,
                        hints = this,
                        preferredAppSourceFile = preferredAppSourceFile,
                    )
                }
            }
        }

        companion object {
            fun createOrNull(): ToolingCompositionRecord? =
                runCatching {
                    val recordClass = Class.forName("androidx.compose.ui.tooling.CompositionDataRecord")
                    val companion = recordClass.getField("Companion").get(null)
                    val record = ComposeReflection.requiredNoArgValue<Any>(companion, "create")
                    val inspectableMethod =
                        ComposeReflection.requiredMethod(
                            Class.forName("androidx.compose.ui.tooling.InspectableKt"),
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

    private const val MIN_FONT_SCALE = 0.01f
    private const val UI_MODE_TYPE_MASK = 0x0f
    private const val UI_MODE_NIGHT_MASK = 0x30
    private const val UI_MODE_NIGHT_NO = 0x10
    private const val UI_MODE_NIGHT_YES = 0x20
    private const val DEFAULT_BACKGROUND_COLOR = -0x1
}
