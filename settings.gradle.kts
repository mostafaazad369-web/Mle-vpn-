import com.android.build.api.dsl.SettingsExtension

pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
plugins {
    id("com.android.settings") version "8.13.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "MLE VPN"
include(":app", ":awg-tunnel")
project(":awg-tunnel").projectDir = file("vendor/amneziawg-android/tunnel")

@Suppress("UnstableApiUsage")
configure<SettingsExtension> {
    buildToolsVersion = "36.0.0"
    compileSdk = 36
    minSdk = 24
    ndkVersion = "26.1.10909125"
}
