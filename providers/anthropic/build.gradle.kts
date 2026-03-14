plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.providers.api)

    implementation(libs.koogAnthropicClient)

    testImplementation(libs.bundles.testing)
}
