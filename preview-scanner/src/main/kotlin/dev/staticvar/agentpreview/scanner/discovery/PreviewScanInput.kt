/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery

import java.io.File

data class PreviewScanInput(
    val projectPath: String,
    val sourceSetName: String,
    val classesDirs: List<File>,
    val runtimeClasspath: List<File>,
)
