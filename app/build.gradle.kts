plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.mle.vpn"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mle.vpn"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}
val xrayVersion = "v26.11.9"
val xrayAar = layout.buildDirectory.file("xray/libv2ray.aar")
val xrayNamespacedAar = layout.buildDirectory.file("xray/libv2ray-ns.aar")
val xraySha256 = "cdfef146218233a31a8b52c8b1016557730d3b63fc121d4c84cacea50e669155"

val downloadXrayAar by tasks.registering {
    outputs.file(xrayAar)
    outputs.upToDateWhen { false }
    doLast {
        val target = xrayAar.get().asFile
        if (!target.exists() || target.length() == 0L) {
            target.parentFile.mkdirs()
            val url = java.net.URL("https://github.com/2dust/AndroidLibXrayLite/releases/download/$xrayVersion/libv2ray.aar")
            url.openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 120_000
            }.getInputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(target.readBytes()).joinToString("") { "%02x".format(it) }
        check(digest == xraySha256) { "Xray AAR checksum mismatch: $digest" }
    }
}

val repackageXrayAar by tasks.registering(Exec::class) {
    dependsOn(downloadXrayAar)
    inputs.file(xrayAar)
    outputs.file(xrayNamespacedAar)
    commandLine(
        "python3",
        rootProject.file("scripts/repackage-gomobile-aar.py").absolutePath,
        xrayAar.get().asFile.absolutePath
    )
    doLast {
        val generated = xrayAar.get().asFile.resolveSibling("libv2ray-ns.aar")
        check(generated.exists() && generated.length() > 0L) {
            "Xray AAR namespace repackaging did not produce libv2ray-ns.aar"
        }
        val output = xrayNamespacedAar.get().asFile
        output.parentFile.mkdirs()
        if (generated.canonicalFile != output.canonicalFile) {
            generated.copyTo(output, overwrite = true)
        }
    }
}

tasks.named("preBuild").configure { dependsOn(repackageXrayAar) }

dependencies {
    implementation(files(xrayNamespacedAar))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Android sing-box core.
    implementation("com.github.singbox-android:libbox:1.14.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
