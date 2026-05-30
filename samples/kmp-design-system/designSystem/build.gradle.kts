/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("dev.staticvar.agentpreview")
}

kotlin {
    androidLibrary {
        namespace = "dev.staticvar.agentpreview.kmp.designsystem"
        compileSdk = 36
        minSdk = 23
        androidResources.enable = true
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("androidx.compose.ui:ui-tooling-preview:1.11.2")
        }
    }
}

agentPreview {
    android {
        viewport("phone", widthDp = 393, heightDp = 852)
        viewport("tablet", widthDp = 800, heightDp = 1280)
    }
}
