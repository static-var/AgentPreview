/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery.bytecode

import dev.staticvar.agentpreview.scanner.model.ParsedMethod
import dev.staticvar.agentpreview.scanner.model.PreviewAnnotation
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

internal class PreviewMethodVisitor(
    private val methodName: String,
    methodDescriptor: String,
    private val onComplete: (ParsedMethod) -> Unit,
) : MethodVisitor(Opcodes.ASM9) {
    private val argumentTypes = Type.getArgumentTypes(methodDescriptor).map(Type::getClassName)
    private val previewAnnotations = mutableListOf<PreviewAnnotation>()
    private val metaAnnotationNames = mutableListOf<String>()

    override fun visitAnnotation(
        descriptor: String,
        visible: Boolean,
    ): AnnotationVisitor = descriptor.previewCollector(previewAnnotations) ?: descriptor.asMetaAnnotationVisitor()

    override fun visitEnd() {
        onComplete(
            ParsedMethod(
                name = methodName,
                argumentTypes = argumentTypes,
                previewAnnotations = previewAnnotations.toList(),
                metaAnnotationNames = metaAnnotationNames.toList(),
            ),
        )
    }

    private fun String.asMetaAnnotationVisitor(): AnnotationVisitor {
        metaAnnotationNames += toClassName()
        return emptyAnnotationVisitor()
    }
}
