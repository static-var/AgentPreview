package dev.staticvar.agentpreview.semantics

import dev.staticvar.agentpreview.model.SnapshotNode

class EmptySemanticsExtractor : SemanticsExtractor {
    override fun extract(rawSemantics: Any?): List<SnapshotNode> {
        return emptyList()
    }
}
