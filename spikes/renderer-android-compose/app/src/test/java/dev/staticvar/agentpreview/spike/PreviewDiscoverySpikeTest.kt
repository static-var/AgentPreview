/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.spike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner

class PreviewDiscoverySpikeTest {
    @Test
    fun discoversLoginPreviewWithComposablePreviewScanner() {
        val previews = AndroidComposablePreviewScanner()
            .scanPackageTrees("dev.staticvar.agentpreview.spike")
            .getPreviews()

        assertEquals(1, previews.size)
        val preview = previews.single()
        val previewClass = preview.javaClass
        val previewInfo = previewClass.getMethod("getPreviewInfo").invoke(preview)
        val previewInfoClass = previewInfo.javaClass

        assertEquals("dev.staticvar.agentpreview.spike.LoginPreviewKt", previewClass.getMethod("getDeclaringClass").invoke(preview))
        assertEquals("LoginPreview", previewClass.getMethod("getMethodName").invoke(preview))
        assertEquals("Login", previewInfoClass.getMethod("getName").invoke(previewInfo))
        assertEquals("Auth", previewInfoClass.getMethod("getGroup").invoke(previewInfo))
        assertEquals(393, previewInfoClass.getMethod("getWidthDp").invoke(previewInfo))
        assertEquals(852, previewInfoClass.getMethod("getHeightDp").invoke(previewInfo))
        assertTrue((previewClass.getMethod("getMethodParametersType").invoke(preview) as String).isEmpty())
    }
}
