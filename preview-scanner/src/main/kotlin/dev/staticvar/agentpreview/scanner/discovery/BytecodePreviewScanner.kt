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
import java.util.jar.JarFile

class BytecodePreviewScanner : PreviewScanner {
    override fun scan(input: PreviewScanInput): PreviewScanResult {
        val parsedClasses = input.classesDirs.flatMap(::parseClassesIn)
        val classpathClasses = input.runtimeClasspath.flatMap(::parseClassesIn)
        val annotationPreviews = (parsedClasses + classpathClasses).annotationPreviewIndex()

        val discovered =
            parsedClasses.flatMap { parsedClass ->
                parsedClass.discoverPreviews(input, annotationPreviews)
            }

        return PreviewScanResult(
            previews = discovered.filterIsInstance<DiscoveredPreview>().map { it.preview }.sortedBy { it.id },
            diagnostics = discovered.filterIsInstance<DiscoveryDiagnostic>().map { it.diagnostic },
        )
    }

    private fun parseClassesIn(path: File): List<ParsedClass> =
        when {
            path.isDirectory -> parseClassesInDirectory(path)
            path.isFile && path.extension == JAR_FILE_EXTENSION -> parseClassesInJar(path)
            path.isFile && path.extension == CLASS_FILE_EXTENSION -> listOfNotNull(parseClass(path.readBytes()))
            else -> emptyList()
        }

    private fun parseClassesInDirectory(classesDir: File): List<ParsedClass> =
        classesDir
            .walkTopDown()
            .filter { classFile -> classFile.isFile && classFile.extension == CLASS_FILE_EXTENSION }
            .mapNotNull { classFile -> parseClass(classFile.readBytes()) }
            .toList()

    private fun parseClassesInJar(jarFile: File): List<ParsedClass> =
        runCatching {
            JarFile(jarFile).use { jar ->
                jar
                    .entries()
                    .asSequence()
                    .filter { entry -> !entry.isDirectory && entry.name.endsWith(CLASS_FILE_SUFFIX) }
                    .mapNotNull { entry -> jar.getInputStream(entry).use { input -> parseClass(input.readBytes()) } }
                    .toList()
            }
        }.getOrDefault(emptyList())

    private fun parseClass(bytecode: ByteArray): ParsedClass? =
        runCatching {
            val visitor = PreviewClassVisitor()
            ClassReader(bytecode).accept(visitor, CLASS_READER_FLAGS)
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

                method.hasUnsupportedParameters() -> {
                    DiscoveryDiagnostic(
                        unsupportedParametersDiagnostic(className = name, methodName = method.name),
                    )
                }

                else -> {
                    DiscoveredPreview(
                        scannedPreview(
                            input = input,
                            className = name,
                            sourceFile = sourceFile,
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

    private fun ParsedMethod.hasUnsupportedParameters(): Boolean =
        argumentTypes.isNotEmpty() && !argumentTypes.all { type -> type == COMPOSER_TYPE || type == INT_TYPE }

    private fun scannedPreview(
        input: PreviewScanInput,
        className: String,
        sourceFile: String?,
        methodName: String,
        annotations: List<PreviewAnnotation>,
    ): ScannedPreview {
        val functionName = sourceQualifiedFunctionName(className, sourceFile, methodName)
        return ScannedPreview(
            id = "${input.projectPath}:${input.sourceSetName}:$functionName",
            name = annotations.firstOrNull()?.name,
            group = annotations.firstOrNull()?.group,
            sourceSet = input.sourceSetName,
            declaringClassName = className,
            sourceFile = sourceFile,
            methodName = methodName,
            fullyQualifiedClassName = className,
            fullyQualifiedFunctionName = functionName,
            annotations = annotations,
        )
    }

    private fun sourceQualifiedFunctionName(
        className: String,
        sourceFile: String?,
        methodName: String,
    ): String {
        val packageName = className.substringBeforeLast('.', missingDelimiterValue = "")
        val simpleClassName = className.substringAfterLast('.')
        val sourceBaseName = sourceFile?.substringBeforeLast('.')
        val ownerName =
            if (sourceBaseName != null && simpleClassName == "${sourceBaseName}Kt") {
                packageName
            } else {
                className
            }
        return listOf(ownerName, methodName)
            .filter { it.isNotBlank() }
            .joinToString(".")
    }

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
        const val CLASS_FILE_SUFFIX = ".$CLASS_FILE_EXTENSION"
        const val JAR_FILE_EXTENSION = "jar"
        const val COMPOSER_TYPE = "androidx.compose.runtime.Composer"
        const val INT_TYPE = "int"
    }
}
