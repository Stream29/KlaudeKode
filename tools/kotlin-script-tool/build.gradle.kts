plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.ui.core)
    implementation(projects.kodeSessionCore)

    implementation(libs.bundles.koog)
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.bundles.serialization)
    implementation(libs.bundles.kotlinScripting)

    testImplementation(libs.bundles.testing)
}
