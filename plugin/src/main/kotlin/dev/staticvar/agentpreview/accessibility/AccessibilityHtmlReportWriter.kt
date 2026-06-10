/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.accessibility

import java.io.File
import java.nio.file.Path

internal object AccessibilityHtmlReportWriter {
    private const val REPORT_FILE_NAME = "accessibility-report.html"

    fun write(
        report: AccessibilityAuditReport,
        bundles: List<AuditedSnapshotBundle>,
        outputDir: File,
    ): File {
        outputDir.mkdirs()
        val reportFile = outputDir.resolve(REPORT_FILE_NAME)
        reportFile.writeText(render(report, bundles, outputDir))
        return reportFile
    }

    private fun render(
        report: AccessibilityAuditReport,
        bundles: List<AuditedSnapshotBundle>,
        outputDir: File,
    ): String {
        val errorCount = report.findings.count { it.severity == AccessibilitySeverity.ERROR }
        val warningCount = report.findings.count { it.severity == AccessibilitySeverity.WARNING } + report.warnings.size
        val findingsByBundle = report.findings.groupBy { it.previewId to it.viewportLabel }

        return buildString {
            appendLine("<!doctype html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("  <meta charset=\"utf-8\">")
            appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            appendLine("  <title>AgentPreview Accessibility Report</title>")
            appendLine(
                "  <style>body{font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif;margin:0;color:#202124;background:#f8f9fa}.page{max-width:1120px;margin:0 auto;padding:32px}h1,h2,h3{margin:0 0 12px}.summary{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:12px;margin:24px 0}.metric,.section,.issue{background:white;border:1px solid #dadce0;border-radius:8px;padding:16px}.metric strong{display:block;font-size:28px}.bundle{margin:20px 0}.screenshot{max-width:100%;border:1px solid #dadce0;border-radius:6px}.issue{margin:12px 0}.meta{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:8px}.label{font-weight:600}.warnings{background:#fff8e1;border-color:#fbbc04}.empty{color:#5f6368}</style>",
            )
            appendLine("</head>")
            appendLine("<body>")
            appendLine("  <main class=\"page\">")
            appendLine("    <h1>AgentPreview Accessibility Report</h1>")
            appendSummaryMetric("Audited", report.auditedBundleCount)
            appendSummaryMetric("Skipped", report.skippedBundleCount)
            appendSummaryMetric("Findings", report.findings.size)
            appendSummaryMetric("Errors", errorCount)
            appendSummaryMetric("Warnings", warningCount)
            appendWarnings(report.warnings)
            appendLine("    <section class=\"section\">")
            appendLine("      <h2>Preview Results</h2>")
            if (report.findings.isEmpty()) {
                appendLine("      <p class=\"empty\">No accessibility findings</p>")
            }
            bundles.forEach { bundle ->
                appendBundleSection(bundle, findingsByBundle[bundle.previewId to bundle.viewportLabel].orEmpty(), outputDir.toPath())
            }
            appendLine("    </section>")
            appendNotChecked(report.notChecked)
            appendLine("  </main>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    private fun StringBuilder.appendSummaryMetric(
        label: String,
        value: Int,
    ) {
        if (!contains("<section class=\"summary\">")) {
            appendLine("    <section class=\"summary\">")
        }
        appendLine("      <div class=\"metric\"><span>${label.escapeHtml()}</span><strong>$value</strong></div>")
        if (label == "Warnings") {
            appendLine("    </section>")
        }
    }

    private fun StringBuilder.appendWarnings(warnings: List<String>) {
        if (warnings.isEmpty()) return

        appendLine("    <section class=\"section warnings\">")
        appendLine("      <h2>Warnings</h2>")
        appendLine("      <ul>")
        warnings.forEach { warning ->
            appendLine("        <li>${warning.escapeHtml()}</li>")
        }
        appendLine("      </ul>")
        appendLine("    </section>")
    }

    private fun StringBuilder.appendBundleSection(
        bundle: AuditedSnapshotBundle,
        findings: List<AccessibilityFinding>,
        outputPath: Path,
    ) {
        appendLine("      <section class=\"bundle\">")
        appendLine("        <h3>${bundle.previewId.escapeHtml()} <small>${bundle.viewportLabel.escapeHtml()}</small></h3>")
        bundle.reportScreenshotFile?.let { screenshot ->
            appendLine(
                "        <img class=\"screenshot\" src=\"${relativePath(
                    outputPath,
                    screenshot,
                ).escapeHtml()}\" alt=\"Screenshot for ${bundle.previewId.escapeHtml()} ${bundle.viewportLabel.escapeHtml()}\">",
            )
        }
        if (findings.isEmpty()) {
            appendLine("        <p class=\"empty\">No accessibility findings</p>")
        } else {
            findings.forEach { finding -> appendFinding(finding) }
        }
        appendLine("      </section>")
    }

    private fun StringBuilder.appendFinding(finding: AccessibilityFinding) {
        appendLine("        <article class=\"issue\">")
        appendLine("          <h4>${finding.severity.name.escapeHtml()} - ${finding.category.name.escapeHtml()}</h4>")
        appendLine("          <p>${finding.message.escapeHtml()}</p>")
        appendLine("          <p><span class=\"label\">Recommendation:</span> ${finding.recommendation.escapeHtml()}</p>")
        appendLine("          <p><span class=\"label\">Guideline:</span> ${finding.guideline.escapeHtml()}</p>")
        appendLine("          <div class=\"meta\">")
        appendMeta("Node id", finding.node.id)
        appendMeta("Role", finding.node.role ?: "None")
        appendMeta("Name", finding.node.accessibleName ?: "None")
        appendMeta(
            "Actions",
            finding.node.actions
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ") ?: "None",
        )
        appendMeta("Bounds", finding.node.bounds.format())
        appendLine("          </div>")
        appendLine("        </article>")
    }

    private fun StringBuilder.appendMeta(
        label: String,
        value: String,
    ) {
        appendLine("            <div><span class=\"label\">${label.escapeHtml()}:</span> ${value.escapeHtml()}</div>")
    }

    private fun StringBuilder.appendNotChecked(notes: List<String>) {
        if (notes.isEmpty()) return

        appendLine("    <section class=\"section\">")
        appendLine("      <h2>Not checked</h2>")
        appendLine("      <ul>")
        notes.forEach { note ->
            appendLine("        <li>${note.escapeHtml()}</li>")
        }
        appendLine("      </ul>")
        appendLine("    </section>")
    }

    private fun relativePath(
        outputPath: Path,
        screenshot: File,
    ): String =
        runCatching {
            outputPath.relativize(screenshot.toPath()).toString()
        }.getOrElse {
            screenshot.name
        }.replace(File.separatorChar, '/')

    private fun dev.staticvar.agentpreview.model.Bounds.format(): String = "x=$x, y=$y, width=$width, height=$height"

    private fun String.escapeHtml(): String =
        buildString(length) {
            this@escapeHtml.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(char)
                }
            }
        }
}
