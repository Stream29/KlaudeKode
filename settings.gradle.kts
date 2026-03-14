pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// New modules for kimi-cli inspired architecture
include(":kode-core")
include(":kode-oauth-core")
include(":session")
include(":agent")

// UI modules
include(":ui:core")
include(":ui:components")

// Tool modules (one module per tool)
include(":tools:kotlin-script-tool")
include(":tools:web-tool")

// Config modules (separated into API, core logic, and filesystem implementation)
include(":config:api")
include(":config:core")
include(":config:fs")

// Provider modules
include(":providers:api")
include(":providers:builtin")
include(":providers:anthropic")
include(":providers:openai")
include(":providers:gemini")
include(":providers:deepseek")
include(":providers:moonshot")
include(":providers:openrouter")
include(":providers:groq")
include(":providers:mistral")
include(":providers:xai")

rootProject.name = "Kode"
