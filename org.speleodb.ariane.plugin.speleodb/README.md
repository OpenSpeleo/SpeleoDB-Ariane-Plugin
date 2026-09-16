This is a plugin to connect Ariane with SpeleoDB

## Java checks

From the repository root, run:

```bash
./gradlew :org.speleodb.ariane.plugin.speleodb:javaLint
```

This type-checks production and test sources with the project's Java 25
toolchain and runs [Error Prone](https://errorprone.info/). It does not run
tests. Lint classes go into separate build directories, without generating
release metadata or changing `VERSION` and `TEST_MODE` in source.

Compiler errors and Error Prone's default error-level checks fail the task.
Warnings remain advisory while existing warnings are addressed; there is no
blanket suppression or `-Werror`. Error Prone runs in the lint tasks, and
`check` includes them. Ordinary `build` retains its assemble-only behavior.

Both the root and module pre-commit configurations run `java-lint` when Java,
Gradle, wrapper, or hook configuration files change. Gradle uses incremental
compilation for repeat checks. To run the module's hook explicitly:

```bash
uvx pre-commit run java-lint --all-files \
  --config org.speleodb.ariane.plugin.speleodb/.pre-commit-config.yaml
```

Run `uvx pre-commit install` from the repository root to enable hooks on
commits. The first lint run may download the Java toolchain and dependencies;
later runs use Gradle's local caches.

## CI tests and draft releases

Linux CI runs JavaFX tests with a virtual display. To reproduce that locally on
Ubuntu, install `xvfb` and `xauth`, then run:

```bash
xvfb-run -a ./gradlew :org.speleodb.ariane.plugin.speleodb:test
```

Pushing a tag in the exact form `YYYY.MM.DD` runs the CI checks. After lint,
tests, and the reproducible-JAR check succeed, a final job builds the plugin and
uploads the JAR and its SHA256 checksum to a **draft** GitHub release.
Publishing the release remains a manual step. Branch pushes, pull requests, and
other tag formats do not create releases.

The release build uses the tag's date for the JAR filename and embedded version,
including when an older tag is rebuilt. To build that version locally:

```bash
./gradlew :org.speleodb.ariane.plugin.speleodb:build -PreleaseVersion=2026.09.16
```
