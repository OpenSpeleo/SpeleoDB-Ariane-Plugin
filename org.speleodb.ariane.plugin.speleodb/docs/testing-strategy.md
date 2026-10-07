# Testing Strategy

## Test Stack

Dependency coordinates are centralized in the repository-root
`gradle.properties`:

| Component | Version property  | Purpose                                                   |
| --------- | ----------------- | --------------------------------------------------------- |
| JUnit 5   | `junitBomVersion` | Test framework (`org.junit.jupiter.api.*`)                |
| AssertJ   | `assertjVersion`  | Fluent assertions                                         |
| Mockito   | `mockitoVersion`  | Mocks and spies; also used by the explicit test JVM agent |
| TestFX    | `testfxVersion`   | JavaFX component testing                                  |
| WireMock  | `wiremockVersion` | Local HTTP server for hermetic service tests              |

## Test Categories

### Unit Tests (No Network, No FX)

- `SpeleoDBConstantsVersionTest` / `SpeleoDBConstantsVersionFallbackTest`:
  version string handling
- `SpeleoDBAccessLevelTest`: enum parsing
- `HTTPRequestMultipartBodyTest`: multipart encoding
- `TmlUploadPreparerTest`: frozen snapshot validation, explicit CRC32/size
  checks, delayed ZIP finalization, capture races, size limits, cancellation,
  and cleanup
- `SpeleoDBSaveDispatchTest`: single FX-dispatched save action and command
  rearming
- `SpeleoDBUploadLifecycleTest`: overlapping requests, context changes, and
  cleanup
- `SpeleoDBHostnameHandlingTest`: URL normalization
- `SpeleoDBServiceSimpleTest` / `SpeleoDBServiceAdvancedTest`: service logic
- `SpeleoDBServiceTest`: authentication, URL handling, JSON parsing, file
  operations
- `TestFixturesTest`: test infrastructure validation

### Legacy Controller Logic Tests (Extracted Logic)

- `SpeleoDBControllerTest`: via inner `SpeleoDBControllerLogic` class
- `SpeleoDBControllerSortingTest`: via inner `SpeleoDBControllerSortingLogic`
  class
- `SpeleoDBControllerStateTest`: state management, JSON handling, UI logic

### Integration Tests (JavaFX Headless)

- `SpeleoDBControllerIntegrationTest`: message counter, project state, URL
  generation
- `SpeleoDBImportFlowTest`: load-before-upload ordering
- `SpeleoDBLockAcquisitionTest`: lock lifecycle
- `SpeleoDBLockReleaseTest`: disconnect/shutdown lock-release behavior
- `SpeleoDBPluginTest` / `SpeleoDBPluginExtendedTest`: plugin lifecycle
- `SpeleoDBPluginUpdateTest`: version comparison, hash verification
- `SpeleoDBModalsInternalTest`: dialog creation
- `NewProjectDialogTest` / `NewProjectDialogFXPropertiesTest`: dialog behavior
- `SpeleoDBReadOnlyPopupTest`: read-only access level handling
- `SpeleoDBProjectOpeningTest`: real FXML, project-pane selection, disabled
  read-only actions (permission and lock failure), writable-state restoration,
  creation from the empty template with zero redraw/centering requests, and data
  checks before each delayed redraw and centering action. Also verifies
  populated surveys still redraw twice and surveys emptied during either delay
  skip the remaining redraws. Uses a mocked service and host LOAD
  acknowledgement; requires a desktop JavaFX toolkit and is skipped when
  `CI=true`.
- `MacOsDialogBehaviorTest`: platform-specific behavior
- `SuccessGifSuppressionTest`: preference handling
- `SpeleoDBPreferenceIsolationTest`: TEST_MODE preference node isolation

### Hermetic API Tests (WireMock)

- `SpeleoDBAuthApiTest`: auth token exchange, malformed-token handling, v2 error
  envelopes
- `SpeleoDBProjectCreateApiTest`: project creation request shape and unwrapped
  201 response contract
- `SpeleoDBProjectListApiTest`: unwrapped list contract and project filtering
- `SpeleoDBProjectUploadApiTest`: upload multipart/error handling using valid
  ZIPs
- `TmlUploadBoundaryTest`: Java-only validation of captured binary multipart
  artifacts, byte/hash identity after live-file mutation, and zero HTTP requests
  for rejected inputs
- `SpeleoDBProjectDownloadApiTest`: binary download/error handling
- `SpeleoDBProjectMutexApiTest`: acquire/release boolean contract plus UI-log
  detail surfacing
- `SpeleoDBAnnouncementsApiTest`: unwrapped list contract and announcement
  filtering
- `SpeleoDBPluginReleasesApiTest`: unwrapped list contract and release filtering
- `SpeleoDBPluginUpdateDownloadApiTest`: binary plugin-download behavior and
  redirects

### Live API Tests (Optional, Requires Configuration)

- `SpeleoDBAPITest`: full round-trip tests against a real SpeleoDB instance
- `TestEnvironmentConfig`: configuration loading and test gating (a helper, not
  a standalone test)
- `TestEnvironmentConfigEnvFallbackTest`: hermetic tests of configuration
  fallback

