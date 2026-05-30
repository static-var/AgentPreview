/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.android

internal interface AndroidPreviewWiringStrategy {
    fun canWire(): Boolean

    fun wire()
}
