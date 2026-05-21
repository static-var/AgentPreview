/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewDescriptor
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class JsonIndexPreviewDiscovery(
    private val indexFile: File,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun discover(): List<PreviewDescriptor> {
        if (!indexFile.isFile) return emptyList()
        return json.decodeFromString(
            ListSerializer(PreviewDescriptor.serializer()),
            indexFile.readText(),
        )
    }
}
