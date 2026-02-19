plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    api(projects.config.api)
    
    implementation(libs.bundles.serialization)
    
    testImplementation(libs.bundles.testing)
}
