/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

class PreviewComposable(
    private val className: String,
    private val methodName: String,
    private val previewParameterProviderClassName: String? = null,
    private val previewParameterIndex: Int? = null,
) : Function2<Any?, Int, Unit> {
    override fun invoke(
        p1: Any?,
        p2: Int,
    ) {
        val invokerClass = Class.forName("androidx.compose.ui.tooling.ComposableInvoker")
        val invoker = invokerClass.getField("INSTANCE").get(null)
        val composerClass = Class.forName("androidx.compose.runtime.Composer")
        val method =
            invokerClass.getMethod(
                "invokeComposable",
                String::class.java,
                String::class.java,
                composerClass,
                Array<Any>::class.java,
            )
        method.invoke(invoker, className, methodName, p1, previewArguments())
    }

    private fun previewArguments(): Array<Any?> {
        val providerClassName = previewParameterProviderClassName ?: return emptyArray()
        val index = previewParameterIndex ?: return emptyArray()
        val providerClass = Class.forName(providerClassName)
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
        repeat(index) {
            check(iterator.hasNext()) { "PreviewParameterProvider $providerClassName has fewer than ${index + 1} values." }
            iterator.next()
        }
        check(iterator.hasNext()) { "PreviewParameterProvider $providerClassName has fewer than ${index + 1} values." }
        return arrayOf(iterator.next())
    }
}
