/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.export

import dev.staticvar.agentpreview.model.Viewport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SnapshotOutputPathTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `keeps non-colliding path format unchanged`() {
        val destination =
            SnapshotOutputPath().resolve(
                outputRoot = dir,
                previewId = ":app:LoginPreview",
                viewport = Viewport(width = 1, height = 1, density = 1f, platform = "android", name = "phone"),
            )

        assertEquals(dir.resolve("app-LoginPreview/android-phone"), destination)
    }

    @Test
    fun `fails when sanitized path already exists`() {
        val path = SnapshotOutputPath()
        val destination = path.resolve(dir, ":app:LoginPreview")
        destination.mkdirs()

        val failure = assertThrows(IllegalStateException::class.java) { path.validateAvailable(destination) }

        assertTrue(failure.message!!.contains("Snapshot output path collision"))
        assertTrue(failure.message!!.contains(destination.absolutePath))
    }

    @Test
    fun `detects duplicate sanitized destinations before directories exist`() {
        val path = SnapshotOutputPath()
        val phone = Viewport(width = 1, height = 1, density = 1f, platform = "android", name = "phone")
        val tablet = Viewport(width = 1, height = 1, density = 1f, platform = "android", name = "tablet")
        val requests =
            listOf(
                path.resolve(dir, ":app:Login Preview", phone),
                path.resolve(dir, ":app:Login/Preview", phone),
                path.resolve(dir, ":app:Login Preview", tablet),
            )

        val duplicates = path.duplicateDestinations(requests)

        assertEquals(listOf(dir.resolve("app-Login-Preview/android-phone")), duplicates)
        assertFalse(dir.resolve("app-Login-Preview/android-phone").exists())
    }
}
