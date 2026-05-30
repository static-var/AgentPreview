/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.config.ConfiguredViewport
import dev.staticvar.agentpreview.model.PreviewDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ViewportResolverTest {
    private val configured =
        listOf(
            ConfiguredViewport("android", "phone", 360, 640, 2.0f),
            ConfiguredViewport("android", "tablet", 800, 1280, 1.5f),
        )

    @Test
    fun `both explicit dimensions create one synthetic android preview viewport`() {
        val viewports = ViewportResolver(configured).resolve(preview(widthDp = 100, heightDp = 200))

        assertEquals(1, viewports.size)
        assertEquals("android", viewports.single().platform)
        assertEquals("preview", viewports.single().name)
        assertEquals(100, viewports.single().width)
        assertEquals(200, viewports.single().height)
        assertEquals(1.0f, viewports.single().density)
    }

    @Test
    fun `one explicit axis overrides configured viewports`() {
        val viewports = ViewportResolver(configured).resolve(preview(widthDp = 411))

        assertEquals(listOf(411, 411), viewports.map { it.width })
        assertEquals(listOf(640, 1280), viewports.map { it.height })
        assertEquals(listOf("phone", "tablet"), viewports.map { it.name })
    }

    private fun preview(
        widthDp: Int? = null,
        heightDp: Int? = null,
    ) = PreviewDescriptor(
        id = ":app:main:com.example.Preview",
        sourceSet = "main",
        fullyQualifiedFunctionName = "com.example.Preview",
        sourceFile = "Preview.kt",
        widthDp = widthDp,
        heightDp = heightDp,
    )
}
