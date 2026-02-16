plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(projects.kodeConfigApi)

    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.ktorClientCio)
    implementation(libs.ktorServerCore)
    implementation(libs.ktorServerCio)

    testImplementation(libs.bundles.testing)
}
