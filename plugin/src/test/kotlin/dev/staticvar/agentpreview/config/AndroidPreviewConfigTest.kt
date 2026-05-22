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
    fun `SDK 36 warns when Gradle runs below Java 21`() {
        val warning = AndroidPreviewConfigValidator.warning(robolectricSdk = 36, javaMajorVersion = 17)

        assertEquals(
            "AgentPreview: android.robolectricSdk=36 requires Java 21+ for Robolectric runtime. " +
                "Current Gradle JVM is Java 17. Use robolectricSdk=35 or run Gradle with Java 21+.",
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
