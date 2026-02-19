plugins {
    id("kotlin-jvm")
}

dependencies {
    api(projects.config.api)
    api(projects.kodeSessionCore)

    testImplementation(libs.bundles.testing)
}
