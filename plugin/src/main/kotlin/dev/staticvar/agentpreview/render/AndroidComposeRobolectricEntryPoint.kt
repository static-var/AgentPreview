/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AndroidComposeRobolectricEntryPoint {
    @Test
    fun renderPreview() {
        AndroidComposeRendererInRobolectric.render(
            className = requireProperty("agentpreview.render.className"),
            methodName = requireProperty("agentpreview.render.methodName"),
            widthPx = requireProperty("agentpreview.render.widthPx").toInt(),
            heightPx = requireProperty("agentpreview.render.heightPx").toInt(),
            density = requireProperty("agentpreview.render.density").toFloat(),
            outputFile = File(requireProperty("agentpreview.render.outputFile")),
            semanticsOutputFile = File(requireProperty("agentpreview.render.semanticsOutputFile")),
            layoutTreeOutputFile = File(requireProperty("agentpreview.render.layoutTreeOutputFile")),
            includeUnmergedSemantics = requireProperty("agentpreview.render.includeUnmergedSemantics").toBoolean(),
            locale = optionalProperty("agentpreview.render.locale"),
            uiMode = optionalProperty("agentpreview.render.uiMode")?.toInt(),
            fontScale = optionalProperty("agentpreview.render.fontScale")?.toFloat(),
            showBackground = requireProperty("agentpreview.render.showBackground").toBoolean(),
            backgroundColor = optionalProperty("agentpreview.render.backgroundColor")?.toLong(),
            previewParameterProviderClassName = optionalProperty("agentpreview.render.previewParameterProviderClassName"),
            previewParameterIndex = optionalProperty("agentpreview.render.previewParameterIndex")?.toInt(),
        )
    }

    private fun requireProperty(name: String): String = requireNotNull(System.getProperty(name)) { "Missing system property $name" }

    private fun optionalProperty(name: String): String? = System.getProperty(name)?.takeIf { it.isNotBlank() }
}
