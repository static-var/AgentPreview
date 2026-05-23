/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
pluginManagement {
    includeBuild("../..")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AgentPreviewCmpComposeSample"
include(":composeApp")
