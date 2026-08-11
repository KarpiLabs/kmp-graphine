pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kmp-graphine"
include(":graphine")
include(":sample")
include(":sample-showcase")
include(":sample-graph-view")
project(":graphine").projectDir = file("library")
project(":sample").projectDir = file("sample")
project(":sample-showcase").projectDir = file("sample-showcase")
project(":sample-graph-view").projectDir = file("sample-graph-view")