For each key, the loader resolves nonempty values from a discovered `.env`, then
the process environment, then test JVM system properties. It searches `.env`,
`org.speleodb.ariane.plugin.speleodb/.env`, `../.env`, and `../../.env`,
relative to the test JVM's working directory. A file is optional; CI supplies
environment variables. See the plugin module's `.env.dist` for the supported
configuration.

`SpeleoDBAPITest` skips when `API_TEST_ENABLED` is false or required
configuration is unavailable. The flag defaults to true, but an instance URL
plus either an OAuth token or email/password credentials are still required.
Root Gradle test configuration forwards the supported `SPELEODB_*` and `API_*`
environment keys to the test JVM.

## Headless JavaFX Rendering

Tests use software rendering with these JVM properties (set in root
`build.gradle`). These properties do not remove the Linux JavaFX GTK toolkit
requirement for a display server; CI installs Xvfb and runs tests under
`xvfb-run -a`:

```
-Djava.awt.headless=true
-Dprism.order=sw
-Dprism.text=t2k
-Dprism.lcdtext=false
-Dprism.subpixeltext=false
--enable-native-access=ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
```

Gradle sets a 120-second JUnit default timeout with `SEPARATE_THREAD` mode and
logs started, passed, failed, and skipped tests. Test authoring and toolkit
setup conventions are in [AGENTS.md](../../AGENTS.md#testing-conventions).

## Test Mode Preference Isolation

When `SpeleoDBConstants.TEST_MODE == true`:

- Preferences use node `org/speleodb/ariane/plugin/speleodb/test` instead of the
  real user node
- This prevents tests from reading/writing real user credentials or settings
- `TEST_MODE` is set by `enableTestMode` Gradle task before test compilation
- Reset to `false` by `settings.gradle` FlowAction after build completes

## Test Fixtures (`TestFixtures.java`)

Reusable test data factory:

- `ProjectFixture`: builder for random project data with optional coordinates
- `generateProjectName()` / `generateProjectDescription()`: realistic random
  data
- `calculateChecksum(Path)`: delegates to `SpeleoDBService.calculateSHA256()`
- `ProjectFixture.generateTmlFile(projectId)`: prepares TML data from the
  bundled artifact; ZIP helpers also support archive-validation tests
- `copyTestTmlFile(projectId)`: copies test TML from
  `src/test/resources/artifacts/`
- `RoundTripResult`: value class for upload/download verification

## Filesystem Side Effects

Not all tests are fully redirected to temporary directories yet.

- `SpeleoDBServiceTest`, `SpeleoDBProjectDownloadApiTest`,
  `SpeleoDBProjectUploadApiTest`, and parts of `TestFixtures` still create files
  under `PATHS.SDB_PROJECT_DIR`.
- In practice that means test runs touch the Ariane project tree under the
  current user home (for example `~/.ariane/speleodb/projects/`) and rely on
  best-effort cleanup.
- Preference state is isolated by `TEST_MODE`; project-file paths are not.

## Coverage Gaps

The following areas have limited or no automated test coverage:

- `SpeleoDBController`: older logic tests use extracted inner classes; direct
  flow tests cover selected upload, import, lock, and project-opening behavior
- `SpeleoDBLogger`: rotation logic is tested implicitly but not with size-based
  triggers
- WebView integration: no automated testing of the in-plugin browser
- Plugin self-update: JAR download/replacement tested via reflection but not
  end-to-end
- `SpeleoDBPluginReleasesApiTest.successAndFiltering()` still depends on
  `SpeleoDBConstants.ARIANE_VERSION` being parseable as `x.y.z`; when the host
  reports a non-semver value, wrapper/error/header coverage still runs but the
  version-bounds filter test is skipped

## Upload integrity regression commands

```bash
./gradlew :org.speleodb.ariane.plugin.speleodb:test \
  --tests '*TmlUpload*' --tests '*SpeleoDBSaveDispatchTest' \
  --tests '*SpeleoDBUploadLifecycleTest' --tests '*SpeleoDBProjectUploadApiTest'
```

No Python interoperability dependency is used. Received ZIPs are checked in Java
with `ZipFile`, explicit CRC32/size assertions, and `ZipInputStream`.

For offline full-suite checks, explicitly exclude `SpeleoDBAPITest` through a
Gradle test filter if the local `.env` enables live tests: `.env` takes
precedence over the `API_TEST_ENABLED` environment variable. Build the
production JAR in a separate Gradle invocation after tests so it contains
`TEST_MODE=false`.

See [TML upload integrity](upload-integrity.md) for the real-host smoke
checklist.

## Project opening regression checks

Run on a machine with a JavaFX display/toolkit available:

```bash
./gradlew :org.speleodb.ariane.plugin.speleodb:test --tests '*SpeleoDBProjectOpeningTest'
```

Manual verification in Ariane (the automated host is mocked):

1. Open a read-only project. Confirm its project pane opens, the description and
   Save/Import/Reload controls are disabled, and the footer displays the
   forbidden icon and modification warning. Refresh the list and reopen the
   pane; actions must remain disabled.
2. Open a writable project whose lock belongs to another user. Confirm the same
   disabled project pane, alongside the existing lock-conflict explanation.
3. Create a project. Confirm the new project's pane opens with an empty change
   description and enabled actions, and no “no data to display” warning appears
   after delayed redraws finish.
4. Switch from read-only to writable mode and back. Confirm both the controls
   and footer follow access mode. Open a populated survey and confirm automatic
   centering still works.
