/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.io.File
import java.util.Properties

internal object RenderHarnessResultFile {
    private const val STATUS = "status"
    private const val FAILURE_KIND = "failureKind"
    private const val STATUS_SUCCESS = "success"
    private const val STATUS_FAILURE = "failure"

    fun writeSuccess(file: File) {
        write(
            file,
            Properties().apply {
                setProperty(STATUS, STATUS_SUCCESS)
            },
        )
    }

    fun writeFailure(
        file: File,
        kind: RenderProcessFailureKind,
    ) {
        write(
            file,
            Properties().apply {
                setProperty(STATUS, STATUS_FAILURE)
                setProperty(FAILURE_KIND, kind.name)
            },
        )
    }

    fun readFailureKind(file: File): RenderProcessFailureKind? {
        if (!file.isFile) return null
        val properties = Properties()
        try {
            file.inputStream().use(properties::load)
        } catch (_: RuntimeException) {
            return null
        } catch (_: java.io.IOException) {
            return null
        }
        if (properties.getProperty(STATUS) != STATUS_FAILURE) return null
        return properties
            .getProperty(FAILURE_KIND)
            ?.let { failureKind -> RenderProcessFailureKind.entries.firstOrNull { it.name == failureKind } }
    }

    private fun write(
        file: File,
        properties: Properties,
    ) {
        file.parentFile?.mkdirs()
        file.outputStream().use { output -> properties.store(output, "agentpreview render harness result") }
    }
}
