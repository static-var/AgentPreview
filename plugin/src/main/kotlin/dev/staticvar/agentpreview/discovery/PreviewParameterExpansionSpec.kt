/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewParameterDescriptor

internal data class PreviewParameterExpansionSpec(
    val parameter: PreviewParameterDescriptor,
    val resolvedCount: Int?,
    val defaultCap: Int,
    val requestedIndexes: Set<Int>,
) {
    fun indexesToExpand(): List<Int> {
        val effectiveCount =
            when {
                resolvedCount != null -> resolvedCount
                parameter.limit != null -> parameter.limit.coerceAtMost(defaultCap)
                requestedIndexes.isNotEmpty() -> defaultCap
                else -> 0
            }
        if (effectiveCount <= 0) return emptyList()

        if (requestedIndexes.isNotEmpty()) {
            return requestedIndexes.filter { index -> index in 0 until effectiveCount }.sorted()
        }
        return (0 until effectiveCount).toList()
    }
}
