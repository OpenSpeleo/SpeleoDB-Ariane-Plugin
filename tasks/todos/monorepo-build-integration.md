# Monorepo build integration

## Plan

- [x] Preserve Ariane's nested `.gitmodules` for standalone checkouts.
- [x] Add fully prefixed root submodule paths for monorepo checkouts.
- [x] Replace the dangling plugin API gitlink with an available upstream commit.
- [x] Verify Gradle 9.4.1 imports the nested project and builds with Java 25.

## Review

The former `com.arianesline.ariane.plugin.api` gitlink referenced commit
`8bb9545f9998fb53f21365f761246fb066964637`, which is not present in any ref of
the declared upstream repository. The repair uses the published aggregation API
commit `7097ca8688954beda1344f64da746bf661ef40b4`, matching the CaveLib aggregation
gitlink. The declared Gradle 9.4.1 wrapper imported both API gitlinks and
completed `./gradlew build test` with Java 25; tests that require live external
services retained their existing intentional skips.
