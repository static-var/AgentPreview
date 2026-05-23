/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.runner.JUnitCore

object AndroidComposeRenderHarness {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == ARG_COUNT) { "Expected $ARG_COUNT arguments, got ${args.size}" }
        System.setProperty("agentpreview.render.className", args[0])
        System.setProperty("agentpreview.render.methodName", args[1])
        System.setProperty("agentpreview.render.widthPx", args[2])
        System.setProperty("agentpreview.render.heightPx", args[3])
        System.setProperty("agentpreview.render.density", args[4])
        System.setProperty("agentpreview.render.robolectricSdk", args[5])
        System.setProperty("agentpreview.render.outputFile", args[6])

        val result = JUnitCore.runClasses(AndroidComposeRobolectricEntryPoint::class.java)
        if (!result.wasSuccessful()) {
            result.failures.forEach { failure ->
                System.err.println(failure.testHeader)
                failure.exception.printStackTrace(System.err)
            }
            kotlin.system.exitProcess(1)
        }
        kotlin.system.exitProcess(0)
    }

    private const val ARG_COUNT = 7
}
