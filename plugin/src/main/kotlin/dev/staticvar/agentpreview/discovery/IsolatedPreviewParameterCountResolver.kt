/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewParameterDescriptor
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit

class IsolatedPreviewParameterCountResolver(
    private val previewClasspath: List<File>,
    private val timeoutSeconds: Long = TIMEOUT_SECONDS,
    private val defaultCap: Int = DEFAULT_CAP,
) : PreviewParameterCountResolver {
    override fun count(parameter: PreviewParameterDescriptor): PreviewParameterCount {
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
            )
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = StringBuilder()
        val readerThread =
            Thread {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> output.appendLine(line) }
                }
            }
        readerThread.isDaemon = true
        readerThread.start()
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return PreviewParameterCount(
                count = 0,
                diagnostics = listOf("Skipping parameterized preview because provider ${parameter.providerClassName} timed out."),
            )
        }
        readerThread.join(1000)
        if (process.exitValue() != 0) {
            return PreviewParameterCount(
                count = 0,
                diagnostics =
                    listOf(
                        "Skipping parameterized preview because provider ${parameter.providerClassName} could not be resolved " +
                            "in the isolated resolver. Cause: ${output.toString().trim()}",
                    ),
            )
        }
        return parseOutput(output.toString(), parameter)
    }

    private fun parseOutput(
        output: String,
        parameter: PreviewParameterDescriptor,
    ): PreviewParameterCount {
        val lines = output.lineSequence().filter { it.isNotBlank() }.toList()
        val count = lines.firstOrNull { it.startsWith("count=") }?.removePrefix("count=")?.toIntOrNull()
        if (count == null) {
            return PreviewParameterCount(
                count = 0,
                diagnostics = listOf("Skipping parameterized preview because provider ${parameter.providerClassName} returned no count."),
            )
        }
        return PreviewParameterCount(
            count = count,
            diagnostics = lines.filter { it.startsWith("diagnostic=") }.map { it.removePrefix("diagnostic=") },
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

    private companion object {
        const val DEFAULT_CAP = 50
        const val TIMEOUT_SECONDS = 10L
    }
}
