# SpeleoDB Ariane Plugin -- Agent Guidelines

## Project Overview

A Java 25 / JavaFX 25 plugin for the Ariane cave survey editor that integrates
with the SpeleoDB platform. The plugin handles authentication, project
management (CRUD, upload/download), collaborative locking, announcements, and
self-updating.

## Temporary agent files

Keep agent plans, task lists, TODO tracking, progress notes, review notes, and
scratch lessons outside the repository tree, including all submodules. Use a
unique task directory under `/tmp/` (for example, create one with
`mktemp -d /tmp/sdb-ariane-plugin-task.XXXXXX`) or another OS temporary
directory whose resolved path is outside every checkout.

Never create or update these working files inside the checkout, even in ignored
directories such as `tasks/`, `todos/`, or `plans/`. Never stage or commit them.
Existing tracked task and lesson files are historical references; do not append
new work to them. Keep durable product and architecture documentation in
`docs/`, without embedding task checklists or linking to temporary files. Before
an authorized commit, inspect the staged filenames and exclude all agent working
files.

## Architecture

- **Multi-module Gradle project** with JPMS, two API git submodules, and a local
  dev container
- **`org.speleodb.ariane.plugin.speleodb`**: the plugin itself (service,
  controller, modals, tooltips, logger)
- **`com.arianesline.ariane.plugin.api`**: host app plugin API interfaces
  (submodule, do not modify)
- **`com.arianesline.cavelib.api`**: cave survey data model interfaces
  (submodule, do not modify)
- **`com.arianesline.plugincontainer`**: local dev JavaFX host for testing the
  plugin

## Java Conventions

### Platform and style

- Use Java 25 with JPMS, JavaFX 25.0.2, and the Gradle 9.4.1 wrapper.
- Each module has a `module-info.java`; keep `requires`, `exports`, `opens`, and
  service-provider directives aligned with dependencies, FXML reflection, and
  host discovery.
- Use four spaces for indentation, never tabs.
- Use PascalCase for classes and camelCase for methods, fields, and locals
  (`sdbInstance`, `httpClient`, `tmlFilepath`). Constants use
  `UPPER_SNAKE_CASE`; do not use snake_case for ordinary Java identifiers.

### Constants

- Centralize string literals, magic numbers, and configuration values in
  `SpeleoDBConstants`.
- Group them in nested final classes such as `API`, `HEADERS`, `HTTP_STATUS`,
  `NETWORK`, `MESSAGES`, `STYLES`, `DIMENSIONS`, `TIMINGS`, and `JSON_FIELDS`.
- Reference constants with static imports or qualified names such as
  `MESSAGES.AUTH_FAILED_STATUS`.

### Logging and error handling

- Use `SpeleoDBLogger.getInstance()` for plugin logging; do not add
  `System.out.println` or `System.err.println` calls. The logger's own console
  fallback and development host's command-line smoke output are infrastructure
  exceptions.
- `debug()` writes to the log file. `info()`, `warn()`, and `error()` also
  appear in the UI console. File rotation and UI delivery are centralized.
- Do not silently swallow unexpected exceptions with
  `catch (Exception ignored)`. Log recoverable failures at least at debug level;
  use `error(message, throwable)` when exception details matter.
- Use `IllegalStateException` for unmet state preconditions, such as missing
  authentication, and `IllegalArgumentException` for invalid input.
- `NotModifiedException` represents HTTP 304 during upload. Preserve endpoint
  contracts, including lock operations that log failures and return `false`.

### JSON and HTTP

- Use `jakarta.json`, including `Json.createReader` and
  `Json.createObjectBuilder`. Do not parse JSON with manual `indexOf` or
  `substring` operations.
- Use `JSON_FIELDS.*` constants for field names.
- Create service `java.net.http.HttpClient` instances through
  `createHttpClientForInstance(String)` or its authenticated-instance wrapper.
  It sets the connect timeout and selects HTTP/1.1 for HTTP URLs and HTTP/2 for
  HTTPS URLs.
- Give every request an explicit timeout from `NETWORK`: use
  `REQUEST_TIMEOUT_SECONDS` for ordinary requests and `DOWNLOAD_TIMEOUT_SECONDS`
  for project and plugin downloads.

### Singletons

- `SpeleoDBController` is eagerly initialized in a private static final field.
  Its `CountDownLatch` coordinates FXML readiness, not arbitrary UI access.
- `SpeleoDBLogger` is lazy: `getInstance()` synchronizes on `instanceLock`
  around its null check and construction. It uses a `ReentrantReadWriteLock` for
  logging state and file operations.

