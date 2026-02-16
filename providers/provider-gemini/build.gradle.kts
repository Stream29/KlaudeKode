plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.providers.providerApi)

    implementation(libs.koogGoogleClient)

    testImplementation(libs.bundles.testing)
}
