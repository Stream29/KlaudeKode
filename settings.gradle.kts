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
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":agent-api-test")
include(":scripting-tool")
include(":virtual-thread-dispatcher")

// New modules for kimi-cli inspired architecture
include(":kode-core")
include(":kode-tools")
include(":kode-ui-core")

// Config modules (separated into API, core logic, and filesystem implementation)
include(":kode-config-api")
include(":kode-config-core")
include(":kode-config-fs")
include(":kode-oauth-core")
// Legacy config module (deprecated, kept for compatibility)
include(":kode-config")

// Session management module (independent from Koog)
include(":kode-session-core")

// Provider preset modules
include(":providers:provider-api")
include(":providers:provider-builtin")
include(":providers:provider-anthropic")
include(":providers:provider-openai")
include(":providers:provider-gemini")
include(":providers:provider-deepseek")
include(":providers:provider-moonshot")
include(":providers:provider-openrouter")
include(":providers:provider-groq")
include(":providers:provider-mistral")
include(":providers:provider-xai")

rootProject.name = "Kode"
