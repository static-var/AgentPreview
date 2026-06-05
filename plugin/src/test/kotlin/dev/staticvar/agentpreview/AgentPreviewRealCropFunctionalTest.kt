/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO

class AgentPreviewRealCropFunctionalTest {
    @Test
    fun `real Android capture renders string dimension and vector resources`() {
        val sdkDir = androidSdkDirOrSkip("Android SDK not configured; skipping real renderer resource functional test.")
        assumeTrue(sampleProjectDir.isDirectory, "Android sample project is unavailable.")
        writeSampleLocalProperties(sdkDir)

        outputRoot.deleteRecursively()
        GradleRunner
            .create()
            .withProjectDir(sampleProjectDir)
            .withArguments(
                ":app:captureComposePreviews",
                "-PagentPreview.previewNameFilter=AndroidStringResourceValidationPreview,AndroidDimensionResourceValidationPreview,AndroidVectorResourceValidationPreview",
                "-PagentPreview.maxParallelRenders=1",
            ).build()
            .also { result ->
                assertEquals(TaskOutcome.SUCCESS, result.task(":app:captureComposePreviews")?.outcome, result.output)
                assertFalse(result.output.contains("diagnostic fallback", ignoreCase = true), result.output)
            }

        assertResourceSnapshot(
            previewFunctionName = "AndroidStringResourceValidationPreview",
            renderedEvidence = "Android resources rendered",
        )
        assertResourceSnapshot(
            previewFunctionName = "AndroidDimensionResourceValidationPreview",
            renderedEvidence = "\"width\": 18",
        )
        assertResourceSnapshot(
            previewFunctionName = "AndroidVectorResourceValidationPreview",
            renderedEvidence = "\"contentDescription\": \"Android resources rendered\"",
        )
    }

    @Test
    fun `real Android capture crops screenshots and honors crop flags`() {
        val sdkDir = androidSdkDirOrSkip("Android SDK not configured; skipping real renderer crop functional test.")
        assumeTrue(sampleProjectDir.isDirectory, "Android sample project is unavailable.")
        writeSampleLocalProperties(sdkDir)

        val defaultImage = runSmallCropCapture()
        assertTrue(defaultImage.width < 200, "default crop width should be smaller than viewport: ${defaultImage.width}")
        assertTrue(defaultImage.height < 200, "default crop height should be smaller than viewport: ${defaultImage.height}")
        assertFalse(cropMetadata().getValue("fallback").jsonPrimitive.boolean)

        val fullViewportImage = runSmallCropCapture("-PagentPreview.cropToContent=false")
        assertEquals(200, fullViewportImage.width)
        assertEquals(200, fullViewportImage.height)
        assertEquals(false, cropMetadata().getValue("enabled").jsonPrimitive.boolean)

        val noPaddingImage = runSmallCropCapture("-PagentPreview.cropPaddingDp=0")
        val paddedImage = runSmallCropCapture("-PagentPreview.cropPaddingDp=12")
        assertEquals(noPaddingImage.width + 12, paddedImage.width)
        assertEquals(noPaddingImage.height + 12, paddedImage.height)
    }

    private fun runSmallCropCapture(vararg extraArguments: String): java.awt.image.BufferedImage {
        outputRoot.deleteRecursively()
        GradleRunner
            .create()
            .withProjectDir(sampleProjectDir)
            .withArguments(
                listOf(
                    ":app:captureComposePreviews",
                    "-PagentPreview.previewNameFilter=Small Crop",
                    "-PagentPreview.maxParallelRenders=1",
                ) + extraArguments,
            ).build()
            .also { result ->
                assertEquals(TaskOutcome.SUCCESS, result.task(":app:captureComposePreviews")?.outcome, result.output)
            }
        return ImageIO.read(screenshotFile) ?: error("Screenshot was not readable at ${screenshotFile.absolutePath}")
    }

    private fun cropMetadata() =
        Json
            .parseToJsonElement(snapshotFile.readText())
            .jsonObject
            .getValue("screenshot")
            .jsonObject
            .getValue("crop")
            .jsonObject

    private fun assertResourceSnapshot(
        previewFunctionName: String,
        renderedEvidence: String,
    ) {
        val snapshot = resourceSnapshotFile(previewFunctionName).readText()
        val snapshotJson = Json.parseToJsonElement(snapshot).jsonObject
        assertEquals(
            "robolectric",
            snapshotJson
                .getValue("render")
                .jsonObject
                .getValue("mode")
                .jsonPrimitive.content,
            snapshot,
        )
        assertTrue(snapshot.contains(renderedEvidence), snapshot)
    }

    private fun androidSdkDirOrSkip(message: String): File {
        val sdkDir = androidSdkDir()
        assumeTrue(sdkDir != null, message)
        return sdkDir ?: error(message)
    }

    private fun androidSdkDir(): File? =
        listOfNotNull(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
        ).map(::File)
            .firstOrNull { it.isDirectory }
            ?: File(System.getProperty("user.home"), "Library/Android/sdk").takeIf { it.isDirectory }

    private fun writeSampleLocalProperties(sdkDir: File) {
        sampleProjectDir.resolve("local.properties").writeText("sdk.dir=${sdkDir.invariantSeparatorsPath}\n")
    }

    private companion object {
        val sampleProjectDir: File = File("../samples/android-compose-app").canonicalFile
        val outputRoot: File = sampleProjectDir.resolve("app/build/agentPreviewSnapshots")
        val screenshotFile: File =
            outputRoot.resolve("app-main-dev.staticvar.agentpreview.sample.SmallCropPreview/android-preview/screenshot.png")
        val snapshotFile: File =
            outputRoot.resolve("app-main-dev.staticvar.agentpreview.sample.SmallCropPreview/android-preview/snapshot.json")

        fun resourceSnapshotFile(previewFunctionName: String): File =
            outputRoot.resolve("app-main-dev.staticvar.agentpreview.sample.$previewFunctionName/android-preview/snapshot.json")
    }
}
