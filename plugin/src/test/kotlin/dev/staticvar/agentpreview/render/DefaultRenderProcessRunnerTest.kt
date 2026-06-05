/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
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
    fun `adds generated robolectric config root before plugin and preview classpath when assets are present`() {
        val runtimeJar = tempDir.resolve("runtime.jar").also { writeTextZip(it, "runtime.txt" to "runtime") }
        val previewJar = tempDir.resolve("preview.jar").also { writeTextZip(it, "preview.txt" to "preview") }
        val assetsDir = tempDir.resolve("merged-assets").also { it.mkdirs() }

        val result =
            runWithFakeJava(
                """
                #!/bin/sh
                CLASSPATH="${'$'}2" ASSETS_DIR="${assetsDir.absolutePath}" RUNTIME_JAR="${runtimeJar.absolutePath}" PREVIEW_JAR="${previewJar.absolutePath}" python3 - <<'PY'
                import os, sys
                entries = os.environ['CLASSPATH'].split(os.pathsep)
                runtime_index = entries.index(os.environ['RUNTIME_JAR'])
                preview_index = entries.index(os.environ['PREVIEW_JAR'])
                config_entries = [entry for entry in entries if os.path.isfile(os.path.join(entry, 'com/android/tools/test_config.properties'))]
                if len(config_entries) != 1:
                    print('expected exactly one generated robolectric config root, got %r in %r' % (config_entries, entries), file=sys.stderr)
                    sys.exit(1)
                config_root = config_entries[0]
                config_index = entries.index(config_root)
                if config_index != 0:
                    print('generated robolectric config root must be first classpath entry before plugin/runtime/preview entries; index %d in %r' % (config_index, entries), file=sys.stderr)
                    sys.exit(1)
                if not (config_index < runtime_index < preview_index):
                    print('expected generated config root before runtime and preview jars, got indexes config=%d runtime=%d preview=%d in %r' % (config_index, runtime_index, preview_index, entries), file=sys.stderr)
                    sys.exit(1)
                if len(entries) <= 3:
                    print('expected plugin classpath entries between generated config root and supplied runtime/preview classpath, got %r' % (entries,), file=sys.stderr)
                    sys.exit(1)
                if not config_root.startswith(os.path.dirname(os.environ['PREVIEW_JAR'])):
                    print('config root should be under request scratch/output area: ' + config_root, file=sys.stderr)
                    sys.exit(1)
                with open(os.path.join(config_root, 'com/android/tools/test_config.properties')) as fh:
                    properties = fh.read()
                if 'android_merged_assets=' + os.environ['ASSETS_DIR'] not in properties:
                    print('missing android_merged_assets in generated config: ' + properties, file=sys.stderr)
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
                previewClasspath = listOf(runtimeJar, previewJar),
                androidAssetsDir = assetsDir,
            )

        assertEquals(RenderProcessResult.Success, result)
    }

    @Test
    fun `packages merged assets into synthetic apk and passes it to isolated harness`() {
        val assetsDir = tempDir.resolve("merged-assets").also { it.mkdirs() }
        assetsDir.resolve("fonts").mkdirs()
        assetsDir.resolve("fonts/sample.otf").writeText("OTTO")

        val result =
            runWithFakeJava(
                """
                #!/bin/sh
                ASSET_APK="${'$'}{23}" python3 - <<'PY'
                import os, sys, zipfile
                asset_apk = os.environ['ASSET_APK']
                if not asset_apk.endswith('agentpreview-assets.apk') or not os.path.isfile(asset_apk):
                    print('missing synthetic asset apk arg: ' + asset_apk, file=sys.stderr)
                    sys.exit(1)
                with zipfile.ZipFile(asset_apk) as zf:
                    if zf.namelist() != ['assets/fonts/sample.otf']:
                        print('unexpected entries: %r' % (zf.namelist(),), file=sys.stderr)
                        sys.exit(1)
                    if zf.read('assets/fonts/sample.otf') != b'OTTO':
                        print('unexpected asset payload', file=sys.stderr)
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
                androidAssetsDir = assetsDir,
            )

        assertEquals(RenderProcessResult.Success, result)
    }

    @Test
    fun `does not add robolectric config root to classpath when assets are absent`() {
        val previewJar = tempDir.resolve("preview.jar").also { writeTextZip(it, "preview.txt" to "preview") }

        val result =
            runWithFakeJava(
                """
                #!/bin/sh
                CLASSPATH="${'$'}2" python3 - <<'PY'
                import os, sys
                entries = os.environ['CLASSPATH'].split(os.pathsep)
                config_entries = [entry for entry in entries if os.path.isfile(os.path.join(entry, 'com/android/tools/test_config.properties'))]
                if config_entries:
                    print('unexpected robolectric config roots without assets: %r' % (config_entries,), file=sys.stderr)
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
                previewClasspath = listOf(previewJar),
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

        val output = materializeSyntheticRJar(aar) ?: error("Expected synthetic R jar")

        assertEquals(52, classMajorVersion(output, "androidx/example/lib/R${'$'}id.class"))
    }

    @Test
    fun `generated aar R jar entries are written in deterministic sorted order`() {
        val aar = aarWithRSymbols("androidx.example.lib", "int style AppTheme 0x0\nint id button 0x0\n")

        val output = materializeSyntheticRJar(aar) ?: error("Expected synthetic R jar")

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

        val output = materializeSyntheticRJar(aar)

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
                androidAssetsDir = tempDir.resolve("assets"),
                androidAssetApk = tempDir.resolve("assets.apk"),
                fontProbe = true,
            )

        assertEquals(command, RenderHarnessCommand.fromArgs(command.toArgs().toTypedArray()))
        assertEquals(tempDir.resolve("assets"), RenderHarnessCommand.fromArgs(command.toArgs().toTypedArray()).androidAssetsDir)
        assertEquals(tempDir.resolve("assets.apk"), RenderHarnessCommand.fromArgs(command.toArgs().toTypedArray()).androidAssetApk)
    }

    @Test
    fun `render process captures bounded output`() {
        assumeTrue(File("/bin/sh").canExecute(), "/bin/sh is required for bounded output test")
        val script =
            tempDir.resolve("large-output.sh").also { file ->
                file.writeText(
                    """
                    #!/bin/sh
                    i=0
                    while [ "${'$'}i" -lt 200 ]; do
                      printf 'xxxxxxxxxx'
                      i=${'$'}((i + 1))
                    done
                    """.trimIndent(),
                )
                file.setExecutable(true)
            }

        val execution =
            RenderProcessService(
                timeout = Duration.ofSeconds(5),
                maxOutputBytes = 128,
            ).run(listOf(script.absolutePath))

        assertFalse(execution.timedOut)
        assertEquals(0, execution.exitCode)
        assertTrue(execution.output.contains("truncated"), execution.output)
        assertTrue(execution.output.length < 1000, execution.output)
    }

    @Test
    fun `render process reports timeout`() {
        val result =
            runWithFakeJava(
                """
                #!/bin/sh
                sleep 5
                """.trimIndent(),
                timeoutMillis = 200,
                maxOutputBytes = 128,
            )

        assertTrue(result is RenderProcessResult.Failure)
        val failure = result as RenderProcessResult.Failure
        assertTrue(failure.message.contains("after timeout"), failure.message)
        assertTrue(failure.message.length < 1000, failure.message)
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
                    sleep 30 &
                    echo "$!" > "${descendantPidFile.absolutePath}"
                    wait
                    """.trimIndent(),
                )
                file.setExecutable(true)
            }

        val execution = RenderProcessService(timeout = Duration.ofSeconds(1)).run(listOf(script.absolutePath))

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
        val isolatedBaseDir = tempDir.resolve("isolated-sdk-lookup").also { it.mkdirs() }

        val resolution = AndroidRendererEnvironment(env = emptyMap(), baseDir = isolatedBaseDir).androidJar(35)

        assertEquals(emptyList<File>(), resolution.files)
        assertTrue(resolution.diagnostic.orEmpty().contains("ANDROID_HOME and ANDROID_SDK_ROOT are not set"))
    }

    @Test
    fun `default render process runner resolves android jar from supplied sdk lookup base dir`() {
        val projectDir = tempDir.resolve("gradle-root")
        val sdkRoot = tempDir.resolve("sdk-from-local-properties")
        val androidJar = sdkRoot.resolve("platforms/android-35/android.jar")
        androidJar.parentFile.mkdirs()
        androidJar.writeText("")
        projectDir.mkdirs()
        projectDir.resolve("local.properties").writeText("sdk.dir=${sdkRoot.absolutePath}\n")

        val result =
            runWithFakeJava(
                """
                #!/bin/sh
                case "$2" in
                  *"${androidJar.absolutePath}"*) ;;
                  *) echo "classpath did not contain supplied-base android.jar: $2"; exit 42 ;;
                esac
                cat > "${'$'}{21}" <<'EOF'
                status=success
                EOF
                exit 0
                """.trimIndent(),
                environment = AndroidRendererEnvironment(env = emptyMap(), baseDir = projectDir),
            )

        assertEquals(RenderProcessResult.Success, result)
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
    fun `successful render suppresses font probe diagnostics by default`() {
        val stderr = ByteArrayOutputStream()
        val originalErr = System.err
        try {
            System.setErr(PrintStream(stderr))

            val result =
                runWithFakeJava(
                    """
                    #!/bin/sh
                    echo 'AgentPreview font probe: path=composeResources/dev/staticvar/font/foo.ttf header=00010000' >&2
                    cat > "${'$'}{21}" <<'EOF'
                    status=success
                    EOF
                    exit 0
                    """.trimIndent(),
                )

            assertEquals(RenderProcessResult.Success, result)
            assertFalse(stderr.toString().contains("AgentPreview font probe:"), stderr.toString())
        } finally {
            System.setErr(originalErr)
        }
    }

    @Test
    fun `successful render surfaces font probe diagnostics only when enabled`() {
        val stderr = ByteArrayOutputStream()
        val originalErr = System.err
        val originalFontProbe = System.getProperty("agentpreview.fontProbe")
        try {
            System.setProperty("agentpreview.fontProbe", "true")
            System.setErr(PrintStream(stderr))

            val result =
                runWithFakeJava(
                    """
                    #!/bin/sh
                    echo 'before unrelated noise' >&2
                    echo 'AgentPreview font probe: path=composeResources/dev/staticvar/font/foo.ttf header=00010000' >&2
                    echo 'after unrelated noise' >&2
                    cat > "${'$'}{21}" <<'EOF'
                    status=success
                    EOF
                    exit 0
                    """.trimIndent(),
                )

            assertEquals(RenderProcessResult.Success, result)
            val output = stderr.toString()
            assertTrue(output.contains("AgentPreview font probe: path=composeResources/dev/staticvar/font/foo.ttf"), output)
            assertFalse(output.contains("before unrelated noise"), output)
            assertFalse(output.contains("after unrelated noise"), output)
        } finally {
            if (originalFontProbe == null) {
                System.clearProperty("agentpreview.fontProbe")
            } else {
                System.setProperty("agentpreview.fontProbe", originalFontProbe)
            }
            System.setErr(originalErr)
        }
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
        androidAssetsDir: File? = null,
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
                        androidAssetsDir = androidAssetsDir,
                        fontProbe = System.getProperty("agentpreview.fontProbe") == "true",
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
        androidAssetsDir: File? = null,
        fontProbe: Boolean = false,
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
            androidAssetsDir = androidAssetsDir,
            fontProbe = fontProbe,
        )

    private fun isProcessAlive(pid: Long): Boolean =
        ProcessHandle
            .of(pid)
            .map(ProcessHandle::isAlive)
            .orElse(false)

    private fun materializeSyntheticRJar(aar: File): File? {
        val materialized = AarClasspathMaterializer().materialize(listOf(aar))
        return materialized.firstOrNull { it.name.endsWith("-r.jar") }
    }

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
