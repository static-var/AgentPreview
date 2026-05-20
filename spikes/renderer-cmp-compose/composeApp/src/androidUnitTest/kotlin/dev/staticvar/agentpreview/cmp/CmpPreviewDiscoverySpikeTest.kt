/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.cmp

import org.junit.Assert.assertEquals
import org.junit.Test
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner

class CmpPreviewDiscoverySpikeTest {
    @Test
    fun discoversCommonMainPreviewThroughAndroidTarget() {
        val previews = AndroidComposablePreviewScanner()
            .scanPackageTrees("dev.staticvar.agentpreview.cmp")
            .getPreviews()

        assertEquals(1, previews.size)
        val preview = previews.single()
        val previewClass = preview.javaClass
        val previewInfo = previewClass.getMethod("getPreviewInfo").invoke(preview)
        val previewInfoClass = previewInfo.javaClass

        assertEquals("dev.staticvar.agentpreview.cmp.ProfilePreviewKt", previewClass.getMethod("getDeclaringClass").invoke(preview))
        assertEquals("ProfilePreview", previewClass.getMethod("getMethodName").invoke(preview))
        assertEquals("Profile", previewInfoClass.getMethod("getName").invoke(previewInfo))
        assertEquals("Account", previewInfoClass.getMethod("getGroup").invoke(previewInfo))
        assertEquals(393, previewInfoClass.getMethod("getWidthDp").invoke(previewInfo))
        assertEquals(852, previewInfoClass.getMethod("getHeightDp").invoke(previewInfo))
    }
}
