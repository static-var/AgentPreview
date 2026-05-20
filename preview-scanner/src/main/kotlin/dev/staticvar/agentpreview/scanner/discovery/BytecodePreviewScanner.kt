/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery

import dev.staticvar.agentpreview.scanner.discovery.bytecode.CLASS_READER_FLAGS
import dev.staticvar.agentpreview.scanner.discovery.bytecode.PreviewClassVisitor
import dev.staticvar.agentpreview.scanner.model.DiscoveredPreview
import dev.staticvar.agentpreview.scanner.model.Discovery
import dev.staticvar.agentpreview.scanner.model.DiscoveryDiagnostic
import dev.staticvar.agentpreview.scanner.model.ParsedClass
import dev.staticvar.agentpreview.scanner.model.ParsedMethod
import dev.staticvar.agentpreview.scanner.model.PreviewAnnotation
import dev.staticvar.agentpreview.scanner.model.ScannedPreview
import org.objectweb.asm.ClassReader
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

    private companion object {
        const val CLASS_FILE_EXTENSION = "class"
    }
}
