plugins {
    kotlin("jvm")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    application
}

dependencies {
    implementation(project(":graphine"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("io.karpilabs.graphine.graphview.MainKt")
}

tasks.named<JavaExec>("run") {
    jvmArgs = listOf("-Xmx2g")
}
