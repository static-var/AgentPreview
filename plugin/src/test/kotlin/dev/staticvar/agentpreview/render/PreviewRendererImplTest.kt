/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.Viewport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PreviewRendererImplTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `renders preview through isolated harness process`() {
        val processRunner = RecordingRenderProcessRunner()
        val renderer =
            PreviewRendererImpl(
                robolectricSdk = 35,
                previewClasspath = listOf(File("app/classes"), File("app/runtime.jar")),
                processRunner = processRunner,
            )
        val preview =
            PreviewDescriptor(
                id = "dev.example.LoginPreview",
                sourceSet = "main",
                fullyQualifiedFunctionName = "dev.example.LoginPreview",
                fullyQualifiedClassName = "dev.example.LoginPreviewKt",
                sourceFile = "LoginPreview.kt",
            )
        val viewport = Viewport(platform = "android", name = "phone", width = 393, height = 852, density = 2.0f)

        val result = renderer.render(preview, viewport, tempDir)

        assertEquals(tempDir.resolve("dev.example.LoginPreview-phone.png"), result.screenshotFile)
        assertEquals(viewport, result.viewport)
        assertEquals(
            AndroidComposeRenderRequest(
                className = "dev.example.LoginPreviewKt",
                methodName = "LoginPreview",
                widthPx = 786,
                heightPx = 1704,
                density = 2.0f,
                robolectricSdk = 35,
                outputFile = result.screenshotFile,
            ),
            processRunner.request,
        )
        assertEquals(listOf(File("app/classes"), File("app/runtime.jar")), processRunner.previewClasspath)
        assertTrue(result.screenshotFile.parentFile.isDirectory)
    }

    private class RecordingRenderProcessRunner : RenderProcessRunner {
        lateinit var request: AndroidComposeRenderRequest
        lateinit var previewClasspath: List<File>

        override fun run(
            request: AndroidComposeRenderRequest,
            previewClasspath: List<File>,
        ) {
            this.request = request
            this.previewClasspath = previewClasspath
            request.outputFile.writeBytes(
                byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0, 0, 0, 0),
            )
        }
    }
}
