import org.gradle.internal.os.OperatingSystem

val os: OperatingSystem = OperatingSystem.current()
val arch: String = System.getProperty("os.arch").lowercase()
val isAarch64: Boolean = arch.contains("aarch64") || arch.contains("arm64")
val composeDesktopCurrentOs: String = when {
    os.isWindows -> if (isAarch64) "desktop-jvm-windows-arm64" else "desktop-jvm-windows-x64"
    os.isMacOsX -> if (isAarch64) "desktop-jvm-macos-arm64" else "desktop-jvm-macos-x64"
    else -> if (isAarch64) "desktop-jvm-linux-arm64" else "desktop-jvm-linux-x64"
}
val javafxPlatform: String = when {
    os.isWindows -> "win"
    os.isMacOsX -> "mac"
    else -> "linux"
} + if (isAarch64) "-aarch64" else ""
val javafxVersion: String = libs.versions.javafx.get()

plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginCompose)
    alias(libs.plugins.composeMultiplatform)
}

dependencies {
    api(projects.kodeSessionCore)

    constraints {
        implementation("io.netty:netty-codec:${libs.versions.netty.get()}") {
            because("Fix CVE-2025-58057 in transitive Netty dependency")
        }
        implementation("io.netty:netty-codec-compression:${libs.versions.netty.get()}") {
            because("Align compression codec with patched Netty line")
        }
    }

    implementation(libs.koogAgents)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.serialization)
    implementation("org.jetbrains.compose.desktop:$composeDesktopCurrentOs:${libs.versions.compose.get()}")
    implementation("org.jetbrains.compose.material3:material3:${libs.versions.compose.get()}")
    implementation(libs.jetbrainsComposeMaterialIconsExtended)
    implementation(libs.markdownRendererM3)
    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-media:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-web:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-swing:$javafxVersion:$javafxPlatform")

    testImplementation(libs.bundles.testing)
}
