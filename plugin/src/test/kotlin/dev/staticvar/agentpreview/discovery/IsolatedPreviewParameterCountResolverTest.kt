/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.staticvar.agentpreview.model.PreviewParameterDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties
import kotlin.system.exitProcess

class IsolatedPreviewParameterCountResolverTest {
    @Test
    fun `ignores provider stdout that looks like a count`() {
        val result = resolver(defaultCap = 50).count(parameter("dev.staticvar.agentpreview.discovery.SpoofingCountProvider"))

        assertEquals(2, result.count)
    }

    @Test
    fun `bounds noisy provider output captured for diagnostics`() {
        val result = resolver(defaultCap = 50).count(parameter("dev.staticvar.agentpreview.discovery.NoisyExitingProvider"))

        assertEquals(0, result.count)
        val diagnostic = result.diagnostics.single()
        assertTrue(diagnostic.length < 8_000, "diagnostic was ${diagnostic.length} chars")
        assertTrue(diagnostic.contains("[truncated"), diagnostic)
    }

    @Test
    fun `caps trusted child count in parent`() {
        val runner =
            StubProcessRunner(
                properties =
                    Properties().apply {
                        setProperty(PreviewParameterCountEntryPoint.COUNT_PROPERTY, "1000000000")
                        setProperty(PreviewParameterCountEntryPoint.DIAGNOSTIC_COUNT_PROPERTY, "0")
                    },
                result = ProcessResult(exitCode = 0, timedOut = false, output = ""),
            )
        val result =
            IsolatedPreviewParameterCountResolver(
                previewClasspath = emptyList(),
                defaultCap = 50,
                processRunner = runner,
            ).count(parameter("example.Provider"))

        assertEquals(50, result.count)
    }

    private fun resolver(defaultCap: Int): IsolatedPreviewParameterCountResolver =
        IsolatedPreviewParameterCountResolver(
            previewClasspath = System.getProperty("java.class.path").split(File.pathSeparator).map(::File),
            defaultCap = defaultCap,
        )

    private fun parameter(providerClassName: String): PreviewParameterDescriptor =
        PreviewParameterDescriptor(
            providerClassName = providerClassName,
            parameterType = "kotlin.String",
            limit = null,
        )

    private class StubProcessRunner(
        private val properties: Properties,
        private val result: ProcessResult,
    ) : IsolatedPreviewParameterCountResolver.ProcessRunner {
        override fun run(
            command: List<String>,
            timeoutSeconds: Long,
        ): ProcessResult {
            File(command.last()).outputStream().use { output -> properties.store(output, null) }
            return result
        }
    }
}

class SpoofingCountProvider : PreviewParameterProvider<String> {
    init {
        println("count=1000000000")
    }

    override val values: Sequence<String> = sequenceOf("first", "second")
}

class NoisyExitingProvider : PreviewParameterProvider<String> {
    init {
        repeat(20_000) { print("x") }
        exitProcess(42)
    }

    override val values: Sequence<String> = emptySequence()
}
