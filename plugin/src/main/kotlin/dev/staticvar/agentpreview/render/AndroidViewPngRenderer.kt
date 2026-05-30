/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.io.File
import java.io.OutputStream

internal class AndroidViewPngRenderer {
    fun render(
        activity: Any,
        widthPx: Int,
        heightPx: Int,
        outputFile: File,
        showBackground: Boolean,
        backgroundColor: Long?,
    ): Any {
        val view = contentRoot(activity)
        if (showBackground) {
            view.javaClass
                .getMethod("setBackgroundColor", Int::class.javaPrimitiveType)
                .invoke(view, AndroidComposeRendererInRobolectric.effectiveBackgroundColor(backgroundColor))
        }
        val measureSpecClass = Class.forName("android.view.View\$MeasureSpec")
        val exactly = measureSpecClass.getField("EXACTLY").getInt(null)
        val makeMeasureSpec = measureSpecClass.getMethod("makeMeasureSpec", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val widthSpec = makeMeasureSpec.invoke(null, widthPx, exactly) as Int
        val heightSpec = makeMeasureSpec.invoke(null, heightPx, exactly) as Int
        view.javaClass.getMethod("measure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(view, widthSpec, heightSpec)
        view.javaClass
            .getMethod(
                "layout",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).invoke(view, 0, 0, widthPx, heightPx)
        val bitmapClass = Class.forName("android.graphics.Bitmap")
        val configClass = Class.forName("android.graphics.Bitmap\$Config")
        val argb8888 = configClass.getField("ARGB_8888").get(null)
        val bitmap =
            bitmapClass
                .getMethod("createBitmap", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, configClass)
                .invoke(null, widthPx, heightPx, argb8888)
        val canvas = Class.forName("android.graphics.Canvas").getConstructor(bitmapClass).newInstance(bitmap)
        view.javaClass.getMethod("draw", Class.forName("android.graphics.Canvas")).invoke(view, canvas)
        val compressFormatClass = Class.forName("android.graphics.Bitmap\$CompressFormat")
        val png = compressFormatClass.getField("PNG").get(null)
        outputFile.outputStream().use { stream ->
            val wrote =
                bitmapClass
                    .getMethod("compress", compressFormatClass, Int::class.javaPrimitiveType, OutputStream::class.java)
                    .invoke(bitmap, png, PNG_QUALITY, stream) as Boolean
            check(wrote) { "Failed to write PNG to ${outputFile.absolutePath}" }
        }
        return view
    }

    private fun contentRoot(activity: Any): Any {
        val contentId = Class.forName("android.R\$id").getField("content").getInt(null)
        return activity.javaClass.getMethod("findViewById", Int::class.javaPrimitiveType).invoke(activity, contentId)
    }

    private companion object {
        const val PNG_QUALITY = 100
    }
}
