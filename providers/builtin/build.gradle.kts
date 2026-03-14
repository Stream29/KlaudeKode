plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(projects.providers.api)

    implementation(projects.providers.anthropic)
    implementation(projects.providers.openai)
    implementation(projects.providers.gemini)
    implementation(projects.providers.deepseek)
    implementation(projects.providers.moonshot)
    implementation(projects.providers.openrouter)
    implementation(projects.providers.groq)
    implementation(projects.providers.mistral)
    implementation(projects.providers.xai)

    implementation(libs.koogAgentsTest)

    testImplementation(libs.bundles.testing)
}
