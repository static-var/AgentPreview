/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.staticvar.agentpreview")
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "dev.staticvar.agentpreview.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.staticvar.agentpreview.sample"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
}

agentPreview {
    android {
        viewport("phone", widthDp = 393, heightDp = 852)
        viewport("tablet", widthDp = 800, heightDp = 1280)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
