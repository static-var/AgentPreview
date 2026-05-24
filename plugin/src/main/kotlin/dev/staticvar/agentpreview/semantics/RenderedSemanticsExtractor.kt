/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.semantics

import dev.staticvar.agentpreview.model.SnapshotNode

class RenderedSemanticsExtractor : SemanticsExtractor {
    override fun extract(rawSemantics: Any?): List<SnapshotNode> =
        when (rawSemantics) {
            is List<*> -> rawSemantics.filterIsInstance<SnapshotNode>()
            else -> emptyList()
        }
}
