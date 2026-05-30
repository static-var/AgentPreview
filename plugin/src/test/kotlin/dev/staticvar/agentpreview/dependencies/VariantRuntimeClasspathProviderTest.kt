/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.dependencies

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VariantRuntimeClasspathProviderTest {
    @Test
    fun `falls back to Android KMP runtime classpath when variant runtime classpath is absent`() {
        val project = ProjectBuilder.builder().build()
        val androidRuntime = project.configurations.create("androidRuntimeClasspath")

        val provider = VariantRuntimeClasspathProvider(project)

        assertEquals(androidRuntime, provider.configurationFor("debug"))
        assertEquals(
            listOf("debugRuntimeClasspath", "debugCompileClasspath", "androidRuntimeClasspath"),
            provider.inspectedConfigurationNames("debug"),
        )
    }

    @Test
    fun `prefers variant runtime classpath over Android KMP runtime classpath`() {
        val project = ProjectBuilder.builder().build()
        val debugRuntime = project.configurations.create("debugRuntimeClasspath")
        project.configurations.create("androidRuntimeClasspath")

        val provider = VariantRuntimeClasspathProvider(project)

        assertEquals(debugRuntime, provider.configurationFor("debug"))
    }
}
