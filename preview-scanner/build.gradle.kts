/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

dependencies {
    implementation(libs.asm)

    testImplementation(libs.junit.jupiter)
    testImplementation(project(":preview-scanner:compose-fixtures"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

mavenPublishing {
    coordinates(
        groupId = providers.gradleProperty("GROUP").getOrElse("dev.staticvar"),
        artifactId = "preview-scanner",
        version = providers.gradleProperty("VERSION_NAME").getOrElse("0.1.0-SNAPSHOT"),
    )

    publishToMavenCentral(automaticRelease = false)
    signAllPublications()

    pom {
        name.set("AgentPreview Preview Scanner")
        description.set("Bytecode scanner for discovering Jetpack Compose previews.")
        inceptionYear.set("2026")
        url.set("https://github.com/static-var/AgentPreview")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("staticvar")
                name.set("Shreyansh Lodha")
            }
        }

        scm {
            url.set("https://github.com/static-var/AgentPreview")
            connection.set("scm:git:https://github.com/static-var/AgentPreview.git")
            developerConnection.set("scm:git:ssh://git@github.com/static-var/AgentPreview.git")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
