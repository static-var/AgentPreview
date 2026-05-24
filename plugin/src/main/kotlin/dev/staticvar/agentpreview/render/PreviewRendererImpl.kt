/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.SnapshotNode
import dev.staticvar.agentpreview.model.Viewport
import dev.staticvar.agentpreview.sanitize
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

class PreviewRendererImpl(
    private val robolectricSdk: Int = DEFAULT_ROBOLECTRIC_SDK,
    private val previewClasspath: List<File> = emptyList(),
    private val processRunner: RenderProcessRunner = DefaultRenderProcessRunner(),
) : PreviewRenderer {
    fun render(
        preview: PreviewDescriptor,
        viewport: Viewport,
        outputDirectory: File,
    ): RenderResult {
        outputDirectory.mkdirs()
        val outputName = preview.id.sanitize() + "-" + (viewport.name ?: "preview")
        val screenshot = outputDirectory.resolve("$outputName.png")
        val semanticsOutput = outputDirectory.resolve("$outputName.semantics.json")
        require(robolectricSdk == SUPPORTED_ROBOLECTRIC_SDK) {
            "AgentPreview Android renderer currently supports only robolectricSdk=$SUPPORTED_ROBOLECTRIC_SDK; " +
                "configured robolectricSdk=$robolectricSdk is not used by the Robolectric entry point."
        }
        require(previewClasspath.isNotEmpty()) {
            "PreviewRendererImpl requires Android compiled classes and runtime classpath for real Robolectric " +
                "Compose rendering. Apply the plugin to an Android-backed variant or use " +
                "-PagentPreview.fakeRenderer=true for JSON-index-only captures."
        }
        val request =
            AndroidComposeRenderRequest(
                className = preview.fullyQualifiedClassName ?: preview.fullyQualifiedFunctionName.substringBeforeLast('.', ""),
                methodName = preview.fullyQualifiedFunctionName.substringAfterLast('.'),
                widthPx = (viewport.width * viewport.density).roundToInt().coerceAtLeast(1),
                heightPx = (viewport.height * viewport.density).roundToInt().coerceAtLeast(1),
                density = viewport.density,
                robolectricSdk = robolectricSdk,
                outputFile = screenshot,
                semanticsOutputFile = semanticsOutput,
            )
        val renderMode =
            when (val result = processRunner.run(request, previewClasspath)) {
                RenderProcessResult.Success -> RenderMode.Robolectric
                is RenderProcessResult.Failure -> handleFailure(result, preview, request, screenshot)
            }
        check(screenshot.isFile && screenshot.length() > PNG_HEADER_BYTES) {
            "Android Compose preview renderer did not produce a valid PNG for ${preview.id} at ${screenshot.absolutePath}."
        }
        return RenderResult(
            screenshotFile = screenshot,
            viewport = viewport,
            rawSemantics = readSemantics(semanticsOutput).takeIf { renderMode == RenderMode.Robolectric },
            renderMode = renderMode,
        )
    }

    private fun readSemantics(semanticsOutput: File): List<SnapshotNode> =
        if (semanticsOutput.isFile) {
            Json.decodeFromString(ListSerializer(SnapshotNode.serializer()), semanticsOutput.readText())
        } else {
            emptyList()
        }

    private fun handleFailure(
        failure: RenderProcessResult.Failure,
        preview: PreviewDescriptor,
        request: AndroidComposeRenderRequest,
        screenshot: File,
    ): RenderMode {
        if (failure.kind != RenderProcessFailureKind.ResourceLoadingGap) {
            error(failure.message)
        }
        System.err.println(
            "AgentPreview: falling back to diagnostic PNG for ${preview.id}; isolated Robolectric rendering hit a resource-loading gap. " +
                failure.message,
        )
        DiagnosticPngRenderer.render(
            outputFile = screenshot,
            widthPx = request.widthPx,
            heightPx = request.heightPx,
            title = "AgentPreview resource fallback",
            detail =
                "Robolectric Compose rendering could not load Android resources for ${preview.id}. " +
                    failure.message,
        )
        return RenderMode.DiagnosticFallback
    }

    override fun render(
        preview: PreviewDescriptor,
        outputDirectory: File,
    ): RenderResult =
        render(
            preview = preview,
            viewport =
                Viewport(
                    platform = "android",
                    name = "preview",
                    width = preview.widthDp ?: DEFAULT_WIDTH_DP,
                    height = preview.heightDp ?: DEFAULT_HEIGHT_DP,
                    density = DEFAULT_DENSITY,
                ),
            outputDirectory = outputDirectory,
        )

    private companion object {
        const val SUPPORTED_ROBOLECTRIC_SDK = 35
        const val DEFAULT_ROBOLECTRIC_SDK = SUPPORTED_ROBOLECTRIC_SDK
        const val DEFAULT_WIDTH_DP = 393
        const val DEFAULT_HEIGHT_DP = 852
        const val DEFAULT_DENSITY = 1.0f
        const val PNG_HEADER_BYTES = 8L
    }
}
