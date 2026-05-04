# Testing Strategy

## Test Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| JUnit 5 | 5.10.1 | Test framework |
| AssertJ | 3.24.2 | Fluent assertions |
| Mockito | 5.8.0 | Mocking framework |
| TestFX | 4.0.16-alpha | JavaFX testing support |

## Test Categories

### Unit Tests (No Network, No FX)
- `SpeleoDBConstantsVersionTest` / `SpeleoDBConstantsVersionFallbackTest`: version string handling
- `SpeleoDBAccessLevelTest`: enum parsing
- `HTTPRequestMultipartBodyTest`: multipart encoding
- `SpeleoDBHostnameHandlingTest`: URL normalization
- `SpeleoDBServiceSimpleTest` / `SpeleoDBServiceAdvancedTest`: service logic
- `SpeleoDBServiceTest`: authentication, URL handling, JSON parsing, file operations
- `TestFixturesTest`: test infrastructure validation

### Controller Logic Tests (Extracted Logic, No FX)
- `SpeleoDBControllerTest`: via inner `SpeleoDBControllerLogic` class
- `SpeleoDBControllerSortingTest`: via inner `SpeleoDBControllerSortingLogic` class
- `SpeleoDBControllerStateTest`: state management, JSON handling, UI logic

### Integration Tests (JavaFX Headless)
- `SpeleoDBControllerIntegrationTest`: message counter, project state, URL generation
- `SpeleoDBImportFlowTest`: upload-before-load ordering
- `SpeleoDBLockAcquisitionTest`: lock lifecycle
- `SpeleoDBLockReleaseTest`: disconnect/shutdown lock-release behavior
- `SpeleoDBPluginTest` / `SpeleoDBPluginExtendedTest`: plugin lifecycle
- `SpeleoDBPluginUpdateTest`: version comparison, hash verification
- `SpeleoDBModalsInternalTest`: dialog creation
- `NewProjectDialogTest` / `NewProjectDialogFXPropertiesTest`: dialog behavior
- `SpeleoDBReadOnlyPopupTest`: read-only access level handling
- `MacOsDialogBehaviorTest`: platform-specific behavior
- `SuccessGifSuppressionTest`: preference handling
- `SpeleoDBPreferenceIsolationTest`: TEST_MODE preference node isolation

### Hermetic API Tests (WireMock)
- `SpeleoDBAuthApiTest`: auth token exchange, malformed-token handling, v2 error envelopes
- `SpeleoDBProjectCreateApiTest`: project creation request shape and unwrapped 201 response contract
- `SpeleoDBProjectListApiTest`: unwrapped list contract and project filtering
- `SpeleoDBProjectUploadApiTest`: upload multipart/error handling
- `SpeleoDBProjectDownloadApiTest`: binary download/error handling
- `SpeleoDBProjectMutexApiTest`: acquire/release boolean contract plus UI-log detail surfacing
- `SpeleoDBAnnouncementsApiTest`: unwrapped list contract and announcement filtering
- `SpeleoDBPluginReleasesApiTest`: unwrapped list contract and release filtering
- `SpeleoDBPluginUpdateDownloadApiTest`: binary plugin-download behavior and redirects

### Live API Tests (Optional, Requires `.env`)
- `SpeleoDBAPITest`: full round-trip tests against a real SpeleoDB instance
- `TestConfigSuccess`: live API configuration validation
- `TestEnvironmentConfig`: `.env` loading and test gating
- Gated by `TestEnvironmentConfig` loading `.env` from the repo or plugin-module directory

## Headless JavaFX Rendering

Tests run without a display server using these JVM properties (set in root `build.gradle`):

```
-Djava.awt.headless=true
-Dprism.order=sw
-Dprism.text=t2k
-Dprism.lcdtext=false
-Dprism.subpixeltext=false
--enable-native-access=ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
```

FX toolkit is initialized in `@BeforeAll` via `Platform.startup(() -> {})`.

## Test Mode Preference Isolation

When `SpeleoDBConstants.TEST_MODE == true`:
- Preferences use node `org/speleodb/ariane/plugin/speleodb/test` instead of the real user node
- This prevents tests from reading/writing real user credentials or settings
- `TEST_MODE` is set by `enableTestMode` Gradle task before test compilation
- Reset to `false` by `settings.gradle` FlowAction after build completes

## Test Fixtures (`TestFixtures.java`)

Reusable test data factory:
- `ProjectFixture`: builder for random project data with optional coordinates
- `generateProjectName()` / `generateProjectDescription()`: realistic random data
- `calculateChecksum(Path)`: delegates to `SpeleoDBService.calculateSHA256()`
- `copyTestTmlFile(projectId)`: copies test TML from `src/test/resources/artifacts/`
- `RoundTripResult`: value class for upload/download verification

## Filesystem Side Effects

Not all tests are fully redirected to temporary directories yet.

- `SpeleoDBServiceTest`, `SpeleoDBProjectDownloadApiTest`, `SpeleoDBProjectUploadApiTest`, and parts of `TestFixtures` still create files under `PATHS.SDB_PROJECT_DIR`.
- In practice that means test runs touch the Ariane project tree under the current user home (for example `~/.ariane/speleodb/projects/`) and rely on best-effort cleanup.
- Preference state is isolated by `TEST_MODE`; project-file paths are not.

## Coverage Gaps

The following areas have limited or no automated test coverage:
- `SpeleoDBController` (3900 lines): most logic is tested via extracted inner classes, but direct controller flow coverage is limited
- `SpeleoDBLogger`: rotation logic is tested implicitly but not with size-based triggers
- WebView integration: no automated testing of the in-plugin browser
- Plugin self-update: JAR download/replacement tested via reflection but not end-to-end
- `SpeleoDBPluginReleasesApiTest.successAndFiltering()` still depends on `SpeleoDBConstants.ARIANE_VERSION` being parseable as `x.y.z`; when the host reports a non-semver value, wrapper/error/header coverage still runs but the version-bounds filter test is skipped
