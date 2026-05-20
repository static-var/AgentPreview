/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery.bytecode

import dev.staticvar.agentpreview.scanner.model.PreviewAnnotation
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes

internal fun String.previewCollector(destination: MutableList<PreviewAnnotation>): AnnotationVisitor? =
    takeIf { descriptor -> descriptor.isPreviewDescriptor() }
        ?.let { PreviewAnnotationCollector(destination) }

internal fun String.isPreviewDescriptor(): Boolean = this == PREVIEW_DESCRIPTOR || endsWith("Preview${'$'}Container;")

internal fun String.toClassName(): String =
    removePrefix("L")
        .removeSuffix(";")
        .replace('/', '.')

internal fun emptyAnnotationVisitor(): AnnotationVisitor = object : AnnotationVisitor(Opcodes.ASM9) {}
