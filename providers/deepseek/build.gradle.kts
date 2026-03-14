plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.providers.api)

    implementation(libs.koogDeepseekClient)

    testImplementation(libs.bundles.testing)
}
