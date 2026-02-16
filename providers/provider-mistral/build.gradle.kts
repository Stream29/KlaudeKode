plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.providers.providerApi)
    implementation(libs.bundles.koog)

    testImplementation(libs.bundles.testing)
}
