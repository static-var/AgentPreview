/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

object AgentPreviewTaskOptions {
    private const val CROP_TO_CONTENT_ERROR =
        "agentPreview.cropToContent must be true or false. Pass -PagentPreview.cropToContent=true|false."

    fun maxPreviewParameterValues(
        defaultValue: Int,
        cliValue: String?,
    ): Int {
        val value = cliValue?.toIntOrNull() ?: defaultValue
        require(cliValue == null || cliValue.toIntOrNull() != null) { maxPreviewParameterValuesError() }
        require(value > 0) { maxPreviewParameterValuesError() }
        return value
    }

    fun maxCaptures(
        defaultValue: Int?,
        cliValue: String?,
    ): Int? {
        val value = cliValue?.toIntOrNull() ?: defaultValue
        require(cliValue == null || cliValue.toIntOrNull() != null) { maxCapturesError() }
        require(value == null || value >= 0) { maxCapturesError() }
        return value
    }

    fun maxParallelRenders(
        defaultValue: Int,
        cliValue: String?,
    ): Int {
        val value = cliValue?.toIntOrNull() ?: defaultValue
        require(cliValue == null || cliValue.toIntOrNull() != null) { maxParallelRendersError() }
        require(value > 0) { maxParallelRendersError() }
        return value
    }

    fun dryRun(
        defaultValue: Boolean,
        cliValue: String?,
    ): Boolean {
        val value = cliValue?.toBooleanStrictOrNull()
        require(cliValue == null || value != null) { dryRunError() }
        return value ?: defaultValue
    }

    fun continueOnError(
        defaultValue: Boolean,
        cliValue: String?,
    ): Boolean {
        val value = cliValue?.toBooleanStrictOrNull()
        require(cliValue == null || value != null) { continueOnErrorError() }
        return value ?: defaultValue
    }

    fun cropToContent(
        defaultValue: Boolean,
        cliValue: String?,
    ): Boolean {
        val value = cliValue?.toBooleanStrictOrNull()
        require(cliValue == null || value != null) { CROP_TO_CONTENT_ERROR }
        return value ?: defaultValue
    }

    fun cropPaddingDp(
        defaultValue: Int,
        cliValue: String?,
    ): Int {
        val value = cliValue?.toIntOrNull() ?: defaultValue
        require(cliValue == null || cliValue.toIntOrNull() != null) { cropPaddingDpError() }
        require(value >= 0) { cropPaddingDpError() }
        return value
    }

    private fun maxPreviewParameterValuesError(): String =
        "agentPreview.maxPreviewParameterValues must be a positive integer. " +
            "Configure agentPreview { maxPreviewParameterValues.set(n) } or pass -PagentPreview.maxPreviewParameterValues=n."

    private fun maxCapturesError(): String =
        "agentPreview.maxCaptures must be a non-negative integer. " +
            "Configure agentPreview { maxCaptures.set(n) } or pass -PagentPreview.maxCaptures=n."

    private fun maxParallelRendersError(): String =
        "agentPreview.maxParallelRenders must be a positive integer. " +
            "Configure agentPreview { maxParallelRenders.set(n) } or pass -PagentPreview.maxParallelRenders=n."

    private fun dryRunError(): String =
        "agentPreview.dryRun must be true or false. " +
            "Pass -PagentPreview.dryRun=true|false."

    private fun continueOnErrorError(): String =
        "agentPreview.continueOnError must be true or false. " +
            "Configure agentPreview { continueOnError.set(true|false) } or pass -PagentPreview.continueOnError=true|false."

    private fun cropPaddingDpError(): String =
        "agentPreview.cropPaddingDp must be a non-negative integer. " +
            "Configure agentPreview { android { screenshot { cropPaddingDp.set(n) } } } or pass -PagentPreview.cropPaddingDp=n."
}
