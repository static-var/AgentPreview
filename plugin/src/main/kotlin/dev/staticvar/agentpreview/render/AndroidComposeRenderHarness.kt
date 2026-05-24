/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.runner.JUnitCore
import java.io.File
import kotlin.system.exitProcess

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
        System.setProperty("agentpreview.render.semanticsOutputFile", args[7])
        val resultFile = File(args[8])

        val result = JUnitCore.runClasses(AndroidComposeRobolectricEntryPoint::class.java)
        if (!result.wasSuccessful()) {
            val failureKind =
                if (result.failures.any { failure -> failure.exception.hasResourceNotFoundCause() }) {
                    RenderProcessFailureKind.ResourceLoadingGap
                } else {
                    RenderProcessFailureKind.HarnessFailure
                }
            RenderHarnessResultFile.writeFailure(resultFile, failureKind)
            result.failures.forEach { failure ->
                System.err.println(failure.testHeader)
                failure.exception.printStackTrace(System.err)
            }
            exitProcess(1)
        }
        RenderHarnessResultFile.writeSuccess(resultFile)
        exitProcess(0)
    }

    private fun Throwable.hasResourceNotFoundCause(): Boolean =
        generateSequence(this) { throwable -> throwable.cause }
            .any { throwable -> throwable.javaClass.name == RESOURCE_NOT_FOUND_EXCEPTION_CLASS_NAME }

    private const val ARG_COUNT = 9
    private const val RESOURCE_NOT_FOUND_EXCEPTION_CLASS_NAME = "android.content.res.Resources\$NotFoundException"
}
