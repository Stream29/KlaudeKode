plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.providers.providerApi)

    implementation(projects.providers.providerAnthropic)
    implementation(projects.providers.providerOpenai)
    implementation(projects.providers.providerGemini)
    implementation(projects.providers.providerDeepseek)
    implementation(projects.providers.providerMoonshot)
    implementation(projects.providers.providerOpenrouter)
    implementation(projects.providers.providerGroq)
    implementation(projects.providers.providerMistral)
    implementation(projects.providers.providerXai)

    implementation(libs.koogAgentsTest)

    testImplementation(libs.bundles.testing)
}
