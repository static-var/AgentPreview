/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.PreviewParameterDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreviewParameterExpanderTest {
    @Test
    fun `expands parameterized preview ids from bounded resolver count`() {
        val expander = PreviewParameterExpander(resolver = StubResolver(PreviewParameterCount(count = 2)))

        val result = expander.expand(listOf(parameterizedPreview()))

        assertEquals(listOf(0, 1), result.previews.map { it.previewParameter?.index })
        assertEquals(
            listOf(
                ":app:main:example.Parameterized:previewParam-0",
                ":app:main:example.Parameterized:previewParam-1",
            ),
            result.previews.map { it.id },
        )
    }

    @Test
    fun `does not expand previews that already have a selected preview parameter index`() {
        val preview = parameterizedPreview().let { it.copy(previewParameter = it.previewParameter?.copy(index = 3)) }
        val expander = PreviewParameterExpander(resolver = StubResolver(PreviewParameterCount(count = 2)))

        val result = expander.expand(listOf(preview))

        assertEquals(listOf(preview), result.previews)
    }

    @Test
    fun `expands already indexed previews without resolving provider count`() {
        val preview = parameterizedPreview().let { it.copy(previewParameter = it.previewParameter?.copy(index = 1)) }
        val expander = PreviewParameterExpander()

        val result = expander.expand(listOf(preview))

        assertEquals(listOf(preview), result.previews)
    }

    @Test
    fun `synthesizes requested preview parameter index from descriptor metadata without resolver`() {
        val expander = PreviewParameterExpander(defaultCap = 50, requestedIndexes = setOf(1))

        val result = expander.expand(listOf(parameterizedPreview()))

        assertEquals(listOf(1), result.previews.map { it.previewParameter?.index })
        assertEquals(listOf(":app:main:example.Parameterized:previewParam-1"), result.previews.map { it.id })
    }

    @Test
    fun `does not synthesize requested preview parameter index beyond descriptor limit`() {
        val expander = PreviewParameterExpander(defaultCap = 50, requestedIndexes = setOf(2))

        val result = expander.expand(listOf(parameterizedPreview(limit = 2)))

        assertEquals(emptyList<PreviewDescriptor>(), result.previews)
    }

    @Test
    fun `skips empty providers with diagnostic`() {
        val expander = PreviewParameterExpander(resolver = StubResolver(PreviewParameterCount(count = 0, diagnostics = listOf("empty"))))

        val result = expander.expand(listOf(parameterizedPreview()))

        assertEquals(emptyList<PreviewDescriptor>(), result.previews)
        assertEquals(listOf("empty"), result.diagnostics.map { it.message })
    }

    private fun parameterizedPreview(limit: Int? = null): PreviewDescriptor =
        PreviewDescriptor(
            id = ":app:main:example.Parameterized",
            name = "Parameterized",
            group = null,
            sourceSet = "main",
            fullyQualifiedFunctionName = "example.Parameterized",
            fullyQualifiedClassName = "example.ExampleKt",
            sourceFile = "Example.kt",
            sourceLine = null,
            previewParameter =
                PreviewParameterDescriptor(
                    providerClassName = "example.Provider",
                    parameterType = "kotlin.String",
                    limit = limit,
                ),
        )

    private class StubResolver(
        private val count: PreviewParameterCount,
    ) : PreviewParameterCountResolver {
        override fun count(parameter: PreviewParameterDescriptor): PreviewParameterCount = count
    }
}
