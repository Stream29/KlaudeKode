plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(projects.kodeSessionCore)
    implementation(projects.ui.core)

    implementation(libs.bundles.koog)
    implementation(libs.bundles.serialization)
}
