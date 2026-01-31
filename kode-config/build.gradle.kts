plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // Re-export new modules for backward compatibility
    api(projects.kodeConfigApi)
    api(projects.kodeConfigCore)
    api(projects.kodeConfigFs)
    
    implementation(projects.virtualThreadDispatcher)
    implementation(libs.bundles.serialization)
    implementation(libs.kotlinxCoroutinesCore)
    
    testImplementation(libs.bundles.testing)
}
