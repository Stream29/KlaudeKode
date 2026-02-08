plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(projects.kodeConfig)
    implementation(projects.virtualThreadDispatcher)
    implementation(libs.koogAgents)
    
    implementation(libs.bundles.serialization)
    implementation(libs.serializationCsv)
    implementation(libs.kotlinxCoroutinesCore)
    api(libs.kotlinxDatetime)
    implementation(libs.kotlinxCollectionsImmutable)
    
    testImplementation(libs.bundles.testing)
}
