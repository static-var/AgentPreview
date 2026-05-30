/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AgentPreviewTaskOptionsTest {
    @Test
    fun `CLI scalar overrides extension default`() {
        assertEquals(7, AgentPreviewTaskOptions.maxPreviewParameterValues(defaultValue = 50, cliValue = "7"))
        assertEquals(3, AgentPreviewTaskOptions.maxParallelRenders(defaultValue = 1, cliValue = "3"))
        assertEquals(12, AgentPreviewTaskOptions.maxCaptures(defaultValue = 4, cliValue = "12"))
        assertEquals(true, AgentPreviewTaskOptions.dryRun(defaultValue = false, cliValue = "true"))
        assertEquals(true, AgentPreviewTaskOptions.continueOnError(defaultValue = false, cliValue = "true"))
        assertEquals(false, AgentPreviewTaskOptions.cropToContent(defaultValue = true, cliValue = "false"))
        assertEquals(12, AgentPreviewTaskOptions.cropPaddingDp(defaultValue = 20, cliValue = "12"))
    }

    @Test
    fun `extension scalar default is used when CLI override is absent`() {
        assertEquals(50, AgentPreviewTaskOptions.maxPreviewParameterValues(defaultValue = 50, cliValue = null))
        assertEquals(1, AgentPreviewTaskOptions.maxParallelRenders(defaultValue = 1, cliValue = null))
        assertEquals(null, AgentPreviewTaskOptions.maxCaptures(defaultValue = null, cliValue = null))
        assertEquals(false, AgentPreviewTaskOptions.dryRun(defaultValue = false, cliValue = null))
        assertEquals(false, AgentPreviewTaskOptions.continueOnError(defaultValue = false, cliValue = null))
        assertEquals(true, AgentPreviewTaskOptions.cropToContent(defaultValue = true, cliValue = null))
        assertEquals(20, AgentPreviewTaskOptions.cropPaddingDp(defaultValue = 20, cliValue = null))
    }

    @Test
    fun `invalid scalar errors preserve task messages`() {
        assertEquals(
            "agentPreview.maxPreviewParameterValues must be a positive integer. " +
                "Configure agentPreview { maxPreviewParameterValues.set(n) } or pass -PagentPreview.maxPreviewParameterValues=n.",
            assertThrows(IllegalArgumentException::class.java) {
                AgentPreviewTaskOptions.maxPreviewParameterValues(defaultValue = 50, cliValue = "0")
            }.message,
        )
        assertEquals(
            "agentPreview.dryRun must be true or false. Pass -PagentPreview.dryRun=true|false.",
            assertThrows(IllegalArgumentException::class.java) {
                AgentPreviewTaskOptions.dryRun(defaultValue = false, cliValue = "yes")
            }.message,
        )
        assertEquals(
            "agentPreview.cropToContent must be true or false. Pass -PagentPreview.cropToContent=true|false.",
            assertThrows(IllegalArgumentException::class.java) {
                AgentPreviewTaskOptions.cropToContent(defaultValue = true, cliValue = "yes")
            }.message,
        )
        assertEquals(
            "agentPreview.cropPaddingDp must be a non-negative integer. " +
                "Configure agentPreview { android { screenshot { cropPaddingDp.set(n) } } } or pass -PagentPreview.cropPaddingDp=n.",
            assertThrows(IllegalArgumentException::class.java) {
                AgentPreviewTaskOptions.cropPaddingDp(defaultValue = 20, cliValue = "-1")
            }.message,
        )
    }
}
