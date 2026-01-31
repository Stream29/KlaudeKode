import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
    implementation(projects.kodeConfig)
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
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    
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
