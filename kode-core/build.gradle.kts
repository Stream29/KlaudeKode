plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(projects.kodeTools)
    implementation(projects.kodeConfigApi)
    implementation(projects.kodeOauthCore)
    implementation(projects.kodeUiCore)
    implementation(projects.kodeSessionCore)
    implementation(projects.providers.providerApi)
    implementation(projects.providers.providerBuiltin)
    implementation(projects.scriptingTool)
    implementation(projects.virtualThreadDispatcher)
    
    implementation(libs.bundles.koog)
    implementation(libs.bundles.serialization)
    
    testImplementation(libs.bundles.testing)
}
