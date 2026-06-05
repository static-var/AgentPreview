/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile

class AndroidAssetApkWriterTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `writes regular files under deterministic assets entries`() {
        val assets = tempDir.resolve("merged-assets")
        assets.resolve("fonts").mkdirs()
        assets.resolve("z.txt").writeText("z")
        assets.resolve("fonts/a.ttf").writeText("font")
        assets.resolve("empty-dir").mkdirs()
        val output = tempDir.resolve("assets.apk").also { it.writeText("stale") }

        AndroidAssetApkWriter().write(assets, output)

        ZipFile(output).use { zip ->
            val entries = zip.entries().asSequence().toList()
            assertEquals(listOf("assets/fonts/a.ttf", "assets/z.txt"), entries.map { it.name })
            assertEquals(listOf(315532800000L, 315532800000L), entries.map { it.time })
            assertEquals("font", zip.getInputStream(zip.getEntry("assets/fonts/a.ttf")).reader().readText())
            assertEquals("z", zip.getInputStream(zip.getEntry("assets/z.txt")).reader().readText())
            assertFalse(entries.any { it.name.contains("empty-dir") })
        }
    }
}
