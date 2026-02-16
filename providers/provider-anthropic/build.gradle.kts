plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.providers.providerApi)

    implementation(libs.koogAnthropicClient)

    testImplementation(libs.bundles.testing)
}
