plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(projects.kodeConfig)
    implementation(projects.virtualThreadDispatcher)
    
    implementation(libs.bundles.serialization)
    implementation(libs.kotlinxCoroutinesCore)
    api(libs.kotlinxDatetime)
    
    testImplementation(libs.bundles.testing)
}
