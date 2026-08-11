import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    id("maven-publish")
    id("signing")
}

group = "io.karpilabs"
version = "1.0.0"

kotlin {
    android {
        namespace = "io.karpilabs.graphine"
        compileSdk = 37
        minSdk = 29
    }

    jvm("desktop")

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.uiToolingPreview)
        }
    }
}

dependencies {
    "androidRuntimeClasspath"(libs.compose.uiTooling)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_standard_function-naming" to "disabled",
                "ktlint_standard_no-wildcard-imports" to "disabled",
            ),
        )
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
    format("misc") {
        target("*.md", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.named("check") {
    dependsOn("detekt", "spotlessCheck")
}

// Configuration for publishing
afterEvaluate {
    publishing {
        publications.withType<MavenPublication> {
            pom {
                name.set("Graphine")
                description.set("A high-performance, modern graph visualization library for Compose Multiplatform.")
                url.set("https://github.com/KarpiLabs/kmp-graphine")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("KarpiLabs")
                        name.set("KarpiLabs LLC")
                        email.set("contact@karpilabs.io")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/KarpiLabs/kmp-graphine.git")
                    developerConnection.set("scm:git:ssh://github.com/KarpiLabs/kmp-graphine.git")
                    url.set("https://github.com/KarpiLabs/kmp-graphine")
                }
            }
        }

        repositories {
            maven {
                name = "OSSRH"
                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials {
                    username = project.findProperty("ossrhUsername")?.toString()
                    password = project.findProperty("ossrhPassword")?.toString()
                }
            }
            maven {
                name = "Local"
                url = uri(layout.buildDirectory.dir("repo"))
            }
        }
    }

    signing {
        val signingKeyId = project.findProperty("signing.keyId")?.toString()
        val signingPassword = project.findProperty("signing.password")?.toString()
        val signingSecretKeyRingFile = project.findProperty("signing.secretKeyRingFile")?.toString()

        if (signingKeyId != null && signingPassword != null && signingSecretKeyRingFile != null) {
            sign(publishing.publications)
        }
    }
}
