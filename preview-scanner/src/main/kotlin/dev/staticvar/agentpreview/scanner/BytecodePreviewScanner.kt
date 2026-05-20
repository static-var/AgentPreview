/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File

class BytecodePreviewScanner : PreviewScanner {
    override fun scan(input: PreviewScanInput): PreviewScanResult {
        val parsedClasses = input.classesDirs.flatMap { classesDir -> parseClassesIn(classesDir) }
        val classPreviews = parsedClasses.associate { parsedClass -> parsedClass.name to parsedClass.previewAnnotations }
        val previews = mutableListOf<ScannedPreview>()
        val diagnostics = mutableListOf<PreviewScanDiagnostic>()

        parsedClasses.forEach { parsedClass ->
            parsedClass.methods.forEach { method ->
                val annotations = method.previewAnnotations + method.metaAnnotationNames.flatMap { classPreviews[it].orEmpty() }
                if (annotations.isEmpty()) return@forEach

                if (method.argumentCount > 0) {
                    diagnostics += unsupportedParametersDiagnostic(parsedClass.name, method.name)
                    return@forEach
                }

                previews += scannedPreview(input, parsedClass.name, method.name, annotations)
            }
        }

        return PreviewScanResult(previews = previews.sortedBy { it.id }, diagnostics = diagnostics)
    }

    private fun parseClassesIn(classesDir: File): List<ParsedClass> {
        if (!classesDir.isDirectory) return emptyList()
        return classesDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .mapNotNull { classFile -> parseClass(classFile) }
            .toList()
    }

    private fun parseClass(classFile: File): ParsedClass? =
        runCatching {
            val visitor = PreviewClassVisitor()
            ClassReader(classFile.readBytes()).accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            visitor.toParsedClass()
        }.getOrNull()

    private fun scannedPreview(
        input: PreviewScanInput,
        className: String,
        methodName: String,
        annotations: List<PreviewAnnotation>,
    ): ScannedPreview =
        ScannedPreview(
            id = "${input.projectPath}:${input.sourceSetName}:$className.$methodName",
            name = annotations.firstOrNull()?.name,
            group = annotations.firstOrNull()?.group,
            sourceSet = input.sourceSetName,
            declaringClassName = className,
            methodName = methodName,
            fullyQualifiedFunctionName = "$className.$methodName",
            annotations = annotations,
        )

    private fun unsupportedParametersDiagnostic(
        className: String,
        methodName: String,
    ): PreviewScanDiagnostic =
        PreviewScanDiagnostic(
            severity = PreviewScanDiagnostic.Severity.WARNING,
            message =
                "Skipping preview $className.$methodName because " +
                    "preview methods with parameters are unsupported.",
            className = className,
            methodName = methodName,
        )

    private data class ParsedClass(
        val name: String,
        val previewAnnotations: List<PreviewAnnotation>,
        val methods: List<ParsedMethod>,
    )

    private data class ParsedMethod(
        val name: String,
        val argumentCount: Int,
        val previewAnnotations: List<PreviewAnnotation>,
        val metaAnnotationNames: List<String>,
    )

    private class PreviewClassVisitor : ClassVisitor(Opcodes.ASM9) {
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
            className = name.replace('/', '.')
        }

        override fun visitAnnotation(
            descriptor: String,
            visible: Boolean,
        ): AnnotationVisitor = previewCollectingVisitor(descriptor, previewAnnotations) ?: emptyAnnotationVisitor()

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor = PreviewMethodVisitor(name, descriptor) { methods += it }

        fun toParsedClass(): ParsedClass =
            ParsedClass(
                name = className,
                previewAnnotations = previewAnnotations.toList(),
                methods = methods.toList(),
            )
    }

    private class PreviewMethodVisitor(
        private val methodName: String,
        methodDescriptor: String,
        private val onComplete: (ParsedMethod) -> Unit,
    ) : MethodVisitor(Opcodes.ASM9) {
        private val argumentCount = Type.getArgumentTypes(methodDescriptor).size
        private val previewAnnotations = mutableListOf<PreviewAnnotation>()
        private val metaAnnotationNames = mutableListOf<String>()

        override fun visitAnnotation(
            descriptor: String,
            visible: Boolean,
        ): AnnotationVisitor {
            val visitor = previewCollectingVisitor(descriptor, previewAnnotations)
            if (visitor != null) return visitor

            metaAnnotationNames += descriptorToClassName(descriptor)
            return emptyAnnotationVisitor()
        }

        override fun visitEnd() {
            onComplete(
                ParsedMethod(
                    name = methodName,
                    argumentCount = argumentCount,
                    previewAnnotations = previewAnnotations.toList(),
                    metaAnnotationNames = metaAnnotationNames.toList(),
                ),
            )
        }
    }

    private class PreviewAnnotationCollector(
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

        override fun visitArray(name: String): AnnotationVisitor =
            object : AnnotationVisitor(Opcodes.ASM9) {
                override fun visitAnnotation(
                    name: String?,
                    descriptor: String,
                ): AnnotationVisitor? {
                    if (descriptor != PREVIEW_DESCRIPTOR) return null
                    return PreviewAnnotationCollector(nestedPreviews)
                }
            }

        override fun visitEnd() {
            destination += nestedPreviews.ifEmpty { listOf(asPreviewAnnotation()) }
        }

        private fun asPreviewAnnotation(): PreviewAnnotation =
            PreviewAnnotation(
                name = stringValue("name").emptyToNull(),
                group = stringValue("group").emptyToNull(),
                widthDp = intValue("widthDp"),
                heightDp = intValue("heightDp"),
                showBackground = booleanValue("showBackground"),
                backgroundColor = longValue("backgroundColor"),
                fontScale = floatValue("fontScale"),
                locale = stringValue("locale").emptyToNull(),
                device = stringValue("device").emptyToNull(),
                uiMode = intValue("uiMode"),
            )

        private fun stringValue(name: String): String = values[name] as? String ?: ""

        private fun intValue(name: String): Int = values[name] as? Int ?: -1

        private fun booleanValue(name: String): Boolean = values[name] as? Boolean ?: false

        private fun longValue(name: String): Long = values[name] as? Long ?: 0L

        private fun floatValue(name: String): Float = values[name] as? Float ?: 1.0f

        private fun String.emptyToNull(): String? = takeIf { it.isNotBlank() }
    }

    private companion object {
        const val PREVIEW_DESCRIPTOR = "Landroidx/compose/ui/tooling/preview/Preview;"

        fun previewCollectingVisitor(
            descriptor: String,
            destination: MutableList<PreviewAnnotation>,
        ): AnnotationVisitor? {
            if (descriptor != PREVIEW_DESCRIPTOR && !descriptor.endsWith("Preview${'$'}Container;")) {
                return null
            }
            return PreviewAnnotationCollector(destination)
        }

        fun emptyAnnotationVisitor(): AnnotationVisitor = object : AnnotationVisitor(Opcodes.ASM9) {}

        fun descriptorToClassName(descriptor: String): String =
            descriptor
                .removePrefix("L")
                .removeSuffix(";")
                .replace('/', '.')
    }
}
