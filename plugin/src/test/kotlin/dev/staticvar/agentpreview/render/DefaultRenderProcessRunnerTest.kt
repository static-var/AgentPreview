/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.reflect.Method
import java.time.Duration
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

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
                cat > "${'$'}{21}" <<'EOF'
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
                cat > "${'$'}{21}" <<'EOF'
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
    fun `materializes aar R classes and embedded jars for isolated harness classpath`() {
        val classesJar =
            tempDir.resolve("classes.jar").also { file ->
                writeZip(
                    file,
                    "androidx/example/lib/UsesGeneratedR.class" to
                        "constant pool reference to androidx/example/lib/R${'$'}id".toByteArray(),
                )
            }
        val nestedJar = tempDir.resolve("nested.jar").also { file -> writeTextZip(file, "nested.txt" to "nested") }
        val aar =
            tempDir.resolve("example-${System.nanoTime()}.aar").also { file ->
                ZipOutputStream(file.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("classes.jar"))
                    zip.write(classesJar.readBytes())
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("R.txt"))
                    zip.write("int id pooled_container_tag 0x0\nint string label 0x0\n".toByteArray())
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("libs/nested.jar"))
                    zip.write(nestedJar.readBytes())
                    zip.closeEntry()
                }
            }

        val result =
            runWithFakeJava(
                """
                #!/bin/sh
                CLASSPATH="${'$'}2" python3 - <<'PY'
                import os, sys, zipfile
                entries = os.environ['CLASSPATH'].split(os.pathsep)
                has_generated_r = False
                has_nested_jar = False
                raw_aars = [entry for entry in entries if entry.endswith('.aar')]
                if raw_aars:
                    print('raw AARs must not be included on isolated Java classpath: ' + ','.join(raw_aars), file=sys.stderr)
                    sys.exit(1)
                for entry in entries:
                    if not zipfile.is_zipfile(entry):
                        continue
                    with zipfile.ZipFile(entry) as jar:
                        names = set(jar.namelist())
                        if 'androidx/example/lib/R${'$'}id.class' in names:
                            r_class = jar.read('androidx/example/lib/R${'$'}id.class')
                            has_generated_r = has_generated_r or b'\x7f\x01\x00\x00' in r_class
                    has_nested_jar = has_nested_jar or 'nested.txt' in names
                if not has_generated_r:
                    print('missing generated androidx/example/lib/R${'$'}id.class', file=sys.stderr)
                    sys.exit(1)
                if not has_nested_jar:
                    print('missing embedded libs/nested.jar on classpath', file=sys.stderr)
                    sys.exit(1)
                PY
                if [ "${'$'}?" -ne 0 ]; then
                  exit 1
                fi
                cat > "${'$'}{21}" <<'EOF'
                status=success
                EOF
                exit 0
                """.trimIndent(),
                previewClasspath = listOf(aar),
            )

        assertEquals(RenderProcessResult.Success, result)
    }

    @Test
    fun `generated aar R jar uses Java 8 compatible bytecode`() {
        val aar = aarWithRSymbols("androidx.example.lib", "int id pooled_container_tag 0x0\n")

        val output = materializeAarRClassesJarMethod().invoke(DefaultRenderProcessRunner(), aar) as File

        assertEquals(52, classMajorVersion(output, "androidx/example/lib/R${'$'}id.class"))
    }

    @Test
    fun `generated aar R jar entries are written in deterministic sorted order`() {
        val aar = aarWithRSymbols("androidx.example.lib", "int style AppTheme 0x0\nint id button 0x0\n")

        val output = materializeAarRClassesJarMethod().invoke(DefaultRenderProcessRunner(), aar) as File

        val entries =
            JarFile(output).use { jar ->
                jar
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .toList()
            }
        assertEquals(
            listOf(
                "androidx/example/lib/R.class",
                "androidx/example/lib/R${'$'}id.class",
                "androidx/example/lib/R${'$'}style.class",
            ),
            entries,
        )
    }

    @Test
    fun `aar R jar materialization returns null when generated source cannot compile`() {
        val aar = aarWithRSymbols("androidx.example.lib", "int id invalid-name 0x0\n")

        val output = materializeAarRClassesJarMethod().invoke(DefaultRenderProcessRunner(), aar)

        assertEquals(null, output)
    }

    @Test
    fun `render harness command round trips named fields`() {
        val command =
            RenderHarnessCommand(
                className = "dev.example.PreviewKt",
                methodName = "Preview",
                widthPx = 10,
                heightPx = 20,
                density = 1.5f,
                robolectricSdk = 35,
                outputFile = tempDir.resolve("preview.png"),
                semanticsOutputFile = tempDir.resolve("preview.semantics.json"),
                layoutTreeOutputFile = tempDir.resolve("preview.layout.json"),
                includeUnmergedSemantics = true,
                locale = "fr-rFR",
                uiMode = 32,
                fontScale = 1.3f,
                showBackground = true,
                backgroundColor = 4279312947,
                previewParameterProviderClassName = "dev.example.Provider",
                previewParameterIndex = 2,
                resultFile = tempDir.resolve("result.properties"),
            )

        assertEquals(command, RenderHarnessCommand.fromArgs(command.toArgs().toTypedArray()))
    }

    @Test
    fun `render process times out and captures bounded output`() {
        val result =
            runWithFakeJava(
                """
                #!/bin/sh
                python3 - <<'PY'
                print('x' * 2000)
                PY
                sleep 5
                """.trimIndent(),
                timeoutMillis = 200,
                maxOutputBytes = 128,
            )

        assertTrue(result is RenderProcessResult.Failure)
        val failure = result as RenderProcessResult.Failure
        assertTrue(failure.message.contains("after timeout"))
        assertTrue(failure.message.contains("truncated"))
        assertTrue(failure.message.length < 1000)
    }

    @Test
    fun `invalid configured java executable returns actionable render failure`() {
        val missingJava = tempDir.resolve("missing-java")
        val originalJavaExecutable = System.getProperty("agentpreview.java.executable")
        try {
            System.setProperty("agentpreview.java.executable", missingJava.absolutePath)

            val result = DefaultRenderProcessRunner().run(defaultRequest(), previewClasspath = emptyList())

            assertTrue(result is RenderProcessResult.Failure)
            val failure = result as RenderProcessResult.Failure
            assertEquals(RenderProcessFailureKind.HarnessFailure, failure.kind)
            assertTrue(failure.message.contains("agentpreview.java.executable"), failure.message)
            assertTrue(failure.message.contains(missingJava.absolutePath), failure.message)
            assertTrue(failure.message.contains("does not exist"), failure.message)
        } finally {
            if (originalJavaExecutable == null) {
                System.clearProperty("agentpreview.java.executable")
            } else {
                System.setProperty("agentpreview.java.executable", originalJavaExecutable)
            }
        }
    }

    @Test
    fun `render process timeout kills descendants`() {
        assumeTrue(File("/bin/sh").canExecute(), "/bin/sh is required for descendant kill test")
        val descendantPidFile = tempDir.resolve("descendant.pid")
        val script =
            tempDir.resolve("spawn-descendant.sh").also { file ->
                file.writeText(
                    """
                    #!/bin/sh
                    DESCENDANT_PID_FILE="${descendantPidFile.absolutePath}" python3 - <<'PY' &
                    import os
                    import time

                    with open(os.environ["DESCENDANT_PID_FILE"], "w") as pid_file:
                        pid_file.write(str(os.getpid()))
                        pid_file.flush()
                    time.sleep(30)
                    PY
                    sleep 30
                    """.trimIndent(),
                )
                file.setExecutable(true)
            }

        val execution = RenderProcessService(timeout = Duration.ofMillis(200)).run(listOf(script.absolutePath))

        assertTrue(execution.timedOut)
        assertTrue(descendantPidFile.exists(), "descendant process did not start")
        val descendantPid = descendantPidFile.readText().trim().toLong()
        val deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos()
        while (isProcessAlive(descendantPid) && System.nanoTime() < deadline) {
            Thread.sleep(50)
        }
        assertTrue(!isProcessAlive(descendantPid), "descendant process survived timeout: pid=$descendantPid")
    }

    @Test
    fun `android renderer environment reports missing sdk variables`() {
        val resolution = AndroidRendererEnvironment(env = emptyMap()).androidJar(35)

        assertEquals(emptyList<File>(), resolution.files)
        assertTrue(resolution.diagnostic.orEmpty().contains("ANDROID_HOME or ANDROID_SDK_ROOT is not set"))
    }

    @Test
    fun `android renderer environment reports missing requested platform and falls back`() {
        val sdkRoot = tempDir.resolve("sdk")
        val androidJar = sdkRoot.resolve("platforms/android-34/android.jar")
        androidJar.parentFile.mkdirs()
        androidJar.writeText("")

        val resolution = AndroidRendererEnvironment(env = mapOf("ANDROID_HOME" to sdkRoot.absolutePath)).androidJar(35)

        assertEquals(listOf(androidJar), resolution.files)
        assertTrue(resolution.diagnostic.orEmpty().contains("platform android-35 was not found"))
    }

    @Test
    fun `successful render surfaces android sdk fallback diagnostic`() {
        val sdkRoot = tempDir.resolve("sdk")
        val androidJar = sdkRoot.resolve("platforms/android-34/android.jar")
        androidJar.parentFile.mkdirs()
        androidJar.writeText("")
        val stderr = ByteArrayOutputStream()
        val originalErr = System.err
        try {
            System.setErr(PrintStream(stderr))

            val result =
                runWithFakeJava(
                    """
                    #!/bin/sh
                    cat > "${'$'}{21}" <<'EOF'
                    status=success
                    EOF
                    exit 0
                    """.trimIndent(),
                    environment = AndroidRendererEnvironment(env = mapOf("ANDROID_HOME" to sdkRoot.absolutePath)),
                )

            assertEquals(RenderProcessResult.Success, result)
            val output = stderr.toString()
            assertTrue(output.contains("platform android-35 was not found"), output)
            assertTrue(output.contains("using android-34 instead"), output)
        } finally {
            System.setErr(originalErr)
        }
    }

    @Test
    fun `structured resource loading gap result is classified as diagnostic fallback`() {
        val result =
            runWithFakeJava(
                """
                #!/bin/sh
                cat > "${'$'}{21}" <<'EOF'
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
        previewClasspath: List<File> = emptyList(),
        timeoutMillis: Long? = null,
        maxOutputBytes: Int? = null,
        environment: AndroidRendererEnvironment = AndroidRendererEnvironment(),
    ): RenderProcessResult {
        val originalJavaExecutable = System.getProperty("agentpreview.java.executable")
        val javaExecutable = tempDir.resolve("fake-java-home/bin/java")
        javaExecutable.parentFile.mkdirs()
        javaExecutable.writeText(script)
        javaExecutable.setExecutable(true)
        return try {
            System.setProperty("agentpreview.java.executable", javaExecutable.absolutePath)
            DefaultRenderProcessRunner(
                environment = environment,
                processService =
                    RenderProcessService(
                        timeout = timeoutMillis?.let(Duration::ofMillis) ?: RenderProcessService.defaultTimeout(),
                        maxOutputBytes = maxOutputBytes ?: RenderProcessService.defaultMaxOutputBytes(),
                    ),
            ).run(
                request =
                    defaultRequest(
                        includeUnmergedSemantics = includeUnmergedSemantics,
                        locale = locale,
                        uiMode = uiMode,
                        fontScale = fontScale,
                        showBackground = showBackground,
                        backgroundColor = backgroundColor,
                    ),
                previewClasspath = previewClasspath,
            )
        } finally {
            if (originalJavaExecutable == null) {
                System.clearProperty("agentpreview.java.executable")
            } else {
                System.setProperty("agentpreview.java.executable", originalJavaExecutable)
            }
        }
    }

    private fun defaultRequest(
        includeUnmergedSemantics: Boolean = false,
        locale: String? = null,
        uiMode: Int? = null,
        fontScale: Float? = null,
        showBackground: Boolean = false,
        backgroundColor: Long? = null,
    ): AndroidComposeRenderRequest =
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
        )

    private fun isProcessAlive(pid: Long): Boolean =
        ProcessHandle
            .of(pid)
            .map(ProcessHandle::isAlive)
            .orElse(false)

    private fun materializeAarRClassesJarMethod(): Method =
        DefaultRenderProcessRunner::class.java
            .getDeclaredMethod("materializeAarRClassesJar", File::class.java)
            .apply { isAccessible = true }

    private fun aarWithRSymbols(
        packageName: String,
        rTxt: String,
    ): File {
        val packagePath = packageName.replace('.', '/')
        val classesJar =
            tempDir.resolve("$packageName-${System.nanoTime()}-classes.jar").also { file ->
                writeZip(
                    file,
                    "$packagePath/UsesGeneratedR.class" to
                        "constant pool reference to $packagePath/R${'$'}id".toByteArray(),
                )
            }
        return tempDir.resolve("$packageName-${System.nanoTime()}.aar").also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("classes.jar"))
                zip.write(classesJar.readBytes())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("R.txt"))
                zip.write(rTxt.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun classMajorVersion(
        jarFile: File,
        entryName: String,
    ): Int =
        ZipFile(jarFile).use { zip ->
            val bytes = zip.getInputStream(zip.getEntry(entryName)).use { it.readBytes() }
            ((bytes[6].toInt() and 0xff) shl 8) or (bytes[7].toInt() and 0xff)
        }

    private fun writeZip(
        file: File,
        vararg entries: Pair<String, ByteArray>,
    ) {
        ZipOutputStream(file.outputStream()).use { zip -> zip.writeZipBytes(*entries) }
    }

    private fun writeTextZip(
        file: File,
        vararg entries: Pair<String, String>,
    ) {
        writeZip(file, *entries.map { it.first to it.second.toByteArray() }.toTypedArray())
    }

    private fun ZipOutputStream.writeZipBytes(vararg entries: Pair<String, ByteArray>) {
        entries.forEach { (name, bytes) ->
            putNextEntry(ZipEntry(name))
            write(bytes)
            closeEntry()
        }
    }
}
