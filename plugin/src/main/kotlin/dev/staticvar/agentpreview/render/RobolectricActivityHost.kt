/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import org.robolectric.Robolectric
import java.util.Locale

internal class RobolectricActivityHost {
    fun createActivity(
        density: Float,
        fontScale: Float?,
        locale: String?,
        uiMode: Int?,
    ): Any {
        val activityClass = Class.forName("androidx.activity.ComponentActivity")
        val controller = Robolectric::class.java.getMethod("buildActivity", Class::class.java).invoke(null, activityClass)
        val activity = controller.javaClass.getMethod("get").invoke(controller)
        setNoActionBarTheme(activity)
        controller.javaClass.getMethod("setup").invoke(controller)
        applyConfiguration(activity, density, fontScale ?: DEFAULT_FONT_SCALE, locale, uiMode)
        return activity
    }

    private fun applyConfiguration(
        activity: Any,
        density: Float,
        fontScale: Float,
        localeTag: String?,
        uiMode: Int?,
    ) {
        val resources = activity.javaClass.getMethod("getResources").invoke(activity)
        val metrics = resources.javaClass.getMethod("getDisplayMetrics").invoke(resources)
        setField(metrics, "density", density)
        setField(metrics, "scaledDensity", AndroidComposeRendererInRobolectric.scaledDensity(density, fontScale))
        setField(metrics, "densityDpi", (density * DENSITY_DEFAULT).toInt().coerceAtLeast(1))
        val configuration = resources.javaClass.getMethod("getConfiguration").invoke(resources)
        setField(configuration, "fontScale", fontScale.coerceAtLeast(MIN_FONT_SCALE))
        localeTag?.let { tag ->
            configuration.javaClass
                .getMethod("setLocale", Locale::class.java)
                .invoke(configuration, AndroidComposeRendererInRobolectric.localeForPreviewQualifier(tag))
        }
        AndroidComposeRendererInRobolectric.applyUiMode(configuration, uiMode)
        resources.javaClass
            .getMethod("updateConfiguration", configuration.javaClass, metrics.javaClass)
            .invoke(resources, configuration, metrics)
    }

    private fun setNoActionBarTheme(activity: Any) {
        val themeId = Class.forName("android.R\$style").getField("Theme_Material_NoActionBar").getInt(null)
        activity.javaClass.getMethod("setTheme", Int::class.javaPrimitiveType).invoke(activity, themeId)
    }

    private fun setField(
        target: Any,
        name: String,
        value: Any,
    ) {
        target.javaClass.getField(name).set(target, value)
    }

    private companion object {
        const val DENSITY_DEFAULT = 160
        const val DEFAULT_FONT_SCALE = 1.0f
        const val MIN_FONT_SCALE = 0.01f
    }
}
