plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.providers.providerApi)

    implementation(libs.koogDeepseekClient)

    testImplementation(libs.bundles.testing)
}
