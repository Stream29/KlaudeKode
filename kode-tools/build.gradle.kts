plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(projects.kodeConfig)
    implementation(projects.kodeUiCore)
    implementation(projects.virtualThreadDispatcher)
    
    implementation(libs.bundles.koog)
    implementation(libs.bundles.serialization)
    implementation(libs.jsoup)
    implementation(libs.ktorClientCio)
    
    testImplementation(libs.bundles.testing)
}
