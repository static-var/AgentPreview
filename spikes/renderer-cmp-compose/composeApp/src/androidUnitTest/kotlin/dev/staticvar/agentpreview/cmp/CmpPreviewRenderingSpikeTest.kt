/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.cmp

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
class CmpPreviewRenderingSpikeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersCommonMainPreviewThroughAndroidTarget() {
        val preview = AndroidComposablePreviewScanner()
            .scanPackageTrees("dev.staticvar.agentpreview.cmp")
            .getPreviews()
            .single()
        val screenshot = File("build/outputs/renderer-cmp-spike/ProfilePreview.png")
        screenshot.parentFile.mkdirs()

        preview.captureRoboImage(screenshot.absolutePath)

        assertTrue(screenshot.isFile)
        assertTrue(screenshot.length() > 8)
    }

    @Test
    fun readsSemanticsForCommonMainPreviewContent() {
        composeRule.setContent { ProfilePreview() }

        composeRule.onAllNodesWithText("Static Var").fetchSemanticsNodes().single()
        composeRule.onAllNodesWithText("Compose Multiplatform preview").fetchSemanticsNodes().single()
        val name = composeRule.onNodeWithTag("profile_name").fetchSemanticsNode()
        val root = composeRule.onRoot().fetchSemanticsNode()

        assertTrue(name.config.contains(SemanticsProperties.TestTag))
        assertTrue(name.size.width > 0)
        assertTrue(name.size.height > 0)
        assertTrue(root.size.width > 0)
        assertTrue(root.size.height > 0)
    }
}
