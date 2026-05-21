/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

gradlePlugin {
    plugins {
        create("agentPreview") {
            id = "dev.staticvar.agentpreview"
            implementationClass = "dev.staticvar.agentpreview.AgentPreviewPlugin"
            displayName = "Preview For Agents"
            description = "Captures Compose previews into agent-readable screenshot and snapshot artifacts."
        }
    }
}

dependencies {
    implementation(project(":preview-scanner"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(gradleTestKit())
}

tasks.test {
    useJUnitPlatform()
}
