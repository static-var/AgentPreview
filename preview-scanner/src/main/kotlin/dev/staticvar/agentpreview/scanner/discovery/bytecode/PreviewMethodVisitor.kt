/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery.bytecode

import dev.staticvar.agentpreview.scanner.model.ParsedMethod
import dev.staticvar.agentpreview.scanner.model.PreviewAnnotation
import dev.staticvar.agentpreview.scanner.model.PreviewParameter
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
    private val previewParameters = mutableListOf<PreviewParameter>()

    override fun visitAnnotation(
        descriptor: String,
        visible: Boolean,
    ): AnnotationVisitor = descriptor.previewCollector(previewAnnotations) ?: descriptor.asMetaAnnotationVisitor()

    override fun visitParameterAnnotation(
        parameter: Int,
        descriptor: String,
        visible: Boolean,
    ): AnnotationVisitor? =
        if (descriptor == PREVIEW_PARAMETER_DESCRIPTOR) {
            previewParameterCollector(parameter)
        } else {
            super.visitParameterAnnotation(parameter, descriptor, visible)
        }

    override fun visitEnd() {
        onComplete(
            ParsedMethod(
                name = methodName,
                argumentTypes = argumentTypes,
                previewAnnotations = previewAnnotations.toList(),
                metaAnnotationNames = metaAnnotationNames.toList(),
                previewParameters = previewParameters.toList(),
            ),
        )
    }

    private fun previewParameterCollector(parameter: Int): AnnotationVisitor =
        object : AnnotationVisitor(Opcodes.ASM9) {
            private var providerClassName: String? = null
            private var limit: Int? = null

            override fun visit(
                name: String?,
                value: Any?,
            ) {
                when (name) {
                    "value", "provider" -> providerClassName = (value as? Type)?.className
                    "limit" -> limit = (value as? Int)?.takeIf { it > 0 && it != Int.MAX_VALUE }
                }
            }

            override fun visitEnd() {
                val provider = providerClassName ?: return
                previewParameters +=
                    PreviewParameter(
                        providerClassName = provider,
                        limit = limit,
                        parameterIndex = parameter,
                        parameterType = argumentTypes.getOrElse(parameter) { "java.lang.Object" },
                    )
            }
        }

    private fun String.asMetaAnnotationVisitor(): AnnotationVisitor {
        metaAnnotationNames += toClassName()
        return emptyAnnotationVisitor()
    }

    private companion object {
        const val PREVIEW_PARAMETER_DESCRIPTOR = "Landroidx/compose/ui/tooling/preview/PreviewParameter;"
    }
}
