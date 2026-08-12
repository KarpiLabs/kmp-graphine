import org.gradle.api.tasks.testing.AbstractTestTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    id("com.vanniktech.maven.publish") version "0.28.0"
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

// Disable desktop tests on headless environments
tasks.withType<AbstractTestTask>().matching { it.name.contains("desktopTest") }.forEach {
    it.enabled = false
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

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(
        groupId = "io.karpilabs",
        artifactId = "kmp-graphine",
        version = version.toString(),
    )

    pom {
        name.set("KMP Graphine")
        description.set("A high-performance, modern graph visualization library for Compose Multiplatform.")
        url.set("https://github.com/KarpiLabs/kmp-graphine")
        inceptionYear.set("2024")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
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
            url.set("https://github.com/KarpiLabs/kmp-graphine")
            connection.set("scm:git:git://github.com/KarpiLabs/kmp-graphine.git")
            developerConnection.set("scm:git:ssh://github.com/KarpiLabs/kmp-graphine.git")
        }
    }
}
