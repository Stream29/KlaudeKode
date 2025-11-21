plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.kotlinPluginCompose)
    alias(libs.plugins.composeMultiplatform)
}

dependencies {
    // Project dependencies
    implementation(project(":utils"))
    implementation(project(":tools"))
    implementation(project(":core"))

    // Koog framework
    implementation(libs.koogAgents)
    implementation(libs.ktorClientCio)
    implementation(libs.kaml)

    // Kotlinx ecosystem
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.kotlinxCoroutinesSwing)
    
    // Compose
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "io.github.stream29.koogagent.AppKt"

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "KoogCodeAgent"
            packageVersion = "1.0.0"
        }
    }
}
