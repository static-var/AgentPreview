/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AndroidFontAssetProbeTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `find font asset paths rejects similarly named non compose resources roots`() {
        tempDir.resolve("notcomposeResources/dev/staticvar/font/foo.ttf").also { file ->
            file.parentFile.mkdirs()
            file.writeText("bad")
        }
        tempDir.resolve("composeResources/dev/staticvar/font/good.ttf").also { file ->
            file.parentFile.mkdirs()
            file.writeText("good")
        }

        val paths = AndroidFontAssetProbe.findFontAssetPaths(tempDir, cap = 8)

        assertEquals(listOf("composeResources/dev/staticvar/font/good.ttf"), paths)
    }

    @Test
    fun `find font asset paths prefers compose resources font files and caps results`() {
        repeat(10) { index ->
            tempDir.resolve("composeResources/dev/staticvar/font/font$index.ttf").also { file ->
                file.parentFile.mkdirs()
                file.writeText("font$index")
            }
        }
        tempDir.resolve("composeResources/dev/staticvar/font/other.otf").writeText("otf")
        tempDir.resolve("composeResources/dev/staticvar/image/logo.png").also { file ->
            file.parentFile.mkdirs()
            file.writeText("png")
        }
        tempDir.resolve("raw-font.ttf").writeText("raw")
        tempDir.resolve("notcomposeResources/dev/staticvar/font/foo.ttf").also { file ->
            file.parentFile.mkdirs()
            file.writeText("bad")
        }

        val paths = AndroidFontAssetProbe.findFontAssetPaths(tempDir, cap = 8)

        assertEquals(8, paths.size)
        assertEquals((0..7).map { "composeResources/dev/staticvar/font/font$it.ttf" }, paths)
    }
}
