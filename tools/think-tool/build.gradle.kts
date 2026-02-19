plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(projects.ui.core)

    implementation(libs.bundles.koog)
    implementation(libs.bundles.serialization)
}
