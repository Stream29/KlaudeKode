import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.internal.os.OperatingSystem

val os: OperatingSystem = OperatingSystem.current()
val arch: String = System.getProperty("os.arch").lowercase()
val isAarch64: Boolean = arch.contains("aarch64") || arch.contains("arm64")
val javafxPlatform: String = when {
    os.isWindows -> "win"
    os.isMacOsX -> "mac"
    else -> "linux"
} + if (isAarch64) "-aarch64" else ""
val javafxVersion: String = libs.versions.javafx.get()

plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.kotlinPluginCompose)
    alias(libs.plugins.composeMultiplatform)
}

dependencies {
    // New modular architecture
    implementation(projects.kodeCore)
    implementation(projects.kodeTools)
    implementation(projects.kodeConfigApi)
    implementation(projects.kodeConfigCore)
    implementation(projects.kodeConfigFs)
    implementation(projects.kodeUiCore)
    implementation(projects.kodeSessionCore)
    
    // Legacy modules
    implementation(projects.scriptingTool)
    implementation(projects.virtualThreadDispatcher)
    
    // External dependencies
    implementation(libs.bundles.koog)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.serialization)
    implementation(libs.kotlinxDatetime)
    implementation(libs.koinCore)
    implementation(libs.koinCompose)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.markdownRendererM3)
    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-media:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-web:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-swing:$javafxVersion:$javafxPlatform")
    
    testImplementation(libs.bundles.testing)
}

compose.desktop {
    application {
        mainClass = "io.github.stream29.kode.app.AppKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Exe
            )
            packageName = "KoogCodeAgent"
            packageVersion = "1.0.0"
        }
    }
}
