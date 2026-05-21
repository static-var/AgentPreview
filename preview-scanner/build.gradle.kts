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
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
