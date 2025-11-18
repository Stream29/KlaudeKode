# KlaudeKode Development SOPs

This file should be updated when essential. (For example, new module being added)

## references

- `koog`(reference/koog) The powerful agent framework. Cloned from GitHub.
- `SimpleMainKts`(reference/SimpleMainKts) Example of using kts scripts. Cloned from GitHub.

## Project Overview

This is a Kotlin JVM application using Gradle as the build system. The project follows a multi-module architecture with:
- `app`: Main application module
- `utils`: Shared utilities module
- `buildSrc`: Shared build logic and convention plugins

## Development Environment Setup

### Initial Setup
1. Clone the repository
2. Run `./gradlew build` to verify setup
3. Open the project in your IDE (IntelliJ IDEA will auto-detect Gradle)

## Build Commands

Always use the Gradle Wrapper (`./gradlew`) instead of a globally installed Gradle:

```bash
# Build the entire project
./gradlew build

# Run the application
./gradlew run

# Clean build outputs
./gradlew clean

# Run all checks (tests, linting, etc.)
./gradlew check

# Build without running tests
./gradlew build -x test
```

## Project Structure

```
KlaudeKode/
├── app/                    # Main application module
│   ├── src/main/kotlin/   # Application source code
│   └── build.gradle.kts   # App module build configuration
├── utils/                  # Shared utilities module
│   ├── src/main/kotlin/   # Utility source code
│   └── build.gradle.kts   # Utils module build configuration
├── buildSrc/              # Shared build logic
│   └── src/main/kotlin/   # Convention plugins
├── gradle/                 # Gradle wrapper and version catalog
│   └── libs.versions.toml # Dependency version management
├── settings.gradle.kts    # Project structure definition
└── gradle.properties      # Build configuration
```

## Code Organization

### Module Dependencies
- `app` depends on `utils`
- Shared logic should be placed in `utils`
- Application-specific code belongs in `app`

### Package Structure
- Main package: `io.github.stream29`
- Application code: `io.github.stream29.app`
- Follow standard Kotlin package naming conventions

## Coding Standards

### General Principles
1. Follow Kotlin coding conventions
2. Use meaningful variable and function names
3. Keep functions small and focused
4. Write self-documenting code with clear intent

### File Naming
- Kotlin files: PascalCase (e.g., `App.kt`, `UserService.kt`)
- Configuration files: lowercase with hyphens (e.g., `build.gradle.kts`)

### Code Style
- Use 4 spaces for indentation (configured in `.editorconfig` if present)
- Maximum line length: 120 characters
- Use trailing commas in multi-line declarations

## Testing

### Running Tests
```bash
# Run tests with detailed output
./gradlew test --info
```

### Test Organization
- Place tests in `src/test/kotlin/` directory
- Mirror the package structure of main sources
- Name test classes with `Test` suffix (e.g., `AppTest.kt`)

## Dependency Management

### Adding Dependencies
1. Add dependencies to `gradle/libs.versions.toml` (version catalog)
2. Reference them in module `build.gradle.kts` files
3. Run `./gradlew build --refresh-dependencies` to update

### Version Catalog Structure
```toml
[versions]
kotlin = "x.y.z"

[libraries]
library-name = { module = "group:artifact", version.ref = "kotlin" }

[plugins]
plugin-name = { id = "plugin.id", version.ref = "version-ref" }
```

## Common Tasks

### Adding a New Module
1. Create module directory
2. Add module in `settings.gradle.kts`: `include(":module-name")`
3. Create `build.gradle.kts` in module directory
4. Apply convention plugin: `id("buildsrc.convention.kotlin-jvm")`

### Modifying Build Logic
- Shared logic: Edit files in `buildSrc/src/main/kotlin/`
- Module-specific: Edit module's `build.gradle.kts`
- After changes to `buildSrc`, run `./gradlew build` to recompile
