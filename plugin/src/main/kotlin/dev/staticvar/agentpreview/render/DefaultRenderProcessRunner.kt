/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.runner.JUnitCore
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLClassLoader
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.tools.ToolProvider

class DefaultRenderProcessRunner : RenderProcessRunner {
    override fun run(
        request: AndroidComposeRenderRequest,
        previewClasspath: List<File>,
    ): RenderProcessResult {
        val classpath = materializeClasspath(currentPluginClasspath() + androidJar(request.robolectricSdk) + previewClasspath)
        val harnessResultFile = File.createTempFile("agentpreview-render-harness-", ".properties")
        harnessResultFile.delete()
        val command =
            listOf(
                javaExecutable(),
                "-cp",
                classpath.joinToString(File.pathSeparator) { it.absolutePath },
                AndroidComposeRenderHarness::class.java.name,
                request.className,
                request.methodName,
                request.widthPx.toString(),
                request.heightPx.toString(),
                request.density.toString(),
                request.robolectricSdk.toString(),
                request.outputFile.absolutePath,
                request.semanticsOutputFile.absolutePath,
                request.layoutTreeOutputFile.absolutePath,
                request.includeUnmergedSemantics.toString(),
                request.locale.orEmpty(),
                request.uiMode?.toString().orEmpty(),
                request.fontScale?.toString().orEmpty(),
                request.showBackground.toString(),
                request.backgroundColor?.toString().orEmpty(),
                harnessResultFile.absolutePath,
            )
        val process =
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            harnessResultFile.delete()
            return RenderProcessResult.Success
        }
        val message =
            "Android Compose preview rendering failed for ${request.className}.${request.methodName} " +
                "with exit code $exitCode.\n$output"
        val failureKind = RenderHarnessResultFile.readFailureKind(harnessResultFile)
        harnessResultFile.delete()
        return RenderProcessResult.Failure(
            kind =
                if (failureKind == RenderProcessFailureKind.ResourceLoadingGap) {
                    RenderProcessFailureKind.ResourceLoadingGap
                } else {
                    RenderProcessFailureKind.HarnessFailure
                },
            message = message,
        )
    }

    private fun currentPluginClasspath(): List<File> {
        val loaderFiles =
            (DefaultRenderProcessRunner::class.java.classLoader as? URLClassLoader)
                ?.urLs
                ?.mapNotNull { url -> url.toURI().takeIf { it.scheme == "file" } }
                ?.map(::File)
                ?: emptyList()
        val requiredCodeSources =
            listOf(
                DefaultRenderProcessRunner::class.java,
                JUnitCore::class.java,
                Robolectric::class.java,
                RobolectricTestRunner::class.java,
                Unit::class.java,
                Function2::class.java,
            ).mapNotNull { clazz ->
                clazz.protectionDomain.codeSource
                    ?.location
                    ?.toURI()
                    ?.let(::File)
            }
        return (loaderFiles + requiredCodeSources)
            .filter { it.exists() }
            .distinctBy { it.absoluteFile.normalize().path }
    }

    private fun materializeClasspath(classpath: List<File>): List<File> =
        classpath.flatMap { file ->
            if (file.extension == "aar" && file.isFile) {
                materializeAarClasspath(file)
            } else {
                listOf(file)
            }
        }

    private fun materializeAarClasspath(aar: File): List<File> =
        listOfNotNull(extractAarClassesJar(aar)) + extractAarEmbeddedJars(aar) + listOfNotNull(generateAarRJar(aar)) + aar

    private fun extractAarClassesJar(aar: File): File? = extractAarEntry(aar, "classes.jar", aarMaterializationFile(aar, "classes", "jar"))

    private fun extractAarEmbeddedJars(aar: File): List<File> {
        val outputDir = aarMaterializationFile(aar, "libs", "dir")
        outputDir.mkdirs()
        ZipFile(aar).use { zip ->
            return zip
                .entries()
                .asSequence()
                .filter { entry -> !entry.isDirectory && entry.name.startsWith("libs/") && entry.name.endsWith(".jar") }
                .sortedBy { entry -> entry.name }
                .mapNotNull { entry -> extractAarEntry(aar, entry.name, File(outputDir, File(entry.name).name)) }
                .toList()
        }
    }

    private fun extractAarEntry(
        aar: File,
        entryName: String,
        output: File,
    ): File? {
        if (output.isFile) return output
        output.parentFile.mkdirs()
        val tempOutput = Files.createTempFile(output.parentFile.toPath(), "${output.name}.", ".tmp").toFile()
        try {
            ZipFile(aar).use { zip ->
                val entry = zip.getEntry(entryName) ?: return null
                copyZipEntry(zip, entry, tempOutput)
            }
            moveAtomically(tempOutput, output)
            return output
        } finally {
            tempOutput.delete()
        }
    }

    private fun copyZipEntry(
        zip: ZipFile,
        entry: ZipEntry,
        output: File,
    ) {
        zip.getInputStream(entry).use { input ->
            output.outputStream().use { outputStream -> input.copyTo(outputStream) }
        }
    }

    private fun generateAarRJar(aar: File): File? {
        val output = aarMaterializationFile(aar, "r", "jar")
        if (output.isFile) return output
        val classesJar = extractAarClassesJar(aar) ?: return null
        val packageName = inferRPackageName(classesJar) ?: return null
        val symbols = readAarSymbols(aar).takeIf { it.isNotEmpty() } ?: return null
        val source = rJavaSource(packageName, symbols)
        output.parentFile.mkdirs()
        val workDir = Files.createTempDirectory(output.parentFile.toPath(), "${output.name}.work.").toFile()
        val tempOutput = Files.createTempFile(output.parentFile.toPath(), "${output.name}.", ".tmp").toFile()
        try {
            val sourceFile = File(workDir, packageName.replace('.', '/') + "/R.java")
            val classesDir = File(workDir, "classes")
            sourceFile.parentFile.mkdirs()
            classesDir.mkdirs()
            sourceFile.writeText(source)
            val compiler = ToolProvider.getSystemJavaCompiler()
            val result = compiler?.run(null, null, null, "-d", classesDir.absolutePath, sourceFile.absolutePath)
            if (result == 0) {
                writeCompiledClassesJar(tempOutput, classesDir)
            } else {
                writeStubRJar(tempOutput, packageName, symbols)
            }
            moveAtomically(tempOutput, output)
            return output
        } finally {
            tempOutput.delete()
            workDir.deleteRecursively()
        }
    }

    private fun writeCompiledClassesJar(
        output: File,
        classesDir: File,
    ) {
        JarOutputStream(output.outputStream()).use { jar ->
            classesDir
                .walkTopDown()
                .filter { it.isFile }
                .sortedBy { file -> file.relativeTo(classesDir).invariantSeparatorsPath }
                .forEach { file ->
                    val name = file.relativeTo(classesDir).invariantSeparatorsPath
                    jar.putNextEntry(JarEntry(name))
                    file.inputStream().use { input -> input.copyTo(jar) }
                    jar.closeEntry()
                }
        }
    }

    private fun inferRPackageName(classesJar: File): String? {
        val pattern = Regex("([A-Za-z_][A-Za-z0-9_]*(?:/[A-Za-z_][A-Za-z0-9_]*)*)/R\\$[A-Za-z_][A-Za-z0-9_]*")
        ZipFile(classesJar).use { zip ->
            return zip
                .entries()
                .asSequence()
                .filter { entry -> !entry.isDirectory && entry.name.endsWith(".class") }
                .mapNotNull { entry ->
                    val text = zip.getInputStream(entry).use { input -> String(input.readBytes(), Charsets.ISO_8859_1) }
                    pattern
                        .find(text)
                        ?.groupValues
                        ?.get(1)
                        ?.replace('/', '.')
                }.firstOrNull()
        }
    }

    private fun readAarSymbols(aar: File): Map<String, List<ResourceSymbol>> =
        ZipFile(aar).use { zip ->
            val entry = zip.getEntry("R.txt") ?: return emptyMap()
            zip.getInputStream(entry).bufferedReader().useLines { lines ->
                lines.mapNotNull(::parseResourceSymbol).groupBy { it.type }
            }
        }

    private fun parseResourceSymbol(line: String): ResourceSymbol? {
        val parts = line.trim().split(Regex("\\s+"), limit = 4)
        if (parts.size < 3) return null
        return ResourceSymbol(valueType = parts[0], type = parts[1], name = parts[2])
    }

    private fun writeStubRJar(
        output: File,
        packageName: String,
        symbols: Map<String, List<ResourceSymbol>>,
    ) {
        val packagePath = packageName.replace('.', '/')
        val assignedInts = assignedIntSymbols(symbols)
        JarOutputStream(output.outputStream()).use { jar ->
            jar.putNextEntry(JarEntry("$packagePath/R.class"))
            jar.write(classFileBytes("$packagePath/R", emptyList()))
            jar.closeEntry()
            symbols.toSortedMap().forEach { (type, _) ->
                jar.putNextEntry(JarEntry("$packagePath/R${'$'}$type.class"))
                jar.write(classFileBytes("$packagePath/R${'$'}$type", assignedInts.getValue(type)))
                jar.closeEntry()
            }
        }
    }

    private fun classFileBytes(
        internalName: String,
        intFields: List<Pair<String, Int>>,
    ): ByteArray {
        val bytes = ByteArrayOutputStream()

        fun writeU1(value: Int) = bytes.write(value)

        fun writeU2(value: Int) {
            bytes.write((value ushr 8) and 0xff)
            bytes.write(value and 0xff)
        }

        fun writeU4(value: Int) {
            bytes.write((value ushr 24) and 0xff)
            bytes.write((value ushr 16) and 0xff)
            bytes.write((value ushr 8) and 0xff)
            bytes.write(value and 0xff)
        }

        fun writeUtf8(value: String) {
            val encoded = value.toByteArray(Charsets.UTF_8)
            writeU1(1)
            writeU2(encoded.size)
            bytes.write(encoded)
        }
        writeU4(0xcafebabe.toInt())
        writeU2(0)
        writeU2(52)
        val integerValueStart = 7
        val fieldNameStart = integerValueStart + intFields.size
        writeU2(fieldNameStart + intFields.size)
        writeUtf8(internalName)
        writeU1(7)
        writeU2(1)
        writeUtf8("java/lang/Object")
        writeU1(7)
        writeU2(3)
        writeUtf8("I")
        writeUtf8("ConstantValue")
        intFields.forEach { (_, value) ->
            writeU1(3)
            writeU4(value)
        }
        intFields.forEach { (name, _) -> writeUtf8(name) }
        writeU2(0x0031)
        writeU2(2)
        writeU2(4)
        writeU2(0)
        writeU2(intFields.size)
        intFields.forEachIndexed { index, (_, value) ->
            writeU2(0x0019)
            writeU2(fieldNameStart + index)
            writeU2(5)
            writeU2(1)
            writeU2(6)
            writeU4(2)
            writeU2(integerValueStart + index)
        }
        writeU2(0)
        writeU2(0)
        return bytes.toByteArray()
    }

    private fun rJavaSource(
        packageName: String,
        symbols: Map<String, List<ResourceSymbol>>,
    ): String =
        buildString {
            appendLine("package $packageName;")
            appendLine("public final class R {")
            appendLine("  private R() {}")
            val assignedInts = assignedIntSymbols(symbols)
            symbols.toSortedMap().forEach { (type, typeSymbols) ->
                appendLine("  public static final class $type {")
                appendLine("    private $type() {}")
                typeSymbols.sortedBy { it.name }.forEach { symbol ->
                    if (symbol.valueType == "int[]") {
                        appendLine("    public static final int[] ${symbol.name} = new int[0];")
                    } else {
                        val value = assignedInts.getValue(type).first { it.first == symbol.name }.second
                        appendLine("    public static final int ${symbol.name} = $value;")
                    }
                }
                appendLine("  }")
            }
            appendLine("}")
        }

    private fun assignedIntSymbols(symbols: Map<String, List<ResourceSymbol>>): Map<String, List<Pair<String, Int>>> {
        var nextValue = SYNTHETIC_RESOURCE_ID_START
        return symbols.toSortedMap().mapValues { (_, typeSymbols) ->
            typeSymbols
                .filter { it.valueType == "int" }
                .sortedBy { it.name }
                .map { symbol -> symbol.name to nextValue++ }
        }
    }

    private fun aarMaterializationFile(
        aar: File,
        kind: String,
        extension: String,
    ): File {
        val fingerprint = sha256("$AAR_MATERIALIZATION_VERSION:${aar.absolutePath}:${aar.length()}:${aar.lastModified()}").take(16)
        return File(
            File(System.getProperty("java.io.tmpdir"), "agentpreview-aar-classes"),
            "${aar.nameWithoutExtension}-$fingerprint-$kind.$extension",
        )
    }

    private fun moveAtomically(
        source: File,
        target: File,
    ) {
        try {
            Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), REPLACE_EXISTING)
        }
    }

    private fun sha256(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val AAR_MATERIALIZATION_VERSION = 3
        const val SYNTHETIC_RESOURCE_ID_START = 0x7f010000
    }

    private data class ResourceSymbol(
        val valueType: String,
        val type: String,
        val name: String,
    )

    private fun androidJar(sdk: Int): List<File> {
        val sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: return emptyList()
        val requested = File(sdkRoot, "platforms/android-$sdk/android.jar")
        if (requested.isFile) return listOf(requested)
        return File(sdkRoot, "platforms")
            .listFiles { file -> file.isDirectory && file.name.startsWith("android-") }
            ?.maxByOrNull { file -> file.name.removePrefix("android-").toIntOrNull() ?: 0 }
            ?.resolve("android.jar")
            ?.takeIf { it.isFile }
            ?.let(::listOf)
            ?: emptyList()
    }

    private fun javaExecutable(): String = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath
}
