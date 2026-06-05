/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AndroidRendererEnvironmentTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `android jar uses ANDROID_HOME before ANDROID_SDK_ROOT and local properties`() {
        val androidHome = sdkWithPlatform("androidHome", 35)
        sdkWithPlatform("androidSdkRoot", 35)
        sdkWithPlatform("localSdk", 35).also { localSdk ->
            File(tempDir, "local.properties").writeText("sdk.dir=${localSdk.absolutePath}\n")
        }

        val resolution =
            AndroidRendererEnvironment(
                env =
                    mapOf(
                        "ANDROID_HOME" to androidHome.absolutePath,
                        "ANDROID_SDK_ROOT" to File(tempDir, "androidSdkRoot").absolutePath,
                    ),
                baseDir = tempDir,
            ).androidJar(35)

        assertEquals(listOf(File(androidHome, "platforms/android-35/android.jar")), resolution.files)
        assertEquals(null, resolution.diagnostic)
    }

    @Test
    fun `android jar uses local properties sdk dir when env vars are absent`() {
        val localSdk = sdkWithPlatform("local sdk with spaces", 34)
        File(tempDir, "local.properties").outputStream().use { output ->
            java.util.Properties().apply {
                setProperty("sdk.dir", localSdk.absolutePath)
                store(output, null)
            }
        }

        val resolution = AndroidRendererEnvironment(env = emptyMap(), baseDir = tempDir).androidJar(34)

        assertEquals(listOf(File(localSdk, "platforms/android-34/android.jar")), resolution.files)
        assertEquals(null, resolution.diagnostic)
    }

    @Test
    fun `missing sdk diagnostic mentions env vars and local properties sdk dir`() {
        val resolution = AndroidRendererEnvironment(env = emptyMap(), baseDir = tempDir).androidJar(33)

        assertTrue(resolution.files.isEmpty())
        assertTrue(resolution.diagnostic!!.contains("ANDROID_HOME"))
        assertTrue(resolution.diagnostic!!.contains("ANDROID_SDK_ROOT"))
        assertTrue(resolution.diagnostic!!.contains("local.properties sdk.dir"))
    }

    private fun sdkWithPlatform(
        name: String,
        sdk: Int,
    ): File {
        val sdkRoot = File(tempDir, name)
        File(sdkRoot, "platforms/android-$sdk").mkdirs()
        File(sdkRoot, "platforms/android-$sdk/android.jar").writeText("jar")
        return sdkRoot
    }
}
