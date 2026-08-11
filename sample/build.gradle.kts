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
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("io.karpilabs.graphine.sample.MainKt")
}

tasks.named<JavaExec>("run") {
    jvmArgs = listOf("-Xmx2g")
}
