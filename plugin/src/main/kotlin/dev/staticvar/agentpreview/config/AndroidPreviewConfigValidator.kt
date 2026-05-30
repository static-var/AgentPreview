/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.config

internal object AndroidPreviewConfigValidator {
    fun warning(
        robolectricSdk: Int,
        javaMajorVersion: Int,
    ): String? =
        when {
            javaMajorVersion < 17 -> {
                "AgentPreview: android.robolectricSdk=$robolectricSdk requires Java 17+. " +
                    "Current Gradle JVM is Java $javaMajorVersion."
            }

            robolectricSdk != SUPPORTED_ROBOLECTRIC_SDK -> {
                "AgentPreview: android.robolectricSdk=$robolectricSdk is not supported by the Android renderer yet. " +
                    "Use robolectricSdk=$SUPPORTED_ROBOLECTRIC_SDK; other values fail for non-fake capture."
            }

            else -> {
                null
            }
        }

    private const val SUPPORTED_ROBOLECTRIC_SDK = 35
}
