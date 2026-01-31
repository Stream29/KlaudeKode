plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(libs.bundles.serialization)
    
    testImplementation(libs.bundles.testing)
}
