/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.dependencies

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AarClasspathMaterializerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `materializes aar classes and embedded jars without keeping raw aar on java classpath`() {
        val classesJar =
            tempDir.resolve("classes-source.jar").also {
                writeJar(it, "androidx/compose/ui/tooling/preview/PreviewParameterProvider.class")
            }
        val embeddedJar = tempDir.resolve("embedded-source.jar").also { writeJar(it, "androidx/compose/ui/tooling/data/Group.class") }
        val aar = tempDir.resolve("ui-tooling-android.aar")
        writeAar(aar, classesJar, embeddedJar)

        val materialized = AarClasspathMaterializer(tempDir.resolve("materialized")).materialize(listOf(aar))

        assertFalse(aar in materialized)
        assertEquals(2, materialized.size)
        assertTrue(materialized.all { it.extension == "jar" })
        assertTrue(materialized.any { containsEntry(it, "androidx/compose/ui/tooling/preview/PreviewParameterProvider.class") })
        assertTrue(materialized.any { containsEntry(it, "androidx/compose/ui/tooling/data/Group.class") })
    }

    private fun writeAar(
        aar: File,
        classesJar: File,
        embeddedJar: File,
    ) {
        ZipOutputStream(aar.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.jar"))
            classesJar.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("libs/embedded.jar"))
            embeddedJar.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun writeJar(
        jar: File,
        entryName: String,
    ) {
        JarOutputStream(jar.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(entryName))
            output.write(byteArrayOf(0))
            output.closeEntry()
        }
    }

    private fun containsEntry(
        jar: File,
        entryName: String,
    ): Boolean =
        java.util.jar
            .JarFile(jar)
            .use { it.getEntry(entryName) != null }
}
