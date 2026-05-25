/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewParameterDescriptor
import java.io.File
import java.net.URLClassLoader
import java.util.Properties
import java.util.concurrent.TimeUnit

class IsolatedPreviewParameterCountResolver(
    private val previewClasspath: List<File>,
    private val timeoutSeconds: Long = TIMEOUT_SECONDS,
    private val defaultCap: Int = DEFAULT_CAP,
    private val processRunner: ProcessRunner = DefaultProcessRunner(),
) : PreviewParameterCountResolver {
    override fun count(parameter: PreviewParameterDescriptor): PreviewParameterCount {
        val resultFile = File.createTempFile("agent-preview-parameter-count", ".properties")
        resultFile.delete()
        try {
            val command =
                listOf(
                    javaExecutable(),
                    "-cp",
                    (currentPluginClasspath() + previewClasspath)
                        .distinctBy { it.absoluteFile.normalize().path }
                        .joinToString(File.pathSeparator) { it.absolutePath },
                    PreviewParameterCountEntryPoint::class.java.name,
                    parameter.providerClassName,
                    parameter.parameterType,
                    parameter.limit?.toString().orEmpty(),
                    defaultCap.toString(),
                    resultFile.absolutePath,
                )
            val processResult = processRunner.run(command, timeoutSeconds)
            if (processResult.timedOut) {
                return PreviewParameterCount(
                    count = 0,
                    diagnostics = listOf("Skipping parameterized preview because provider ${parameter.providerClassName} timed out."),
                )
            }
            if (processResult.exitCode != 0) {
                return PreviewParameterCount(
                    count = 0,
                    diagnostics =
                        listOf(
                            "Skipping parameterized preview because provider ${parameter.providerClassName} could not be resolved " +
                                "in the isolated resolver. Cause: ${processResult.output.trim()}",
                        ),
                )
            }
            return parseResultFile(resultFile, parameter)
        } finally {
            resultFile.delete()
        }
    }

    private fun parseResultFile(
        resultFile: File,
        parameter: PreviewParameterDescriptor,
    ): PreviewParameterCount {
        if (!resultFile.isFile) {
            return PreviewParameterCount(
                count = 0,
                diagnostics = listOf("Skipping parameterized preview because provider ${parameter.providerClassName} returned no count."),
            )
        }
        val properties = Properties()
        resultFile.inputStream().use(properties::load)
        val rawCount = properties.getProperty(PreviewParameterCountEntryPoint.COUNT_PROPERTY)?.toIntOrNull()
        if (rawCount == null) {
            return PreviewParameterCount(
                count = 0,
                diagnostics = listOf("Skipping parameterized preview because provider ${parameter.providerClassName} returned no count."),
            )
        }
        val diagnosticCount = properties.getProperty(PreviewParameterCountEntryPoint.DIAGNOSTIC_COUNT_PROPERTY)?.toIntOrNull() ?: 0
        return PreviewParameterCount(
            count = rawCount.coerceIn(0, defaultCap),
            diagnostics = (0 until diagnosticCount).mapNotNull { index -> properties.getProperty("diagnostic.$index") },
        )
    }

    private fun currentPluginClasspath(): List<File> {
        val loaderFiles =
            (IsolatedPreviewParameterCountResolver::class.java.classLoader as? URLClassLoader)
                ?.urLs
                ?.mapNotNull { url -> url.toURI().takeIf { it.scheme == "file" } }
                ?.map(::File)
                ?: emptyList()
        val requiredCodeSources =
            listOf(
                IsolatedPreviewParameterCountResolver::class.java,
                PreviewParameterCountEntryPoint::class.java,
                PreviewParameterDescriptor::class.java,
                Unit::class.java,
                Sequence::class.java,
            ).mapNotNull { clazz ->
                clazz.protectionDomain.codeSource
                    ?.location
                    ?.toURI()
                    ?.let(::File)
            }
        return (loaderFiles + requiredCodeSources).filter { it.exists() }
    }

    private fun javaExecutable(): String = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath

    interface ProcessRunner {
        fun run(
            command: List<String>,
            timeoutSeconds: Long,
        ): ProcessResult
    }

    private class DefaultProcessRunner : ProcessRunner {
        override fun run(
            command: List<String>,
            timeoutSeconds: Long,
        ): ProcessResult {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = BoundedProcessOutput(MAX_PROCESS_OUTPUT_CHARS)
            val readerThread =
                Thread {
                    process.inputStream.bufferedReader().use { reader ->
                        var next = reader.read()
                        while (next != -1) {
                            output.append(next.toChar())
                            next = reader.read()
                        }
                    }
                }
            readerThread.isDaemon = true
            readerThread.start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                readerThread.join(1000)
                return ProcessResult(exitCode = null, timedOut = true, output = output.toString())
            }
            readerThread.join(1000)
            return ProcessResult(exitCode = process.exitValue(), timedOut = false, output = output.toString())
        }
    }

    private companion object {
        const val DEFAULT_CAP = 50
        const val TIMEOUT_SECONDS = 10L
        const val MAX_PROCESS_OUTPUT_CHARS = 4096
    }
}

data class ProcessResult(
    val exitCode: Int?,
    val timedOut: Boolean,
    val output: String,
)

private class BoundedProcessOutput(
    private val maxChars: Int,
) {
    private val builder = StringBuilder()
    private var discardedChars = 0

    fun append(char: Char) {
        if (builder.length < maxChars) {
            builder.append(char)
        } else {
            discardedChars++
        }
    }

    override fun toString(): String =
        if (discardedChars == 0) {
            builder.toString()
        } else {
            builder.toString() + "\n[truncated process output after $maxChars chars; discarded $discardedChars chars]"
        }
}
