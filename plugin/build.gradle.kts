/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.gradle.plugin.publish)
}

val agentPreviewGroup = providers.gradleProperty("GROUP").getOrElse("dev.staticvar")
val agentPreviewVersion = providers.gradleProperty("VERSION_NAME").getOrElse("0.1.0-SNAPSHOT")

group = agentPreviewGroup
version = agentPreviewVersion

gradlePlugin {
    website.set("https://github.com/static-var/AgentPreview")
    vcsUrl.set("https://github.com/static-var/AgentPreview.git")

    plugins {
        create("agentPreview") {
            id = "dev.staticvar.agentpreview"
            implementationClass = "dev.staticvar.agentpreview.AgentPreviewPlugin"
            displayName = "Preview For Agents"
            description = "Captures Compose previews into agent-readable screenshot and snapshot artifacts."
            tags.set(listOf("compose", "android", "preview", "screenshots", "ai-agents"))
        }
    }
}

dependencies {
    implementation("dev.staticvar:preview-scanner:0.1.0")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.junit4)
    implementation(libs.robolectric)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(gradleTestKit())
}

tasks.test {
    useJUnitPlatform()
}
