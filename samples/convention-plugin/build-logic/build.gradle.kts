/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("agentPreviewConvention") {
            id = "com.example.agentpreview-convention"
            implementationClass = "com.example.AgentPreviewConventionPlugin"
        }
    }
}

dependencies {
    implementation("dev.staticvar:plugin:0.1.0-SNAPSHOT")
}
