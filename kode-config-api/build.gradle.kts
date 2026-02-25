plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(libs.bundles.serialization)
    
    testImplementation(libs.bundles.testing)
}
