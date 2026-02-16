plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.providers.providerApi)

    implementation(libs.koogOpenAiClient)

    testImplementation(libs.bundles.testing)
}
