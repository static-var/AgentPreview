/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewDescriptor
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

internal class JsonIndexPreviewDiscovery(
    private val indexFile: File,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun discover(): List<PreviewDescriptor> {
        if (!indexFile.isFile) return emptyList()
        return try {
            json.decodeFromString(
                ListSerializer(PreviewDescriptor.serializer()),
                indexFile.readText(),
            )
        } catch (exception: SerializationException) {
            throw IllegalArgumentException("Failed to parse preview index at ${indexFile.absolutePath}: ${exception.message}", exception)
        }
    }
}
