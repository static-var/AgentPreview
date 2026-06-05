/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class RenderHarnessCommandTest {
    @Test
    fun `serializes synthetic asset apk through args and system properties`() {
        val command =
            RenderHarnessCommand(
                className = "Class",
                methodName = "Preview",
                widthPx = 1,
                heightPx = 2,
                density = 3f,
                robolectricSdk = 35,
                outputFile = File("out.png").absoluteFile,
                semanticsOutputFile = File("semantics.json").absoluteFile,
                layoutTreeOutputFile = File("layout.json").absoluteFile,
                includeUnmergedSemantics = true,
                locale = "en-rUS",
                uiMode = 32,
                fontScale = 1.2f,
                showBackground = true,
                backgroundColor = 123L,
                previewParameterProviderClassName = "Provider",
                previewParameterIndex = 4,
                resultFile = File("result.properties").absoluteFile,
                androidAssetsDir = File("assets").absoluteFile,
                androidAssetApk = File("assets.apk").absoluteFile,
                fontProbe = true,
            )

        val parsed = RenderHarnessCommand.fromArgs(command.toArgs().toTypedArray())

        assertEquals(command, parsed)
        command.applyToSystemProperties()
        assertEquals(File("assets.apk").absolutePath, System.getProperty("agentpreview.render.androidAssetApk"))
        assertEquals("true", System.getProperty("agentpreview.render.fontProbe"))
    }
}
