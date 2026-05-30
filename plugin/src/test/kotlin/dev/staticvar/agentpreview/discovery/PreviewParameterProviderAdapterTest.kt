/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PreviewParameterProviderAdapterTest {
    @Test
    fun `creates iterator for sequence values`() {
        val iterator =
            PreviewParameterProviderAdapter(
                javaClass.classLoader,
            ).iterator("dev.staticvar.agentpreview.discovery.StringProvider")

        assertEquals("first", iterator.next())
        assertEquals("second", iterator.next())
    }

    @Test
    fun `fails descriptively when values do not expose an iterator`() {
        val failure =
            assertThrows<IllegalStateException> {
                PreviewParameterProviderAdapter(javaClass.classLoader).iterator(
                    "dev.staticvar.agentpreview.discovery.UnsupportedValuesProvider",
                )
            }

        assertTrue(failure.message!!.contains("Unsupported PreviewParameterProvider values"), failure.message)
        assertTrue(failure.message!!.contains("UnsupportedValuesProvider"), failure.message)
    }

    @Test
    fun `fails descriptively when iterator method returns a non iterator`() {
        val failure =
            assertThrows<IllegalStateException> {
                PreviewParameterProviderAdapter(javaClass.classLoader).iterator(
                    "dev.staticvar.agentpreview.discovery.InvalidIteratorProvider",
                )
            }

        assertTrue(failure.message!!.contains("iterator() returned java.lang.String"), failure.message)
        assertTrue(failure.message!!.contains("InvalidIteratorProvider"), failure.message)
    }
}

class UnsupportedValuesProvider {
    private val unsupportedValues = 42

    fun getValues(): Any = unsupportedValues
}

class InvalidIteratorProvider {
    fun getValues(): Any = InvalidIteratorValues()
}

class InvalidIteratorValues {
    private val invalidIterator = "not an iterator"

    fun iterator(): Any = invalidIterator
}
