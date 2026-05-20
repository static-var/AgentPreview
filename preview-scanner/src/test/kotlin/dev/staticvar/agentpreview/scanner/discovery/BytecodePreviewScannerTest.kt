/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BytecodePreviewScannerTest {
    @Test
    fun `discovers direct top-level AndroidX preview annotation`() {
        val result = scanTestClasses()

        val preview = result.previews.single { it.methodName == "topLevelPreview" }
        assertEquals(":app:test:dev.staticvar.agentpreview.scanner.fixtures.PreviewFixturesKt.topLevelPreview", preview.id)
        assertEquals("test", preview.sourceSet)
        assertEquals("dev.staticvar.agentpreview.scanner.fixtures.PreviewFixturesKt", preview.declaringClassName)
        assertEquals("dev.staticvar.agentpreview.scanner.fixtures.PreviewFixturesKt.topLevelPreview", preview.fullyQualifiedFunctionName)
        assertEquals("Top Level", preview.name)
        assertEquals("Auth", preview.group)

        val annotation = preview.annotations.single()
        assertEquals(411, annotation.widthDp)
        assertEquals(891, annotation.heightDp)
        assertTrue(annotation.showBackground)
        assertEquals(0xFFFF_FFFFL, annotation.backgroundColor)
        assertEquals(1.2f, annotation.fontScale)
        assertEquals("en", annotation.locale)
        assertEquals("spec:width=411dp,height=891dp", annotation.device)
        assertEquals(33, annotation.uiMode)
    }

    @Test
    fun `discovers object preview through preview meta-annotation`() {
        val result = scanTestClasses()

        val preview = result.previews.single { it.methodName == "objectPreview" }
        assertEquals("dev.staticvar.agentpreview.scanner.fixtures.ObjectPreviewFixtures", preview.declaringClassName)
        assertEquals("Phone", preview.name)
        assertEquals("Auth", preview.group)
        assertEquals(393, preview.annotations.single().widthDp)
        assertEquals(852, preview.annotations.single().heightDp)
    }

    @Test
    fun `discovers nested object preview`() {
        val result = scanTestClasses()

        val preview = result.previews.single { it.methodName == "nestedObjectPreview" }
        assertEquals("Nested", preview.name)
        assertTrue(preview.declaringClassName.endsWith("dev.staticvar.agentpreview.scanner.fixtures.ObjectPreviewFixtures${'$'}Nested"))
    }

    @Test
    fun `expands repeated preview annotations on multipreview annotation`() {
        val result = scanTestClasses()

        val preview = result.previews.single { it.methodName == "classPreview" }
        assertEquals(listOf("Small", "Large"), preview.annotations.map { it.name })
        assertEquals(listOf(320, 800), preview.annotations.map { it.widthDp })
        assertEquals("Small", preview.name)
        assertEquals("Multi", preview.group)
    }

    @Test
    fun `reports diagnostic for preview methods with parameters`() {
        val result = scanTestClasses()

        assertTrue(result.previews.none { it.methodName == "unsupportedPreview" })
        assertTrue(
            result.diagnostics.any { diagnostic ->
                diagnostic.severity == PreviewScanDiagnostic.Severity.WARNING &&
                    diagnostic.message.contains("unsupportedPreview") &&
                    diagnostic.message.contains("parameters")
            },
        )
    }

    private fun scanTestClasses(): PreviewScanResult =
        BytecodePreviewScanner().scan(
            PreviewScanInput(
                projectPath = ":app",
                sourceSetName = "test",
                classesDirs =
                    listOf(
                        javaClass.protectionDomain.codeSource.location
                            .toURI()
                            .let(::File),
                    ),
                runtimeClasspath = emptyList(),
            ),
        )
}
