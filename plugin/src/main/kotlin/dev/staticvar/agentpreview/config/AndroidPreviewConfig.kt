/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.config

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class AndroidPreviewConfig
    @Inject
    constructor() {
        abstract val robolectricSdk: Property<Int>
        abstract val variant: Property<String>
        abstract val viewports: ListProperty<ConfiguredViewport>

        init {
            robolectricSdk.convention(AndroidPreviewConfigDefaults.ROBOLECTRIC_SDK)
            variant.convention(AndroidPreviewConfigDefaults.VARIANT)
            viewports.convention(AndroidPreviewConfigDefaults.viewports)
        }

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
