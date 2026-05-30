/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.config.ConfiguredViewport
import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.Viewport

internal class ViewportResolver(
    private val configuredViewports: List<ConfiguredViewport>,
) {
    /**
     * Compose @Preview dimensions are partial overrides unless both axes are explicit.
     * When width and height are both set, rendering uses one synthetic android-preview
     * viewport. When only one axis is set, that axis overrides every configured viewport
     * while the other axis, density, platform, and name stay configured.
     */
    fun resolve(preview: PreviewDescriptor): List<Viewport> =
        if (preview.hasExplicitWidth() && preview.hasExplicitHeight()) {
            listOf(
                Viewport(
                    platform = "android",
                    name = "preview",
                    width = requireNotNull(preview.widthDp),
                    height = requireNotNull(preview.heightDp),
                    density = 1.0f,
                ),
            )
        } else {
            configuredViewports.map { configured ->
                Viewport(
                    platform = configured.platform,
                    name = configured.name,
                    width = preview.widthDp.takeIf { preview.hasExplicitWidth() } ?: configured.width,
                    height = preview.heightDp.takeIf { preview.hasExplicitHeight() } ?: configured.height,
                    density = configured.density,
                )
            }
        }

    private fun PreviewDescriptor.hasExplicitWidth(): Boolean = widthDp != null && widthDp > 0

    private fun PreviewDescriptor.hasExplicitHeight(): Boolean = heightDp != null && heightDp > 0
}
