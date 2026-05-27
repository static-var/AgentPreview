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
import java.util.zip.ZipFile

/** Materializes Android AAR artifacts into entries that are loadable by a plain Java classpath. */
internal class AarClasspathMaterializer(
    private val outputDir: File = File(System.getProperty("java.io.tmpdir"), "agentpreview-aar-classpath"),
) {
    private val cacheVersion = "v2"

    fun materialize(classpath: Iterable<File>): List<File> =
        classpath.flatMap { file ->
            if (file.isFile && file.extension.equals("aar", ignoreCase = true)) {
                materializeAar(file)
            } else {
                listOf(file)
            }
        }

    private fun materializeAar(aar: File): List<File> =
        ZipFile(aar).use { zip ->
            zip
                .entries()
                .asSequence()
                .filter { entry ->
                    !entry.isDirectory && (entry.name == "classes.jar" || (entry.name.startsWith("libs/") && entry.name.endsWith(".jar")))
                }.sortedBy { entry -> entry.name }
                .map { entry ->
                    val output = outputFile(aar, entry.name)
                    output.parentFile.mkdirs()
                    if (!output.exists()) {
                        val temp = Files.createTempFile(output.parentFile.toPath(), "${output.name}.", ".tmp")
                        try {
                            zip.getInputStream(entry).use { input -> Files.newOutputStream(temp).use(input::copyTo) }
                            moveAtomically(temp.toFile(), output)
                        } finally {
                            Files.deleteIfExists(temp)
                        }
                    }
                    output
                }.toList()
        }

    private fun outputFile(
        aar: File,
        entryName: String,
    ): File {
        val fingerprint = sha256("${aar.absolutePath}:${aar.length()}:${aar.lastModified()}").take(16)
        val name = if (entryName == "classes.jar") "classes.jar" else File(entryName).name
        return File(outputDir, "$cacheVersion/${aar.nameWithoutExtension}-$fingerprint/$name")
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
            .joinToString("") { "%02x".format(it) }
}
