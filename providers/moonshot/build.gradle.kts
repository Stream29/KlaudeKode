plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.providers.api)

    implementation(libs.koogOpenAiClient)

    testImplementation(libs.bundles.testing)
}
