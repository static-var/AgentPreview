/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.dependencies

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile
import javax.tools.JavaCompiler
import javax.tools.ToolProvider

internal class SyntheticRJarWriter {
    fun writeForAar(
        aar: File,
        classesJar: File,
    ): File? {
        val output = aarMaterializationFile(aar, "r", "jar")
        if (output.isFile) return output
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
            if (!compileGeneratedRJava(sourceFile, classesDir)) return null
            writeCompiledClassesJar(tempOutput, classesDir)
            moveAtomically(tempOutput, output)
            return output
        } finally {
            tempOutput.delete()
            workDir.deleteRecursively()
        }
    }

    private fun compileGeneratedRJava(
        sourceFile: File,
        classesDir: File,
    ): Boolean {
        val compiler = ToolProvider.getSystemJavaCompiler() ?: return false
        val args = listOf("--release", "8", "-Xlint:-options", "-d", classesDir.absolutePath)
        return runJavac(compiler, args, sourceFile)
    }

    private fun runJavac(
        compiler: JavaCompiler,
        args: List<String>,
        sourceFile: File,
    ): Boolean =
        runCatching {
            compiler.getStandardFileManager(null, null, null).use { fileManager ->
                compiler.getTask(null, fileManager, null, args, null, fileManager.getJavaFileObjects(sourceFile)).call()
            }
        }.getOrDefault(false)

    private fun writeCompiledClassesJar(
        output: File,
        classesDir: File,
    ) {
        JarOutputStream(output.outputStream()).use { jar ->
            classesDir
                .walkTopDown()
                .filter { it.isFile }
                .sortedBy { file -> jarEntrySortKey(file.relativeTo(classesDir).invariantSeparatorsPath) }
                .forEach { file ->
                    val name = file.relativeTo(classesDir).invariantSeparatorsPath
                    jar.putNextEntry(JarEntry(name))
                    file.inputStream().use { input -> input.copyTo(jar) }
                    jar.closeEntry()
                }
        }
    }

    private fun jarEntrySortKey(entryName: String): String = entryName.replace('$', '~')

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

    /**
     * Synthetic R classes provide best-effort binary compatibility for code that references IDs.
     * They do not load Android resources; missing resource contents are still handled by renderer diagnostics.
     */
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
        const val AAR_MATERIALIZATION_VERSION = 5
        const val SYNTHETIC_RESOURCE_ID_START = 0x7f010000
    }

    private data class ResourceSymbol(
        val valueType: String,
        val type: String,
        val name: String,
    )
}
