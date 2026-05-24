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
import java.util.zip.ZipFile

class DefaultRenderProcessRunner : RenderProcessRunner {
    override fun run(
        request: AndroidComposeRenderRequest,
        previewClasspath: List<File>,
    ): RenderProcessResult {
        val classpath = materializeClasspath(currentPluginClasspath() + androidJar(request.robolectricSdk) + previewClasspath)
        val harnessResultFile = File.createTempFile("agentpreview-render-harness-", ".properties")
        harnessResultFile.delete()
        val command =
            listOf(
                javaExecutable(),
                "-cp",
                classpath.joinToString(File.pathSeparator) { it.absolutePath },
                AndroidComposeRenderHarness::class.java.name,
                request.className,
                request.methodName,
                request.widthPx.toString(),
                request.heightPx.toString(),
                request.density.toString(),
                request.robolectricSdk.toString(),
                request.outputFile.absolutePath,
                harnessResultFile.absolutePath,
            )
        val process =
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            harnessResultFile.delete()
            return RenderProcessResult.Success
        }
        val message =
            "Android Compose preview rendering failed for ${request.className}.${request.methodName} " +
                "with exit code $exitCode.\n$output"
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

    private fun materializeClasspath(classpath: List<File>): List<File> =
        classpath.flatMap { file ->
            if (file.extension == "aar" && file.isFile) {
                listOfNotNull(extractAarClassesJar(file), file)
            } else {
                listOf(file)
            }
        }

    private fun extractAarClassesJar(aar: File): File? {
        val output =
            File(
                File(System.getProperty("java.io.tmpdir"), "agentpreview-aar-classes"),
                aar.nameWithoutExtension + "-${aar.length()}.jar",
            )
        if (output.isFile) return output
        output.parentFile.mkdirs()
        ZipFile(aar).use { zip ->
            val entry = zip.getEntry("classes.jar") ?: return null
            zip.getInputStream(entry).use { input ->
                output.outputStream().use { outputStream -> input.copyTo(outputStream) }
            }
        }
        return output
    }

    private fun androidJar(sdk: Int): List<File> {
        val sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: return emptyList()
        val requested = File(sdkRoot, "platforms/android-$sdk/android.jar")
        if (requested.isFile) return listOf(requested)
        return File(sdkRoot, "platforms")
            .listFiles { file -> file.isDirectory && file.name.startsWith("android-") }
            ?.maxByOrNull { file -> file.name.removePrefix("android-").toIntOrNull() ?: 0 }
            ?.resolve("android.jar")
            ?.takeIf { it.isFile }
            ?.let(::listOf)
            ?: emptyList()
    }

    private fun javaExecutable(): String = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath
}
