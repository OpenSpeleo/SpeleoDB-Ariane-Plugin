# Build and Release

## Modules and toolchain

Run commands from the Ariane plugin repository root, including when it is
checked out at `apps/ariane_plugin` in the SpeleoDB monorepo. Use the
repository's Gradle 9.4.1 wrapper (`./gradlew`), not a system Gradle
installation.

`settings.gradle` includes four JPMS modules:

| Module                                | Ownership and purpose                                     |
| ------------------------------------- | --------------------------------------------------------- |
| `com.arianesline.ariane.plugin.api`   | Read-only Git submodule containing host plugin interfaces |
| `com.arianesline.cavelib.api`         | Read-only Git submodule containing cave survey interfaces |
| `org.speleodb.ariane.plugin.speleodb` | Plugin implementation tracked in this repository          |
| `com.arianesline.plugincontainer`     | Local JavaFX host tracked in this repository              |

The root `subprojects` block applies `java-library`, the JavaFX plugin, Java 25
source/target compatibility and toolchain, and `-Xlint:all` (with selected
warning categories disabled). The Foojay resolver in `settings.gradle` can
provision the Java 25 toolchain when it is not installed locally. Dependency
versions shared across modules live in `gradle.properties`, including JavaFX
25.0.2 and the Mockito version used by both the test runtime and the explicit
Java agent.

The plugin and development host use Jakarta JSON and JAXB. JUnit 5, AssertJ,
Mockito, TestFX, and WireMock are test dependencies of the plugin module; the
host does not declare those test dependencies.

## CalVer Versioning

The plugin uses Calendar Versioning in `YYYY.MM.DD` format:

- Version defaults to the build date; `-PreleaseVersion=YYYY.MM.DD` overrides it
- The `generateReleaseVersion` Gradle task injects the selected version
- It mutates `SpeleoDBConstants.java` to replace `VERSION = null` with
  `VERSION = "YYYY.MM.DD"`
- After every build, a FlowAction in `settings.gradle` resets VERSION back to
  `null` and TEST_MODE to `false` in source
- This ensures the source tree stays clean while built artifacts carry the
  correct version

## Build and test ordering

The plugin's `build` task depends only on `assemble`; it does not run tests or
`check`. Run `test` or `check` explicitly. `check` also runs the separate
`javaLint` tasks for type checking and Error Prone.

`compileJava` and `jar` depend on `generateReleaseVersion`. Tests depend on
version injection, `enableTestMode`, and `jar`, since packaging tests inspect
the built artifact. `enableTestMode` runs after version injection and before
production/test compilation when both are scheduled, setting `TEST_MODE = true`
for preference isolation. The post-build FlowAction resets the source constants
after execution; it does not rewrite already compiled classes or JARs.

`./gradlew build test` is suitable for local verification, but its JAR may
contain `TEST_MODE = true`. For a distributable artifact, finish testing first
and run a separate build invocation, as CI does:

```bash
./gradlew :org.speleodb.ariane.plugin.speleodb:test
./gradlew :org.speleodb.ariane.plugin.speleodb:build
```

On Linux without a display, prefix the test invocation with `xvfb-run -a`.

## Gradle Tasks

| Task                               | Description                                          |
| ---------------------------------- | ---------------------------------------------------- |
| `./gradlew build`                  | Assemble JAR only (no tests)                         |
| `./gradlew build test`             | Build and run all tests                              |
| `./gradlew check`                  | Run tests and checks                                 |
| `./gradlew copyPlugin`             | Copy an existing built JAR to plugincontainer        |
| `./gradlew copyAndRun`             | Copy an existing JAR and launch the development host |
| `./gradlew generateReleaseVersion` | Inject CalVer into source                            |
| `./gradlew enableTestMode`         | Set TEST_MODE=true for test isolation                |
| `./gradlew help-build`             | Show available build tasks                           |

`copyPlugin` clears `com.arianesline.plugincontainer/plugins/` and copies the
contents of the plugin's `build/libs/` directory. Neither it nor `copyAndRun`
depends on assembling the plugin, so build first:

```bash
./gradlew :org.speleodb.ariane.plugin.speleodb:build
./gradlew copyAndRun
```

