plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(libs.kotlinxCoroutinesCore)
    testImplementation(libs.bundles.testing)
}