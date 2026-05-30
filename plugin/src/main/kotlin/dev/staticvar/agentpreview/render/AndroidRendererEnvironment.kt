/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.io.File

internal class AndroidRendererEnvironment(
    private val env: Map<String, String> = System.getenv(),
) {
    fun javaExecutable(): JavaExecutableResolution {
        val configured = System.getProperty("agentpreview.java.executable")
        val path = configured ?: File(File(System.getProperty("java.home"), "bin"), "java").absolutePath
        val source = if (configured == null) "java.home" else "agentpreview.java.executable"
        val file = File(path)
        val diagnostic =
            when {
                !file.exists() -> {
                    "$source points to '$path', but that file does not exist. " +
                        "Set agentpreview.java.executable to an executable java binary or configure java.home correctly."
                }

                !file.isFile -> {
                    "$source points to '$path', but it is not a file. " +
                        "Set agentpreview.java.executable to an executable java binary or configure java.home correctly."
                }

                !file.canExecute() -> {
                    "$source points to '$path', but it is not executable. " +
                        "Make it executable or set agentpreview.java.executable to a valid java binary."
                }

                else -> {
                    null
                }
            }
        return JavaExecutableResolution(path, diagnostic)
    }

    fun androidJar(sdk: Int): AndroidJarResolution {
        val sdkRoot = env["ANDROID_HOME"] ?: env["ANDROID_SDK_ROOT"]
        if (sdkRoot.isNullOrBlank()) {
            return AndroidJarResolution(
                files = emptyList(),
                diagnostic =
                    "ANDROID_HOME or ANDROID_SDK_ROOT is not set; renderer classpath will not include android.jar. " +
                        "Install the Android SDK and set ANDROID_HOME, or set ANDROID_SDK_ROOT, so platform android-$sdk can be located.",
            )
        }
        val platformsDir = File(sdkRoot, "platforms")
        val requested = File(platformsDir, "android-$sdk/android.jar")
        if (requested.isFile) return AndroidJarResolution(listOf(requested), null)
        val fallback =
            platformsDir
                .listFiles { file -> file.isDirectory && file.name.startsWith("android-") }
                ?.maxByOrNull { file -> file.name.removePrefix("android-").toIntOrNull() ?: 0 }
                ?.resolve("android.jar")
                ?.takeIf { it.isFile }
        return if (fallback != null) {
            AndroidJarResolution(
                files = listOf(fallback),
                diagnostic =
                    "Android SDK platform android-$sdk was not found under $platformsDir; using ${fallback.parentFile.name} instead. " +
                        "Install the requested platform with sdkmanager 'platforms;android-$sdk' for exact Robolectric SDK matching.",
            )
        } else {
            AndroidJarResolution(
                files = emptyList(),
                diagnostic =
                    "No Android SDK platforms with android.jar were found under $platformsDir. " +
                        "Install platform android-$sdk with sdkmanager 'platforms;android-$sdk'.",
            )
        }
    }
}

data class JavaExecutableResolution(
    val path: String,
    val diagnostic: String?,
)

data class AndroidJarResolution(
    val files: List<File>,
    val diagnostic: String?,
)