`./gradlew resetSuccessGifPreference` resets `SDB_SUPPRESS_SUCCESS_GIF` to
`false` in the production preference node
`/org/speleodb/ariane/plugin/speleodb`, allowing the upload success animation to
appear again.

## CI Pipeline (GitHub Actions)

Defined in `.github/workflows/gradle.yml`:

The workflow runs on pushes to `master` and `dev`, pull requests to `master`,
and date tags. Its jobs cover:

- Prek checks with recursive checkout.
- JDK 25 (Temurin), Gradle setup/caching, and plugin tests under Xvfb on Ubuntu.
- A separate production build after tests, a source-constant reset check, and
  upload of the resulting plugin JAR.
- SHA-256 comparison of two clean builds to check JAR reproducibility.
- Draft releases for `YYYY.MM.DD` tags after lint, tests/build, and
  reproducibility pass. The release build uses `-PreleaseVersion` to match the
  tag and uploads the JAR with a SHA-256 sidecar. Release titles use
  `v{tag} - SpeleoDB Ariane Plugin`.

The dependency-submission job is conditional on pushes to `main`, which is not
one of the configured push branches; it is not an active step for `master`
builds.

The root and plugin-module pre-commit configurations define whitespace, EOF,
JSON/TOML/XML/YAML, case-conflict, private-key, and Java lint checks. The root
configuration additionally supplies Markdown formatting through Prettier and CI
linting. Run checks with prek; do not install Git hooks.

## JAR Packaging

- Filename: `org.speleodb.ariane.plugin.speleodb-YYYY.MM.DD.jar`
- Compression: DEFLATED with ZIP64 extensions
- Manifest: sealed, Implementation-Title/Version/Vendor, Built-By, Build-Jdk
- Excludes: `*.psd` files (kept in repo for design reference)
- No debug symbols (`options.debug = false`) in normal plugin compilation
- Stable entry order and stripped timestamps for reproducible archives
- Previous JARs in `build/libs/` are removed when the `jar` task executes

## Submodule Management

Two Git submodules are defined in `.gitmodules`:

| Submodule                           | Source                                            | Purpose                    |
| ----------------------------------- | ------------------------------------------------- | -------------------------- |
| `com.arianesline.ariane.plugin.api` | `Ariane-s-Line/com.arianesline.ariane.plugin.api` | Plugin interface contracts |
| `com.arianesline.cavelib.api`       | `Ariane-s-Line/com.arianesline.cavelib.api`       | Cave survey data model     |

The plugin implementation is an ordinary tracked module, not a third submodule.
Do not modify the API submodule sources as part of plugin development.

CI checks out with `submodules: recursive` to ensure all dependencies are
available.

## Working inside the SpeleoDB monorepo

Ariane remains an independent Gradle project at `apps/ariane_plugin`; the
monorepo has no root Gradle build. Build and test from Ariane's directory with
its own wrapper, just as in a standalone checkout:

```bash
cd apps/ariane_plugin
./gradlew build test
```

The monorepo's `.gitmodules` declares the top-level Ariane repository only.
Ariane's own `.gitmodules` owns its two API gitlinks using paths relative to
Ariane. Do not duplicate prefixed API submodule entries in the monorepo root.
Keep the nested declarations usable in standalone recursive checkouts as well.

The API revisions must support the aggregation interfaces used by the host and
plugin. The recorded plugin API revision provides `LOAD_AGR`/`SAVE_AGR` and the
aggregation methods on `DataServerPlugin`; CaveLib provides
`AggregationInterface` and its related model interfaces. Gitlinks in the
repository tree are the source of truth for exact revisions. When an update is
authorized, use a revision reachable from its declared upstream and retain
compatible API revisions.

The monorepo VS Code settings import Ariane through
`gradle.nestedProjects: ["apps/ariane_plugin"]`, with Gradle wrapper import, the
Gradle build server, and automatic Java build configuration enabled. These
settings expose the nested build without adding another Gradle project.

## Plugin Installation

Users install the plugin by copying the JAR file to Ariane's
`~/.ariane/Plugins/` directory. The plugin self-update mechanism can download
and replace its own JAR, requiring an Ariane restart.
