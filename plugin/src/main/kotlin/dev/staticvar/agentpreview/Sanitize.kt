/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview

internal fun String.sanitize(): String =
    trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .ifEmpty { "preview" }
