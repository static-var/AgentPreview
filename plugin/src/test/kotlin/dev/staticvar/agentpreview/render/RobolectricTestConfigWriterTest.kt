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
    fun `removes stale resource files and leaves empty res directory for assets-only config`() {
        val configRoot = tempDir.resolve("config")
        val staleFile = configRoot.resolve("res/values/stale.xml")
        staleFile.parentFile.mkdirs()
        staleFile.writeText("<resources />")

        RobolectricTestConfigWriter().write(
            configRoot,
            RobolectricTestConfig(
                mergedAssetsDir = tempDir.resolve("assets"),
            ),
        )

        val resources = configRoot.resolve("res")
        assertTrue(resources.isDirectory)
        assertEquals(emptyList<File>(), resources.walkTopDown().drop(1).toList())
    }

    @Test
    fun `writes robolectric test config files and returns classpath root`() {
        val configRoot = tempDir.resolve("config")
        val mergedAssetsDir = tempDir.resolve("assets").also { it.mkdirs() }

        val classpathRoot =
            RobolectricTestConfigWriter().write(
                configRoot,
                RobolectricTestConfig(
                    mergedAssetsDir = mergedAssetsDir,
                ),
            )

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
    fun `writes android_resource_apk with real merged manifest and custom package`() {
        val configRoot = tempDir.resolve("config")
        val resourceApk =
            tempDir.resolve("intermediates/resources-debug.ap_").also { file ->
                file.parentFile.mkdirs()
                file.writeText("apk")
            }
        val mergedManifest =
            tempDir.resolve("intermediates/AndroidManifest.xml").also { file ->
                file.parentFile.mkdirs()
                file.writeText("<manifest package=\"dev.example.app\" />")
            }

        RobolectricTestConfigWriter().write(
            configRoot,
            RobolectricTestConfig(
                resourceApk = resourceApk,
                mergedManifest = mergedManifest,
                customPackage = "dev.example.app",
            ),
        )

        val properties = configRoot.resolve("com/android/tools/test_config.properties").loadProperties()
        assertEquals(resourceApk.absolutePath, properties.getProperty("android_resource_apk"))
        assertEquals(mergedManifest.absolutePath, properties.getProperty("android_merged_manifest"))
        assertEquals("dev.example.app", properties.getProperty("android_custom_package"))
        assertEquals(null, properties.getProperty("android_merged_resources"))
        assertFalse(configRoot.resolve("AndroidManifest.xml").exists())
        assertFalse(configRoot.resolve("res").exists())
    }

    @Test
    fun `resources-only config writes no android_merged_assets`() {
        val configRoot = tempDir.resolve("config")
        val resourceApk = tempDir.resolve("resources.ap_").also { it.writeText("apk") }
        val mergedManifest =
            tempDir.resolve("manifest/AndroidManifest.xml").also { file ->
                file.parentFile.mkdirs()
                file.writeText("<manifest />")
            }

        RobolectricTestConfigWriter().write(
            configRoot,
            RobolectricTestConfig(
                resourceApk = resourceApk,
                mergedManifest = mergedManifest,
                customPackage = "dev.example.resources",
            ),
        )

        val properties = configRoot.resolve("com/android/tools/test_config.properties").loadProperties()
        assertEquals(resourceApk.absolutePath, properties.getProperty("android_resource_apk"))
        assertEquals(null, properties.getProperty("android_merged_assets"))
    }

    @Test
    fun `resource config preserves optional assets when present`() {
        val configRoot = tempDir.resolve("config")
        val mergedAssetsDir = tempDir.resolve("merged-assets").also { it.mkdirs() }
        val resourceApk = tempDir.resolve("resources.ap_").also { it.writeText("apk") }
        val mergedManifest =
            tempDir.resolve("manifest/AndroidManifest.xml").also { file ->
                file.parentFile.mkdirs()
                file.writeText("<manifest />")
            }

        RobolectricTestConfigWriter().write(
            configRoot,
            RobolectricTestConfig(
                resourceApk = resourceApk,
                mergedAssetsDir = mergedAssetsDir,
                mergedManifest = mergedManifest,
                customPackage = "dev.example.resources",
            ),
        )

        val properties = configRoot.resolve("com/android/tools/test_config.properties").loadProperties()
        assertEquals(resourceApk.absolutePath, properties.getProperty("android_resource_apk"))
        assertEquals(mergedAssetsDir.absolutePath, properties.getProperty("android_merged_assets"))
        assertEquals(null, properties.getProperty("android_merged_resources"))
    }

    @Test
    fun `properties escaping preserves backslashes and non ascii paths`() {
        val configRoot = tempDir.resolve("config")
        val mergedAssetsDir = tempDir.resolve("asset\\folder-é-你好")

        RobolectricTestConfigWriter().write(
            configRoot,
            RobolectricTestConfig(
                mergedAssetsDir = mergedAssetsDir,
            ),
        )

        val propertiesFile = configRoot.resolve("com/android/tools/test_config.properties")
        val properties = Properties().also { props -> propertiesFile.inputStream().use(props::load) }
        assertEquals(mergedAssetsDir.absolutePath, properties.getProperty("android_merged_assets"))
    }

    @Test
    fun `writes deterministic test config properties without comments`() {
        val configRoot = tempDir.resolve("config")
        val mergedAssetsDir = tempDir.resolve("assets").also { it.mkdirs() }
        val propertiesFile = configRoot.resolve("com/android/tools/test_config.properties")

        RobolectricTestConfigWriter().write(
            configRoot,
            RobolectricTestConfig(
                mergedAssetsDir = mergedAssetsDir,
            ),
        )
        val firstWrite = propertiesFile.readText()

        Thread.sleep(1100)

        RobolectricTestConfigWriter().write(
            configRoot,
            RobolectricTestConfig(
                mergedAssetsDir = mergedAssetsDir,
            ),
        )
        val secondWrite = propertiesFile.readText()

        assertEquals(firstWrite, secondWrite)
        assertFalse(secondWrite.lineSequence().any { it.startsWith("#") })
    }

    private fun File.loadProperties(): Properties = Properties().also { props -> inputStream().use(props::load) }
}
