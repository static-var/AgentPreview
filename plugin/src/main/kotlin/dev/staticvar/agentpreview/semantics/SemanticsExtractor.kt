/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.semantics

import dev.staticvar.agentpreview.model.SnapshotNode

interface SemanticsExtractor {
    fun extract(rawSemantics: Any?): List<SnapshotNode>
}
