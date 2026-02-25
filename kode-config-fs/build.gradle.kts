plugins {
    id("kotlin-jvm")
}

dependencies {
    api(projects.config.api)
    implementation(projects.config.core)
    
    implementation(libs.bundles.serialization)
    implementation(libs.kotlinxCoroutinesCore)
    
    testImplementation(libs.bundles.testing)
}
