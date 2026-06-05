/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties

class RobolectricTestConfigWriterTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `removes stale resource files and leaves empty res directory`() {
        val configRoot = tempDir.resolve("config")
        val staleFile = configRoot.resolve("res/values/stale.xml")
        staleFile.parentFile.mkdirs()
        staleFile.writeText("<resources />")

        RobolectricTestConfigWriter().write(configRoot, tempDir.resolve("assets"))

        val resources = configRoot.resolve("res")
        assertTrue(resources.isDirectory)
        assertEquals(emptyList<File>(), resources.walkTopDown().drop(1).toList())
    }

    @Test
    fun `writes robolectric test config files and returns classpath root`() {
        val configRoot = tempDir.resolve("config")
        val mergedAssetsDir = tempDir.resolve("assets").also { it.mkdirs() }

        val classpathRoot = RobolectricTestConfigWriter().write(configRoot, mergedAssetsDir)

        assertEquals(configRoot, classpathRoot)
        val propertiesFile = configRoot.resolve("com/android/tools/test_config.properties")
        val manifest = configRoot.resolve("AndroidManifest.xml")
        val resources = configRoot.resolve("res")
        assertTrue(propertiesFile.isFile)
        assertTrue(manifest.isFile)
        assertTrue(resources.isDirectory)
        assertTrue(manifest.readText().contains("<manifest"))

        val properties = Properties().also { props -> propertiesFile.inputStream().use(props::load) }
        assertEquals(mergedAssetsDir.absolutePath, properties.getProperty("android_merged_assets"))
        assertEquals(resources.absolutePath, properties.getProperty("android_merged_resources"))
        assertEquals(manifest.absolutePath, properties.getProperty("android_merged_manifest"))
        assertEquals("dev.staticvar.agentpreview.render", properties.getProperty("android_custom_package"))
    }

    @Test
    fun `properties escaping preserves backslashes and non ascii paths`() {
        val configRoot = tempDir.resolve("config")
        val mergedAssetsDir = tempDir.resolve("asset\\folder-é-你好")

        RobolectricTestConfigWriter().write(configRoot, mergedAssetsDir)

        val propertiesFile = configRoot.resolve("com/android/tools/test_config.properties")
        val properties = Properties().also { props -> propertiesFile.inputStream().use(props::load) }
        assertEquals(mergedAssetsDir.absolutePath, properties.getProperty("android_merged_assets"))
    }

    @Test
    fun `writes deterministic test config properties without comments`() {
        val configRoot = tempDir.resolve("config")
        val mergedAssetsDir = tempDir.resolve("assets").also { it.mkdirs() }
        val propertiesFile = configRoot.resolve("com/android/tools/test_config.properties")

        RobolectricTestConfigWriter().write(configRoot, mergedAssetsDir)
        val firstWrite = propertiesFile.readText()

        Thread.sleep(1100)

        RobolectricTestConfigWriter().write(configRoot, mergedAssetsDir)
        val secondWrite = propertiesFile.readText()

        assertEquals(firstWrite, secondWrite)
        assertFalse(secondWrite.lineSequence().any { it.startsWith("#") })
    }
}
