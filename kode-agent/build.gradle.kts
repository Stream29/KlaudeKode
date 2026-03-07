plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(libs.bundles.koog)
    implementation(libs.bundles.serialization)
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxCollectionsImmutable)
}
