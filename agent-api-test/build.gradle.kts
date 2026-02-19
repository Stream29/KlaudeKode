plugins {
    id("kotlin-jvm")
    id("application")
}

dependencies {
    implementation(projects.app)
    implementation(projects.providers.providerBuiltin)
    implementation(projects.kodeSessionCore)
    implementation(projects.ui.core)
    implementation(projects.config.api)
    implementation(projects.config.core)
    implementation(projects.kodeCore)
    implementation(projects.providers.providerBuiltin)
    implementation(libs.androidxViewmodel)
    implementation(libs.koinCore)
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxSerializationJson)
}

application {
    mainClass = "io.github.stream29.kode.agentapitest.AgentApiTestMainKt"
}
