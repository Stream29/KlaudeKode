import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.kotlinPluginCompose)
    alias(libs.plugins.composeMultiplatform)
}

dependencies {
    implementation(projects.scriptingTool)
    implementation(projects.virtualThreadDispatcher)
    implementation(libs.bundles.koog)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.serialization)
    implementation(compose.desktop.currentOs)
    testImplementation(libs.bundles.testing)
}

compose.desktop {
    application {
        mainClass = "io.github.stream29.koogagent.AppKt"

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
