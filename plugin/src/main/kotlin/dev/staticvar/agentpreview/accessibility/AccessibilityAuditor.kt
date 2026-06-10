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
        val findings = mutableListOf<AccessibilityFinding>()
        val warnings = mutableListOf<String>()
        var auditedBundleCount = 0
        var skippedBundleCount = 0

        bundles.forEach { bundle ->
            val snapshotFile = bundle.snapshotFile
            if (!snapshotFile.isFile || !snapshotFile.canRead()) {
                skippedBundleCount++
                warnings +=
                    "Skipping accessibility audit for ${bundle.previewId}/${bundle.viewportLabel}: unreadable snapshot ${snapshotFile.path}."
                return@forEach
            }

            val snapshot =
                runCatching {
                    json.decodeFromString<PreviewSnapshot>(snapshotFile.readText())
                }.getOrElse { throwable ->
                    skippedBundleCount++
                    warnings +=
                        "Skipping accessibility audit for ${bundle.previewId}/${bundle.viewportLabel}: malformed snapshot ${snapshotFile.path} (${throwable.message})."
                    return@forEach
                }

            val renderMode = snapshot.render?.mode ?: bundle.renderMode
            if (renderMode != "robolectric") {
                skippedBundleCount++
                warnings +=
                    "Skipping accessibility audit for ${bundle.previewId}/${bundle.viewportLabel}: render.mode=${renderMode ?: "null"} is not supported."
                return@forEach
            }

            auditedBundleCount++
            findings += AccessibilityRules.evaluate(snapshot, bundle.viewportLabel)
        }

        return AccessibilityAuditReport(
            auditedBundleCount = auditedBundleCount,
            skippedBundleCount = skippedBundleCount,
            findings = findings,
            warnings = warnings,
        )
    }
}
