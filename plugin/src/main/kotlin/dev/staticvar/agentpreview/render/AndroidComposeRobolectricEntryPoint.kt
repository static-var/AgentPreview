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
        )
    }

    private fun requireProperty(name: String): String = requireNotNull(System.getProperty(name)) { "Missing system property $name" }
}
