/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewParameterDescriptor

object PreviewParameterCountEntryPoint {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 4) { "Expected providerClassName, parameterType, limit, defaultCap." }
        val parameter =
            PreviewParameterDescriptor(
                providerClassName = args[0],
                parameterType = args[1],
                limit = args[2].takeIf { it.isNotBlank() }?.toInt(),
            )
        val result = BoundedPreviewParameterValueCounter(Thread.currentThread().contextClassLoader, args[3].toInt()).count(parameter)
        println("count=${result.count}")
        result.diagnostics.forEach { diagnostic -> println("diagnostic=${diagnostic.replace('\n', ' ')}") }
    }
}
