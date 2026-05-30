/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.config

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/** Android-specific defaults used by AgentPreview discovery and rendering tasks. */
abstract class AndroidPreviewConfig
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        /** Robolectric SDK API level used in the isolated render JVM. */
        abstract val robolectricSdk: Property<Int>

        /**
         * Selected Android runtime variant used for preview discovery and rendering. Defaults to debug.
         * AgentPreview reads this variant classpath but resolves any missing renderer support in plugin-owned,
         * detached configurations so those artifacts are not packaged into the app.
         */
        abstract val variant: Property<String>

        /** Named Android viewport presets expanded by `captureComposePreviews` unless viewport filters apply. */
        abstract val viewports: ListProperty<ConfiguredViewport>

        /** Screenshot export options for Android-backed captures. */
        val screenshot: AndroidScreenshotConfig = objects.newInstance(AndroidScreenshotConfig::class.java)

        init {
            robolectricSdk.convention(AndroidPreviewConfigDefaults.ROBOLECTRIC_SDK)
            variant.convention(AndroidPreviewConfigDefaults.VARIANT)
            viewports.convention(AndroidPreviewConfigDefaults.viewports)
        }

        /** Configures screenshot export behavior. */
        fun screenshot(action: Action<in AndroidScreenshotConfig>) {
            action.execute(screenshot)
        }

        /** Adds a named Android viewport preset in dp for capture expansion. */
        fun viewport(
            name: String,
            widthDp: Int,
            heightDp: Int,
            density: Float = 1.0f,
        ) {
            viewports.add(
                ConfiguredViewport(
                    platform = "android",
                    name = name,
                    width = widthDp,
                    height = heightDp,
                    density = density,
                ),
            )
        }
    }

/** Android screenshot export configuration. */
abstract class AndroidScreenshotConfig
    @Inject
    constructor() {
        /** Crop screenshots to detected content bounds when reliable bounds exist. */
        abstract val cropToContent: Property<Boolean>

        /** Padding added around detected content before cropping, in dp. */
        abstract val cropPaddingDp: Property<Int>

        init {
            cropToContent.convention(AndroidPreviewConfigDefaults.CROP_TO_CONTENT)
            cropPaddingDp.convention(AndroidPreviewConfigDefaults.CROP_PADDING_DP)
        }
    }
