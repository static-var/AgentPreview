/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes a deterministic synthetic APK/zip exposing a merged assets directory under assets/. */
internal class AndroidAssetApkWriter {
    fun write(
        mergedAssetsDir: File,
        outputFile: File,
    ): File {
        require(mergedAssetsDir.isDirectory) { "Merged assets directory does not exist: ${mergedAssetsDir.absolutePath}" }
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        val root = mergedAssetsDir.toPath()
        val files =
            mergedAssetsDir
                .walkTopDown()
                .filter { it.isFile }
                .sortedBy { root.relativize(it.toPath()).joinToString("/") }
                .toList()

        ZipOutputStream(outputFile.outputStream()).use { zip ->
            files.forEach { file ->
                val relativePath = root.relativize(file.toPath()).joinToString("/")
                val entry = ZipEntry("assets/$relativePath")
                entry.time = STABLE_ZIP_TIMESTAMP_MS
                zip.putNextEntry(entry)
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return outputFile
    }

    private fun java.nio.file.Path.joinToString(separator: String): String =
        iterator().asSequence().joinToString(separator) { it.toString() }

    private companion object {
        // 1980-01-01, the earliest broadly supported ZIP timestamp.
        const val STABLE_ZIP_TIMESTAMP_MS = 315532800000L
    }
}
