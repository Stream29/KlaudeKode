plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.tools.kotlinScriptTool)
    implementation(projects.tools.webTool)
    implementation(projects.config.api)
    implementation(projects.kodeOauthCore)
    implementation(projects.ui.core)
    implementation(projects.session)
    implementation(projects.providers.providerApi)
    implementation(projects.providers.providerBuiltin)

    implementation(libs.bundles.koog)
    implementation(libs.bundles.serialization)

    testImplementation(libs.koogAgentsTest)
    testImplementation(libs.bundles.testing)
}
