/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.discovery.PreviewParameterProviderAdapter

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
        val iterator = PreviewParameterProviderAdapter(javaClass.classLoader).iterator(providerClassName)
        repeat(index) {
            check(iterator.hasNext()) { "PreviewParameterProvider $providerClassName has fewer than ${index + 1} values." }
            iterator.next()
        }
        check(iterator.hasNext()) { "PreviewParameterProvider $providerClassName has fewer than ${index + 1} values." }
        return arrayOf(iterator.next())
    }
}
