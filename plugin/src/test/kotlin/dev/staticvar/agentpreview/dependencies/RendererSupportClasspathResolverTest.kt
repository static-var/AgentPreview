/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.dependencies

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RendererSupportClasspathResolverTest {
    private val resolver = RendererSupportClasspathResolver()

    @Test
    fun `keeps consumer provided renderer support artifacts and adds only missing ones`() {
        val resolution =
            resolver.resolve(
                selectedVariant = "debug",
                inspectedConfigurations = listOf("debugRuntimeClasspath"),
                runtimeArtifacts =
                    listOf(
                        ResolvedArtifactCoordinate("androidx.compose.ui", "ui", "1.9.0", "/consumer/ui.jar"),
                        ResolvedArtifactCoordinate("androidx.compose.ui", "ui-tooling", "1.9.0", "/consumer/ui-tooling.jar"),
                        ResolvedArtifactCoordinate("androidx.test", "core", "1.7.1", "/consumer/test-core.jar"),
                        ResolvedArtifactCoordinate("androidx.test", "monitor", "1.8.1", "/consumer/test-monitor.jar"),
                    ),
            )

        assertEquals("1.9.0", resolution.composeVersion)
        assertEquals(
            listOf(
                "/consumer/ui-tooling.jar",
                "/consumer/test-core.jar",
                "/consumer/test-monitor.jar",
            ),
            resolution.consumerArtifactFiles,
        )
        assertEquals(
            listOf(
                "androidx.compose.ui:ui-tooling-data:1.9.0",
            ),
            resolution.pluginArtifactCoordinates,
        )
        assertTrue(resolution.warnings.isEmpty())
    }

    @Test
    fun `keeps consumer provided Android renderer support artifacts and adds only missing ones`() {
        val resolution =
            resolver.resolve(
                selectedVariant = "debug",
                inspectedConfigurations = listOf("debugRuntimeClasspath"),
                runtimeArtifacts =
                    listOf(
                        ResolvedArtifactCoordinate("androidx.compose.ui", "ui-android", "1.11.2", "/consumer/ui.aar"),
                        ResolvedArtifactCoordinate("androidx.compose.ui", "ui-tooling-android", "1.11.2", "/consumer/ui-tooling.aar"),
                        ResolvedArtifactCoordinate(
                            "androidx.compose.ui",
                            "ui-tooling-data-android",
                            "1.11.2",
                            "/consumer/ui-tooling-data.aar",
                        ),
                    ),
            )

        assertEquals("1.11.2", resolution.composeVersion)
        assertEquals(
            listOf(
                "/consumer/ui-tooling.aar",
                "/consumer/ui-tooling-data.aar",
            ),
            resolution.consumerArtifactFiles,
        )
        assertEquals(
            listOf(
                "androidx.test:core:1.7.0",
                "androidx.test:monitor:1.8.0",
            ),
            resolution.pluginArtifactCoordinates,
        )
        assertTrue(resolution.warnings.isEmpty())
    }

    @Test
    fun `adds only missing androidx test renderer support artifacts`() {
        val resolution =
            resolver.resolve(
                selectedVariant = "debug",
                inspectedConfigurations = listOf("debugRuntimeClasspath"),
                runtimeArtifacts =
                    listOf(
                        ResolvedArtifactCoordinate("androidx.compose.ui", "ui", "1.8.1", "/consumer/ui.jar"),
                        ResolvedArtifactCoordinate("androidx.test", "core", "1.7.1", "/consumer/test-core.jar"),
                    ),
            )

        assertEquals(listOf("/consumer/test-core.jar"), resolution.consumerArtifactFiles)
        assertEquals(
            listOf(
                "androidx.compose.ui:ui-tooling:1.8.1",
                "androidx.compose.ui:ui-tooling-data:1.8.1",
                "androidx.test:monitor:1.8.0",
            ),
            resolution.pluginArtifactCoordinates,
        )
    }

    @Test
    fun `adds matching tooling artifacts when consumer lacks them`() {
        val resolution =
            resolver.resolve(
                selectedVariant = "debug",
                inspectedConfigurations = listOf("debugRuntimeClasspath"),
                runtimeArtifacts =
                    listOf(
                        ResolvedArtifactCoordinate("androidx.compose.ui", "ui", "1.8.1", "/consumer/ui.jar"),
                    ),
            )

        assertEquals(
            listOf(
                "androidx.compose.ui:ui-tooling:1.8.1",
                "androidx.compose.ui:ui-tooling-data:1.8.1",
                "androidx.test:core:1.7.0",
                "androidx.test:monitor:1.8.0",
            ),
            resolution.pluginArtifactCoordinates,
        )
    }

    @Test
    fun `rejects flavored release variants`() {
        listOf("paidRelease", "stagingRelease", "release").forEach { variant ->
            val exception =
                org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                    RendererDependencyPolicy.requireSupportedVariant(variant)
                }

            assertTrue(exception.message!!.contains("agentPreview.android.variant=$variant is not supported"))
        }
    }

    @Test
    fun `allows debug and debug-like variants`() {
        listOf("debug", "stagingDebug", "releaseCandidateDebug").forEach { variant ->
            RendererDependencyPolicy.requireSupportedVariant(variant)
        }
    }

    @Test
    fun `falls back to default tooling coordinates and warns when compose version is unknown`() {
        val resolution =
            resolver.resolve(
                selectedVariant = "stagingDebug",
                inspectedConfigurations = listOf("stagingDebugRuntimeClasspath", "stagingDebugCompileClasspath"),
                runtimeArtifacts =
                    listOf(
                        ResolvedArtifactCoordinate("androidx.activity", "activity-compose", "1.10.1", "/consumer/activity-compose.jar"),
                    ),
            )

        assertEquals(RendererDependencyPolicy.FALLBACK_COMPOSE_VERSION, resolution.composeVersion)
        assertTrue(
            resolution.warnings.single().contains("selected variant 'stagingDebug'") &&
                resolution.warnings.single().contains("stagingDebugRuntimeClasspath") &&
                resolution.warnings.single().contains("stagingDebugCompileClasspath"),
            resolution.warnings.single(),
        )
        assertEquals(
            listOf(
                "androidx.compose.ui:ui-tooling:${RendererDependencyPolicy.FALLBACK_COMPOSE_VERSION}",
                "androidx.compose.ui:ui-tooling-data:${RendererDependencyPolicy.FALLBACK_COMPOSE_VERSION}",
                "androidx.test:core:1.7.0",
                "androidx.test:monitor:1.8.0",
            ),
            resolution.pluginArtifactCoordinates,
        )
    }
}
