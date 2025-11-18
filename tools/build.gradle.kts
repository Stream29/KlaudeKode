plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    // Core module
    implementation(project(":core"))

    // Koog framework
    implementation(libs.koogAgents)
    implementation(libs.ktorClientCio)

    // Kotlin scripting
    implementation(libs.bundles.kotlinScripting)

    // Kotlinx ecosystem
    implementation(libs.bundles.kotlinxEcosystem)

    // Testing
    testImplementation(libs.bundles.testing)
}
