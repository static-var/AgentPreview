/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("io.github.takahirom.roborazzi")
}

kotlin {
    androidTarget()

    sourceSets {
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.12.0")
            implementation("androidx.compose.ui:ui-tooling-preview:1.11.2")
        }

        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.11.0")
            implementation("org.jetbrains.compose.foundation:foundation:1.11.0")
            implementation("org.jetbrains.compose.material3:material3:1.11.0-alpha07")
        }

        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation("junit:junit:4.13.2")
            implementation("org.robolectric:robolectric:4.16.1")
            implementation("androidx.test.ext:junit:1.3.0")
            implementation("androidx.compose.ui:ui-test-junit4")
            implementation("androidx.compose.ui:ui-tooling:1.11.2")
            implementation("io.github.takahirom.roborazzi:roborazzi:1.63.0")
            implementation("io.github.takahirom.roborazzi:roborazzi-compose:1.63.0")
            implementation("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support:1.63.0")
            implementation("io.github.sergio-sastre.ComposablePreviewScanner:android:0.9.0")
        }
    }
}

android {
    namespace = "dev.staticvar.agentpreview.cmp"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.staticvar.agentpreview.cmp"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}
