/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.accessibility

import dev.staticvar.agentpreview.model.PreviewSnapshot
import kotlinx.serialization.json.Json

internal object AccessibilityAuditor {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    fun audit(bundles: List<AuditedSnapshotBundle>): AccessibilityAuditReport {
        val bundleResults = mutableListOf<AccessibilityBundleResult>()
        var auditedBundleCount = 0
        var skippedBundleCount = 0

        bundles.forEach { bundle ->
            val snapshotFile = bundle.snapshotFile
            if (!snapshotFile.isFile || !snapshotFile.canRead()) {
                skippedBundleCount++
                bundleResults +=
                    bundle.skipped("unreadable snapshot ${snapshotFile.path}")
                return@forEach
            }

            val snapshot =
                runCatching {
                    json.decodeFromString<PreviewSnapshot>(snapshotFile.readText())
                }.getOrElse { throwable ->
                    skippedBundleCount++
                    bundleResults +=
                        bundle.skipped("malformed snapshot ${snapshotFile.path} (${throwable.message})")
                    return@forEach
                }

            val renderMode = snapshot.render?.mode ?: bundle.renderMode
            if (renderMode != "robolectric") {
                skippedBundleCount++
                bundleResults +=
                    bundle.skipped("render.mode=${renderMode ?: "null"} is not supported")
                return@forEach
            }

            auditedBundleCount++
            val findings = AccessibilityRules.evaluate(snapshot, bundle.viewportLabel)
            bundleResults +=
                if (snapshot.preview.id == bundle.previewId) {
                    AccessibilityBundleResult(
                        bundle = bundle,
                        status = AccessibilityBundleStatus.CHECKED,
                        findings = findings,
                    )
                } else {
                    AccessibilityBundleResult(
                        bundle = bundle,
                        status = AccessibilityBundleStatus.MISMATCHED_SNAPSHOT,
                        findings = findings,
                        warnings =
                            listOf(
                                "Snapshot preview id ${snapshot.preview.id} does not match bundle preview id " +
                                    "${bundle.previewId} for viewport ${bundle.viewportLabel}.",
                            ),
                    )
                }
        }

        val findings = bundleResults.flatMap { it.findings }
        val warnings = bundleResults.flatMap { it.warnings }
        return AccessibilityAuditReport(
            auditedBundleCount = auditedBundleCount,
            skippedBundleCount = skippedBundleCount,
            findings = findings,
            warnings = warnings,
            bundleResults = bundleResults,
        )
    }

    private fun AuditedSnapshotBundle.skipped(reason: String): AccessibilityBundleResult =
        AccessibilityBundleResult(
            bundle = this,
            status = AccessibilityBundleStatus.SKIPPED,
            warnings = listOf("Skipping accessibility audit for $previewId/$viewportLabel: $reason."),
        )
}
