/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery.bytecode

import dev.staticvar.agentpreview.scanner.model.ParsedClass
import dev.staticvar.agentpreview.scanner.model.ParsedMethod
import dev.staticvar.agentpreview.scanner.model.PreviewAnnotation
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

internal class PreviewClassVisitor : ClassVisitor(Opcodes.ASM9) {
    private var className = ""
    private val previewAnnotations = mutableListOf<PreviewAnnotation>()
    private val methods = mutableListOf<ParsedMethod>()

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        className = name.toClassName()
    }

    override fun visitAnnotation(
        descriptor: String,
        visible: Boolean,
    ): AnnotationVisitor = descriptor.previewCollector(previewAnnotations) ?: emptyAnnotationVisitor()

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor =
        PreviewMethodVisitor(name, descriptor) { parsedMethod ->
            methods += parsedMethod
        }

    fun toParsedClass(): ParsedClass =
        ParsedClass(
            name = className,
            previewAnnotations = previewAnnotations.toList(),
            methods = methods.toList(),
        )
}
