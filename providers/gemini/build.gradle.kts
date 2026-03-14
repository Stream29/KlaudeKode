plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.providers.api)

    implementation(libs.koogGoogleClient)

    testImplementation(libs.bundles.testing)
}
