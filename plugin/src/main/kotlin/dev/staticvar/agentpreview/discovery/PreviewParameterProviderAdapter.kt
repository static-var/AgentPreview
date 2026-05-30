/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

internal class PreviewParameterProviderAdapter(
    private val classLoader: ClassLoader,
) {
    fun iterator(providerClassName: String): Iterator<*> {
        val providerClass = Class.forName(providerClassName, true, classLoader)
        val constructor = providerClass.getDeclaredConstructor()
        if (!constructor.canAccess(null)) constructor.isAccessible = true
        val provider = constructor.newInstance()
        val values = providerClass.methods.first { it.name == "getValues" && it.parameterTypes.isEmpty() }.invoke(provider)
        return values.toIterator(providerClassName)
    }

    private fun Any?.toIterator(providerClassName: String): Iterator<*> =
        when (this) {
            is Sequence<*> -> iterator()
            is Iterable<*> -> iterator()
            null -> unsupportedValues(providerClassName, "null")
            else -> iteratorFromMethod(providerClassName)
        }

    private fun Any.iteratorFromMethod(providerClassName: String): Iterator<*> {
        val iteratorMethod =
            javaClass.methods.firstOrNull { it.name == "iterator" && it.parameterTypes.isEmpty() }
                ?: unsupportedValues(providerClassName, javaClass.name)
        val iterator = iteratorMethod.invoke(this)
        return iterator as? Iterator<*>
            ?: error(
                "PreviewParameterProvider $providerClassName values ${javaClass.name} iterator() returned " +
                    "${iterator?.javaClass?.name ?: "null"}, expected java.util.Iterator.",
            )
    }

    private fun unsupportedValues(
        providerClassName: String,
        valuesType: String,
    ): Nothing = error("Unsupported PreviewParameterProvider values for $providerClassName: $valuesType does not expose iterator().")
}
