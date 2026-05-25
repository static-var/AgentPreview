/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.PreviewParameterDescriptor
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreviewFilterMatchingTest {
    @Test
    fun `full expanded preview parameter filter only matches exact parent preview before expansion`() {
        val foo = preview(id = ":app:main:com.example.Foo")
        val fooBar = preview(id = ":app:main:com.example.FooBar")
        val unrelated = preview(id = ":app:main:com.example.Unrelated", name = "Foo")

        val filters = setOf(":app:main:com.example.Foo:previewParam-1")

        assertTrue(foo.matchesBeforePreviewParameterExpansion(filters))
        assertFalse(fooBar.matchesBeforePreviewParameterExpansion(filters))
        assertFalse(unrelated.matchesBeforePreviewParameterExpansion(filters))
    }

    @Test
    fun `full expanded preview parameter filter matches already expanded preview before expansion`() {
        val expanded =
            preview(
                id = ":app:main:com.example.Foo:previewParam-1",
                previewParameter =
                    PreviewParameterDescriptor(
                        providerClassName = "com.example.Provider",
                        parameterType = "kotlin.String",
                        index = 1,
                    ),
            )
        val sibling =
            preview(
                id = ":app:main:com.example.Foo:previewParam-10",
                previewParameter =
                    PreviewParameterDescriptor(
                        providerClassName = "com.example.Provider",
                        parameterType = "kotlin.String",
                        index = 10,
                    ),
            )

        val filters = setOf(":app:main:com.example.Foo:previewParam-1")

        assertTrue(expanded.matchesBeforePreviewParameterExpansion(filters))
        assertFalse(sibling.matchesBeforePreviewParameterExpansion(filters))
    }

    @Test
    fun `shorthand preview parameter filter matches all parameterized previews before expansion`() {
        val foo = preview(id = ":app:main:com.example.Foo")
        val fooBar = preview(id = ":app:main:com.example.FooBar")
        val nonParameterized = preview(id = ":app:main:com.example.NonParameterized", previewParameter = null)

        val filters = setOf("previewParam-1")

        assertTrue(foo.matchesBeforePreviewParameterExpansion(filters))
        assertTrue(fooBar.matchesBeforePreviewParameterExpansion(filters))
        assertFalse(nonParameterized.matchesBeforePreviewParameterExpansion(filters))
    }

    private fun preview(
        id: String,
        name: String? = null,
        previewParameter: PreviewParameterDescriptor? =
            PreviewParameterDescriptor(
                providerClassName = "com.example.Provider",
                parameterType = "kotlin.String",
            ),
    ): PreviewDescriptor =
        PreviewDescriptor(
            id = id,
            name = name,
            sourceSet = "main",
            fullyQualifiedFunctionName = id.substringAfterLast(':'),
            sourceFile = "Preview.kt",
            previewParameter = previewParameter,
        )
}
