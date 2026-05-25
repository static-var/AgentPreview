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
    fun `passes include unmerged semantics flag to isolated harness`() {
        val result =
            runWithFakeJava(
                """
                #!/bin/sh
                if [ "${'$'}{13}" != "true" ]; then
                  echo "Expected includeUnmergedSemantics arg true, got ${'$'}{13}" >&2
                  exit 1
                fi
                cat > "${'$'}{19}" <<'EOF'
                status=success
                EOF
                exit 0
                """.trimIndent(),
                includeUnmergedSemantics = true,
            )

        assertEquals(RenderProcessResult.Success, result)
    }

    @Test
    fun `passes preview configuration fields to isolated harness`() {
        val result =
            runWithFakeJava(
                """
                #!/bin/sh
                if [ "${'$'}{14}" != "fr-rFR" ] || [ "${'$'}{15}" != "32" ] || [ "${'$'}{16}" != "1.3" ] || [ "${'$'}{17}" != "true" ] || [ "${'$'}{18}" != "4279312947" ]; then
                  echo "Unexpected preview config args: ${'$'}{14} ${'$'}{15} ${'$'}{16} ${'$'}{17} ${'$'}{18}" >&2
                  exit 1
                fi
                cat > "${'$'}{19}" <<'EOF'
                status=success
                EOF
                exit 0
                """.trimIndent(),
                locale = "fr-rFR",
                uiMode = 0x20,
                fontScale = 1.3f,
                showBackground = true,
                backgroundColor = 0xFF112233,
            )

        assertEquals(RenderProcessResult.Success, result)
    }

    @Test
    fun `structured resource loading gap result is classified as diagnostic fallback`() {
        val result =
            runWithFakeJava(
                """
                #!/bin/sh
                cat > "${'$'}{19}" <<'EOF'
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

    private fun runWithFakeJava(
        script: String,
        includeUnmergedSemantics: Boolean = false,
        locale: String? = null,
        uiMode: Int? = null,
        fontScale: Float? = null,
        showBackground: Boolean = false,
        backgroundColor: Long? = null,
    ): RenderProcessResult {
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
                        semanticsOutputFile = tempDir.resolve("preview.semantics.json"),
                        layoutTreeOutputFile = tempDir.resolve("preview.layout-tree.json"),
                        includeUnmergedSemantics = includeUnmergedSemantics,
                        locale = locale,
                        uiMode = uiMode,
                        fontScale = fontScale,
                        showBackground = showBackground,
                        backgroundColor = backgroundColor,
                    ),
                previewClasspath = emptyList(),
            )
        } finally {
            System.setProperty("java.home", originalJavaHome)
        }
    }
}
