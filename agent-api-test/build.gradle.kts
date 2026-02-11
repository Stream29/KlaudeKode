plugins {
    id("kotlin-jvm")
    id("application")
}

dependencies {
    implementation(projects.kodeConfigApi)
    implementation(projects.kodeConfigCore)
    implementation(projects.kodeCore)
    implementation(libs.kotlinxCoroutinesCore)
}

application {
    mainClass = "io.github.stream29.kode.agentapitest.AgentApiTestMainKt"
}
