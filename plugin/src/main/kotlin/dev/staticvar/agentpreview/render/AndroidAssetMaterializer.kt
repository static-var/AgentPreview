/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.io.File

class AndroidAssetMaterializer {
    fun materialize(
        inputRoots: Set<File>,
        outputRoot: File,
    ): File? {
        val assets = collectAssets(inputRoots)
        if (outputRoot.exists()) {
            outputRoot.deleteRecursively()
        }
        if (assets.isEmpty()) {
            return null
        }

        assets.forEach { asset ->
            val destination = outputRoot.resolve(asset.relativePath)
            destination.parentFile.mkdirs()
            asset.source.copyTo(destination, overwrite = true)
        }
        return outputRoot
    }

    private fun collectAssets(inputRoots: Set<File>): List<Asset> {
        val assetsByPath = linkedMapOf<String, Asset>()
        inputRoots
            .asSequence()
            .filter { it.isDirectory }
            .sortedBy { it.absoluteFile.invariantSeparatorsPath }
            .forEach { root ->
                root
                    .walkTopDown()
                    .filter { it.isFile }
                    .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
                    .forEach { source ->
                        val relativePath = source.relativeTo(root).invariantSeparatorsPath
                        val existing = assetsByPath[relativePath]
                        if (existing == null) {
                            assetsByPath[relativePath] = Asset(relativePath, source)
                        } else if (!existing.source.readBytes().contentEquals(source.readBytes())) {
                            throw IllegalStateException(
                                "Conflicting Android asset '$relativePath': " +
                                    "${existing.source.absolutePath} conflicts with ${source.absolutePath}",
                            )
                        }
                    }
            }
        return assetsByPath.values.toList()
    }

    private data class Asset(
        val relativePath: String,
        val source: File,
    )
}
