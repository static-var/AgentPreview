/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

class PreviewComposable(
    private val className: String,
    private val methodName: String,
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
        method.invoke(invoker, className, methodName, p1, emptyArray<Any>())
    }
}
