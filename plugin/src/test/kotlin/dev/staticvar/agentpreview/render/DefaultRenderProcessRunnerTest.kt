/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DefaultRenderProcessRunnerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `resource-like process output is not classified as resource loading gap without structured result`() {
        val result =
            runWithFakeJava(
                """
                #!/bin/sh
                echo 'android.content.res.Resources${'$'}NotFoundException: Resource ID #0x7f010001' >&2
                exit 1
                """.trimIndent(),
            )

        assertTrue(result is RenderProcessResult.Failure)
        assertEquals(RenderProcessFailureKind.HarnessFailure, (result as RenderProcessResult.Failure).kind)
        assertTrue(result.message.contains("Resources${'$'}NotFoundException"))
    }

    @Test
    fun `structured resource loading gap result is classified as diagnostic fallback`() {
        val result =
            runWithFakeJava(
                """
                #!/bin/sh
                cat > "${'$'}{11}" <<'EOF'
                status=failure
                failureKind=ResourceLoadingGap
                EOF
                echo 'render harness reported structured resource loading gap' >&2
                exit 1
                """.trimIndent(),
            )

        assertTrue(result is RenderProcessResult.Failure)
        assertEquals(RenderProcessFailureKind.ResourceLoadingGap, (result as RenderProcessResult.Failure).kind)
    }

    private fun runWithFakeJava(script: String): RenderProcessResult {
        val originalJavaHome = System.getProperty("java.home")
        val javaHome = tempDir.resolve("fake-java-home")
        val javaExecutable = javaHome.resolve("bin/java")
        javaExecutable.parentFile.mkdirs()
        javaExecutable.writeText(script)
        javaExecutable.setExecutable(true)
        return try {
            System.setProperty("java.home", javaHome.absolutePath)
            DefaultRenderProcessRunner().run(
                request =
                    AndroidComposeRenderRequest(
                        className = "dev.example.PreviewKt",
                        methodName = "Preview",
                        widthPx = 10,
                        heightPx = 10,
                        density = 1.0f,
                        robolectricSdk = 35,
                        outputFile = tempDir.resolve("preview.png"),
                    ),
                previewClasspath = emptyList(),
            )
        } finally {
            System.setProperty("java.home", originalJavaHome)
        }
    }
}
