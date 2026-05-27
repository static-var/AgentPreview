/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.dependencies

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RendererSupportConfigurationFactoryTest {
    @Test
    fun `detached fallback configuration copies selected variant attributes`() {
        val project = ProjectBuilder.builder().build()
        val buildType = Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java)
        val source =
            project.configurations.create("debugRuntimeClasspath") {
                it.attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
                it.attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.LIBRARY))
                it.attributes.attribute(buildType, "debug")
            }

        val detached =
            RendererSupportConfigurationFactory().create(
                project = project,
                coordinates = listOf("androidx.compose.ui:ui-tooling:1.11.2"),
                variantRuntimeConfiguration = source,
            )

        assertTrue(detached.isCanBeResolved)
        assertFalse(detached.isCanBeConsumed)
        assertEquals(source.attributes.getAttribute(Usage.USAGE_ATTRIBUTE), detached.attributes.getAttribute(Usage.USAGE_ATTRIBUTE))
        assertEquals(
            source.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE),
            detached.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE),
        )
        assertEquals("debug", detached.attributes.getAttribute(buildType))
    }
}
