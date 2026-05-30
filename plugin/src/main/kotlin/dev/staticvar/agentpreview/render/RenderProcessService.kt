/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.time.Duration
import java.util.concurrent.TimeUnit

class RenderProcessService(
    private val timeout: Duration = defaultTimeout(),
    private val maxOutputBytes: Int = defaultMaxOutputBytes(),
) {
    fun run(command: List<String>): RenderProcessExecution {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = BoundedOutput(maxOutputBytes)
        val reader =
            Thread {
                process.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.append(buffer, read)
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
        val completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!completed) {
            destroyProcessTree(process)
            reader.join(1000)
            return RenderProcessExecution(exitCode = null, timedOut = true, output = output.text())
        }
        reader.join(1000)
        return RenderProcessExecution(exitCode = process.exitValue(), timedOut = false, output = output.text())
    }

    private fun destroyProcessTree(process: Process) {
        runCatching {
            process
                .toHandle()
                .descendants()
                .forEach { handle -> runCatching { handle.destroyForcibly() } }
        }
        process.destroyForcibly()
    }

    private class BoundedOutput(
        private val maxBytes: Int,
    ) {
        private val bytes = ArrayDeque<Byte>()
        private var truncatedBytes = 0

        @Synchronized
        fun append(
            buffer: ByteArray,
            length: Int,
        ) {
            repeat(length) { index ->
                if (maxBytes == 0) {
                    truncatedBytes++
                } else {
                    if (bytes.size == maxBytes) {
                        bytes.removeFirst()
                        truncatedBytes++
                    }
                    bytes.addLast(buffer[index])
                }
            }
        }

        @Synchronized
        fun text(): String {
            val body = ByteArray(bytes.size) { index -> bytes[index] }.toString(Charsets.UTF_8)
            return if (truncatedBytes > 0) "[agentpreview: truncated $truncatedBytes bytes of render output]\n$body" else body
        }
    }

    companion object {
        private const val DEFAULT_OUTPUT_BYTES = 64 * 1024
        private const val DEFAULT_TIMEOUT_SECONDS = 60L

        fun defaultTimeout(): Duration =
            Duration.ofMillis(System.getProperty("agentpreview.render.timeoutMillis")?.toLongOrNull() ?: DEFAULT_TIMEOUT_SECONDS * 1000)

        fun defaultMaxOutputBytes(): Int =
            System.getProperty("agentpreview.render.maxOutputBytes")?.toIntOrNull()?.coerceAtLeast(0) ?: DEFAULT_OUTPUT_BYTES
    }
}

data class RenderProcessExecution(
    val exitCode: Int?,
    val timedOut: Boolean,
    val output: String,
)
