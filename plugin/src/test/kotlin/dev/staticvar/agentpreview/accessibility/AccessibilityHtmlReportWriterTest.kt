/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.accessibility

import dev.staticvar.agentpreview.model.Bounds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AccessibilityHtmlReportWriterTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `writes escaped html with summary screenshot and findings`() {
        val assetsDir = tempDir.resolve("assets").apply { mkdirs() }
        val screenshot = assetsDir.resolve("LoginPreview-phone.png").apply { writeText("png") }
        val bundle =
            AuditedSnapshotBundle(
                previewId = "Login <Preview> & welcome",
                viewportLabel = "phone & tablet",
                snapshotFile = tempDir.resolve("snapshot.json"),
                screenshotFile = tempDir.resolve("source.png"),
                reportScreenshotFile = screenshot,
                renderMode = "robolectric",
            )
        val report =
            AccessibilityAuditReport(
                auditedBundleCount = 1,
                skippedBundleCount = 2,
                findings =
                    listOf(
                        AccessibilityFinding(
                            id = "missing_accessible_name:Login <Preview> & welcome:node <1>",
                            severity = AccessibilitySeverity.ERROR,
                            category = AccessibilityCategory.MISSING_ACCESSIBLE_NAME,
                            message = "Actionable node <button> has no accessible name & label.",
                            recommendation = "Add text or contentDescription <now>.",
                            guideline = "WCAG 4.1.2 Name, Role & Value",
                            previewId = "Login <Preview> & welcome",
                            viewportLabel = "phone & tablet",
                            node =
                                AccessibilityNodeSummary(
                                    id = "node <1>",
                                    role = "Button & link",
                                    accessibleName = "Save <draft> & close",
                                    actions = listOf("OnClick", "Custom <Action>"),
                                    bounds = Bounds(x = 1, y = 2, width = 44, height = 45),
                                    tag = "primary",
                                    source = "Login.kt:12",
                                ),
                        ),
                    ),
                warnings = listOf("Skipped render.mode=desktop <unsupported> & ignored"),
            )

        val htmlFile = AccessibilityHtmlReportWriter.write(report, listOf(bundle), tempDir)

        assertEquals(tempDir.resolve("accessibility-report.html"), htmlFile)
        val html = htmlFile.readText()
        assertContains(html, "AgentPreview Accessibility Report")
        assertContains(html, "Audited")
        assertContains(html, "1")
        assertContains(html, "Skipped")
        assertContains(html, "2")
        assertContains(html, "Findings")
        assertContains(html, "1")
        assertContains(html, "Errors")
        assertContains(html, "1")
        assertContains(html, "Warnings")
        assertContains(html, "1")
        assertContains(html, "Login &lt;Preview&gt; &amp; welcome")
        assertContains(html, "phone &amp; tablet")
        assertContains(html, "assets/LoginPreview-phone.png")
        assertContains(html, "ERROR")
        assertContains(html, "MISSING_ACCESSIBLE_NAME")
        assertContains(html, "Actionable node &lt;button&gt; has no accessible name &amp; label.")
        assertContains(html, "Add text or contentDescription &lt;now&gt;.")
        assertContains(html, "WCAG 4.1.2 Name, Role &amp; Value")
        assertContains(html, "node &lt;1&gt;")
        assertContains(html, "Button &amp; link")
        assertContains(html, "Save &lt;draft&gt; &amp; close")
        assertContains(html, "OnClick, Custom &lt;Action&gt;")
        assertContains(html, "x=1, y=2, width=44, height=45")
        assertContains(html, "Skipped render.mode=desktop &lt;unsupported&gt; &amp; ignored")
        assertFalse(html.contains("Login <Preview> & welcome"), html)
        assertFalse(html.contains("Actionable node <button>"), html)
    }

    @Test
    fun `writes no findings and not checked notes when no findings exist`() {
        val bundle =
            AuditedSnapshotBundle(
                previewId = "SettingsPreview",
                viewportLabel = "desktop",
                snapshotFile = tempDir.resolve("snapshot.json"),
                screenshotFile = null,
                reportScreenshotFile = null,
                renderMode = "robolectric",
            )
        val report =
            AccessibilityAuditReport(
                auditedBundleCount = 1,
                skippedBundleCount = 0,
                findings = emptyList(),
                warnings = emptyList(),
            )

        val html = AccessibilityHtmlReportWriter.write(report, listOf(bundle), tempDir).readText()

        assertContains(html, "SettingsPreview")
        assertContains(html, "desktop")
        assertContains(html, "No accessibility findings")
        assertContains(html, "Not checked")
        defaultNotCheckedNotes.forEach { note -> assertContains(html, note) }
        assertFalse(html.contains("<img"), html)
    }

    @Test
    fun `asset writer recreates asset directory and copies available screenshots with sanitized names`() {
        val sourceScreenshot = tempDir.resolve("source screenshot.png").apply { writeText("png") }
        val assetsDir = tempDir.resolve("assets").apply { mkdirs() }
        assetsDir.resolve("stale.png").writeText("stale")
        val available =
            AuditedSnapshotBundle(
                previewId = "Preview <Name>",
                viewportLabel = "phone/1",
                snapshotFile = tempDir.resolve("snapshot.json"),
                screenshotFile = sourceScreenshot,
                reportScreenshotFile = null,
                renderMode = "robolectric",
            )
        val missing =
            available.copy(
                previewId = "MissingPreview",
                screenshotFile = tempDir.resolve("missing.png"),
            )

        val result = AccessibilityReportAssetWriter.write(listOf(available, missing), assetsDir)

        val copiedScreenshot = assetsDir.resolve("Preview-Name-phone-1.png")
        assertTrue(copiedScreenshot.isFile, copiedScreenshot.path)
        assertEquals("png", copiedScreenshot.readText())
        assertFalse(assetsDir.resolve("stale.png").exists())
        assertEquals(copiedScreenshot, result.bundles[0].reportScreenshotFile)
        assertEquals(null, result.bundles[1].reportScreenshotFile)
        assertTrue(result.warnings.single().contains("MissingPreview"), result.warnings.toString())
    }

    @Test
    fun `auditor evaluates robolectric snapshots and skips malformed or unsupported render modes`() {
        val robolectricSnapshot = tempDir.resolve("robolectric.json")
        robolectricSnapshot.writeText(
            """
            {
              "schemaVersion": 1,
              "preview": {"id": "LoginPreview", "name": "Login"},
              "viewport": {"name": "phone", "width": 360, "height": 640, "density": 2.0},
              "render": {"mode": "robolectric"},
              "nodes": [
                {
                  "id": "missing-name",
                  "role": "Button",
                  "bounds": {"x": 0, "y": 0, "width": 96, "height": 96},
                  "actions": ["OnClick"]
                }
              ]
            }
            """.trimIndent(),
        )
        val unsupportedSnapshot = tempDir.resolve("unsupported.json")
        unsupportedSnapshot.writeText(
            """
            {
              "schemaVersion": 1,
              "preview": {"id": "DesktopPreview"},
              "viewport": {"name": "desktop", "width": 800, "height": 600, "density": 1.0},
              "render": {"mode": "desktop"},
              "nodes": []
            }
            """.trimIndent(),
        )
        val fallbackRenderSnapshot = tempDir.resolve("fallback-render.json")
        fallbackRenderSnapshot.writeText(
            """
            {
              "schemaVersion": 1,
              "preview": {"id": "FallbackPreview"},
              "viewport": {"name": "phone", "width": 360, "height": 640, "density": 1.0},
              "nodes": []
            }
            """.trimIndent(),
        )
        val malformedSnapshot = tempDir.resolve("malformed.json").apply { writeText("{") }

        val report =
            AccessibilityAuditor.audit(
                listOf(
                    bundle("LoginPreview", "phone", robolectricSnapshot, renderMode = null),
                    bundle("FallbackPreview", "phone", fallbackRenderSnapshot, renderMode = "robolectric"),
                    bundle("DesktopPreview", "desktop", unsupportedSnapshot, renderMode = "robolectric"),
                    bundle("BrokenPreview", "phone", malformedSnapshot, renderMode = "robolectric"),
                ),
            )

        assertEquals(2, report.auditedBundleCount)
        assertEquals(2, report.skippedBundleCount)
        assertEquals(1, report.findings.size)
        assertEquals(AccessibilityCategory.MISSING_ACCESSIBLE_NAME, report.findings.single().category)
        assertTrue(report.warnings.any { it.contains("render.mode=desktop") }, report.warnings.toString())
        assertTrue(report.warnings.any { it.contains("malformed snapshot") }, report.warnings.toString())
    }

    private fun bundle(
        previewId: String,
        viewportLabel: String,
        snapshotFile: File,
        renderMode: String?,
    ) = AuditedSnapshotBundle(
        previewId = previewId,
        viewportLabel = viewportLabel,
        snapshotFile = snapshotFile,
        screenshotFile = null,
        reportScreenshotFile = null,
        renderMode = renderMode,
    )

    private fun assertContains(
        html: String,
        expected: String,
    ) {
        assertTrue(html.contains(expected), html)
    }
}
