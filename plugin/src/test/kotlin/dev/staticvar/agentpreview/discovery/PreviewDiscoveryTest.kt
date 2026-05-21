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

        val login = previews.single { it.fullyQualifiedFunctionName.endsWith("PreviewFixturesKt.loginPreview") }
        assertEquals(":app:test:dev.staticvar.agentpreview.discovery.PreviewFixturesKt.loginPreview", login.id)
        assertEquals("Login", login.name)
        assertEquals("Auth", login.group)
        assertEquals("test", login.sourceSet)
        assertEquals("dev.staticvar.agentpreview.discovery.PreviewFixturesKt.loginPreview", login.fullyQualifiedFunctionName)
        assertEquals("PreviewFixtures.kt", login.sourceFile)
        assertEquals(411, login.widthDp)
        assertEquals(891, login.heightDp)
        assertEquals("en", login.locale)
        assertEquals(33, login.uiMode)
        assertEquals(1.2f, login.fontScale)
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
                ":app:test:dev.staticvar.agentpreview.discovery.PreviewFixtureObject.multiPreview:small",
                ":app:test:dev.staticvar.agentpreview.discovery.PreviewFixtureObject.multiPreview:large",
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
