/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewParameterDescriptor
import java.io.File
import java.util.Properties

object PreviewParameterCountEntryPoint {
    const val COUNT_PROPERTY = "count"
    const val DIAGNOSTIC_COUNT_PROPERTY = "diagnostic.count"

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 5) { "Expected providerClassName, parameterType, limit, defaultCap, resultFile." }
        val parameter =
            PreviewParameterDescriptor(
                providerClassName = args[0],
                parameterType = args[1],
                limit = args[2].takeIf { it.isNotBlank() }?.toInt(),
            )
        val result = BoundedPreviewParameterValueCounter(Thread.currentThread().contextClassLoader, args[3].toInt()).count(parameter)
        val properties =
            Properties().apply {
                setProperty(COUNT_PROPERTY, result.count.toString())
                setProperty(DIAGNOSTIC_COUNT_PROPERTY, result.diagnostics.size.toString())
                result.diagnostics.forEachIndexed { index, diagnostic -> setProperty("diagnostic.$index", diagnostic) }
            }
        File(args[4]).outputStream().use { output -> properties.store(output, null) }
    }
}
