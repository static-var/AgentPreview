/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewParameterDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoundedPreviewParameterValueCounterTest {
    @Test
    fun `counts provider values up to default cap`() {
        val result = counter().count(parameter("dev.staticvar.agentpreview.discovery.InfiniteStringProvider"))

        assertEquals(50, result.count)
        assertTrue(result.diagnostics.single().contains("capped at 50"), result.diagnostics.toString())
    }

    @Test
    fun `uses explicit smaller limit`() {
        val result = counter().count(parameter("dev.staticvar.agentpreview.discovery.InfiniteStringProvider", limit = 3))

        assertEquals(3, result.count)
        assertEquals(emptyList<String>(), result.diagnostics)
    }

    @Test
    fun `caps explicit limit larger than safe cap`() {
        val result = counter().count(parameter("dev.staticvar.agentpreview.discovery.InfiniteStringProvider", limit = 100))

        assertEquals(50, result.count)
        assertTrue(result.diagnostics.any { it.contains("limit 100 was capped at 50") }, result.diagnostics.toString())
    }

    @Test
    fun `reports empty provider`() {
        val result = counter().count(parameter("dev.staticvar.agentpreview.discovery.EmptyStringProvider"))

        assertEquals(0, result.count)
        assertTrue(result.diagnostics.single().contains("produced no values"), result.diagnostics.toString())
    }

    private fun counter(): BoundedPreviewParameterValueCounter = BoundedPreviewParameterValueCounter(javaClass.classLoader)

    private fun parameter(
        providerClassName: String,
        limit: Int? = null,
    ): PreviewParameterDescriptor =
        PreviewParameterDescriptor(
            providerClassName = providerClassName,
            parameterType = "kotlin.String",
            limit = limit,
        )
}
