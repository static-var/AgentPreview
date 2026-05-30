/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndroidPreviewConfigTest {
    @Test
    fun `default Android viewport is phone`() {
        assertEquals(
            listOf(ConfiguredViewport(platform = "android", name = "phone", width = 393, height = 852)),
            AndroidPreviewConfigDefaults.viewports,
        )
    }

    @Test
    fun `default Android variant is debug`() {
        assertEquals("debug", AndroidPreviewConfigDefaults.VARIANT)
    }

    @Test
    fun `SDK 36 warns that configurable renderer SDK is not supported`() {
        val warning = AndroidPreviewConfigValidator.warning(robolectricSdk = 36, javaMajorVersion = 21)

        assertEquals(
            "AgentPreview: android.robolectricSdk=36 is not supported by the Android renderer yet. " +
                "Use robolectricSdk=35; other values fail for non-fake capture.",
            warning,
        )
    }

    @Test
    fun `SDK 35 does not warn on Java 17 or Java 21`() {
        assertEquals(null, AndroidPreviewConfigValidator.warning(robolectricSdk = 35, javaMajorVersion = 17))
        assertEquals(null, AndroidPreviewConfigValidator.warning(robolectricSdk = 35, javaMajorVersion = 21))
    }

    @Test
    fun `all SDKs warn below Java 17`() {
        val warning = AndroidPreviewConfigValidator.warning(robolectricSdk = 35, javaMajorVersion = 11)

        assertEquals(
            "AgentPreview: android.robolectricSdk=35 requires Java 17+. Current Gradle JVM is Java 11.",
            warning,
        )
    }
}
