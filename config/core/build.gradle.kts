plugins {
    id("kotlin-jvm")
}

dependencies {
    api(projects.config.api)

    implementation(libs.bundles.serialization)

    testImplementation(libs.bundles.testing)
}
