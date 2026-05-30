/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.runner.JUnitCore
import kotlin.system.exitProcess

object AndroidComposeRenderHarness {
    @JvmStatic
    fun main(args: Array<String>) {
        val command = RenderHarnessCommand.fromArgs(args)
        command.applyToSystemProperties()
        val resultFile = command.resultFile

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

    private const val RESOURCE_NOT_FOUND_EXCEPTION_CLASS_NAME = "android.content.res.Resources\$NotFoundException"
}
