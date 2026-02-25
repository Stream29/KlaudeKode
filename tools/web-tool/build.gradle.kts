plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.ui.core)

    implementation(libs.bundles.koog)
    implementation(libs.bundles.serialization)
    implementation(libs.jsoup)
    implementation(libs.ktorClientCio)
}
