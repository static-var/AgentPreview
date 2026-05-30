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

    @Test
    fun `materializes synthetic R jar for aar binary resource references`() {
        val classesJar =
            tempDir.resolve("classes-source.jar").also {
                writeJar(
                    it,
                    "androidx/example/lib/UsesR.class",
                    "constant pool reference to androidx/example/lib/R${'$'}id".toByteArray(),
                )
            }
        val embeddedJar = tempDir.resolve("embedded-source.jar").also { writeJar(it, "androidx/example/lib/Embedded.class") }
        val aar = tempDir.resolve("example.aar")
        writeAar(aar, classesJar, embeddedJar, rTxt = "int id action_button 0x0\n")

        val materialized = AarClasspathMaterializer(tempDir.resolve("materialized")).materialize(listOf(aar))

        assertTrue(materialized.any { containsEntry(it, "androidx/example/lib/R${'$'}id.class") })
    }

    private fun writeAar(
        aar: File,
        classesJar: File,
        embeddedJar: File,
        rTxt: String? = null,
    ) {
        ZipOutputStream(aar.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.jar"))
            classesJar.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("libs/embedded.jar"))
            embeddedJar.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            if (rTxt != null) {
                zip.putNextEntry(ZipEntry("R.txt"))
                zip.write(rTxt.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun writeJar(
        jar: File,
        entryName: String,
        bytes: ByteArray = byteArrayOf(0),
    ) {
        JarOutputStream(jar.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(entryName))
            output.write(bytes)
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
