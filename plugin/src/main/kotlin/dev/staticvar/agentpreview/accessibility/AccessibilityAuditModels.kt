/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.accessibility

import dev.staticvar.agentpreview.model.Bounds
import java.io.File

internal data class AccessibilityAuditReport(
    val auditedBundleCount: Int,
    val skippedBundleCount: Int,
    val findings: List<AccessibilityFinding>,
    val warnings: List<String>,
    val bundleResults: List<AccessibilityBundleResult> = emptyList(),
    val notChecked: List<String> = defaultNotCheckedNotes,
)

internal data class AccessibilityBundleResult(
    val bundle: AuditedSnapshotBundle,
    val status: AccessibilityBundleStatus,
    val findings: List<AccessibilityFinding> = emptyList(),
    val warnings: List<String> = emptyList(),
)

internal enum class AccessibilityBundleStatus {
    CHECKED,
    SKIPPED,
    MISMATCHED_SNAPSHOT,
}

internal data class AuditedSnapshotBundle(
    val previewId: String,
    val viewportLabel: String,
    val snapshotFile: File,
    val screenshotFile: File?,
    val reportScreenshotFile: File?,
    val renderMode: String?,
)

internal data class AccessibilityFinding(
    val id: String,
    val severity: AccessibilitySeverity,
    val category: AccessibilityCategory,
    val message: String,
    val recommendation: String,
    val guideline: String,
    val previewId: String,
    val viewportLabel: String,
    val node: AccessibilityNodeSummary,
)

internal enum class AccessibilitySeverity {
    ERROR,
    WARNING,
    INFO,
}

internal enum class AccessibilityCategory {
    MISSING_ACCESSIBLE_NAME,
    PLACEHOLDER_LABEL,
    DUPLICATE_ACCESSIBLE_NAME,
    MISSING_ROLE,
    SMALL_TOUCH_TARGET,
    TRAVERSAL_ORDER_SUSPICION,
}

internal data class AccessibilityNodeSummary(
    val id: String,
    val role: String?,
    val accessibleName: String?,
    val actions: List<String>,
    val bounds: Bounds,
    val tag: String?,
    val source: String?,
)

internal val defaultNotCheckedNotes =
    listOf(
        "TalkBack and Switch Access behavior is not checked by static snapshot rules.",
        "Color contrast is not checked until screenshot pixel analysis is added.",
        "large text, orientation changes, and reflow behavior are not checked by this first audit layer.",
        "end-to-end accessibility behavior and Google Play pre-launch accessibility coverage still require device or Play validation.",
    )
