/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewParameterDescriptor

class BoundedPreviewParameterValueCounter(
    private val classLoader: ClassLoader,
    private val defaultCap: Int = DEFAULT_CAP,
) : PreviewParameterCountResolver {
    override fun count(parameter: PreviewParameterDescriptor): PreviewParameterCount =
        runCatching { countOrThrow(parameter) }
            .getOrElse { throwable ->
                PreviewParameterCount(
                    count = 0,
                    diagnostics =
                        listOf(
                            "Skipping parameterized preview because provider ${parameter.providerClassName} could not be instantiated. " +
                                "Ensure it implements AndroidX PreviewParameterProvider and has an accessible no-arg constructor. " +
                                "Cause: ${throwable.javaClass.name}: ${throwable.message}",
                        ),
                )
            }

    private fun countOrThrow(parameter: PreviewParameterDescriptor): PreviewParameterCount {
        val effectiveLimit = effectiveLimit(parameter)
        val providerClass = Class.forName(parameter.providerClassName, true, classLoader)
        val constructor = providerClass.getDeclaredConstructor()
        if (!constructor.canAccess(null)) constructor.isAccessible = true
        val provider = constructor.newInstance()
        val values = providerClass.methods.first { it.name == "getValues" && it.parameterTypes.isEmpty() }.invoke(provider)
        val iterator =
            when (values) {
                is Sequence<*> -> {
                    values.iterator()
                }

                is Iterable<*> -> {
                    values.iterator()
                }

                else -> {
                    values.javaClass.methods
                        .first { it.name == "iterator" && it.parameterTypes.isEmpty() }
                        .invoke(values) as Iterator<*>
                }
            }

        var count = 0
        while (count < effectiveLimit && iterator.hasNext()) {
            iterator.next()
            count++
        }

        val diagnostics =
            buildList {
                if (count == 0) {
                    add("Skipping parameterized preview because provider ${parameter.providerClassName} produced no values.")
                }
                if (parameter.limit != null && parameter.limit > defaultCap) {
                    add(
                        "PreviewParameter provider ${parameter.providerClassName} requested limit ${parameter.limit} " +
                            "but limit ${parameter.limit} was capped at $defaultCap values.",
                    )
                } else if (parameter.limit == null && count == defaultCap) {
                    add("PreviewParameter provider ${parameter.providerClassName} expansion was capped at $defaultCap values.")
                }
            }
        return PreviewParameterCount(count = count, diagnostics = diagnostics)
    }

    private fun effectiveLimit(parameter: PreviewParameterDescriptor): Int = parameter.limit?.coerceAtMost(defaultCap) ?: defaultCap

    private companion object {
        const val DEFAULT_CAP = 50
    }
}
