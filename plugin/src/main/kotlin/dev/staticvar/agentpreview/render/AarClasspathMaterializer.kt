/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.dependencies.SyntheticRJarWriter
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal class AarClasspathMaterializer(
    private val syntheticRJarWriter: SyntheticRJarWriter = SyntheticRJarWriter(),
) : ClasspathMaterializer {
    /* AARs are not directly loadable by the child JVM; materialization exposes classes.jar, embedded libs, and
     * a best-effort synthetic R jar for binary compatibility without pretending to load Android resources. */
    override fun materialize(classpath: List<File>): List<File> =
        classpath.flatMap { file ->
            if (file.extension == "aar" && file.isFile) {
                materializeAarClasspath(file)
            } else {
                listOf(file)
            }
        }

    private fun materializeAarClasspath(aar: File): List<File> {
        val classesJar = extractAarClassesJar(aar)
        return listOfNotNull(classesJar) +
            extractAarEmbeddedJars(aar) +
            listOfNotNull(classesJar?.let { syntheticRJarWriter.writeForAar(aar, it) })
    }

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
    }
}
