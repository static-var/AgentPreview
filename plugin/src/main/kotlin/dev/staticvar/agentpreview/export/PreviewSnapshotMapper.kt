/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.export

import dev.staticvar.agentpreview.model.CURRENT_SNAPSHOT_SCHEMA_VERSION
import dev.staticvar.agentpreview.model.PreviewDescriptor
import dev.staticvar.agentpreview.model.PreviewMetadata
import dev.staticvar.agentpreview.model.PreviewSnapshot
import dev.staticvar.agentpreview.model.ScreenshotCropMetadata
import dev.staticvar.agentpreview.model.ScreenshotMetadata
import dev.staticvar.agentpreview.model.SnapshotRenderMetadata
import dev.staticvar.agentpreview.render.RenderResult
import dev.staticvar.agentpreview.semantics.EmptySemanticsExtractor
import dev.staticvar.agentpreview.semantics.RenderedSemanticsExtractor

internal class PreviewSnapshotMapper {
    fun map(
        preview: PreviewDescriptor,
        renderResult: RenderResult,
        useFakeRenderer: Boolean,
        cropPlan: ScreenshotCropPlan,
    ): PreviewSnapshot =
        PreviewSnapshot(
            schemaVersion = CURRENT_SNAPSHOT_SCHEMA_VERSION,
            preview =
                PreviewMetadata(
                    id = preview.id,
                    name = preview.name,
                    group = preview.group,
                    source = sourceLabel(preview),
                    sourceSet = preview.sourceSet,
                    previewParameter = preview.previewParameter,
                ),
            viewport = renderResult.viewport,
            nodes =
                if (useFakeRenderer) {
                    EmptySemanticsExtractor().extract(renderResult.rawSemantics)
                } else {
                    RenderedSemanticsExtractor().extract(renderResult.rawSemantics)
                },
            layoutTree = renderResult.layoutTree.takeUnless { useFakeRenderer }.orEmpty(),
            render = SnapshotRenderMetadata(mode = renderResult.renderMode.logLabel),
            screenshot = cropPlan.toMetadata(),
        )

    private fun ScreenshotCropPlan.toMetadata(): ScreenshotMetadata =
        ScreenshotMetadata(
            width = screenshotWidth,
            height = screenshotHeight,
            crop =
                ScreenshotCropMetadata(
                    enabled = enabled,
                    fallback = fallback,
                    x = rect.x.takeUnless { fallback },
                    y = rect.y.takeUnless { fallback },
                    width = rect.width.takeUnless { fallback },
                    height = rect.height.takeUnless { fallback },
                    paddingDp = paddingDp,
                    reason = reason,
                ),
        )

    private fun sourceLabel(preview: PreviewDescriptor): String =
        if (preview.sourceLine == null) preview.sourceFile else "${preview.sourceFile}:${preview.sourceLine}"
}
