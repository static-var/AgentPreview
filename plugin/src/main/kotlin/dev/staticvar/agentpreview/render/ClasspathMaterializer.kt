/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.io.File

interface ClasspathMaterializer {
    fun materialize(classpath: List<File>): List<File>
}
