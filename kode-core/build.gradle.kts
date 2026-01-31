plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(projects.kodeTools)
    implementation(projects.kodeConfig)
    implementation(projects.kodeUiCore)
    implementation(projects.kodeSessionCore)
    implementation(projects.scriptingTool)
    implementation(projects.virtualThreadDispatcher)
    
    implementation(libs.bundles.koog)
    implementation(libs.bundles.serialization)
    
    testImplementation(libs.bundles.testing)
}
