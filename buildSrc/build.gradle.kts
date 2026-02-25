plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.kotlinGradlePlugin)
    implementation("org.jetbrains.kotlin:kotlin-serialization:${libs.versions.kotlin.get()}")
}
