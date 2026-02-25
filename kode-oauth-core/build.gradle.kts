plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.config.api)

    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.oidcCore)
    implementation(libs.ktorClientCio)
    implementation(libs.ktorServerCore)
    implementation(libs.ktorServerCio)

    testImplementation(libs.bundles.testing)
}
