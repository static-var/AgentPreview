/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.asm)

    testImplementation(libs.junit.jupiter)
    testImplementation(project(":preview-scanner-compose-fixtures"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
