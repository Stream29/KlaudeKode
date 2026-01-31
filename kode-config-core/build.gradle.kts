plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    api(projects.kodeConfigApi)
    
    implementation(libs.bundles.serialization)
    
    testImplementation(libs.bundles.testing)
}
