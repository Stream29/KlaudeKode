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
project(":session").projectDir = file("kode-session-core")
include(":agent")
project(":agent").projectDir = file("kode-agent")

// UI modules
include(":ui:core")
project(":ui:core").projectDir = file("kode-ui-core")
include(":ui:components")
project(":ui:components").projectDir = file("kode-ui-components")
include(":ui:bridge")
project(":ui:bridge").projectDir = file("kode-ui-bridge")

// Tool modules (one module per tool)
include(":tools:kotlin-script-tool")
include(":tools:web-tool")

// Config modules (separated into API, core logic, and filesystem implementation)
include(":config:api")
project(":config:api").projectDir = file("kode-config-api")
include(":config:core")
project(":config:core").projectDir = file("kode-config-core")
include(":config:fs")
project(":config:fs").projectDir = file("kode-config-fs")

// Provider modules
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
