# SpeleoDB Ariane Plugin -- Agent Guidelines

## Project Overview

A Java 21 / JavaFX 23 plugin for the Ariane cave survey editor that integrates with the SpeleoDB platform. The plugin handles authentication, project management (CRUD, upload/download), collaborative locking, announcements, and self-updating.

## Architecture

- **Multi-module Gradle project** with JPMS, three git submodules, and a local dev container
- **`org.speleodb.ariane.plugin.speleodb`**: the plugin itself (service, controller, modals, tooltips, logger)
- **`com.arianesline.ariane.plugin.api`**: host app plugin API interfaces (submodule, do not modify)
- **`com.arianesline.cavelib.api`**: cave survey data model interfaces (submodule, do not modify)
- **`com.arianesline.plugincontainer`**: local dev JavaFX host for testing the plugin

## Key Patterns

1. **Constants centralization**: All literals go in `SpeleoDBConstants` nested classes
2. **Centralized logging**: Use `SpeleoDBLogger.getInstance()`, never `System.out`
3. **Modal dialogs**: Use `SpeleoDBModals` helpers, never raw `Alert`/`Dialog`
4. **Tooltips**: Use `SpeleoDBTooltips`, never raw `Popup`
5. **HTTP clients**: Use `createHttpClientForInstance(url)` with proper timeout constants
6. **JSON parsing**: Use `jakarta.json` API with `JSON_FIELDS.*` constants
7. **Threading**: All UI on FX thread via `Platform.runLater()`, background work on `executorService`

## Build & Test

```bash
./gradlew build          # Build only (no tests)
./gradlew build test     # Build and run tests
./gradlew check          # Run tests and checks
./gradlew copyAndRun     # Build, copy JAR, launch dev container
```

- `build` is overridden to only run `assemble` -- tests require explicit `test` or `check`
- CalVer versioning (`YYYY.MM.DD`) via `generateReleaseVersion` task
- `TEST_MODE` flag in `SpeleoDBConstants` is automatically toggled by the build system
- Post-build FlowAction in `settings.gradle` resets VERSION and TEST_MODE in source

## Testing

- JUnit 5 + AssertJ + Mockito + TestFX
- Every test class must have `@Test` methods (no `main()` tests)
- JavaFX tests run headless with `prism.order=sw`
- Test preferences are isolated via `TEST_MODE` flag
- Live API tests are optional, gated by `.env` configuration

## Code Style

- Java camelCase for all identifiers (no snake_case)
- 4-space indentation (no tabs)
- `-Xlint:all` compiler warnings enabled
- Pre-commit hooks: trailing whitespace, EOF fixer, JSON/XML/YAML checks

## Detailed Rules

See `.cursor/rules/` for file-type-specific conventions:
- `java-conventions.mdc` -- Java language and library patterns
- `gradle-build.mdc` -- Build system and versioning
- `testing.mdc` -- Test framework and patterns
- `javafx-ui.mdc` -- UI component patterns
