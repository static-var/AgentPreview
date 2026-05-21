/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery.bytecode

import dev.staticvar.agentpreview.scanner.model.PreviewAnnotation
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes

internal class PreviewAnnotationArrayCollector(
    private val destination: MutableList<PreviewAnnotation>,
) : AnnotationVisitor(Opcodes.ASM9) {
    override fun visitAnnotation(
        name: String?,
        descriptor: String,
    ): AnnotationVisitor? =
        when (descriptor) {
            PREVIEW_DESCRIPTOR -> PreviewAnnotationCollector(destination)
            else -> null
        }
}
