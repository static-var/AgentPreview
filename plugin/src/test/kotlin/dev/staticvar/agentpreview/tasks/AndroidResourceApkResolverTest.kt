/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AndroidResourceApkResolverTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `uses direct apk for local test before linked resource fallback`() {
        val directApk =
            tempDir.resolve("unit-test/resources.ap_").also { file ->
                file.parentFile.mkdirs()
                file.writeText("direct")
            }
        val linkedApk =
            tempDir.resolve("linked/processDebugResources/linked-resources-binary-format-debug.ap_").also { file ->
                file.parentFile.mkdirs()
                file.writeText("linked")
            }

        val resolved = AndroidResourceApkResolver.resolve(directApk, listOf(linkedApk.parentFile))

        assertEquals(directApk, resolved)
    }

    @Test
    fun `keeps direct apk for local test preferred when producer has not materialized it yet`() {
        val directApk = tempDir.resolve("unit-test/resources.ap_")
        val linkedApk =
            tempDir.resolve("linked/processDebugResources/linked-resources-binary-format-debug.ap_").also { file ->
                file.parentFile.mkdirs()
                file.writeText("linked")
            }

        val resolved = AndroidResourceApkResolver.resolve(directApk, listOf(linkedApk.parentFile))

        assertEquals(directApk, resolved)
    }

    @Test
    fun `finds linked binary resource apk inside AGP artifact directory`() {
        val linkedApk =
            tempDir
                .resolve("linked_resources_binary_format/debug/processDebugResources/linked-resources-binary-format-debug.ap_")
                .also { file ->
                    file.parentFile.mkdirs()
                    file.writeText("resources")
                }
        tempDir.resolve("linked_resources_binary_format/debug/processDebugResources/notes.txt").writeText("ignore")

        val resolved = AndroidResourceApkResolver.resolve(null, listOf(tempDir.resolve("linked_resources_binary_format")))

        assertEquals(linkedApk, resolved)
    }
}
