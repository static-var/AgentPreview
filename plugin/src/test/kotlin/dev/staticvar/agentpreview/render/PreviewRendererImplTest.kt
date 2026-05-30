/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.model.Bounds
import dev.staticvar.agentpreview.model.DpBounds
import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.PreviewParameterDescriptor
import dev.staticvar.agentpreview.model.SnapshotLayoutNode
import dev.staticvar.agentpreview.model.SnapshotNode
import dev.staticvar.agentpreview.model.Viewport
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
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
                includeUnmergedSemantics = true,
                processRunner = processRunner,
            )
        val preview =
            PreviewDescriptor(
                id = "dev.example.LoginPreview",
                sourceSet = "main",
                fullyQualifiedFunctionName = "dev.example.LoginPreview",
                fullyQualifiedClassName = "dev.example.LoginPreviewKt",
                sourceFile = "LoginPreview.kt",
                locale = "fr-rFR",
                uiMode = 0x20,
                fontScale = 1.3f,
                showBackground = true,
                backgroundColor = 0xFF112233,
            )
        val viewport = Viewport(platform = "android", name = "phone", width = 393, height = 852, density = 2.0f)

        val result = renderer.render(preview, viewport, tempDir)

        assertEquals(tempDir, result.screenshotFile.parentFile)
        assertEquals("preview.png", result.screenshotFile.name)
        assertEquals(viewport, result.viewport)
        assertEquals(RenderMode.Robolectric, result.renderMode)
        assertEquals(
            AndroidComposeRenderRequest(
                className = "dev.example.LoginPreviewKt",
                methodName = "LoginPreview",
                widthPx = 786,
                heightPx = 1704,
                density = 2.0f,
                robolectricSdk = 35,
                outputFile = result.screenshotFile,
                semanticsOutputFile = result.screenshotFile.resolveSibling(result.screenshotFile.nameWithoutExtension + ".semantics.json"),
                layoutTreeOutputFile =
                    result.screenshotFile.resolveSibling(
                        result.screenshotFile.nameWithoutExtension + ".layout-tree.json",
                    ),
                includeUnmergedSemantics = true,
                locale = "fr-rFR",
                uiMode = 0x20,
                fontScale = 1.3f,
                showBackground = true,
                backgroundColor = 0xFF112233,
            ),
            processRunner.request,
        )
        assertEquals(listOf(File("app/classes"), File("app/runtime.jar")), processRunner.previewClasspath)
        assertTrue(result.screenshotFile.parentFile.isDirectory)
    }

    @Test
    fun `passes preview parameter metadata to isolated harness request`() {
        val processRunner = RecordingRenderProcessRunner()
        val renderer =
            PreviewRendererImpl(
                robolectricSdk = 35,
                previewClasspath = listOf(File("app/classes")),
                processRunner = processRunner,
            )
        val preview =
            PreviewDescriptor(
                id = "dev.example.ParameterizedPreview:previewParam-1",
                sourceSet = "main",
                fullyQualifiedFunctionName = "dev.example.ParameterizedPreview",
                fullyQualifiedClassName = "dev.example.ParameterizedPreviewKt",
                sourceFile = "ParameterizedPreview.kt",
                previewParameter =
                    PreviewParameterDescriptor(
                        providerClassName = "dev.example.StringProvider",
                        parameterType = "java.lang.String",
                        index = 1,
                    ),
            )

        renderer.render(preview, Viewport(platform = "android", name = "phone", width = 1, height = 1, density = 1.0f), tempDir)

        assertEquals("dev.example.StringProvider", processRunner.request.previewParameterProviderClassName)
        assertEquals(1, processRunner.request.previewParameterIndex)
    }

    @Test
    fun `reads structured layout tree output from isolated harness process`() {
        val expectedLayoutTree =
            listOf(
                SnapshotLayoutNode(
                    id = "layout-1",
                    boundsPx = Bounds(x = 10, y = 20, width = 80, height = 40),
                    boundsDp = DpBounds(x = 5.0f, y = 10.0f, width = 40.0f, height = 20.0f),
                    componentHint = "ColumnMeasurePolicy",
                    semanticsId = "3",
                ),
            )
        val renderer =
            PreviewRendererImpl(
                robolectricSdk = 35,
                previewClasspath = listOf(File("app/classes")),
                processRunner = LayoutTreeRenderProcessRunner(expectedLayoutTree),
            )
        val preview =
            PreviewDescriptor(
                id = "dev.example.LayoutPreview",
                sourceSet = "main",
                fullyQualifiedFunctionName = "dev.example.LayoutPreview",
                fullyQualifiedClassName = "dev.example.LayoutPreviewKt",
                sourceFile = "LayoutPreview.kt",
            )
        val viewport = Viewport(platform = "android", name = "phone", width = 393, height = 852, density = 2.0f)

        val result = renderer.render(preview, viewport, tempDir)

        assertEquals(expectedLayoutTree, result.layoutTree)
    }

    @Test
    fun `missing optional layout tree sidecar returns empty layout tree`() {
        val renderer =
            PreviewRendererImpl(
                robolectricSdk = 35,
                previewClasspath = listOf(File("app/classes")),
                processRunner = ScreenshotOnlyRenderProcessRunner(),
            )
        val preview =
            PreviewDescriptor(
                id = "dev.example.MissingLayoutPreview",
                sourceSet = "main",
                fullyQualifiedFunctionName = "dev.example.MissingLayoutPreview",
                fullyQualifiedClassName = "dev.example.MissingLayoutPreviewKt",
                sourceFile = "MissingLayoutPreview.kt",
            )
        val viewport = Viewport(platform = "android", name = "phone", width = 393, height = 852, density = 2.0f)

        val result = renderer.render(preview, viewport, tempDir)

        assertEquals(emptyList<SnapshotLayoutNode>(), result.layoutTree)
        assertEquals(RenderMode.Robolectric, result.renderMode)
    }

    @Test
    fun `malformed optional layout tree output does not fail render`() {
        val renderer =
            PreviewRendererImpl(
                robolectricSdk = 35,
                previewClasspath = listOf(File("app/classes")),
                processRunner = MalformedLayoutTreeRenderProcessRunner(),
            )
        val preview =
            PreviewDescriptor(
                id = "dev.example.MalformedLayoutPreview",
                sourceSet = "main",
                fullyQualifiedFunctionName = "dev.example.MalformedLayoutPreview",
                fullyQualifiedClassName = "dev.example.MalformedLayoutPreviewKt",
                sourceFile = "MalformedLayoutPreview.kt",
            )
        val viewport = Viewport(platform = "android", name = "phone", width = 393, height = 852, density = 2.0f)

        val result = renderer.render(preview, viewport, tempDir)

        assertEquals(emptyList<SnapshotLayoutNode>(), result.layoutTree)
        assertEquals(RenderMode.Robolectric, result.renderMode)
    }

    @Test
    fun `reads structured semantics output from isolated harness process`() {
        val expectedNodes =
            listOf(
                SnapshotNode(
                    id = "1",
                    text = "Welcome back",
                    contentDescription = "Login heading",
                    bounds = Bounds(x = 4, y = 8, width = 120, height = 32),
                ),
            )
        val renderer =
            PreviewRendererImpl(
                robolectricSdk = 35,
                previewClasspath = listOf(File("app/classes")),
                processRunner = SemanticsRenderProcessRunner(expectedNodes),
            )
        val preview =
            PreviewDescriptor(
                id = "dev.example.SemanticsPreview",
                sourceSet = "main",
                fullyQualifiedFunctionName = "dev.example.SemanticsPreview",
                fullyQualifiedClassName = "dev.example.SemanticsPreviewKt",
                sourceFile = "SemanticsPreview.kt",
            )
        val viewport = Viewport(platform = "android", name = "phone", width = 393, height = 852, density = 1.0f)

        val result = renderer.render(preview, viewport, tempDir)

        assertEquals(expectedNodes, result.rawSemantics)
    }

    @Test
    fun `non resource harness failures fail instead of exporting diagnostic png`() {
        val renderer =
            PreviewRendererImpl(
                robolectricSdk = 35,
                previewClasspath = listOf(File("app/classes")),
                processRunner = FailingRenderProcessRunner("java.lang.ClassNotFoundException: dev.example.MissingPreview"),
            )
        val preview =
            PreviewDescriptor(
                id = "dev.example.MissingPreview",
                sourceSet = "main",
                fullyQualifiedFunctionName = "dev.example.MissingPreview",
                fullyQualifiedClassName = "dev.example.MissingPreviewKt",
                sourceFile = "MissingPreview.kt",
            )
        val viewport = Viewport(platform = "android", name = "phone", width = 393, height = 852, density = 1.0f)

        val error = assertThrows(IllegalStateException::class.java) { renderer.render(preview, viewport, tempDir) }

        assertTrue(error.message.orEmpty().contains("ClassNotFoundException"))
        assertFalse(tempDir.resolve("preview.png").exists())
    }

    @Test
    fun `resource loading gaps are explicitly marked as diagnostic fallback`() {
        val renderer =
            PreviewRendererImpl(
                robolectricSdk = 35,
                previewClasspath = listOf(File("app/classes")),
                processRunner = ResourceGapRenderProcessRunner(),
            )
        val preview =
            PreviewDescriptor(
                id = "dev.example.ResourcePreview",
                sourceSet = "main",
                fullyQualifiedFunctionName = "dev.example.ResourcePreview",
                fullyQualifiedClassName = "dev.example.ResourcePreviewKt",
                sourceFile = "ResourcePreview.kt",
            )
        val viewport = Viewport(platform = "android", name = "phone", width = 20, height = 10, density = 1.0f)

        val result = renderer.render(preview, viewport, tempDir)

        assertEquals(RenderMode.DiagnosticFallback, result.renderMode)
        assertTrue(result.screenshotFile.isFile)
    }

    private class FailingRenderProcessRunner(
        private val message: String,
    ) : RenderProcessRunner {
        override fun run(
            request: AndroidComposeRenderRequest,
            previewClasspath: List<File>,
        ): RenderProcessResult = RenderProcessResult.Failure(RenderProcessFailureKind.HarnessFailure, message)
    }

    private class ResourceGapRenderProcessRunner : RenderProcessRunner {
        override fun run(
            request: AndroidComposeRenderRequest,
            previewClasspath: List<File>,
        ): RenderProcessResult =
            RenderProcessResult.Failure(
                RenderProcessFailureKind.ResourceLoadingGap,
                "android.content.res.Resources\$NotFoundException: Resource ID #0x7f010001",
            )
    }

    private class LayoutTreeRenderProcessRunner(
        private val layoutTree: List<SnapshotLayoutNode>,
    ) : RenderProcessRunner {
        override fun run(
            request: AndroidComposeRenderRequest,
            previewClasspath: List<File>,
        ): RenderProcessResult {
            request.outputFile.writeBytes(
                byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0, 0, 0, 0),
            )
            request.layoutTreeOutputFile.writeText(Json.encodeToString(ListSerializer(SnapshotLayoutNode.serializer()), layoutTree))
            return RenderProcessResult.Success
        }
    }

    private class ScreenshotOnlyRenderProcessRunner : RenderProcessRunner {
        override fun run(
            request: AndroidComposeRenderRequest,
            previewClasspath: List<File>,
        ): RenderProcessResult {
            request.outputFile.writeBytes(
                byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0, 0, 0, 0),
            )
            return RenderProcessResult.Success
        }
    }

    private class MalformedLayoutTreeRenderProcessRunner : RenderProcessRunner {
        override fun run(
            request: AndroidComposeRenderRequest,
            previewClasspath: List<File>,
        ): RenderProcessResult {
            request.outputFile.writeBytes(
                byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0, 0, 0, 0),
            )
            request.layoutTreeOutputFile.writeText("not-json")
            return RenderProcessResult.Success
        }
    }

    private class SemanticsRenderProcessRunner(
        private val nodes: List<SnapshotNode>,
    ) : RenderProcessRunner {
        override fun run(
            request: AndroidComposeRenderRequest,
            previewClasspath: List<File>,
        ): RenderProcessResult {
            request.outputFile.writeBytes(
                byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0, 0, 0, 0),
            )
            request.semanticsOutputFile.writeText(Json.encodeToString(ListSerializer(SnapshotNode.serializer()), nodes))
            return RenderProcessResult.Success
        }
    }

    private class RecordingRenderProcessRunner : RenderProcessRunner {
        lateinit var request: AndroidComposeRenderRequest
        lateinit var previewClasspath: List<File>

        override fun run(
            request: AndroidComposeRenderRequest,
            previewClasspath: List<File>,
        ): RenderProcessResult {
            this.request = request
            this.previewClasspath = previewClasspath
            request.outputFile.writeBytes(
                byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0, 0, 0, 0),
            )
            return RenderProcessResult.Success
        }
    }
}
