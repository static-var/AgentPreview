package dev.staticvar.agentpreview.discovery

import dev.staticvar.agentpreview.model.PreviewDescriptor

interface PreviewDiscovery {
    fun discover(): List<PreviewDescriptor>
}
