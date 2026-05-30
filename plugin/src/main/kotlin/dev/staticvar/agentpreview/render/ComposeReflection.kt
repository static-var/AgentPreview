/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.lang.reflect.Method

internal object ComposeReflection {
    fun <T> requiredNoArgValue(
        target: Any,
        name: String,
    ): T {
        @Suppress("UNCHECKED_CAST")
        return requiredNoArgMethod(target, name).invoke(target) as T
    }

    fun <T> optionalNoArgValue(
        target: Any,
        name: String,
    ): T? {
        @Suppress("UNCHECKED_CAST")
        return optionalNoArgMethod(target, name)?.invoke(target) as? T
    }

    fun requiredNoArgMethod(
        target: Any,
        name: String,
    ): Method =
        optionalNoArgMethod(target, name)
            ?: error("Required reflective method ${target.javaClass.name}.$name() was not found.")

    fun optionalNoArgMethod(
        target: Any,
        name: String,
    ): Method? =
        target.javaClass.methods
            .firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
            ?.apply { isAccessible = true }

    fun requiredMethod(
        targetClass: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>?,
    ): Method =
        optionalMethod(targetClass, name, *parameterTypes)
            ?: error(
                "Required reflective method ${targetClass.name}.$name(${parameterTypes.joinToString {
                    it?.name ?: "null"
                }}) was not found.",
            )

    fun optionalMethod(
        targetClass: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>?,
    ): Method? =
        targetClass.methods
            .firstOrNull { method -> method.name == name && method.parameterTypes.contentEquals(parameterTypes) }
            ?.makeAccessible()

    fun requiredMethodMatching(
        targetClass: Class<*>,
        name: String,
        signatureDescription: String,
        predicate: (Method) -> Boolean,
    ): Method =
        optionalMethodMatching(targetClass, name, predicate)
            ?: error("Required reflective method ${targetClass.name}.$name($signatureDescription) was not found.")

    fun optionalMethodMatching(
        targetClass: Class<*>,
        name: String,
        predicate: (Method) -> Boolean,
    ): Method? =
        targetClass.methods
            .firstOrNull { method -> method.name == name && predicate(method) }
            ?.makeAccessible()

    private fun Method.makeAccessible(): Method = apply { isAccessible = true }
}
