/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.spike

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PreviewRenderingSpikeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersLoginPreviewScreenshotWithRoborazzi() {
        val preview = AndroidComposablePreviewScanner()
            .scanPackageTrees("dev.staticvar.agentpreview.spike")
            .getPreviews()
            .single()
        val screenshot = File("build/outputs/renderer-spike/LoginPreview.png")
        screenshot.parentFile.mkdirs()

        preview.captureRoboImage(screenshot.absolutePath)

        assertTrue(screenshot.isFile)
        assertTrue(screenshot.length() > 8)
    }

    @Test
    fun readsMergedSemanticsForPreviewContent() {
        composeRule.setContent { LoginPreview() }

        composeRule.onAllNodesWithText("Welcome back").fetchSemanticsNodes().single()
        composeRule.onAllNodesWithText("Continue").fetchSemanticsNodes().single()
        val button = composeRule.onNodeWithTag("continue_button").fetchSemanticsNode()
        val root = composeRule.onRoot().fetchSemanticsNode()

        assertTrue(button.config.contains(SemanticsProperties.TestTag))
        assertTrue(button.size.width > 0)
        assertTrue(button.size.height > 0)
        assertTrue(root.size.width > 0)
        assertTrue(root.size.height > 0)
    }
}
