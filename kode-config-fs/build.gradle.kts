plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    api(projects.kodeConfigApi)
    implementation(projects.kodeConfigCore)
    implementation(projects.virtualThreadDispatcher)
    
    implementation(libs.bundles.serialization)
    implementation(libs.kotlinxCoroutinesCore)
    
    testImplementation(libs.bundles.testing)
}