## Gradle Build Conventions

- Use this repository's `./gradlew`, including from `apps/ariane_plugin` in the
  monorepo. Do not add a monorepo-root Gradle build or modify API submodule
  sources.
- The root `subprojects` block applies `java-library`, the JavaFX plugin, Java
  25 toolchain/source/target, and `-Xlint:all` with selected warning categories
  disabled.
- Shared dependency versions live in `gradle.properties`. Keep the Mockito test
  runtime and explicit Java agent on the same `mockitoVersion`.
- The plugin and development host use Jakarta JSON and JAXB. JUnit 5, AssertJ,
  Mockito, TestFX, and WireMock are plugin test dependencies, not host test
  dependencies.

### Versioning and test ordering

- Use CalVer (`YYYY.MM.DD`). `generateReleaseVersion` injects `VERSION` into
  `SpeleoDBConstants.java`; `-PreleaseVersion=YYYY.MM.DD` overrides the build
  date.
- The plugin's `build` task depends only on `assemble`. Run `test` or `check`
  explicitly; `check` also runs Java type checking and Error Prone via
  `javaLint`.
- `enableTestMode` sets `TEST_MODE = true` before compilation for tests.
- The FlowAction in `settings.gradle` resets source `VERSION` to `null` and
  `TEST_MODE` to `false` after execution; it does not rewrite compiled
  artifacts.
- `./gradlew build test` verifies the build and tests, but its JAR may contain
  `TEST_MODE = true`. CI must execute tests explicitly and produce a
  distributable JAR in a separate build invocation after tests.

```bash
./gradlew build          # Assemble only (no tests)
./gradlew build test     # Build and run tests
./gradlew check          # Run tests and checks
./gradlew :org.speleodb.ariane.plugin.speleodb:test
./gradlew :org.speleodb.ariane.plugin.speleodb:build  # Production JAR after tests
```

### Packaging and development tasks

- Preserve `archiveBaseName = 'org.speleodb.ariane.plugin.speleodb'`, the CalVer
  filename, DEFLATED compression, ZIP64, and the sealed manifest.
- Normal plugin compilation uses `options.debug = false`. JAR entries have
  stable ordering and stripped timestamps; Photoshop source files are excluded.
- `copyPlugin` clears the development host's `plugins/` folder and copies an
  existing JAR from the plugin's `build/libs/`. It does not assemble the plugin.
- `copyAndRun` copies the existing JAR and launches the development host. Build
  first with `./gradlew :org.speleodb.ariane.plugin.speleodb:build`.
- `resetSuccessGifPreference` resets `SDB_SUPPRESS_SUCCESS_GIF` to `false` in
  `/org/speleodb/ariane/plugin/speleodb` so the upload success animation can
  appear.
- `help-build` lists build tasks. Run configured checks through prek, including
  Prettier for Markdown and whitespace, EOF, JSON/XML/YAML, and Java checks.

## JavaFX UI Conventions

### Dialogs and tooltips

- Controllers must use `SpeleoDBModals`; do not construct raw `Alert` or
  `Dialog` instances there.
- Within the modal system, use `createBaseAlert()` for owner, modality, and
  header setup; `applySimpleDialogStyle()` for white/shadow/header-panel
  styling; and `applyMaterialButton()` or its convenience wrappers for
  consistent buttons.
- Use `SpeleoDBTooltips` instead of raw `Popup` objects in controllers.
- Initialize tooltips once during startup with
  `SpeleoDBTooltips.initialize(stage)` or `initialize(scene)` once the scene has
  a window. The controller uses the scene overload and a scene-property listener
  when necessary.
- Tooltip types are `SUCCESS`, `ERROR`, `INFO`, and `WARNING`.

### Threading and FXML

- Keep all UI updates on the JavaFX application thread. From background code,
  dispatch them with `Platform.runLater()`.
- Never block the FX thread with synchronous network calls, busy-waits, or
  `Thread.sleep`. Use `PauseTransition` for delayed UI actions.
- Submit background work to `SpeleoDBPlugin.executorService`, a cached pool with
  daemon workers.
- The main panel uses `/fxml/SpeleoDB.fxml`. Set the singleton
  `SpeleoDBController` programmatically with `FXMLLoader.setController()`.
- `fxmlInitializedLatch` coordinates access after `initialize()` completes; it
  does not replace FX-thread dispatch for UI access.

### Styles and resources

- The main stylesheet is `/css/fxmlmain.css`; slider styles are in
  `/css/slider.css`.
