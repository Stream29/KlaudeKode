plugins {
    id("kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    constraints {
        implementation("io.netty:netty-codec:${libs.versions.netty.get()}") {
            because("Fix CVE-2025-58057 in transitive Netty dependency")
        }
        implementation("io.netty:netty-codec-compression:${libs.versions.netty.get()}") {
            because("Align compression codec with patched Netty line")
        }
    }

    implementation(projects.config.fs)
    implementation(libs.koogAgents)
    
    implementation(libs.bundles.serialization)
    implementation(libs.serializationCsv)
    implementation(libs.kotlinxCoroutinesCore)
    api(libs.kotlinxDatetime)
    implementation(libs.kotlinxCollectionsImmutable)
    
    testImplementation(libs.bundles.testing)
}
