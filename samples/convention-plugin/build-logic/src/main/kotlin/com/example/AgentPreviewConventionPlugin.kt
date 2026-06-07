/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package com.example

import dev.staticvar.agentpreview.AgentPreviewExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AgentPreviewConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("dev.staticvar.agentpreview")

        target.extensions.configure<AgentPreviewExtension>("agentPreview") {
            maxPreviewParameterValues.set(3)
            maxCaptures.set(8)
            maxParallelRenders.set(1)
            continueOnError.set(true)

            android {
                variant.set("debug")
                viewport("phone", widthDp = 393, heightDp = 852)
                viewport("tablet", widthDp = 800, heightDp = 1280)
            }
        }
    }
}
