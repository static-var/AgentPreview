/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.config

object AndroidPreviewConfigValidator {
    fun warning(
        robolectricSdk: Int,
        javaMajorVersion: Int,
    ): String? =
        when {
            javaMajorVersion < 17 -> {
                "AgentPreview: android.robolectricSdk=$robolectricSdk requires Java 17+. " +
                    "Current Gradle JVM is Java $javaMajorVersion."
            }

            robolectricSdk >= 36 && javaMajorVersion < 21 -> {
                "AgentPreview: android.robolectricSdk=$robolectricSdk requires Java 21+ for Robolectric runtime. " +
                    "Current Gradle JVM is Java $javaMajorVersion. Use robolectricSdk=35 or run Gradle with Java 21+."
            }

            else -> {
                null
            }
        }
}
