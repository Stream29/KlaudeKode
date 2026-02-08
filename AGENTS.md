# Cowork Requirements

Unless specified, always respond in Chinese.

Use IDEA tools as long as possible. (for grammar checking, building, running, etc.)

Use IDEA's tools to build instead of `gradlew build`

Use IDEA's tools instead of LSP tools. Never use Kotlin LSP.

# Kode Development SOPs

This file should be updated when essential. (For example, new module being added)

## references

- `koog`(reference/koog) The powerful agent framework. Cloned from GitHub.
- `SimpleMainKts`(reference/SimpleMainKts) Example of using kts scripts. Cloned from GitHub.
- `kimi-cli`(reference/kimi-cli) Moonshot AI's CLI tool for Kimi. Cloned from GitHub.
- `opencode`(reference/opencode) OpenCode base repo. Cloned from GitHub.
- `oh-my-opencode`(reference/oh-my-opencode) OhMyOpenCode agents/framework. Cloned from GitHub.
- `kotlinx-serialization-csv`(reference/kotlinx-serialization-csv) CSV serialization for Kotlinx Serialization. Cloned from GitHub.
- `kotlinx.collections.immutable`(reference/kotlinx.collections.immutable) Immutable collections for Kotlin. Cloned from GitHub.

## Coding Standards

### General Principles
1. Follow Kotlin coding conventions
2. Use meaningful variable and function names
3. Keep functions small and focused
4. Write self-documenting code with clear intent
5. Avoid using default parameters
6. Use named parameters when it's better

### File Naming
- Kotlin files: PascalCase (e.g., `App.kt`, `UserService.kt`)
- Configuration files: lowercase with hyphens (e.g., `build.gradle.kts`)

### Code Style
- Use 4 spaces for indentation (configured in `.editorconfig` if present)
- Maximum line length: 120 characters
- Use trailing commas in multi-line declarations

### Compose State Management

UI state management rule: expose UI-visible state from ViewModel via `StateFlow`, and collect it in Compose with `collectAsStateWithLifecycle`.
Do not let composables directly depend on mutable ViewModel fields as the primary render source.
For session runtime state, keep `MutableStateFlow` in domain/session layer and bridge it into ViewModel `StateFlow`.


## Dependency Management

### Adding Dependencies
1. Add dependencies to `gradle/libs.versions.toml` (version catalog)
2. Reference them in module `build.gradle.kts` files
3. Run `./gradlew build --refresh-dependencies` to update

### Time Dependency Compatibility
- Keep `kotlinx-datetime` on `0.7.1-0.6.x-compat` until Koog public APIs fully migrate away from `kotlinx.datetime.Instant/Clock`.
- Do not switch to plain `0.7.1` while Koog artifacts in use still require old ABI classes at runtime.

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
- After changes to `buildSrc`, run full build to recompile
