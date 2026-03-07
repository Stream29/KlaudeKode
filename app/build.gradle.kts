import org.gradle.internal.os.OperatingSystem
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
    // New modular architecture
    implementation(projects.kodeCore)
    implementation(projects.tools.webTool)
    implementation(projects.config.api)
    implementation(projects.config.core)
    implementation(projects.config.fs)
    implementation(projects.kodeOauthCore)
    implementation(projects.ui.core)
    implementation(projects.ui.components)
    implementation(projects.ui.bridge)
    implementation(projects.session)
    implementation(projects.providers.providerApi)
    implementation(projects.providers.providerBuiltin)
    implementation(projects.providers.providerAnthropic)
    implementation(projects.providers.providerOpenai)
    implementation(projects.providers.providerGemini)
    implementation(projects.providers.providerDeepseek)
    implementation(projects.providers.providerMoonshot)
    implementation(projects.providers.providerOpenrouter)
    implementation(projects.providers.providerGroq)
    implementation(projects.providers.providerMistral)
    implementation(projects.providers.providerXai)

    // Script engine + script tool module
    implementation(projects.tools.kotlinScriptTool)

    // External dependencies
    implementation(libs.bundles.koog)
    implementation(libs.bundles.compose)
    implementation(libs.jetbrainsLifecycleViewmodelNavigation3)
    implementation(libs.jetbrainsNavigation3Ui)
    implementation(libs.bundles.serialization)
    implementation(libs.kotlinxDatetime)
    implementation(libs.koinCore)
    implementation(libs.koinCompose)
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
