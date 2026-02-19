plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.kodeSessionCore)
    implementation(projects.ui.core)

    implementation(libs.bundles.koog)
}
