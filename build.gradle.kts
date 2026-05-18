import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless) apply false
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

    configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint()
            licenseHeader(
                """
                /*
                 * MIT License
                 *
                 * Copyright (c) 2026 Shreyansh Lodha
                 */

                """.trimIndent()
            )
        }

        kotlinGradle {
            target("*.gradle.kts")
            ktlint()
            licenseHeader(
                """
                /*
                 * MIT License
                 *
                 * Copyright (c) 2026 Shreyansh Lodha
                 */

                """.trimIndent(),
                "^(plugins|import|buildscript|pluginManagement|dependencyResolutionManagement)",
            )
        }
    }
}
