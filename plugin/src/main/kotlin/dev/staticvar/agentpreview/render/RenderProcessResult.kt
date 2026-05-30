/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

/** Result returned to the Gradle/plugin JVM after the isolated renderer child JVM exits. */
sealed interface RenderProcessResult {
    data object Success : RenderProcessResult

    data class Failure(
        val kind: RenderProcessFailureKind,
        val message: String,
    ) : RenderProcessResult
}
