plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.providers.api)
    implementation(libs.bundles.koog)

    testImplementation(libs.bundles.testing)
}
