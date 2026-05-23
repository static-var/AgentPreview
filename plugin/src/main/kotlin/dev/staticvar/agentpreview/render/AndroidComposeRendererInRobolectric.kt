/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.robolectric.Robolectric
import java.io.File

object AndroidComposeRendererInRobolectric {
    fun render(
        className: String,
        methodName: String,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        outputFile: File,
    ) {
        outputFile.parentFile.mkdirs()
        val activityClass = Class.forName("androidx.activity.ComponentActivity")
        val activity =
            Robolectric::class.java
                .getMethod("buildActivity", Class::class.java)
                .invoke(null, activityClass)
                .let { controller -> controller.javaClass.getMethod("setup").invoke(controller) }
                .let { controller -> controller.javaClass.getMethod("get").invoke(controller) }
        setDensity(activity, density)
        setContent(activity, className, methodName)
        draw(activity, widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), outputFile)
    }

    private fun setDensity(
        activity: Any,
        density: Float,
    ) {
        val resources = activity.javaClass.getMethod("getResources").invoke(activity)
        val metrics = resources.javaClass.getMethod("getDisplayMetrics").invoke(resources)
        setField(metrics, "density", density)
        setField(metrics, "scaledDensity", density)
        setField(metrics, "densityDpi", (density * DENSITY_DEFAULT).toInt().coerceAtLeast(1))
    }

    private fun setContent(
        activity: Any,
        className: String,
        methodName: String,
    ) {
        val content = PreviewComposable(className, methodName)
        val ownerClass = Class.forName("androidx.activity.ComponentActivity")
        val setContent =
            Class
                .forName("androidx.activity.compose.ComponentActivityKt")
                .methods
                .single { method ->
                    method.name == "setContent" && method.parameterTypes.size == 3 && method.parameterTypes[0] == ownerClass
                }
        setContent.invoke(null, activity, null, content)
    }

    private fun draw(
        activity: Any,
        widthPx: Int,
        heightPx: Int,
        outputFile: File,
    ) {
        val window = activity.javaClass.getMethod("getWindow").invoke(activity)
        val view = window.javaClass.getMethod("getDecorView").invoke(window)
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
                    .getMethod("compress", compressFormatClass, Int::class.javaPrimitiveType, java.io.OutputStream::class.java)
                    .invoke(bitmap, png, PNG_QUALITY, stream) as Boolean
            check(wrote) { "Failed to write PNG to ${outputFile.absolutePath}" }
        }
    }

    private fun setField(
        target: Any,
        name: String,
        value: Any,
    ) {
        val field = target.javaClass.getField(name)
        field.set(target, value)
    }

    private const val DENSITY_DEFAULT = 160
    private const val PNG_QUALITY = 100
}
