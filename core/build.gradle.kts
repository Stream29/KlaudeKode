plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    // Kotlinx ecosystem
    implementation(libs.bundles.kotlinxEcosystem)

    // Testing
    testImplementation(libs.bundles.testing)
}
