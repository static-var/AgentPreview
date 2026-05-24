/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery.bytecode

import dev.staticvar.agentpreview.scanner.model.PreviewAnnotation
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes

internal class PreviewAnnotationCollector(
    private val destination: MutableList<PreviewAnnotation>,
) : AnnotationVisitor(Opcodes.ASM9) {
    private val values = linkedMapOf<String, Any?>()
    private val nestedPreviews = mutableListOf<PreviewAnnotation>()

    override fun visit(
        name: String,
        value: Any?,
    ) {
        values[name] = value
    }

    override fun visitArray(name: String): AnnotationVisitor = PreviewAnnotationArrayCollector(nestedPreviews)

    override fun visitEnd() {
        destination += nestedPreviews.ifEmpty { listOf(toPreviewAnnotation()) }
    }

    private fun toPreviewAnnotation(): PreviewAnnotation =
        PreviewAnnotation(
            name = stringValue(PREVIEW_NAME).emptyToNull(),
            group = stringValue(PREVIEW_GROUP).emptyToNull(),
            widthDp = intValue(PREVIEW_WIDTH_DP, default = -1),
            heightDp = intValue(PREVIEW_HEIGHT_DP, default = -1),
            showBackground = booleanValue(PREVIEW_SHOW_BACKGROUND),
            backgroundColor = longValue(PREVIEW_BACKGROUND_COLOR),
            fontScale = floatValue(PREVIEW_FONT_SCALE),
            locale = stringValue(PREVIEW_LOCALE).emptyToNull(),
            device = stringValue(PREVIEW_DEVICE).emptyToNull(),
            uiMode = intValue(PREVIEW_UI_MODE, default = 0),
        )

    private fun stringValue(name: String): String = values[name] as? String ?: ""

    private fun intValue(
        name: String,
        default: Int,
    ): Int = values[name] as? Int ?: default

    private fun booleanValue(name: String): Boolean = values[name] as? Boolean ?: false

    private fun longValue(name: String): Long = values[name] as? Long ?: 0L

    private fun floatValue(name: String): Float = values[name] as? Float ?: 1.0f

    private fun String.emptyToNull(): String? = takeIf(String::isNotBlank)
}
