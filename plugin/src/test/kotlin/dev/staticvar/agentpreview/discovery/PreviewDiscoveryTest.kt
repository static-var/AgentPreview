/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class PreviewDiscoveryTest {
    @Test
    fun `discovers previews from bytecode scanner`() {
        val discovery =
            PreviewDiscovery(
                projectPath = ":app",
                sourceSetName = "test",
                classesDirs = listOf(testClassesDir()),
                runtimeClasspath = emptyList(),
            )

        val previews = discovery.discover()

        val login = previews.single { it.fullyQualifiedFunctionName.endsWith("discovery.loginPreview") }
        assertEquals(":app:test:dev.staticvar.agentpreview.discovery.loginPreview", login.id)
        assertEquals("Login", login.name)
        assertEquals("Auth", login.group)
        assertEquals("test", login.sourceSet)
        assertEquals("dev.staticvar.agentpreview.discovery.loginPreview", login.fullyQualifiedFunctionName)
        assertEquals("dev.staticvar.agentpreview.discovery.PreviewFixturesKt", login.fullyQualifiedClassName)
        assertEquals("PreviewFixtures.kt", login.sourceFile)
        assertEquals(411, login.widthDp)
        assertEquals(891, login.heightDp)
        assertEquals("en", login.locale)
        assertEquals(33, login.uiMode)
        assertEquals(1.2f, login.fontScale)
        assertEquals(true, login.showBackground)
        assertEquals(0xFFFF_FFFFL, login.backgroundColor)
    }

    @Test
    fun `discovers previews through meta annotations`() {
        val discovery =
            PreviewDiscovery(
                projectPath = ":app",
                sourceSetName = "test",
                classesDirs = listOf(testClassesDir()),
                runtimeClasspath = emptyList(),
            )

        val previews = discovery.discover()

        val objectPreview = previews.single { it.fullyQualifiedFunctionName.endsWith("PreviewFixtureObject.objectPreview") }
        assertEquals("Phone", objectPreview.name)
        assertEquals("Auth", objectPreview.group)
        assertEquals(393, objectPreview.widthDp)
        assertEquals(852, objectPreview.heightDp)
    }

    @Test
    fun `expands multipreview annotations with stable variant ids`() {
        val discovery =
            PreviewDiscovery(
                projectPath = ":app",
                sourceSetName = "test",
                classesDirs = listOf(testClassesDir()),
                runtimeClasspath = emptyList(),
            )

        val previews =
            discovery
                .discover()
                .filter { it.fullyQualifiedFunctionName.endsWith("PreviewFixtureObject.multiPreview") }
                .sortedBy { it.widthDp }

        assertEquals(listOf("Small", "Large"), previews.map { it.name })
        assertEquals(
            listOf(
                ":app:test:dev.staticvar.agentpreview.discovery.PreviewFixtureObject.multiPreview:1-small",
                ":app:test:dev.staticvar.agentpreview.discovery.PreviewFixtureObject.multiPreview:2-large",
            ),
            previews.map { it.id },
        )
    }

    @Test
    fun `expands duplicate multipreview names with unique variant ids`() {
        val discovery =
            PreviewDiscovery(
                projectPath = ":app",
                sourceSetName = "test",
                classesDirs = listOf(testClassesDir()),
                runtimeClasspath = emptyList(),
            )

        val previews =
            discovery
                .discover()
                .filter { it.fullyQualifiedFunctionName.endsWith("PreviewFixtureObject.duplicateNamePreview") }
                .sortedBy { it.widthDp }

        assertEquals(listOf("Phone", "Phone"), previews.map { it.name })
        assertEquals(
            listOf(
                ":app:test:dev.staticvar.agentpreview.discovery.PreviewFixtureObject.duplicateNamePreview:1-phone",
                ":app:test:dev.staticvar.agentpreview.discovery.PreviewFixtureObject.duplicateNamePreview:2-phone",
            ),
            previews.map { it.id },
        )
    }

    @Test
    fun `reports empty list when there are no class directories`() {
        val discovery =
            PreviewDiscovery(
                projectPath = ":app",
                sourceSetName = "test",
                classesDirs = emptyList(),
                runtimeClasspath = emptyList(),
            )

        assertTrue(discovery.discover().isEmpty())
    }

    private fun testClassesDir(): File =
        javaClass.protectionDomain.codeSource.location
            .toURI()
            .let(::File)
}
