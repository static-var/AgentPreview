/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery

import java.io.File

/**
 * Inputs for bytecode preview scanning.
 *
 * `classesDirs` are the only locations that produce previews. `runtimeClasspath` is parsed only to resolve
 * composed preview annotations used as meta-annotations, so dependency previews are not reported as project previews.
 */
data class PreviewScanInput(
    val projectPath: String,
    val sourceSetName: String,
    val classesDirs: List<File>,
    val runtimeClasspath: List<File>,
)
