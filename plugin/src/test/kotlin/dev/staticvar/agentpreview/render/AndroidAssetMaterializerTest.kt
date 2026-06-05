/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AndroidAssetMaterializerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `copies regular files from input roots preserving relative paths and cleans stale output`() {
        val rootA = tempDir.resolve("rootA")
        val rootB = tempDir.resolve("rootB")
        rootA.resolve("images/icon.txt").writeTextCreatingParents("icon")
        rootB.resolve("data/config.json").writeTextCreatingParents("{}")
        val outputRoot = tempDir.resolve("merged")
        outputRoot.resolve("stale.txt").writeTextCreatingParents("stale")

        val materialized = AndroidAssetMaterializer().materialize(setOf(rootB, rootA, tempDir.resolve("missing")), outputRoot)

        assertEquals(outputRoot, materialized)
        assertEquals("icon", outputRoot.resolve("images/icon.txt").readText())
        assertEquals("{}", outputRoot.resolve("data/config.json").readText())
        assertFalse(outputRoot.resolve("stale.txt").exists())
    }

    @Test
    fun `returns null when no files are materialized`() {
        val emptyRoot = tempDir.resolve("empty").also { it.mkdirs() }
        val outputRoot = tempDir.resolve("merged")
        outputRoot.resolve("stale.txt").writeTextCreatingParents("stale")

        val materialized = AndroidAssetMaterializer().materialize(setOf(emptyRoot, tempDir.resolve("missing")), outputRoot)

        assertNull(materialized)
        assertFalse(outputRoot.exists())
    }

    @Test
    fun `allows duplicate relative paths with identical bytes`() {
        val rootA = tempDir.resolve("rootA")
        val rootB = tempDir.resolve("rootB")
        rootA.resolve("shared/asset.txt").writeTextCreatingParents("same")
        rootB.resolve("shared/asset.txt").writeTextCreatingParents("same")
        val outputRoot = tempDir.resolve("merged")

        val materialized = AndroidAssetMaterializer().materialize(setOf(rootA, rootB), outputRoot)

        assertEquals(outputRoot, materialized)
        assertEquals("same", outputRoot.resolve("shared/asset.txt").readText())
    }

    @Test
    fun `fails on duplicate relative paths with different bytes and names both sources`() {
        val rootA = tempDir.resolve("rootA")
        val rootB = tempDir.resolve("rootB")
        val first = rootA.resolve("shared/asset.txt").also { it.writeTextCreatingParents("first") }
        val conflict = rootB.resolve("shared/asset.txt").also { it.writeTextCreatingParents("second") }
        val outputRoot = tempDir.resolve("merged")

        val error =
            assertThrows(IllegalStateException::class.java) {
                AndroidAssetMaterializer().materialize(setOf(rootA, rootB), outputRoot)
            }

        assertTrue(error.message!!.contains("shared/asset.txt"))
        assertTrue(error.message!!.contains(first.absolutePath))
        assertTrue(error.message!!.contains(conflict.absolutePath))
    }

    private fun File.writeTextCreatingParents(text: String) {
        parentFile.mkdirs()
        writeText(text)
    }
}
