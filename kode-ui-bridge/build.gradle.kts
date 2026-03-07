plugins {
    id("kotlin-jvm")
}

dependencies {
    api(projects.config.api)
    api(projects.session)

    testImplementation(libs.bundles.testing)
}
