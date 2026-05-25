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
import java.lang.reflect.Method
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
                cat > "${'$'}{19}" <<'EOF'
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

        val output = generateAarRJarMethod().invoke(DefaultRenderProcessRunner(), aar) as File

        assertEquals(52, classMajorVersion(output, "androidx/example/lib/R${'$'}id.class"))
    }

    @Test
    fun `stub R jar entries are written in deterministic sorted order`() {
        val output = tempDir.resolve("r.jar")
        val symbols =
            linkedMapOf(
                "style" to listOf(resourceSymbol("int", "style", "AppTheme")),
                "id" to listOf(resourceSymbol("int", "id", "button")),
            )

        writeStubRJarMethod().invoke(DefaultRenderProcessRunner(), output, "androidx.example.lib", symbols)

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
        previewClasspath: List<File> = emptyList(),
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
                previewClasspath = previewClasspath,
            )
        } finally {
            System.setProperty("java.home", originalJavaHome)
        }
    }

    private fun generateAarRJarMethod(): Method =
        DefaultRenderProcessRunner::class.java
            .getDeclaredMethod("generateAarRJar", File::class.java)
            .apply { isAccessible = true }

    private fun writeStubRJarMethod(): Method =
        DefaultRenderProcessRunner::class.java
            .getDeclaredMethod(
                "writeStubRJar",
                File::class.java,
                String::class.java,
                Map::class.java,
            ).apply { isAccessible = true }

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

    private fun resourceSymbol(
        valueType: String,
        type: String,
        name: String,
    ): Any {
        val resourceSymbolClass =
            DefaultRenderProcessRunner::class.java.declaredClasses.single { nestedClass -> nestedClass.simpleName == "ResourceSymbol" }
        return resourceSymbolClass
            .declaredConstructors
            .single()
            .apply { isAccessible = true }
            .newInstance(valueType, type, name)
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
