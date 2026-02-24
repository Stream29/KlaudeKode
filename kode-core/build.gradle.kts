plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(projects.tools.communicationTool)
    implementation(projects.tools.kotlinScriptTool)
    implementation(projects.tools.shellTool)
    implementation(projects.tools.taskTool)
    implementation(projects.tools.thinkTool)
    implementation(projects.tools.todoTool)
    implementation(projects.tools.webTool)
    implementation(projects.config.api)
    implementation(projects.kodeOauthCore)
    implementation(projects.ui.core)
    implementation(projects.kodeSessionCore)
    implementation(projects.providers.providerApi)
    implementation(projects.providers.providerBuiltin)
    
    implementation(libs.bundles.koog)
    implementation(libs.bundles.serialization)
    
    testImplementation(libs.bundles.testing)
}
