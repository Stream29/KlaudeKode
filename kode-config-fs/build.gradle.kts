plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    api(projects.config.api)
    implementation(projects.config.core)
    
    implementation(libs.bundles.serialization)
    implementation(libs.kotlinxCoroutinesCore)
    
    testImplementation(libs.bundles.testing)
}
