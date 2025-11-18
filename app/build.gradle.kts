plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    // Project dependencies
    implementation(project(":utils"))
    implementation(project(":tools"))
    implementation(project(":core"))

    // Koog framework
    implementation(libs.koogAgents)
    implementation(libs.ktorClientCio)

    // Kotlinx ecosystem
    implementation(libs.bundles.kotlinxEcosystem)
}

application {
    // Define the Fully Qualified Name for the application main class
    mainClass = "io.github.stream29.koogagent.AppKt"
}
