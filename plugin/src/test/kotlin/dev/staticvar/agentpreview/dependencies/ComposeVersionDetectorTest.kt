/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.dependencies

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ComposeVersionDetectorTest {
    @Test
    fun `detects version from compose ui module`() {
        val result =
            ComposeVersionDetector().detect(
                listOf(
                    ResolvedModuleCoordinate("androidx.compose.ui", "ui", "1.9.3"),
                ),
            )

        assertEquals("1.9.3", result.version)
        assertEquals(listOf("androidx.compose.ui:ui:1.9.3"), result.matchedModules)
    }

    @Test
    fun `detects version from ui tooling preview when compose ui is absent`() {
        val result =
            ComposeVersionDetector().detect(
                listOf(
                    ResolvedModuleCoordinate("androidx.compose.ui", "ui-tooling-preview", "1.8.2"),
                ),
            )

        assertEquals("1.8.2", result.version)
        assertEquals(listOf("androidx.compose.ui:ui-tooling-preview:1.8.2"), result.matchedModules)
    }

    @Test
    fun `detects BOM resolved version from resolved graph`() {
        val result =
            ComposeVersionDetector().detect(
                listOf(
                    ResolvedModuleCoordinate("androidx.compose", "compose-bom", "2025.01.00"),
                    ResolvedModuleCoordinate("androidx.compose.ui", "ui", "1.10.0-alpha04"),
                    ResolvedModuleCoordinate("androidx.compose.ui", "ui-tooling-preview", "1.10.0-alpha04"),
                ),
            )

        assertEquals("1.10.0-alpha04", result.version)
        assertEquals(
            listOf(
                "androidx.compose.ui:ui:1.10.0-alpha04",
                "androidx.compose.ui:ui-tooling-preview:1.10.0-alpha04",
            ),
            result.matchedModules,
        )
    }

    @Test
    fun `returns null when no compose ui version can be inferred`() {
        val result =
            ComposeVersionDetector().detect(
                listOf(
                    ResolvedModuleCoordinate("androidx.activity", "activity-compose", "1.10.1"),
                ),
            )

        assertNull(result.version)
        assertEquals(emptyList<String>(), result.matchedModules)
    }
}
