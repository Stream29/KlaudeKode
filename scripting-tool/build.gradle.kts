plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.bundles.serialization)
    implementation(libs.bundles.kotlinScripting)
    testImplementation(libs.bundles.testing)
}