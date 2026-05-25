/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CaptureReportSerializationTest {
    @Test
    fun `capture report serializes run controls and failure details`() {
        val report =
            CaptureReport(
                discoveredPreviewCount = 3,
                expandedPreviewCount = 4,
                selectedPreviewCount = 2,
                plannedViewportCaptureCount = 5,
                capturedViewportCaptureCount = 1,
                failedViewportCaptureCount = 1,
                skippedByPreviewFilterCount = 1,
                skippedByViewportFilterCount = 2,
                dryRun = true,
                continueOnError = true,
                maxCaptures = 20,
                maxParallelRenders = 4,
                previewFilters = listOf("Login"),
                viewportFilters = listOf("phone"),
                failures =
                    listOf(
                        CaptureFailure(
                            previewId = ":app:main:BrokenPreview",
                            viewport = "android-phone",
                            message = "boom",
                        ),
                    ),
            )

        val json = Json { prettyPrint = true }.encodeToString(CaptureReport.serializer(), report)
        val decoded = Json.decodeFromString(CaptureReport.serializer(), json)

        assertEquals(report, decoded)
        assertTrue(json.contains("\"dryRun\": true"), json)
        assertTrue(json.contains("\"previewFilters\": ["), json)
        assertTrue(json.contains("\"failedViewportCaptureCount\": 1"), json)
        assertTrue(json.contains("\"maxParallelRenders\": 4"), json)
    }
}
