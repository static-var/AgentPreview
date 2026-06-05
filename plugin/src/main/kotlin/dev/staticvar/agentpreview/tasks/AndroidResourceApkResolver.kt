/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import java.io.File

internal object AndroidResourceApkResolver {
    fun resolve(
        directApk: File?,
        linkedResourceApkDirs: Collection<File>,
    ): File? =
        directApk?.takeIf { it.isFile }
            ?: linkedResourceApkDirs
                .asSequence()
                .flatMap { root -> root.resourceApkCandidates() }
                .sortedBy { file -> file.absolutePath }
                .firstOrNull()

    private fun File.resourceApkCandidates(): Sequence<File> =
        when {
            isFile && isResourceApkCandidate() -> {
                sequenceOf(this)
            }

            isDirectory -> {
                walkTopDown()
                    .filter { file -> file.isFile && file.isResourceApkCandidate() }
            }

            else -> {
                emptySequence()
            }
        }

    private fun File.isResourceApkCandidate(): Boolean = name.endsWith(".ap_") || name.endsWith(".apk")
}
