/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

enum class RenderMode(
    val logLabel: String,
) {
    Fake("fake"),
    Robolectric("robolectric"),
    DiagnosticFallback("diagnostic-fallback"),
}
