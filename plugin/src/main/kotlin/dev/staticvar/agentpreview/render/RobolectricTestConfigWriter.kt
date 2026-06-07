/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.io.File

data class RobolectricTestConfig(
    val resourceApk: File? = null,
    val mergedAssetsDir: File? = null,
    val mergedManifest: File? = null,
    val customPackage: String? = null,
)

class RobolectricTestConfigWriter {
    fun write(
        configRoot: File,
        config: RobolectricTestConfig,
    ): File {
        val manifest = configRoot.resolve("AndroidManifest.xml")
        val resources = configRoot.resolve("res")
        val propertiesFile = configRoot.resolve("com/android/tools/test_config.properties")

        if (resources.exists()) {
            resources.deleteRecursively()
        }
        if (manifest.exists()) {
            manifest.delete()
        }

        val manifestForConfig =
            config.mergedManifest ?: run {
                manifest.parentFile.mkdirs()
                manifest.writeText(
                    """<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
""",
                )
                manifest
            }

        val mergedResources =
            if (config.resourceApk == null) {
                resources.mkdirs()
                resources
            } else {
                null
            }

        propertiesFile.parentFile.mkdirs()
        val properties =
            buildMap {
                config.mergedAssetsDir?.let { put("android_merged_assets", it.absolutePath) }
                mergedResources?.let { put("android_merged_resources", it.absolutePath) }
                put("android_merged_manifest", manifestForConfig.absolutePath)
                put("android_custom_package", config.customPackage ?: DEFAULT_CUSTOM_PACKAGE)
                config.resourceApk?.let { put("android_resource_apk", it.absolutePath) }
            }
        propertiesFile.writeText(
            properties
                .toSortedMap()
                .entries
                .joinToString(separator = "\n", postfix = "\n") { (key, value) ->
                    "${escapePropertiesText(key, escapeSpace = true)}=${escapePropertiesText(value, escapeSpace = false)}"
                },
        )

        return configRoot
    }

    private fun escapePropertiesText(
        text: String,
        escapeSpace: Boolean,
    ): String =
        buildString {
            text.forEachIndexed { index, char ->
                when (char) {
                    ' ' -> {
                        if (escapeSpace || index == 0) append('\\')
                        append(' ')
                    }

                    '\\' -> {
                        append("\\\\")
                    }

                    '\t' -> {
                        append("\\t")
                    }

                    '\n' -> {
                        append("\\n")
                    }

                    '\r' -> {
                        append("\\r")
                    }

                    '\u000C' -> {
                        append("\\f")
                    }

                    '=', ':', '#', '!' -> {
                        append('\\').append(char)
                    }

                    else -> {
                        if (char.code < 0x20 || char.code > 0x7e) {
                            append("\\u")
                            append(
                                char.code
                                    .toString(16)
                                    .uppercase()
                                    .padStart(4, '0'),
                            )
                        } else {
                            append(char)
                        }
                    }
                }
            }
        }

    private companion object {
        const val DEFAULT_CUSTOM_PACKAGE = "dev.staticvar.agentpreview.render"
    }
}
