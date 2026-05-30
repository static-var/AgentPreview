/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.runner.JUnitCore
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.net.URLClassLoader

/**
 * Runs Android Compose preview rendering across an explicit process boundary.
 *
 * Target preview code executes in an isolated child JVM whose classpath contains the plugin runtime,
 * the Android SDK android.jar (when available), and the selected preview runtime classpath. This keeps
 * app/preview classes out of the Gradle daemon/plugin classloader while still using the consumer
 * project's runtime artifacts for rendering.
 */
internal class DefaultRenderProcessRunner(
    private val environment: AndroidRendererEnvironment = AndroidRendererEnvironment(),
    private val processService: RenderProcessService = RenderProcessService(),
    private val classpathMaterializer: ClasspathMaterializer = AarClasspathMaterializer(),
) : RenderProcessRunner {
    override fun run(
        request: AndroidComposeRenderRequest,
        previewClasspath: List<File>,
    ): RenderProcessResult {
        val javaExecutable = environment.javaExecutable()
        if (javaExecutable.diagnostic != null) {
            return RenderProcessResult.Failure(RenderProcessFailureKind.HarnessFailure, javaExecutable.diagnostic)
        }
        val androidJar = environment.androidJar(request.robolectricSdk)
        val classpath = classpathMaterializer.materialize(currentPluginClasspath() + androidJar.files + previewClasspath)
        val harnessResultFile = File.createTempFile("agentpreview-render-harness-", ".properties")
        harnessResultFile.delete()
        val harnessCommand =
            RenderHarnessCommand(
                className = request.className,
                methodName = request.methodName,
                widthPx = request.widthPx,
                heightPx = request.heightPx,
                density = request.density,
                robolectricSdk = request.robolectricSdk,
                outputFile = request.outputFile,
                semanticsOutputFile = request.semanticsOutputFile,
                layoutTreeOutputFile = request.layoutTreeOutputFile,
                includeUnmergedSemantics = request.includeUnmergedSemantics,
                locale = request.locale,
                uiMode = request.uiMode,
                fontScale = request.fontScale,
                showBackground = request.showBackground,
                backgroundColor = request.backgroundColor,
                previewParameterProviderClassName = request.previewParameterProviderClassName,
                previewParameterIndex = request.previewParameterIndex,
                resultFile = harnessResultFile,
            )
        val command =
            listOf(
                javaExecutable.path,
                "-cp",
                classpath.joinToString(File.pathSeparator) { it.absolutePath },
                AndroidComposeRenderHarness::class.java.name,
            ) + harnessCommand.toArgs()
        val execution =
            try {
                processService.run(command)
            } catch (e: java.io.IOException) {
                val message =
                    "Failed to start Android Compose preview renderer using java executable '${javaExecutable.path}'. " +
                        "Set agentpreview.java.executable to an executable java binary or configure java.home correctly. ${e.message.orEmpty()}"
                harnessResultFile.delete()
                return RenderProcessResult.Failure(RenderProcessFailureKind.HarnessFailure, message)
            }
        val output = listOfNotNull(androidJar.diagnostic, execution.output.takeIf { it.isNotBlank() }).joinToString("\n")
        if (execution.exitCode == 0) {
            androidJar.diagnostic?.let { diagnostic -> System.err.println("AgentPreview: $diagnostic") }
            harnessResultFile.delete()
            return RenderProcessResult.Success
        }
        val exitDescription = if (execution.timedOut) "after timeout" else "with exit code ${execution.exitCode}"
        val message =
            "Android Compose preview rendering failed for ${request.className}.${request.methodName} " +
                "$exitDescription.\n$output"
        val failureKind = RenderHarnessResultFile.readFailureKind(harnessResultFile)
        harnessResultFile.delete()
        return RenderProcessResult.Failure(
            kind =
                if (failureKind == RenderProcessFailureKind.ResourceLoadingGap) {
                    RenderProcessFailureKind.ResourceLoadingGap
                } else {
                    RenderProcessFailureKind.HarnessFailure
                },
            message = message,
        )
    }

    private fun currentPluginClasspath(): List<File> {
        val loaderFiles =
            (DefaultRenderProcessRunner::class.java.classLoader as? URLClassLoader)
                ?.urLs
                ?.mapNotNull { url -> url.toURI().takeIf { it.scheme == "file" } }
                ?.map(::File)
                ?: emptyList()
        val requiredCodeSources =
            listOf(
                DefaultRenderProcessRunner::class.java,
                JUnitCore::class.java,
                Robolectric::class.java,
                RobolectricTestRunner::class.java,
                Unit::class.java,
                Function2::class.java,
            ).mapNotNull { clazz ->
                clazz.protectionDomain.codeSource
                    ?.location
                    ?.toURI()
                    ?.let(::File)
            }
        return (loaderFiles + requiredCodeSources)
            .filter { it.exists() }
            .distinctBy { it.absoluteFile.normalize().path }
    }
}
