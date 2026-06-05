/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.io.File

/**
 * Named command payload passed from the Gradle/plugin JVM to the isolated renderer harness JVM.
 *
 * Keeping argument marshalling here prevents the process launcher, harness main method, and
 * Robolectric entry point from each carrying their own positional argument knowledge.
 */
data class RenderHarnessCommand(
    val className: String,
    val methodName: String,
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val robolectricSdk: Int,
    val outputFile: File,
    val semanticsOutputFile: File,
    val layoutTreeOutputFile: File,
    val includeUnmergedSemantics: Boolean,
    val locale: String?,
    val uiMode: Int?,
    val fontScale: Float?,
    val showBackground: Boolean,
    val backgroundColor: Long?,
    val previewParameterProviderClassName: String?,
    val previewParameterIndex: Int?,
    val resultFile: File,
    val androidAssetsDir: File?,
    val androidAssetApk: File?,
    val fontProbe: Boolean = false,
) {
    fun toArgs(): List<String> =
        listOf(
            className,
            methodName,
            widthPx.toString(),
            heightPx.toString(),
            density.toString(),
            robolectricSdk.toString(),
            outputFile.absolutePath,
            semanticsOutputFile.absolutePath,
            layoutTreeOutputFile.absolutePath,
            includeUnmergedSemantics.toString(),
            locale.orEmpty(),
            uiMode?.toString().orEmpty(),
            fontScale?.toString().orEmpty(),
            showBackground.toString(),
            backgroundColor?.toString().orEmpty(),
            previewParameterProviderClassName.orEmpty(),
            previewParameterIndex?.toString().orEmpty(),
            resultFile.absolutePath,
            androidAssetsDir?.absolutePath.orEmpty(),
            androidAssetApk?.absolutePath.orEmpty(),
            fontProbe.toString(),
        )

    fun applyToSystemProperties() {
        setProperty("className", className)
        setProperty("methodName", methodName)
        setProperty("widthPx", widthPx.toString())
        setProperty("heightPx", heightPx.toString())
        setProperty("density", density.toString())
        setProperty("robolectricSdk", robolectricSdk.toString())
        setProperty("outputFile", outputFile.absolutePath)
        setProperty("semanticsOutputFile", semanticsOutputFile.absolutePath)
        setProperty("layoutTreeOutputFile", layoutTreeOutputFile.absolutePath)
        setProperty("includeUnmergedSemantics", includeUnmergedSemantics.toString())
        setProperty("locale", locale.orEmpty())
        setProperty("uiMode", uiMode?.toString().orEmpty())
        setProperty("fontScale", fontScale?.toString().orEmpty())
        setProperty("showBackground", showBackground.toString())
        setProperty("backgroundColor", backgroundColor?.toString().orEmpty())
        setProperty("previewParameterProviderClassName", previewParameterProviderClassName.orEmpty())
        setProperty("previewParameterIndex", previewParameterIndex?.toString().orEmpty())
        setProperty("androidAssetsDir", androidAssetsDir?.absolutePath.orEmpty())
        setProperty("androidAssetApk", androidAssetApk?.absolutePath.orEmpty())
        setProperty("fontProbe", fontProbe.toString())
    }

    private fun setProperty(
        name: String,
        value: String,
    ) {
        System.setProperty("agentpreview.render.$name", value)
    }

    companion object {
        private const val ARG_COUNT = 21

        fun fromArgs(args: Array<String>): RenderHarnessCommand {
            require(args.size == ARG_COUNT) { "Expected $ARG_COUNT arguments, got ${args.size}" }
            return RenderHarnessCommand(
                className = args[0],
                methodName = args[1],
                widthPx = args[2].toInt(),
                heightPx = args[3].toInt(),
                density = args[4].toFloat(),
                robolectricSdk = args[5].toInt(),
                outputFile = File(args[6]),
                semanticsOutputFile = File(args[7]),
                layoutTreeOutputFile = File(args[8]),
                includeUnmergedSemantics = args[9].toBoolean(),
                locale = args[10].ifBlank { null },
                uiMode = args[11].ifBlank { null }?.toInt(),
                fontScale = args[12].ifBlank { null }?.toFloat(),
                showBackground = args[13].toBoolean(),
                backgroundColor = args[14].ifBlank { null }?.toLong(),
                previewParameterProviderClassName = args[15].ifBlank { null },
                previewParameterIndex = args[16].ifBlank { null }?.toInt(),
                resultFile = File(args[17]),
                androidAssetsDir = args[18].ifBlank { null }?.let(::File),
                androidAssetApk = args[19].ifBlank { null }?.let(::File),
                fontProbe = args[20].toBoolean(),
            )
        }
    }
}
