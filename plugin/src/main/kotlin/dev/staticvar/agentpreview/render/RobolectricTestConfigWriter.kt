/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.io.File

class RobolectricTestConfigWriter {
    fun write(
        configRoot: File,
        mergedAssetsDir: File,
    ): File {
        val manifest = configRoot.resolve("AndroidManifest.xml")
        val resources = configRoot.resolve("res")
        val propertiesFile = configRoot.resolve("com/android/tools/test_config.properties")

        if (resources.exists()) {
            resources.deleteRecursively()
        }
        resources.mkdirs()
        manifest.parentFile.mkdirs()
        manifest.writeText(
            """<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
""",
        )
        propertiesFile.parentFile.mkdirs()
        val properties =
            mapOf(
                "android_merged_assets" to mergedAssetsDir.absolutePath,
                "android_merged_resources" to resources.absolutePath,
                "android_merged_manifest" to manifest.absolutePath,
                "android_custom_package" to "dev.staticvar.agentpreview.render",
            )
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
}
