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
        val parsedClasses = input.classesDirs.flatMap(::parseClassesIn)
        val annotationPreviews = parsedClasses.annotationPreviewIndex()

        val discovered =
            parsedClasses.flatMap { parsedClass ->
                parsedClass.discoverPreviews(input, annotationPreviews)
            }

        return PreviewScanResult(
            previews = discovered.filterIsInstance<DiscoveredPreview>().map { it.preview }.sortedBy { it.id },
            diagnostics = discovered.filterIsInstance<DiscoveryDiagnostic>().map { it.diagnostic },
        )
    }

    private fun parseClassesIn(classesDir: File): List<ParsedClass> {
        if (!classesDir.isDirectory) return emptyList()

        return classesDir
            .walkTopDown()
            .filter { classFile -> classFile.isFile && classFile.extension == CLASS_FILE_EXTENSION }
            .mapNotNull(::parseClass)
            .toList()
    }

    private fun parseClass(classFile: File): ParsedClass? =
        runCatching {
            val visitor = PreviewClassVisitor()
            ClassReader(classFile.readBytes()).accept(visitor, CLASS_READER_FLAGS)
            visitor.toParsedClass()
        }.getOrNull()

    private fun List<ParsedClass>.annotationPreviewIndex(): Map<String, List<PreviewAnnotation>> =
        associate { parsedClass -> parsedClass.name to parsedClass.previewAnnotations }

    private fun ParsedClass.discoverPreviews(
        input: PreviewScanInput,
        annotationPreviews: Map<String, List<PreviewAnnotation>>,
    ): List<Discovery> =
        methods.mapNotNull { method ->
            val annotations = method.resolvePreviewAnnotations(annotationPreviews)
            when {
                annotations.isEmpty() -> {
                    null
                }

                method.argumentCount > 0 -> {
                    DiscoveryDiagnostic(
                        unsupportedParametersDiagnostic(className = name, methodName = method.name),
                    )
                }

                else -> {
                    DiscoveredPreview(
                        scannedPreview(
                            input = input,
                            className = name,
                            methodName = method.name,
                            annotations = annotations,
                        ),
                    )
                }
            }
        }

    private fun ParsedMethod.resolvePreviewAnnotations(annotationPreviews: Map<String, List<PreviewAnnotation>>): List<PreviewAnnotation> =
        previewAnnotations +
            metaAnnotationNames.flatMap { annotationName ->
                annotationPreviews[annotationName].orEmpty()
            }

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
            message = "Skipping preview $className.$methodName because preview methods with parameters are unsupported.",
            className = className,
            methodName = methodName,
        )

    private sealed interface Discovery

    private data class DiscoveredPreview(
        val preview: ScannedPreview,
    ) : Discovery

    private data class DiscoveryDiagnostic(
        val diagnostic: PreviewScanDiagnostic,
    ) : Discovery

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
        ): AnnotationVisitor = descriptor.previewCollector(previewAnnotations) ?: descriptor.asMetaAnnotationVisitor()

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

        private fun String.asMetaAnnotationVisitor(): AnnotationVisitor {
            metaAnnotationNames += toClassName()
            return emptyAnnotationVisitor()
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

        override fun visitArray(name: String): AnnotationVisitor = PreviewAnnotationArrayCollector(nestedPreviews)

        override fun visitEnd() {
            destination += nestedPreviews.ifEmpty { listOf(toPreviewAnnotation()) }
        }

        private fun toPreviewAnnotation(): PreviewAnnotation =
            PreviewAnnotation(
                name = stringValue(PREVIEW_NAME).emptyToNull(),
                group = stringValue(PREVIEW_GROUP).emptyToNull(),
                widthDp = intValue(PREVIEW_WIDTH_DP),
                heightDp = intValue(PREVIEW_HEIGHT_DP),
                showBackground = booleanValue(PREVIEW_SHOW_BACKGROUND),
                backgroundColor = longValue(PREVIEW_BACKGROUND_COLOR),
                fontScale = floatValue(PREVIEW_FONT_SCALE),
                locale = stringValue(PREVIEW_LOCALE).emptyToNull(),
                device = stringValue(PREVIEW_DEVICE).emptyToNull(),
                uiMode = intValue(PREVIEW_UI_MODE),
            )

        private fun stringValue(name: String): String = values[name] as? String ?: ""

        private fun intValue(name: String): Int = values[name] as? Int ?: -1

        private fun booleanValue(name: String): Boolean = values[name] as? Boolean ?: false

        private fun longValue(name: String): Long = values[name] as? Long ?: 0L

        private fun floatValue(name: String): Float = values[name] as? Float ?: 1.0f

        private fun String.emptyToNull(): String? = takeIf(String::isNotBlank)
    }

    private class PreviewAnnotationArrayCollector(
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

    private companion object {
        const val CLASS_FILE_EXTENSION = "class"
        const val PREVIEW_DESCRIPTOR = "Landroidx/compose/ui/tooling/preview/Preview;"
        const val CLASS_READER_FLAGS = ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES

        const val PREVIEW_NAME = "name"
        const val PREVIEW_GROUP = "group"
        const val PREVIEW_WIDTH_DP = "widthDp"
        const val PREVIEW_HEIGHT_DP = "heightDp"
        const val PREVIEW_SHOW_BACKGROUND = "showBackground"
        const val PREVIEW_BACKGROUND_COLOR = "backgroundColor"
        const val PREVIEW_FONT_SCALE = "fontScale"
        const val PREVIEW_LOCALE = "locale"
        const val PREVIEW_DEVICE = "device"
        const val PREVIEW_UI_MODE = "uiMode"

        fun String.previewCollector(destination: MutableList<PreviewAnnotation>): AnnotationVisitor? =
            takeIf { descriptor -> descriptor.isPreviewDescriptor() }
                ?.let { PreviewAnnotationCollector(destination) }

        fun String.isPreviewDescriptor(): Boolean = this == PREVIEW_DESCRIPTOR || endsWith("Preview${'$'}Container;")

        fun String.toClassName(): String =
            removePrefix("L")
                .removeSuffix(";")
                .replace('/', '.')

        fun emptyAnnotationVisitor(): AnnotationVisitor = object : AnnotationVisitor(Opcodes.ASM9) {}
    }
}
