plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // Re-export new modules for backward compatibility
    api(projects.config.api)
    api(projects.config.core)
    api(projects.config.fs)
    
    implementation(libs.bundles.serialization)
    implementation(libs.kotlinxCoroutinesCore)
    
    testImplementation(libs.bundles.testing)
}