- Keep new Java inline `-fx-*` styles in `SpeleoDBConstants.STYLES`, including
  the `STYLES.MATERIAL_COLORS` palette, instead of hardcoding them in
  controllers.
- Classpath image resources include `/images/logo.png`, `/images/icons/`, and
  `/images/success_gifs/`; Lato font variants are under `/fonts/Lato-*.ttf`.
- Country data lives at `/org/speleodb/ariane/plugin/speleodb/countries.json`,
  loaded relative to `NewProjectDialog`'s package. The empty survey template is
  `/tml/empty_project.tml`.

## Testing Conventions

### Framework and structure

- Use JUnit 5 (`org.junit.jupiter.api.*`), AssertJ, Mockito, and TestFX.
- Concrete test classes must contain JUnit 5 `@Test` methods, never `main()`
  tests. Shared fixtures, configuration helpers, and abstract test bases are
  support code.
- Use `@DisplayName` on test classes and methods and `@Nested` for logical
  groups.
- Use AssertJ's `assertThat`, `assertThatThrownBy`, and `assertThatCode`; do not
  use Java's `assert` keyword.
- Use `@ExtendWith(MockitoExtension.class)` where mock injection is needed.
- Assert expected exceptions or let unexpected ones propagate. Do not suppress
  failures with `catch (Exception ignored)`.
- Exercise production code through real objects, mocked dependencies, or Mockito
  spies. Do not duplicate production logic in test-only inner classes; legacy
  tests using extracted copies are not a pattern to extend.

### Isolation and fixtures

- `enableTestMode` enables isolated preferences before test compilation. With
  `TEST_MODE = true`, the preference node is
  `org/speleodb/ariane/plugin/speleodb/test`, protecting real user preferences.
  The post-build FlowAction resets the source flag to `false`.
- Keep reusable project fixtures, TML preparation, and checksum helpers in
  `TestFixtures.java`; its `calculateChecksum(Path)` delegates to
  `SpeleoDBService.calculateSHA256()`.
- Store committed TML fixtures under `src/test/resources/artifacts/`.
- Use `TestEnvironmentConfig.java` for optional live API tests. Configuration
  resolves from `.env`, then process environment, then test JVM system
  properties. Live tests skip when disabled or required instance/authentication
  data is missing.

### JavaFX tests

- Root Gradle test configuration sets software rendering (`prism.order=sw`) and
  headless AWT properties. Linux JavaFX still needs a display; use `xvfb-run -a`
  for tests without a desktop display, as CI does.
- Initialize the toolkit in `@BeforeAll` with `Platform.startup`. If already
  initialized, schedule setup with `Platform.runLater`. Keep a shared toolkit
  alive with `Platform.setImplicitExit(false)` when tests close their last
  window.
- Wait from the test thread using a bounded `CountDownLatch`, for example
  `assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()`. Never busy-wait or
  await a latch on the FX application thread.

## Product and Architecture References

- [Build and release](org.speleodb.ariane.plugin.speleodb/docs/build-and-release.md)
- [Testing strategy](org.speleodb.ariane.plugin.speleodb/docs/testing-strategy.md)
- [UI architecture](org.speleodb.ariane.plugin.speleodb/docs/ui-architecture.md)
- [Project switching](docs/project-switching.md)

# Changelog Maintenance

Before completing any product change, agents must assess whether it represents a
meaningful customer-facing feature, fix, performance improvement, or other
notable outcome. When it does, ensure the appropriate section under
`CHANGELOG.md` `Unreleased` cites the final implementation commit. If the
implementation is not committed yet, make the changelog update immediately after
that commit and before merge or release.

The changelog is public marketing material and must:

- Include only the most meaningful customer-facing changes.
- Use high-level language with the absolute minimum technical detail.
- End every entry with the short commit ID that best represents the final
  change.
- Group entries under conventional headings such as `Features`, ` UI/UX`,
  `Fixes`, and `Performance`.
- Replace an earlier entry when the same change evolves again before release, so
  only its final outcome and newest relevant commit ID remain.
- Omit routine refactors, dependency updates, CI changes, tests, documentation,
  and implementation details unless they materially change the customer
  experience.
- The commit should be named `[Changelog Update]`
- Only update `CHANGELOG.md` if the change is visible to the customer,
  everything else should be internal and not documented.

When creating a version tag, move the applicable `Unreleased` entries into a new
version section named `vYYYY.MM.DD`, then leave an empty `Unreleased` section at
the top for future work.
