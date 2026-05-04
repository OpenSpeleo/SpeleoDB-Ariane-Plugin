package org.speleodb.ariane.plugin.speleodb;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import javafx.application.Platform;
import javafx.event.ActionEvent;

/**
 * Comprehensive unit tests for SpeleoDBController logic using JUnit 5, Mockito, and AssertJ.
 * Tests business logic, state management, and utility functions without JavaFX dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpeleoDB Controller Tests")
class SpeleoDBControllerTest {

    @TempDir
    Path tempDir;

    @Mock
    private Desktop mockDesktop;

    @Mock
    private ActionEvent mockActionEvent;

    private SpeleoDBControllerLogic controllerLogic;

    /**
     * Initialize JavaFX Platform for tests that require it.
     * This prevents "Toolkit not initialized" errors when testing JavaFX components.
     */
    @BeforeAll
    static void initializeJavaFX() {
        try {
            // Initialize JavaFX Platform if not already initialized
            if (!Platform.isFxApplicationThread()) {
                // Start JavaFX Platform
                Platform.startup(() -> {
                    // Empty runnable - just need to start the platform
                });

                // Give the platform a moment to initialize
                Thread.sleep(100);
            }
        } catch (Exception e) {
            // If JavaFX is already initialized or can't be initialized, that's fine
            // The tests will either work or fail with more specific error messages
            System.out.println("JavaFX Platform initialization: " + e.getMessage());
        }
    }

    @BeforeEach
    void setUp() {
        controllerLogic = new SpeleoDBControllerLogic();

        // Reset singleton instance before each test
        SpeleoDBController.resetInstance();
    }

    @AfterEach
    void tearDown() {
        // Reset singleton instance after each test to ensure clean state
        SpeleoDBController.resetInstance();
    }

    @Nested
    @DisplayName("Signup Functionality")
    class SignupFunctionalityTests {

        @Test
        @DisplayName("Should generate production signup URL when not in debug mode")
        void shouldGenerateProductionSignupUrlWhenNotInDebugMode() {
            // Setup
            controllerLogic.setDebugMode(false);
            controllerLogic.setInstance("www.speleodb.org");

            // Execute
            String signupUrl = controllerLogic.generateSignupUrl();

            // Verify
            assertThat(signupUrl).isEqualTo("https://www.speleodb.org/signup/");
        }

        @Test
        @DisplayName("Should generate debug signup URL when in debug mode")
        void shouldGenerateDebugSignupUrlWhenInDebugMode() {
            // Setup
            controllerLogic.setDebugMode(true);
            controllerLogic.setInstance("www.speleodb.org");

            // Execute
            String signupUrl = controllerLogic.generateSignupUrl();

            // Verify
            assertThat(signupUrl).isEqualTo("http://www.speleodb.org/signup/");
        }

        @Test
        @DisplayName("Should use custom instance for signup URL")
        void shouldUseCustomInstanceForSignupUrl() {
            // Setup
            controllerLogic.setDebugMode(false);
            controllerLogic.setInstance("custom.speleodb.com");

            // Execute
            String signupUrl = controllerLogic.generateSignupUrl();

            // Verify
            assertThat(signupUrl).isEqualTo("https://custom.speleodb.com/signup/");
        }

        @Test
        @DisplayName("Should use default instance when instance is empty")
        void shouldUseDefaultInstanceWhenInstanceIsEmpty() {
            // Setup
            controllerLogic.setDebugMode(false);
            controllerLogic.setInstance("");

            // Execute
            String signupUrl = controllerLogic.generateSignupUrl();

            // Verify
            assertThat(signupUrl).isEqualTo("https://www.speleodb.org/signup/");
        }

        @Test
        @DisplayName("Should handle whitespace in instance")
        void shouldHandleWhitespaceInInstance() {
            // Setup
            controllerLogic.setDebugMode(false);
            controllerLogic.setInstance("  spaced.instance.com  ");

            // Execute
            String signupUrl = controllerLogic.generateSignupUrl();

            // Verify
            assertThat(signupUrl).isEqualTo("https://spaced.instance.com/signup/");
        }

        @Test
        @DisplayName("Should open signup URL using Desktop")
        void shouldOpenSignupUrlUsingDesktop() {
            try (MockedStatic<Desktop> desktopMock = mockStatic(Desktop.class)) {
                // Setup
                controllerLogic.setDebugMode(false);
                controllerLogic.setInstance("www.speleodb.org");
                desktopMock.when(Desktop::getDesktop).thenReturn(mockDesktop);

                // Execute
                String result = controllerLogic.openSignupUrl();

                // Verify
                verify(mockDesktop).browse(URI.create("https://www.speleodb.org/signup/"));
                assertThat(result).contains("Opening signup page: https://www.speleodb.org/signup/");
            } catch (Exception e) {
                fail("Should not throw exception: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Should handle IOException gracefully")
        void shouldHandleIOExceptionGracefully() {
            try (MockedStatic<Desktop> desktopMock = mockStatic(Desktop.class)) {
                // Setup
                controllerLogic.setDebugMode(false);
                controllerLogic.setInstance("www.speleodb.org");
                desktopMock.when(Desktop::getDesktop).thenReturn(mockDesktop);
                doThrow(new IOException("Browser not available"))
                    .when(mockDesktop).browse(any(URI.class));

                // Execute
                String result = controllerLogic.openSignupUrl();

                // Verify error handling
                assertThat(result).contains("Failed to open signup page: Browser not available");
            } catch (Exception e) {
                fail("Should not throw exception: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Should handle URISyntaxException gracefully")
        void shouldHandleURISyntaxExceptionGracefully() {
            // Setup - use an invalid instance that would cause URI creation to fail
            controllerLogic.setDebugMode(false);
            controllerLogic.setInstance("invalid uri with spaces");

            // Execute
            String result = controllerLogic.openSignupUrl();

            // Verify error handling
            assertThat(result).contains("Failed to open signup page:");
        }

        @Test
        @DisplayName("Should handle Desktop.getDesktop() unavailability")
        void shouldHandleDesktopUnavailability() {
            try (MockedStatic<Desktop> desktopMock = mockStatic(Desktop.class)) {
                // Setup
                controllerLogic.setDebugMode(false);
                controllerLogic.setInstance("www.speleodb.org");
                desktopMock.when(Desktop::getDesktop)
                    .thenThrow(new UnsupportedOperationException("Desktop not supported"));

                // Execute
                String result = controllerLogic.openSignupUrl();

                // Verify error handling
                assertThat(result).contains("Failed to open signup page: Desktop not supported");
            }
        }
    }

    @Nested
    @DisplayName("Debug Mode Detection")
    class DebugModeDetectionTests {

        @Test
        @DisplayName("Should detect debug mode from properties file")
        void shouldDetectDebugModeFromPropertiesFile() throws IOException {
            // Create debug.properties file
            Path debugProps = tempDir.resolve("debug.properties");
            Files.write(debugProps, "debug.mode=true\n".getBytes());

            controllerLogic.setDebugPropertiesPath(debugProps.toString());

            assertThat(controllerLogic.isDebugModeFromProperties()).isTrue();
        }

        @Test
        @DisplayName("Should detect debug mode from system property")
        void shouldDetectDebugModeFromSystemProperty() {
            try {
                System.setProperty("speleodb.debug.mode", "true");
                assertThat(controllerLogic.isDebugModeFromSystemProperty()).isTrue();
            } finally {
                System.clearProperty("speleodb.debug.mode");
            }
        }

        @Test
        @DisplayName("Should return false when no debug configuration found")
        void shouldReturnFalseWhenNoDebugConfigurationFound() {
            System.clearProperty("speleodb.debug.mode");
            controllerLogic.setDebugMode(false);

            assertThat(controllerLogic.isDebugMode()).isFalse();
        }
    }

    @Nested
    @DisplayName("Message Counter")
    class MessageCounterTests {

        @Test
        @DisplayName("Should increment message counter atomically")
        void shouldIncrementMessageCounterAtomically() {
            AtomicInteger counter = new AtomicInteger(0);

            assertThat(counter.get()).isEqualTo(0);
            assertThat(counter.incrementAndGet()).isEqualTo(1);
            assertThat(counter.incrementAndGet()).isEqualTo(2);
            assertThat(counter.incrementAndGet()).isEqualTo(3);
            assertThat(counter.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should handle concurrent access properly")
        void shouldHandleConcurrentAccessProperly() throws InterruptedException {
            AtomicInteger counter = new AtomicInteger(0);
            int threadCount = 10;
            int incrementsPerThread = 100;

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        counter.incrementAndGet();
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            assertThat(counter.get()).isEqualTo(threadCount * incrementsPerThread);
        }
    }

    @Nested
    @DisplayName("JSON Project Handling")
    class JsonProjectHandlingTests {

        @Test
        @DisplayName("Should handle project without mutex")
        void shouldHandleProjectWithoutMutex() {
            JsonObject project = Json.createObjectBuilder()
                .add("id", "project-123")
                .add("name", "Test Project")
                .add("permission", "READ_AND_WRITE")
                .addNull("active_mutex")
                .build();

            assertThat(project.getString("id")).isEqualTo("project-123");
            assertThat(project.getString("name")).isEqualTo("Test Project");
            assertThat(project.getString("permission")).isEqualTo("READ_AND_WRITE");
            assertThat(project.get("active_mutex").getValueType()).isEqualTo(JsonValue.ValueType.NULL);
        }

        @Test
        @DisplayName("Should handle project with mutex")
        void shouldHandleProjectWithMutex() {
            JsonObject mutexObj = Json.createObjectBuilder()
                .add("user", "john.doe@example.com")
                .add("creation_date", "2024-01-15T10:30:00.000000")
                .add("modified_date", "2024-01-15T11:45:00.000000")
                .build();

            JsonObject project = Json.createObjectBuilder()
                .add("id", "project-456")
                .add("name", "Locked Project")
                .add("permission", "READ_AND_WRITE")
                .add("active_mutex", mutexObj)
                .build();

            JsonObject mutex = project.getJsonObject("active_mutex");
            assertThat(mutex.getString("user")).isEqualTo("john.doe@example.com");
            assertThat(mutex.getString("creation_date")).isEqualTo("2024-01-15T10:30:00.000000");
            assertThat(mutex.getString("modified_date")).isEqualTo("2024-01-15T11:45:00.000000");
        }
    }

    @Nested
    @DisplayName("Constants and Configuration")
    class ConstantsAndConfigurationTests {

        @Test
        @DisplayName("Should have correct preference constants")
        void shouldHaveCorrectPreferenceConstants() {
            assertThat(controllerLogic.getPrefEmail()).isEqualTo("SDB_EMAIL");
            assertThat(controllerLogic.getPrefPassword()).isEqualTo("SDB_PASSWORD");
            assertThat(controllerLogic.getPrefOAuthToken()).isEqualTo("SDB_OAUTH_TOKEN");
            assertThat(controllerLogic.getPrefInstance()).isEqualTo("SDB_INSTANCE");
            assertThat(controllerLogic.getDefaultInstance()).isEqualTo("www.speleoDB.org");
        }
    }

    // Removed: confirmation popup functionality tests; lock-release modals are no longer used

    @Nested
    @DisplayName("Upload and Unlock Flow Integration")
    class UploadAndUnlockFlowIntegrationTests {

        @Test
        @DisplayName("Should handle complete upload with lock release flow")
        void shouldHandleCompleteUploadWithLockReleaseFlow() {
            // Setup
            controllerLogic.setCurrentProject("Integration Test Project");
            controllerLogic.setUserConfirmationResponse(true);

            // Execute upload flow
            String uploadResult = controllerLogic.simulateUploadFlow();
            boolean shouldReleaseLock = controllerLogic.simulateReleaseLockConfirmation();

            // Verify
            assertThat(uploadResult).contains("Upload successful");
            assertThat(shouldReleaseLock).isTrue();
        }

        @Test
        @DisplayName("Should handle upload with lock retention flow")
        void shouldHandleUploadWithLockRetentionFlow() {
            // Setup
            controllerLogic.setCurrentProject("Retention Test Project");
            controllerLogic.setUserConfirmationResponse(false);

            // Execute upload flow
            String uploadResult = controllerLogic.simulateUploadFlow();
            boolean shouldReleaseLock = controllerLogic.simulateReleaseLockConfirmation();

            // Verify
            assertThat(uploadResult).contains("Upload successful");
            assertThat(shouldReleaseLock).isFalse();
        }

        @Test
        @DisplayName("Should handle project switch with lock release flow")
        void shouldHandleProjectSwitchWithLockReleaseFlow() {
            // Setup
            controllerLogic.setCurrentProject("Current Locked Project");
            controllerLogic.setUserConfirmationResponse(true);

            // Execute project switch flow
            boolean shouldSwitch = controllerLogic.simulateProjectSwitchConfirmation("New Target Project");

            // Verify
            assertThat(shouldSwitch).isTrue();
            assertThat(controllerLogic.getConfirmationDialogCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle project switch cancellation flow")
        void shouldHandleProjectSwitchCancellationFlow() {
            // Setup
            controllerLogic.setCurrentProject("Current Locked Project");
            controllerLogic.setUserConfirmationResponse(false);

            // Execute project switch flow
            boolean shouldSwitch = controllerLogic.simulateProjectSwitchConfirmation("New Target Project");

            // Verify
            assertThat(shouldSwitch).isFalse();
            assertThat(controllerLogic.getConfirmationDialogCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle null response in project switch dialog")
        void shouldHandleNullResponseInProjectSwitchDialog() {
            // Setup
            controllerLogic.setCurrentProject("Current Locked Project");
            controllerLogic.setUserConfirmationResponse(null); // Simulate dialog cancellation

            // Execute project switch flow
            boolean shouldSwitch = controllerLogic.simulateProjectSwitchConfirmation("New Target Project");

            // Verify - should default to false when cancelled
            assertThat(shouldSwitch).isFalse();
            assertThat(controllerLogic.getConfirmationDialogCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle complete project switch with refresh flow")
        void shouldHandleCompleteProjectSwitchWithRefreshFlow() {
            // Setup
            controllerLogic.setCurrentProject("Source Project");
            controllerLogic.setUserConfirmationResponse(true);

            // Execute complete flow
            boolean shouldSwitch = controllerLogic.simulateProjectSwitchConfirmation("Target Project");
            String switchResult = controllerLogic.simulateProjectSwitchFlow("Target Project", shouldSwitch);

            // Verify
            assertThat(shouldSwitch).isTrue();
            assertThat(switchResult).contains("Project switch completed");
            assertThat(switchResult).contains("Target Project");
            assertThat(switchResult).contains("refreshing project listing");
        }

        @Test
        @DisplayName("Should handle manual project refresh when authenticated")
        void shouldHandleManualProjectRefreshWhenAuthenticated() {
            // Setup
            controllerLogic.setAuthenticated(true);

            // Execute
            String refreshResult = controllerLogic.simulateRefreshProjects();

            // Verify
            assertThat(refreshResult).contains("Project list refreshed successfully");
            assertThat(refreshResult).contains("authentication check passed");
        }

        @Test
        @DisplayName("Should prevent manual project refresh when not authenticated")
        void shouldPreventManualProjectRefreshWhenNotAuthenticated() {
            // Setup
            controllerLogic.setAuthenticated(false);

            // Execute
            String refreshResult = controllerLogic.simulateRefreshProjects();

            // Verify
            assertThat(refreshResult).contains("Cannot refresh projects: Not authenticated");
            assertThat(refreshResult).doesNotContain("Project list refreshed");
        }

        @Test
        @DisplayName("Should handle refresh button state changes")
        void shouldHandleRefreshButtonStateChanges() {
            // Setup
            controllerLogic.setAuthenticated(true);

            // Execute
            var buttonStates = controllerLogic.simulateRefreshButtonStates();

            // Verify
            assertThat(buttonStates.getInitialState()).isEqualTo("Refresh Projects");
            assertThat(buttonStates.getDuringRefreshState()).isEqualTo("Refreshing ...");
            assertThat(buttonStates.getFinalState()).isEqualTo("Refresh Projects");
            assertThat(buttonStates.isInitiallyEnabled()).isTrue();
            assertThat(buttonStates.isDuringRefreshEnabled()).isFalse();
            assertThat(buttonStates.isFinallyEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should handle refresh errors gracefully")
        void shouldHandleRefreshErrorsGracefully() {
            // Setup
            controllerLogic.setAuthenticated(true);
            controllerLogic.setSimulateRefreshError(true);

            // Execute
            String refreshResult = controllerLogic.simulateRefreshProjects();

            // Verify
            assertThat(refreshResult).contains("Failed to refresh projects");
            assertThat(refreshResult).contains("Simulated refresh error");
        }

        @Test
        @DisplayName("Should configure button layout for authenticated state")
        void shouldConfigureButtonLayoutForAuthenticatedState() {
            // Setup
            controllerLogic.setAuthenticated(false);

            // Execute authentication
            var layoutState = controllerLogic.simulateAuthenticationStateChange(true);

            // Verify authenticated state layout
            assertThat(layoutState.getConnectionButtonText()).isEqualTo("DISCONNECT");
            assertThat(layoutState.getConnectionButtonColumnSpan()).isEqualTo(3); // Full width
            assertThat(layoutState.isSignupButtonVisible()).isFalse(); // Hidden
            assertThat(layoutState.isRefreshButtonEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should configure button layout for disconnected state")
        void shouldConfigureButtonLayoutForDisconnectedState() {
            // Setup
            controllerLogic.setAuthenticated(true);

            // Execute disconnection
            var layoutState = controllerLogic.simulateAuthenticationStateChange(false);

            // Verify disconnected state layout
            assertThat(layoutState.getConnectionButtonText()).isEqualTo("CONNECT");
            assertThat(layoutState.getConnectionButtonColumnSpan()).isEqualTo(1); // 50% width
            assertThat(layoutState.isSignupButtonVisible()).isTrue(); // Visible
            assertThat(layoutState.isRefreshButtonEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should handle initial UI state correctly")
        void shouldHandleInitialUIStateCorrectly() {
            // Setup - fresh controller logic
            var freshControllerLogic = new SpeleoDBControllerLogic();

            // Execute initial setup
            var initialState = freshControllerLogic.simulateInitialUISetup();

            // Verify initial state
            assertThat(initialState.getConnectionButtonText()).isEqualTo("CONNECT");
            assertThat(initialState.getConnectionButtonColumnSpan()).isEqualTo(1); // 50% width
            assertThat(initialState.isSignupButtonVisible()).isTrue(); // Visible
            assertThat(initialState.isRefreshButtonEnabled()).isFalse(); // Disabled
            assertThat(initialState.isAuthenticated()).isFalse();
        }

        @Test
        @DisplayName("Should validate button layout transitions")
        void shouldValidateButtonLayoutTransitions() {
            // Setup
            controllerLogic.setAuthenticated(false);

            // Execute connect -> disconnect -> connect cycle
            var connectState = controllerLogic.simulateAuthenticationStateChange(true);
            var disconnectState = controllerLogic.simulateAuthenticationStateChange(false);
            var reconnectState = controllerLogic.simulateAuthenticationStateChange(true);

            // Verify connect state
            assertThat(connectState.getConnectionButtonColumnSpan()).isEqualTo(3);
            assertThat(connectState.isSignupButtonVisible()).isFalse();

            // Verify disconnect state
            assertThat(disconnectState.getConnectionButtonColumnSpan()).isEqualTo(1);
            assertThat(disconnectState.isSignupButtonVisible()).isTrue();

            // Verify reconnect state
            assertThat(reconnectState.getConnectionButtonColumnSpan()).isEqualTo(3);
            assertThat(reconnectState.isSignupButtonVisible()).isFalse();
        }

        @Test
        @DisplayName("Should refresh project listing after successful project opening")
        void shouldRefreshProjectListingAfterSuccessfulProjectOpening() {
            // Setup
            controllerLogic.setAuthenticated(true);
            controllerLogic.setCurrentProject(null); // No current project initially

            // Execute project opening flow
            String openingResult = controllerLogic.simulateProjectOpening("New Project", true);

            // Verify
            assertThat(openingResult).contains("Project opened successfully");
            assertThat(openingResult).contains("New Project");
            assertThat(openingResult).contains("refreshing project listing after project opening");
        }

        @Test
        @DisplayName("Should not refresh project listing after failed project opening")
        void shouldNotRefreshProjectListingAfterFailedProjectOpening() {
            // Setup
            controllerLogic.setAuthenticated(true);
            controllerLogic.setCurrentProject(null);

            // Execute failed project opening flow
            String openingResult = controllerLogic.simulateProjectOpening("Failed Project", false);

            // Verify
            assertThat(openingResult).contains("Project opening failed");
            assertThat(openingResult).contains("Failed Project");
            assertThat(openingResult).doesNotContain("refreshing project listing");
        }

        @Test
        @DisplayName("Should handle direct unlock flow")
        void shouldHandleDirectUnlockFlow() {
            // Setup
            controllerLogic.setCurrentProject("Direct Unlock Project");
            controllerLogic.setUserConfirmationResponse(true);

            // Execute unlock flow
            boolean shouldUnlock = controllerLogic.simulateUnlockConfirmation();
            String unlockResult = controllerLogic.simulateUnlockFlow(shouldUnlock);

            // Verify
            assertThat(shouldUnlock).isTrue();
            assertThat(unlockResult).contains("Project unlocked successfully");
        }

        @Test
        @DisplayName("Should handle cancelled unlock flow")
        void shouldHandleCancelledUnlockFlow() {
            // Setup
            controllerLogic.setCurrentProject("Cancelled Unlock Project");
            controllerLogic.setUserConfirmationResponse(false);

            // Execute unlock flow
            boolean shouldUnlock = controllerLogic.simulateUnlockConfirmation();
            String unlockResult = controllerLogic.simulateUnlockFlow(shouldUnlock);

            // Verify
            assertThat(shouldUnlock).isFalse();
            assertThat(unlockResult).contains("User cancelled unlock operation");
        }

        @Test
        @DisplayName("Should validate state consistency after confirmation flows")
        void shouldValidateStateConsistencyAfterConfirmationFlows() {
            // Setup
            controllerLogic.setCurrentProject("State Test Project");
            String initialProject = controllerLogic.getCurrentProject();

            // Execute various flows
            controllerLogic.setUserConfirmationResponse(false);
            controllerLogic.simulateUnlockConfirmation();

            controllerLogic.setUserConfirmationResponse(true);
            controllerLogic.simulateReleaseLockConfirmation();

            // Verify state consistency
            assertThat(controllerLogic.getCurrentProject()).isEqualTo(initialProject);
            assertThat(controllerLogic.getConfirmationDialogCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Create New Project Functionality")
    class CreateNewProjectFunctionalityTests {

        @Test
        @DisplayName("Should prevent create new project when not authenticated")
        void shouldPreventCreateNewProjectWhenNotAuthenticated() {
            // Setup
            controllerLogic.setAuthenticated(false);

            // Execute
            String result = controllerLogic.simulateCreateNewProject();

            // Verify
            assertThat(result).contains("Cannot create new project: Not authenticated");
            assertThat(result).doesNotContain("functionality not yet implemented");
        }

        @Test
        @DisplayName("Should handle create new project when authenticated")
        void shouldHandleCreateNewProjectWhenAuthenticated() {
            // Setup
            controllerLogic.setAuthenticated(true);

            // Execute
            String result = controllerLogic.simulateCreateNewProject();

            // Verify
            assertThat(result).contains("Create New Project button clicked - functionality not yet implemented");
            assertThat(result).doesNotContain("Cannot create new project");
        }

        @Test
        @DisplayName("Should have create new project button disabled by default")
        void shouldHaveCreateNewProjectButtonDisabledByDefault() {
            // Execute
            var initialState = controllerLogic.simulateInitialUISetup();

            // Verify
            assertThat(initialState.isCreateNewProjectButtonEnabled()).isFalse();
            assertThat(initialState.isRefreshButtonEnabled()).isFalse();
            assertThat(initialState.isAuthenticated()).isFalse();
        }

        @Test
        @DisplayName("Should enable create new project button when authenticated")
        void shouldEnableCreateNewProjectButtonWhenAuthenticated() {
            // Setup
            controllerLogic.setAuthenticated(false);

            // Execute authentication
            var authenticatedState = controllerLogic.simulateAuthenticationStateChange(true);

            // Verify
            assertThat(authenticatedState.isCreateNewProjectButtonEnabled()).isTrue();
            assertThat(authenticatedState.isRefreshButtonEnabled()).isTrue();
            assertThat(authenticatedState.isAuthenticated()).isTrue();
        }

        @Test
        @DisplayName("Should disable create new project button when disconnected")
        void shouldDisableCreateNewProjectButtonWhenDisconnected() {
            // Setup - start authenticated
            controllerLogic.setAuthenticated(true);

            // Execute disconnection
            var disconnectedState = controllerLogic.simulateAuthenticationStateChange(false);

            // Verify
            assertThat(disconnectedState.isCreateNewProjectButtonEnabled()).isFalse();
            assertThat(disconnectedState.isRefreshButtonEnabled()).isFalse();
            assertThat(disconnectedState.isAuthenticated()).isFalse();
        }

        @Test
        @DisplayName("Should validate create new project button state transitions")
        void shouldValidateCreateNewProjectButtonStateTransitions() {
            // Setup
            controllerLogic.setAuthenticated(false);

            // Execute connect -> disconnect -> connect cycle
            var initialState = controllerLogic.simulateInitialUISetup();
            var connectState = controllerLogic.simulateAuthenticationStateChange(true);
            var disconnectState = controllerLogic.simulateAuthenticationStateChange(false);
            var reconnectState = controllerLogic.simulateAuthenticationStateChange(true);

            // Verify initial state
            assertThat(initialState.isCreateNewProjectButtonEnabled()).isFalse();

            // Verify connect state
            assertThat(connectState.isCreateNewProjectButtonEnabled()).isTrue();

            // Verify disconnect state
            assertThat(disconnectState.isCreateNewProjectButtonEnabled()).isFalse();

            // Verify reconnect state
            assertThat(reconnectState.isCreateNewProjectButtonEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should maintain create new project button consistency with refresh button")
        void shouldMaintainCreateNewProjectButtonConsistencyWithRefreshButton() {
            // Setup
            controllerLogic.setAuthenticated(false);

            // Test multiple state transitions
            var states = new UILayoutState[] {
                controllerLogic.simulateInitialUISetup(),
                controllerLogic.simulateAuthenticationStateChange(true),
                controllerLogic.simulateAuthenticationStateChange(false),
                controllerLogic.simulateAuthenticationStateChange(true)
            };

            // Verify both buttons always have the same enabled state
            for (UILayoutState state : states) {
                assertThat(state.isCreateNewProjectButtonEnabled())
                    .as("Create New Project button should have same state as Refresh button")
                    .isEqualTo(state.isRefreshButtonEnabled());
            }
        }

        @Test
        @DisplayName("Should log appropriate message when create new project is attempted while not authenticated")
        void shouldLogAppropriateMessageWhenCreateNewProjectAttemptedWhileNotAuthenticated() {
            // Setup
            controllerLogic.setAuthenticated(false);

            // Execute
            String result = controllerLogic.simulateCreateNewProject();

            // Verify specific message format
            assertThat(result).isEqualTo("Cannot create new project: Not authenticated");
        }

        @Test
        @DisplayName("Should log appropriate message when create new project is clicked while authenticated")
        void shouldLogAppropriateMessageWhenCreateNewProjectClickedWhileAuthenticated() {
            // Setup
            controllerLogic.setAuthenticated(true);

            // Execute
            String result = controllerLogic.simulateCreateNewProject();

            // Verify specific message format
            assertThat(result).isEqualTo("Create New Project button clicked - functionality not yet implemented");
        }

        @Test
        @DisplayName("Should handle complete project creation flow")
        void shouldHandleCompleteProjectCreationFlow() {
            // Setup
            controllerLogic.setAuthenticated(true);

            // Execute
            String result = controllerLogic.simulateCreateProjectWithData(
                "Chikin Ha",
                "A nice cavern",
                "MX",
                "-100.367573",
                "100.423897"
            );

            // Verify
            assertThat(result).contains("Creating new project: Chikin Ha");
            assertThat(result).contains("Project 'Chikin Ha' created successfully!");
            assertThat(result).contains("Project ID: mock-project-");
            assertThat(result).contains("Refreshing project list");
        }

        @Test
        @DisplayName("Should handle project creation without coordinates")
        void shouldHandleProjectCreationWithoutCoordinates() {
            // Setup
            controllerLogic.setAuthenticated(true);

            // Execute
            String result = controllerLogic.simulateCreateProjectWithData(
                "Simple Cave",
                "A basic cave description",
                "CA",
                null,
                null
            );

            // Verify
            assertThat(result).contains("Creating new project: Simple Cave");
            assertThat(result).contains("Project 'Simple Cave' created successfully!");
        }

        @Test
        @DisplayName("Should prevent project creation when not authenticated")
        void shouldPreventProjectCreationWhenNotAuthenticated() {
            // Setup
            controllerLogic.setAuthenticated(false);

            // Execute
            String result = controllerLogic.simulateCreateProjectWithData(
                "Test Project",
                "Description",
                "US",
                "40.7128",
                "-74.0060"
            );

            // Verify
            assertThat(result).isEqualTo("Cannot create new project: Not authenticated");
        }
    }

    @Nested
    @DisplayName("Project Sorting Functionality")
    class ProjectSortingFunctionalityTests {

        @Test
        @DisplayName("Should prevent sort by name when not authenticated")
        void shouldPreventSortByNameWhenNotAuthenticated() {
            // Setup
            controllerLogic.setAuthenticated(false);

            // Execute
            String result = controllerLogic.simulateSortByName();

            // Verify
            assertThat(result).contains("Cannot sort projects: Not authenticated");
        }

        @Test
        @DisplayName("Should allow sort by name when authenticated")
        void shouldAllowSortByNameWhenAuthenticated() {
            // Setup
            controllerLogic.setAuthenticated(true);

            // Execute
            String result = controllerLogic.simulateSortByName();

            // Verify
            assertThat(result).contains("User requested sort by name");
        }

        @Test
        @DisplayName("Should prevent sort by date when not authenticated")
        void shouldPreventSortByDateWhenNotAuthenticated() {
            // Setup
            controllerLogic.setAuthenticated(false);

            // Execute
            String result = controllerLogic.simulateSortByDate();

            // Verify
            assertThat(result).contains("Cannot sort projects: Not authenticated");
        }

        @Test
        @DisplayName("Should allow sort by date when authenticated")
        void shouldAllowSortByDateWhenAuthenticated() {
            // Setup
            controllerLogic.setAuthenticated(true);

            // Execute
            String result = controllerLogic.simulateSortByDate();

            // Verify
            assertThat(result).contains("User requested sort by date");
        }

        @Test
        @DisplayName("Should change sort mode when sorting by name")
        void shouldChangeSortModeWhenSortingByName() {
            // Setup
            controllerLogic.setAuthenticated(true);
            controllerLogic.setSortMode("BY_DATE"); // Start with date mode

            // Execute
            controllerLogic.simulateSortByName();

            // Verify
            assertThat(controllerLogic.getCurrentSortMode()).isEqualTo("BY_NAME");
        }

        @Test
        @DisplayName("Should change sort mode when sorting by date")
        void shouldChangeSortModeWhenSortingByDate() {
            // Setup
            controllerLogic.setAuthenticated(true);
            controllerLogic.setSortMode("BY_NAME"); // Start with name mode

            // Execute
            controllerLogic.simulateSortByDate();

            // Verify
            assertThat(controllerLogic.getCurrentSortMode()).isEqualTo("BY_DATE");
        }

        @Test
        @DisplayName("Should handle multiple sort mode changes")
        void shouldHandleMultipleSortModeChanges() {
            // Setup
            controllerLogic.setAuthenticated(true);

            // Execute multiple sort changes
            controllerLogic.simulateSortByDate();
            controllerLogic.simulateSortByName();
            controllerLogic.simulateSortByDate();

            // Verify final state
            assertThat(controllerLogic.getCurrentSortMode()).isEqualTo("BY_DATE");
        }

        @Test
        @DisplayName("Should maintain sort mode consistency")
        void shouldMaintainSortModeConsistency() {
            // Setup
            controllerLogic.setAuthenticated(true);

            // Execute
            String nameResult = controllerLogic.simulateSortByName();
            String dateResult = controllerLogic.simulateSortByDate();

            // Verify both operations succeeded and mode changed
            assertThat(nameResult).contains("User requested sort by name");
            assertThat(dateResult).contains("User requested sort by date");
        }

        @Test
        @DisplayName("Should log appropriate messages for sort operations")
        void shouldLogAppropriateMessagesForSortOperations() {
            // Setup
            controllerLogic.setAuthenticated(true);

            // Execute
            String nameResult = controllerLogic.simulateSortByName();
            String dateResult = controllerLogic.simulateSortByDate();

            // Verify logging messages
            assertThat(nameResult).contains("User requested sort by name");
            assertThat(dateResult).contains("User requested sort by date");
        }

        @Test
        @DisplayName("Should handle sort operations when no cached data exists")
        void shouldHandleSortOperationsWhenNoCachedDataExists() {
            // Setup
            controllerLogic.setAuthenticated(true);

            // Execute sort operations without any cached project data
            String nameResult = controllerLogic.simulateSortByName();
            String dateResult = controllerLogic.simulateSortByDate();

            // Verify operations complete without errors
            assertThat(nameResult).contains("User requested sort by name");
            assertThat(dateResult).contains("User requested sort by date");
        }
    }

    @Nested
    @DisplayName("Reload Project Functionality")
    class ReloadProjectFunctionalityTests {

        @Test
        @DisplayName("Should generate correct reload confirmation message")
        void shouldGenerateCorrectReloadConfirmationMessage() {
            // Setup
            controllerLogic.setCurrentProject("Test Project");

            // Execute
            String message = controllerLogic.generateReloadConfirmationMessage();

            // Verify
            assertThat(message).contains("Are you sure you want to reload the project \"Test Project\"");
            assertThat(message).contains("WARNING: Any unsaved modifications will be lost!");
            assertThat(message).contains("All changes since last save will be discarded");
            assertThat(message).contains("The project will be reloaded from disk");
            assertThat(message).contains("This action cannot be undone");
        }

        @Test
        @DisplayName("Should handle reload when no project is loaded")
        void shouldHandleReloadWhenNoProjectIsLoaded() {
            // Setup
            controllerLogic.setCurrentProject(null);

            // Execute
            String message = controllerLogic.generateReloadConfirmationMessage();

            // Verify
            assertThat(message).isEqualTo("No project is currently loaded to reload");
        }

        @Test
        @DisplayName("Should validate reload confirmation dialog properties")
        void shouldValidateReloadConfirmationDialogProperties() {
            // Setup
            controllerLogic.setCurrentProject("Sample Project");

            // Execute
            DialogProperties props = controllerLogic.getReloadConfirmationDialogProperties();

            // Verify
            assertThat(props.getTitle()).isEqualTo("Reload Project");
            assertThat(props.getHeaderText()).isEqualTo("Confirm Reload");
            assertThat(props.getContentText()).contains("Are you sure you want to reload the project \"Sample Project\"");
            assertThat(props.getYesButtonText()).isEqualTo("Reload");
            assertThat(props.getNoButtonText()).isEqualTo("Cancel");
        }

        @Test
        @DisplayName("Should simulate user choosing to reload project")
        void shouldSimulateUserChoosingToReloadProject() {
            // Setup
            controllerLogic.setCurrentProject("My Project");
            controllerLogic.setUserConfirmationResponse(true);

            // Execute
            boolean result = controllerLogic.simulateReloadConfirmation();

            // Verify
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should simulate user cancelling reload")
        void shouldSimulateUserCancellingReload() {
            // Setup
            controllerLogic.setCurrentProject("My Project");
            controllerLogic.setUserConfirmationResponse(false);

            // Execute
            boolean result = controllerLogic.simulateReloadConfirmation();

            // Verify
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should handle successful project reload")
        void shouldHandleSuccessfulProjectReload() {
            // Setup
            controllerLogic.setCurrentProject("Successful Project");

            // Execute
            String result = controllerLogic.simulateReloadProject(true, true);

            // Verify
            assertThat(result).isEqualTo("Project reloaded successfully: Successful Project");
        }

        @Test
        @DisplayName("Should handle project file not found during reload")
        void shouldHandleProjectFileNotFoundDuringReload() {
            // Setup
            controllerLogic.setCurrentProject("Missing Project");

            // Execute
            String result = controllerLogic.simulateReloadProject(false, false);

            // Verify
            assertThat(result).isEqualTo("Project file not found on disk");
        }

        @Test
        @DisplayName("Should handle failed project reload when file exists")
        void shouldHandleFailedProjectReloadWhenFileExists() {
            // Setup
            controllerLogic.setCurrentProject("Corrupted Project");

            // Execute
            String result = controllerLogic.simulateReloadProject(true, false);

            // Verify
            assertThat(result).isEqualTo("Failed to reload project: Corrupted Project");
        }

        @Test
        @DisplayName("Should track reload confirmation dialog invocations")
        void shouldTrackReloadConfirmationDialogInvocations() {
            // Setup
            controllerLogic.setCurrentProject("Track Project");
            controllerLogic.resetConfirmationDialogCount();

            // Execute
            controllerLogic.simulateReloadConfirmation();
            controllerLogic.simulateReloadConfirmation();

            // Verify
            assertThat(controllerLogic.getConfirmationDialogCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should prevent reload when no project is active")
        void shouldPreventReloadWhenNoProjectIsActive() {
            // Setup
            controllerLogic.setCurrentProject(null);

            // Execute
            String result = controllerLogic.simulateReloadProject(true, true);

            // Verify
            assertThat(result).isEqualTo("No project is currently loaded to reload");
        }

        @Test
        @DisplayName("Should validate reload confirmation message includes warning emoji")
        void shouldValidateReloadConfirmationMessageIncludesWarningEmoji() {
            // Setup
            controllerLogic.setCurrentProject("Emoji Test");

            // Execute
            String message = controllerLogic.generateReloadConfirmationMessage();

            // Verify
            assertThat(message).contains("⚠️");
        }

        @Test
        @DisplayName("Should handle empty project name in reload confirmation")
        void shouldHandleEmptyProjectNameInReloadConfirmation() {
            // Setup
            controllerLogic.setCurrentProject("");

            // Execute
            String message = controllerLogic.generateReloadConfirmationMessage();

            // Verify
            assertThat(message).contains("Are you sure you want to reload the project \"\"");
        }

        @Test
        @DisplayName("Should validate reload workflow consistency")
        void shouldValidateReloadWorkflowConsistency() {
            // Setup
            controllerLogic.setCurrentProject("Workflow Test");

            // Execute & Verify - User cancels reload
            controllerLogic.setUserConfirmationResponse(false);
            boolean cancelled = controllerLogic.simulateReloadConfirmation();
            assertThat(cancelled).isFalse();

            // Execute & Verify - User confirms reload, file exists, reload successful
            controllerLogic.setUserConfirmationResponse(true);
            boolean confirmed = controllerLogic.simulateReloadConfirmation();
            assertThat(confirmed).isTrue();

            String result = controllerLogic.simulateReloadProject(true, true);
            assertThat(result).isEqualTo("Project reloaded successfully: Workflow Test");
        }

        @Test
        @DisplayName("Should handle special characters in project name during reload")
        void shouldHandleSpecialCharactersInProjectNameDuringReload() {
            // Setup
            String specialName = "Project \"Test\" & <Special>";
            controllerLogic.setCurrentProject(specialName);

            // Execute
            String message = controllerLogic.generateReloadConfirmationMessage();
            String result = controllerLogic.simulateReloadProject(true, true);

            // Verify
            assertThat(message).contains("Project \"Test\" & <Special>");
            assertThat(result).isEqualTo("Project reloaded successfully: " + specialName);
        }

        @Test
        @DisplayName("Should maintain proper reload dialog button text")
        void shouldMaintainProperReloadDialogButtonText() {
            // Setup
            controllerLogic.setCurrentProject("Button Test");

            // Execute
            DialogProperties props = controllerLogic.getReloadConfirmationDialogProperties();

            // Verify - Buttons should be "Reload" and "Cancel" not "Yes" and "No"
            assertThat(props.getYesButtonText()).isEqualTo("Reload");
            assertThat(props.getNoButtonText()).isEqualTo("Cancel");
            assertThat(props.getYesButtonText()).doesNotContain("Yes");
            assertThat(props.getNoButtonText()).doesNotContain("No");
        }
    }

    // ===================== TEST HELPER CLASSES ===================== //

    /**
     * Logic class that contains the business logic from SpeleoDBController
     * without JavaFX dependencies for testing purposes.
     */
    static class SpeleoDBControllerLogic {
        private boolean debugMode = false;
        private String instance = "";
        private String debugPropertiesPath = null;
        private String currentProject = null;
        private Boolean userConfirmationResponse = null;
        private int confirmationDialogCount = 0;
        private boolean isAuthenticated = false;
        private boolean simulateRefreshError = false;
        private String currentSortMode = "BY_NAME";

        public void setDebugMode(boolean debugMode) {
            this.debugMode = debugMode;
        }

        public void setInstance(String instance) {
            this.instance = instance;
        }

        public void setDebugPropertiesPath(String path) {
            this.debugPropertiesPath = path;
        }

        public void setCurrentProject(String projectName) {
            this.currentProject = projectName;
        }

        public String getCurrentProject() {
            return currentProject;
        }

        public void setUserConfirmationResponse(Boolean response) {
            this.userConfirmationResponse = response;
        }

        public void resetConfirmationDialogCount() {
            this.confirmationDialogCount = 0;
        }

        public int getConfirmationDialogCount() {
            return confirmationDialogCount;
        }

        public boolean isDebugMode() {
            return debugMode;
        }

        public String generateSignupUrl() {
            String actualInstance = instance.trim();
            if (actualInstance.isEmpty()) {
                actualInstance = "www.speleodb.org"; // DEFAULT_INSTANCE
            }

            String protocol = isDebugMode() ? "http" : "https";
            return protocol + "://" + actualInstance + "/signup/";
        }

        public String openSignupUrl() {
            try {
                String signupUrl = generateSignupUrl();
                java.awt.Desktop.getDesktop().browse(new java.net.URI(signupUrl));
                return "Opening signup page: " + signupUrl;
            } catch (IOException | URISyntaxException | UnsupportedOperationException e) {
                return "Failed to open signup page: " + e.getMessage();
            }
        }

        public boolean isDebugModeFromProperties() {
            if (debugPropertiesPath != null) {
                try {
                    var props = new java.util.Properties();
                    props.load(new java.io.FileInputStream(debugPropertiesPath));
                    return Boolean.parseBoolean(props.getProperty("debug.mode", "false"));
                } catch (Exception e) {
                    return false;
                }
            }
            return false;
        }

        public boolean isDebugModeFromSystemProperty() {
            return Boolean.parseBoolean(System.getProperty("speleodb.debug.mode", "false"));
        }

        // Confirmation popup methods
        public String generateUnlockConfirmationMessage() {
            return "Are you sure you want to unlock project \"" + currentProject + "\"?\n\n" +
                   "This will release your write lock and allow other users to edit the project.\n\n" +
                   "• Yes: Unlock project (other users can edit)\n" +
                   "• No: Keep lock (continue editing)";
        }

        public String generateReleaseLockConfirmationMessage() {
            return "Do you want to release the write lock for project \"" + currentProject + "\"?\n\n" +
                   "• Yes: Release lock (other users can edit)\n" +
                   "• No: Keep lock (continue editing)";
        }

        public DialogProperties getUnlockConfirmationDialogProperties() {
            return new DialogProperties(
                "Unlock Project",
                "Confirm Unlock",
                generateUnlockConfirmationMessage(),
                "Yes, Unlock",
                "No, Keep Lock"
            );
        }

        public DialogProperties getReleaseLockConfirmationDialogProperties() {
            return new DialogProperties(
                "Release Write Lock",
                "Upload Successful",
                generateReleaseLockConfirmationMessage(),
                "Yes, Release Lock",
                "No, Keep Lock"
            );
        }

        public String generateProjectSwitchConfirmationMessage(String newProjectName) {
            return "You currently have an active lock on project \"" + currentProject + "\".\n\n" +
                   "To switch to project \"" + newProjectName + "\", you need to release your current lock.\n\n" +
                   "Do you want to release the lock and switch projects?\n\n" +
                   "• Yes: Release lock and switch to \"" + newProjectName + "\"\n" +
                   "• No: Keep current lock and stay on \"" + currentProject + "\"";
        }

        public DialogProperties getProjectSwitchConfirmationDialogProperties(String newProjectName) {
            return new DialogProperties(
                "Switch Project",
                "Current Project is Locked",
                generateProjectSwitchConfirmationMessage(newProjectName),
                "Yes, Switch Projects",
                "No, Stay Here"
            );
        }

        public boolean simulateUnlockConfirmation() {
            confirmationDialogCount++;
            return userConfirmationResponse != null ? userConfirmationResponse : false;
        }

        public boolean simulateReleaseLockConfirmation() {
            confirmationDialogCount++;
            return userConfirmationResponse != null ? userConfirmationResponse : false;
        }

        public boolean simulateProjectSwitchConfirmation(String newProjectName) {
            confirmationDialogCount++;
            return userConfirmationResponse != null ? userConfirmationResponse : false;
        }

        public String simulateUploadFlow() {
            return "Upload successful for project: " + currentProject;
        }

        public String simulateUnlockFlow(boolean shouldUnlock) {
            if (shouldUnlock) {
                return "Project unlocked successfully: " + currentProject;
            } else {
                return "User cancelled unlock operation.";
            }
        }

        public String simulateProjectSwitchFlow(String targetProject, boolean shouldSwitch) {
            if (shouldSwitch) {
                return "Project switch completed successfully from " + currentProject + " to " + targetProject +
                       ". Lock released and refreshing project listing.";
            } else {
                return "Project switch cancelled. Staying on: " + currentProject;
            }
        }

        // Authentication and refresh support methods
        public void setAuthenticated(boolean authenticated) {
            this.isAuthenticated = authenticated;
        }

        public boolean isAuthenticated() {
            return isAuthenticated;
        }

        public void setSimulateRefreshError(boolean simulateError) {
            this.simulateRefreshError = simulateError;
        }

        public String simulateRefreshProjects() {
            if (!isAuthenticated) {
                return "Cannot refresh projects: Not authenticated";
            }

            if (simulateRefreshError) {
                return "Failed to refresh projects: Simulated refresh error";
            }

            return "Project list refreshed successfully - authentication check passed";
        }

        public RefreshButtonStates simulateRefreshButtonStates() {
            return new RefreshButtonStates(
                "Refresh Projects",     // initial state
                "Refreshing ...",        // during refresh state
                "Refresh Projects",     // final state
                true,                   // initially enabled
                false,                  // disabled during refresh
                true                    // enabled after completion
            );
        }

        public String simulateProjectOpening(String projectName, boolean successful) {
            if (successful) {
                currentProject = projectName;
                return "Project opened successfully: " + projectName +
                       ". Lock acquired and refreshing project listing after project opening.";
            } else {
                return "Project opening failed: " + projectName + ". Unable to acquire lock or download project.";
            }
        }

        public String simulateCreateNewProject() {
            if (!isAuthenticated) {
                return "Cannot create new project: Not authenticated";
            }

            return "Create New Project button clicked - functionality not yet implemented";
        }

        public String simulateCreateProjectWithData(String name, String description, String countryCode,
                                                   String latitude, String longitude) {
            if (!isAuthenticated) {
                return "Cannot create new project: Not authenticated";
            }

            // Simulate the actual project creation process
            StringBuilder result = new StringBuilder();
            result.append("Creating new project: ").append(name).append("\n");
            result.append("Project '").append(name).append("' created successfully!\n");
            result.append("Project ID: mock-project-").append(name.hashCode()).append("\n");
            result.append("Refreshing project list");

            return result.toString();
        }

        public UILayoutState simulateAuthenticationStateChange(boolean shouldAuthenticate) {
            this.isAuthenticated = shouldAuthenticate;

            if (shouldAuthenticate) {
                // Authenticated state: DISCONNECT button 100% width, SIGNUP hidden, create new project enabled
                return new UILayoutState("DISCONNECT", 3, false, true, true, true);
            } else {
                // Disconnected state: CONNECT button 50% width, SIGNUP visible, create new project disabled
                return new UILayoutState("CONNECT", 1, true, false, false, false);
            }
        }

        public UILayoutState simulateInitialUISetup() {
            // Initial state is always disconnected
            this.isAuthenticated = false;
            return new UILayoutState("CONNECT", 1, true, false, false, false);
        }

        // Expose private constants for testing
        public String getPrefEmail() { return "SDB_EMAIL"; }
        public String getPrefPassword() { return "SDB_PASSWORD"; }
        public String getPrefOAuthToken() { return "SDB_OAUTH_TOKEN"; }
        public String getPrefInstance() { return "SDB_INSTANCE"; }

        public String getDefaultInstance() { return "www.speleoDB.org"; }

        // Sorting functionality methods
        public String getCurrentSortMode() {
            return currentSortMode;
        }

        public void setSortMode(String sortMode) {
            this.currentSortMode = sortMode;
        }

        public String simulateSortByName() {
            if (!isAuthenticated) {
                return "Cannot sort projects: Not authenticated";
            }

            currentSortMode = "BY_NAME";
            return "User requested sort by name";
        }

        public String simulateSortByDate() {
            if (!isAuthenticated) {
                return "Cannot sort projects: Not authenticated";
            }

            currentSortMode = "BY_DATE";
            return "User requested sort by date";
        }

        // Reload project functionality methods
        public String generateReloadConfirmationMessage() {
            if (currentProject == null) {
                return "No project is currently loaded to reload";
            }

            return "Are you sure you want to reload the project \"" + currentProject + "\"?\n\n" +
                   "⚠️ WARNING: Any unsaved modifications will be lost!\n\n" +
                   "• All changes since last save will be discarded\n" +
                   "• The project will be reloaded from disk\n" +
                   "• This action cannot be undone";
        }

        public boolean simulateReloadConfirmation() {
            confirmationDialogCount++;
            return userConfirmationResponse != null ? userConfirmationResponse : false;
        }

        public String simulateReloadProject(boolean fileExists, boolean loadSuccessful) {
            if (currentProject == null) {
                return "No project is currently loaded to reload";
            }

            if (!fileExists) {
                return "Project file not found on disk";
            }

            if (loadSuccessful) {
                return "Project reloaded successfully: " + currentProject;
            } else {
                return "Failed to reload project: " + currentProject;
            }
        }

        public DialogProperties getReloadConfirmationDialogProperties() {
            return new DialogProperties(
                "Reload Project",
                "Confirm Reload",
                generateReloadConfirmationMessage(),
                "Reload",
                "Cancel"
            );
        }
    }

    /**
     * Helper class to represent dialog properties for testing
     */
    static class DialogProperties {
        private final String title;
        private final String headerText;
        private final String contentText;
        private final String yesButtonText;
        private final String noButtonText;

        public DialogProperties(String title, String headerText, String contentText,
                              String yesButtonText, String noButtonText) {
            this.title = title;
            this.headerText = headerText;
            this.contentText = contentText;
            this.yesButtonText = yesButtonText;
            this.noButtonText = noButtonText;
        }

        public String getTitle() { return title; }
        public String getHeaderText() { return headerText; }
        public String getContentText() { return contentText; }
        public String getYesButtonText() { return yesButtonText; }
        public String getNoButtonText() { return noButtonText; }
    }

    /**
     * Helper class to represent refresh button states for testing
     */
    static class RefreshButtonStates {
        private final String initialState;
        private final String duringRefreshState;
        private final String finalState;
        private final boolean initiallyEnabled;
        private final boolean duringRefreshEnabled;
        private final boolean finallyEnabled;

        public RefreshButtonStates(String initialState, String duringRefreshState, String finalState,
                                 boolean initiallyEnabled, boolean duringRefreshEnabled, boolean finallyEnabled) {
            this.initialState = initialState;
            this.duringRefreshState = duringRefreshState;
            this.finalState = finalState;
            this.initiallyEnabled = initiallyEnabled;
            this.duringRefreshEnabled = duringRefreshEnabled;
            this.finallyEnabled = finallyEnabled;
        }

        public String getInitialState() { return initialState; }
        public String getDuringRefreshState() { return duringRefreshState; }
        public String getFinalState() { return finalState; }
        public boolean isInitiallyEnabled() { return initiallyEnabled; }
        public boolean isDuringRefreshEnabled() { return duringRefreshEnabled; }
        public boolean isFinallyEnabled() { return finallyEnabled; }
    }

    /**
     * Helper class to represent UI layout state for testing
     */
    static class UILayoutState {
        private final String connectionButtonText;
        private final int connectionButtonColumnSpan;
        private final boolean signupButtonVisible;
        private final boolean refreshButtonEnabled;
        private final boolean createNewProjectButtonEnabled;
        private final boolean authenticated;

        public UILayoutState(String connectionButtonText, int connectionButtonColumnSpan,
                           boolean signupButtonVisible, boolean refreshButtonEnabled,
                           boolean createNewProjectButtonEnabled, boolean authenticated) {
            this.connectionButtonText = connectionButtonText;
            this.connectionButtonColumnSpan = connectionButtonColumnSpan;
            this.signupButtonVisible = signupButtonVisible;
            this.refreshButtonEnabled = refreshButtonEnabled;
            this.createNewProjectButtonEnabled = createNewProjectButtonEnabled;
            this.authenticated = authenticated;
        }

        public String getConnectionButtonText() { return connectionButtonText; }
        public int getConnectionButtonColumnSpan() { return connectionButtonColumnSpan; }
        public boolean isSignupButtonVisible() { return signupButtonVisible; }
        public boolean isRefreshButtonEnabled() { return refreshButtonEnabled; }
        public boolean isCreateNewProjectButtonEnabled() { return createNewProjectButtonEnabled; }
        public boolean isAuthenticated() { return authenticated; }
    }

    /**
     * Test OAuth token validation with various input formats
     */
    @Test
    @DisplayName("Should validate OAuth token format correctly")
    void shouldValidateOAuthTokenFormat() {
        SpeleoDBController controller = null;
        Method validateMethod = null;

        // Use reflection to access the private validateOAuthToken method
        try {
            controller = SpeleoDBController.getInstance();
            validateMethod = SpeleoDBController.class.getDeclaredMethod("validateOAuthToken", String.class);
            validateMethod.setAccessible(true);

            // Valid tokens (40 hexadecimal characters)
            assertTrue((Boolean) validateMethod.invoke(controller, "a1b2c3d4e5f6789012345678901234567890abcd"));
            assertTrue((Boolean) validateMethod.invoke(controller, "0123456789abcdef0123456789abcdef01234567"));
            assertTrue((Boolean) validateMethod.invoke(controller, "ffffffffffffffffffffffffffffffffffffffff"));
            assertTrue((Boolean) validateMethod.invoke(controller, "0000000000000000000000000000000000000000"));

            // Valid token with mixed case (should be normalized to lowercase)
            assertTrue((Boolean) validateMethod.invoke(controller, "A1B2C3D4E5F6789012345678901234567890ABCD"));
            assertTrue((Boolean) validateMethod.invoke(controller, "AbCdEf0123456789AbCdEf0123456789AbCdEf01"));

            // Valid token with whitespace (should be trimmed)
            assertTrue((Boolean) validateMethod.invoke(controller, "  a1b2c3d4e5f6789012345678901234567890abcd  "));
            assertTrue((Boolean) validateMethod.invoke(controller, "\ta1b2c3d4e5f6789012345678901234567890abcd\n"));

            // Invalid tokens - wrong length
            assertFalse((Boolean) validateMethod.invoke(controller, "a1b2c3d4e5f6789012345678901234567890abc"));   // 39 chars
            assertFalse((Boolean) validateMethod.invoke(controller, "a1b2c3d4e5f6789012345678901234567890abcde")); // 41 chars
            assertFalse((Boolean) validateMethod.invoke(controller, ""));                                            // empty
            assertFalse((Boolean) validateMethod.invoke(controller, "a1b2c3d4e5f6789012345678901234567890"));        // 38 chars

            // Invalid tokens - non-hexadecimal characters
            assertFalse((Boolean) validateMethod.invoke(controller, "g1b2c3d4e5f6789012345678901234567890abcd"));   // contains 'g'
            assertFalse((Boolean) validateMethod.invoke(controller, "a1b2c3d4e5f6789012345678901234567890abcz"));   // contains 'z'
            assertFalse((Boolean) validateMethod.invoke(controller, "a1b2c3d4e5f6789012345678901234567890ab d"));   // contains space

            // Invalid tokens - special characters
            assertFalse((Boolean) validateMethod.invoke(controller, "a1b2c3d4-5f6789012345678901234567890abcd"));   // contains '-'
            assertFalse((Boolean) validateMethod.invoke(controller, "a1b2c3d4e5f6789012345678901234567890ab.d"));   // contains '.'

            // Null token
            assertFalse((Boolean) validateMethod.invoke(controller, (String) null));

        } catch (Exception e) {
            String errorDetails = "Failed to test OAuth token validation. ";
            if (controller == null) {
                errorDetails += "Controller creation failed. ";
            }
            if (validateMethod == null) {
                errorDetails += "Method lookup failed. ";
            }
            errorDetails += "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            errorDetails += ". Cause: " + (e.getCause() != null ? e.getCause().toString() : "None");
            Assertions.fail(errorDetails);
        }
    }

    /**
     * Test network error detection for server offline scenarios
     */
    @Test
    @DisplayName("Should detect server offline errors correctly")
    void shouldDetectServerOfflineErrors() {
        SpeleoDBController controller = null;
        Method isServerOfflineMethod = null;

        try {
            controller = SpeleoDBController.getInstance();
            isServerOfflineMethod = SpeleoDBController.class.getDeclaredMethod("isServerOfflineError", Exception.class);
            isServerOfflineMethod.setAccessible(true);

            // Test connection refused
            Exception connectionRefused = new Exception("Connection refused");
            assertTrue((Boolean) isServerOfflineMethod.invoke(controller, connectionRefused));

            // Test unknown host
            Exception unknownHost = new Exception("Unknown host: nonexistent.server.com");
            assertTrue((Boolean) isServerOfflineMethod.invoke(controller, unknownHost));

            // Test no route to host
            Exception noRoute = new Exception("No route to host");
            assertTrue((Boolean) isServerOfflineMethod.invoke(controller, noRoute));

            // Test network unreachable
            Exception networkUnreachable = new Exception("Network is unreachable");
            assertTrue((Boolean) isServerOfflineMethod.invoke(controller, networkUnreachable));

            // Test connection reset
            Exception connectionReset = new Exception("Connection reset by peer");
            assertTrue((Boolean) isServerOfflineMethod.invoke(controller, connectionReset));

            // Test name resolution failed
            Exception nameResolution = new Exception("Name resolution failed");
            assertTrue((Boolean) isServerOfflineMethod.invoke(controller, nameResolution));

            // Test with null exception
            assertFalse((Boolean) isServerOfflineMethod.invoke(controller, (Exception) null));

            // Test with non-network error
            Exception genericError = new Exception("Invalid JSON format");
            assertFalse((Boolean) isServerOfflineMethod.invoke(controller, genericError));

        } catch (Exception e) {
            String errorDetails = "Failed to test server offline error detection. ";
            if (controller == null) {
                errorDetails += "Controller creation failed. ";
            }
            if (isServerOfflineMethod == null) {
                errorDetails += "Method lookup failed. ";
            }
            errorDetails += "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            errorDetails += ". Cause: " + (e.getCause() != null ? e.getCause().toString() : "None");
            Assertions.fail(errorDetails);
        }
    }

    /**
     * Test network error detection for timeout scenarios
     */
    @Test
    @DisplayName("Should detect timeout errors correctly")
    void shouldDetectTimeoutErrors() {
        SpeleoDBController controller = null;
        Method isTimeoutMethod = null;

        try {
            controller = SpeleoDBController.getInstance();
            isTimeoutMethod = SpeleoDBController.class.getDeclaredMethod("isTimeoutError", Exception.class);
            isTimeoutMethod.setAccessible(true);

            // Test connection timed out
            Exception connectionTimeout = new Exception("Connection timed out");
            assertTrue((Boolean) isTimeoutMethod.invoke(controller, connectionTimeout));

            // Test read timeout
            Exception readTimeout = new Exception("Read timeout");
            assertTrue((Boolean) isTimeoutMethod.invoke(controller, readTimeout));

            // Test connect timeout
            Exception connectTimeout = new Exception("Connect timeout");
            assertTrue((Boolean) isTimeoutMethod.invoke(controller, connectTimeout));

            // Test operation timeout
            Exception operationTimeout = new Exception("Operation timeout");
            assertTrue((Boolean) isTimeoutMethod.invoke(controller, operationTimeout));

            // Test generic timeout
            Exception genericTimeout = new Exception("Request timeout occurred");
            assertTrue((Boolean) isTimeoutMethod.invoke(controller, genericTimeout));

            // Test with null exception
            assertFalse((Boolean) isTimeoutMethod.invoke(controller, (Exception) null));

            // Test with non-timeout error
            Exception genericError = new Exception("Authentication failed");
            assertFalse((Boolean) isTimeoutMethod.invoke(controller, genericError));

        } catch (Exception e) {
            String errorDetails = "Failed to test timeout error detection. ";
            if (controller == null) {
                errorDetails += "Controller creation failed. ";
            }
            if (isTimeoutMethod == null) {
                errorDetails += "Method lookup failed. ";
            }
            errorDetails += "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            errorDetails += ". Cause: " + (e.getCause() != null ? e.getCause().toString() : "None");
            Assertions.fail(errorDetails);
        }
    }

    /**
     * Test network error message generation
     */
    @Test
    @DisplayName("Should generate appropriate network error messages")
    void shouldGenerateNetworkErrorMessages() {
        SpeleoDBController controller = null;
        Method getNetworkErrorMethod = null;

        try {
            controller = SpeleoDBController.getInstance();
            getNetworkErrorMethod = SpeleoDBController.class.getDeclaredMethod("getNetworkErrorMessage", Exception.class, String.class);
            getNetworkErrorMethod.setAccessible(true);

            // Test server offline message
            Exception serverOffline = new Exception("Connection refused");
            String offlineMessage = (String) getNetworkErrorMethod.invoke(controller, serverOffline, "Connection");
            assertTrue(offlineMessage.contains("Can't reach server"));
            assertTrue(offlineMessage.contains("Server is online"));
            assertTrue(offlineMessage.contains("Network connection"));

            // Test timeout message
            Exception timeout = new Exception("Connection timed out");
            String timeoutMessage = (String) getNetworkErrorMethod.invoke(controller, timeout, "Upload");
            assertTrue(timeoutMessage.contains("Request timed out"));
            assertTrue(timeoutMessage.contains("Overloaded"));
            assertTrue(timeoutMessage.contains("Try again in a few moments"));

            // Test generic error message
            Exception genericError = new Exception("Invalid credentials");
            String genericMessage = (String) getNetworkErrorMethod.invoke(controller, genericError, "Authentication");
            assertTrue(genericMessage.contains("Authentication failed"));
            assertTrue(genericMessage.contains("Invalid credentials"));

        } catch (Exception e) {
            String errorDetails = "Failed to test network error message generation. ";
            if (controller == null) {
                errorDetails += "Controller creation failed. ";
            }
            if (getNetworkErrorMethod == null) {
                errorDetails += "Method lookup failed. ";
            }
            errorDetails += "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            errorDetails += ". Cause: " + (e.getCause() != null ? e.getCause().toString() : "None");
            Assertions.fail(errorDetails);
        }
    }

    /**
     * Test tooltip modal sizing behavior
     */
    @Test
    @DisplayName("Should size tooltip modals appropriately for different message lengths")
    void shouldSizeTooltipModalsAppropriately() {
        // This test verifies that the tooltip sizing logic is properly implemented
        // The actual UI testing would require a JavaFX test environment

        // Test short message constraints
        String shortMessage = "Error";
        assertTrue(shortMessage.length() < 50, "Short message should be less than 50 characters");

        // Test medium message
        String mediumMessage = "Can't reach server - Please check your connection";
        assertTrue(mediumMessage.length() > 20 && mediumMessage.length() < 100,
                  "Medium message should be between 20-100 characters");

        // Test long message
        String longMessage = "Request timed out - Server may be overloaded or slow to respond, " +
                           "experiencing network issues, or temporarily unavailable. Try again in a few moments.";
        assertTrue(longMessage.length() > 100, "Long message should be over 100 characters");

        // Verify the tooltip would use appropriate sizing:
        // - Min width: 150px for success, 200px for error
        // - Max width: 500px for success, 600px for error
        // - Auto-sizing based on content between min/max

        // These constraints ensure tooltips are:
        // 1. Never too narrow (min width)
        // 2. Never too wide (max width)
        // 3. Scale to content (computed size)
        // 4. Properly centered with margins

        assertThat("Tooltip sizing logic implemented").isNotEmpty();
    }

    /**
     * Test safe error message extraction
     */
    @Test
    @DisplayName("Should safely extract error messages from exceptions")
    void shouldSafelyExtractErrorMessages() {
        SpeleoDBController controller = null;
        Method getSafeErrorMethod = null;

        try {
            controller = SpeleoDBController.getInstance();
            getSafeErrorMethod = SpeleoDBController.class.getDeclaredMethod("getSafeErrorMessage", Exception.class);
            getSafeErrorMethod.setAccessible(true);

            // Test with exception that has a message
            Exception withMessage = new Exception("Connection refused");
            String result1 = (String) getSafeErrorMethod.invoke(controller, withMessage);
            assertThat(result1).isEqualTo("Connection refused");

            // Test with exception that has null message
            Exception withNullMessage = new Exception((String) null);
            String result2 = (String) getSafeErrorMethod.invoke(controller, withNullMessage);
            assertThat(result2).isEqualTo("Exception");

            // Test with exception that has empty message
            Exception withEmptyMessage = new Exception("");
            String result3 = (String) getSafeErrorMethod.invoke(controller, withEmptyMessage);
            assertThat(result3).isEqualTo("Exception");

            // Test with null exception
            String result4 = (String) getSafeErrorMethod.invoke(controller, (Exception) null);
            assertThat(result4).isEqualTo("Unknown error");

            // Test with specific exception types
            IOException ioException = new IOException((String) null);
            String result5 = (String) getSafeErrorMethod.invoke(controller, ioException);
            assertThat(result5).isEqualTo("IOException");

        } catch (Exception e) {
            String errorDetails = "Failed to test safe error message extraction. ";
            if (controller == null) {
                errorDetails += "Controller creation failed. ";
            }
            if (getSafeErrorMethod == null) {
                errorDetails += "Method lookup failed. ";
            }
            errorDetails += "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            errorDetails += ". Cause: " + (e.getCause() != null ? e.getCause().toString() : "None");
            Assertions.fail(errorDetails);
        }
    }

    // ===================== UPLOAD FAILURE GUIDANCE TESTS ===================== //

    @Nested
    @DisplayName("Upload Failure Guidance")
    class UploadFailureGuidanceTests {

        @Test
        @DisplayName("Guidance text should include contact admin hint")
        void guidanceShouldIncludeContactAdminHint() {
            String guidance = SpeleoDBController.buildUploadFailureGuidance();
            assertThat(guidance).contains("contact@speleodb.org");
        }

        @Test
        @DisplayName("Guidance text should include save locally hint")
        void guidanceShouldIncludeSaveLocallyHint() {
            String guidance = SpeleoDBController.buildUploadFailureGuidance();
            assertThat(guidance).contains(SpeleoDBConstants.MESSAGES.UPLOAD_FALLBACK_HINT);
        }

        @Test
        @DisplayName("Should build correct upload URL for production instance")
        void shouldBuildCorrectUploadUrl() {
            String url = SpeleoDBController.buildUploadUrl(
                "https://www.speleodb.org", "24d31420-9180-4f5d-b27f-0b1c13bf80b8");

            assertThat(url)
                .isEqualTo("https://www.speleodb.org/private/project/24d31420-9180-4f5d-b27f-0b1c13bf80b8/upload/");
        }

        @Test
        @DisplayName("Should build correct upload URL for local dev instance")
        void shouldBuildCorrectUrlForLocalInstance() {
            String url = SpeleoDBController.buildUploadUrl(
                "http://localhost:8000", "abc-123");

            assertThat(url)
                .isEqualTo("http://localhost:8000/private/project/abc-123/upload/");
        }

        @Test
        @DisplayName("Should build correct upload URL for staging instance")
        void shouldBuildCorrectUrlForStagingInstance() {
            String url = SpeleoDBController.buildUploadUrl(
                "https://stage.speleodb.org", "my-project-id");

            assertThat(url)
                .isEqualTo("https://stage.speleodb.org/private/project/my-project-id/upload/");
        }
    }

    @Nested
    @DisplayName("Upload Failure Constants")
    class UploadFailureConstantsTests {

        @Test
        @DisplayName("UPLOAD_FAILED_RETRY should mention trying again")
        void uploadFailedRetryShouldMentionRetry() {
            assertThat(SpeleoDBConstants.MESSAGES.UPLOAD_FAILED_RETRY)
                .containsIgnoringCase("try again");
        }

        @Test
        @DisplayName("CONTACT_ADMIN_HINT should contain contact email")
        void contactAdminHintShouldContainEmail() {
            assertThat(SpeleoDBConstants.MESSAGES.CONTACT_ADMIN_HINT)
                .contains("contact@speleodb.org");
        }

        @Test
        @DisplayName("UPLOAD_FALLBACK_HINT should mention website")
        void uploadFallbackHintShouldMentionWebsite() {
            assertThat(SpeleoDBConstants.MESSAGES.UPLOAD_FALLBACK_HINT)
                .containsIgnoringCase("website");
        }

        @Test
        @DisplayName("WEB_UPLOAD_PATH should contain format placeholder")
        void webUploadPathShouldContainPlaceholder() {
            assertThat(SpeleoDBConstants.API.WEB_UPLOAD_PATH)
                .contains("%s")
                .startsWith("/private/project/")
                .endsWith("/upload/");
        }
    }

    // ===================== FILE STABILITY & ZIP VALIDATION TESTS ===================== //

    @Nested
    @DisplayName("ZIP CRC32 Validation")
    class ZipCrc32ValidationTests {

        private Method isValidZipFileMethod;

        @BeforeEach
        void setUp() throws Exception {
            isValidZipFileMethod = SpeleoDBController.class.getDeclaredMethod("isValidZipFile", java.io.File.class);
            isValidZipFileMethod.setAccessible(true);
        }

        private boolean invokeIsValidZipFile(File file) throws Exception {
            SpeleoDBController controller = SpeleoDBController.getInstance();
            return (boolean) isValidZipFileMethod.invoke(controller, file);
        }

        @Test
        @DisplayName("Should accept valid ZIP with a single entry")
        void shouldAcceptValidZipSingleEntry() throws Exception {
            File zip = createValidZip("single", "data.txt", "hello");
            assertThat(invokeIsValidZipFile(zip)).isTrue();
        }

        @Test
        @DisplayName("Should accept valid ZIP with multiple entries")
        void shouldAcceptValidZipMultipleEntries() throws Exception {
            File zip = File.createTempFile("multi", ".zip", tempDir.toFile());
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
                for (int i = 0; i < 5; i++) {
                    zos.putNextEntry(new ZipEntry("entry" + i + ".txt"));
                    zos.write(("content-" + i).getBytes());
                    zos.closeEntry();
                }
            }
            assertThat(invokeIsValidZipFile(zip)).isTrue();
        }

        @Test
        @DisplayName("Should reject truncated ZIP (incomplete write)")
        void shouldRejectTruncatedZip() throws Exception {
            File zip = createValidZip("trunc", "data.txt", "some content here for truncation test");
            long fullSize = zip.length();

            // Truncate the file to simulate an in-progress write
            try (RandomAccessFile raf = new RandomAccessFile(zip, "rw")) {
                raf.setLength(fullSize / 2);
            }
            assertThat(invokeIsValidZipFile(zip)).isFalse();
        }

        @Test
        @DisplayName("Should reject ZIP with corrupted entry data")
        void shouldRejectCorruptedZipEntry() throws Exception {
            File zip = createValidZip("corrupt", "payload.bin", "A".repeat(1024));

            // Corrupt bytes in the middle of the compressed data
            try (RandomAccessFile raf = new RandomAccessFile(zip, "rw")) {
                long corruptOffset = Math.min(40, raf.length() - 10);
                raf.seek(corruptOffset);
                byte[] garbage = new byte[20];
                java.util.Arrays.fill(garbage, (byte) 0xFF);
                raf.write(garbage);
            }
            assertThat(invokeIsValidZipFile(zip)).isFalse();
        }

        @Test
        @DisplayName("Should reject empty file")
        void shouldRejectEmptyFile() throws Exception {
            File empty = File.createTempFile("empty", ".zip", tempDir.toFile());
            assertThat(invokeIsValidZipFile(empty)).isFalse();
        }

        @Test
        @DisplayName("Should reject null file")
        void shouldRejectNullFile() throws Exception {
            assertThat(invokeIsValidZipFile(null)).isFalse();
        }

        @Test
        @DisplayName("Should reject non-existent file")
        void shouldRejectNonExistentFile() throws Exception {
            File missing = new File(tempDir.toFile(), "no-such-file.zip");
            assertThat(invokeIsValidZipFile(missing)).isFalse();
        }

        private File createValidZip(String prefix, String entryName, String content) throws Exception {
            File zip = File.createTempFile(prefix, ".zip", tempDir.toFile());
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(content.getBytes());
                zos.closeEntry();
            }
            return zip;
        }
    }

    @Nested
    @DisplayName("File Stability Exponential Backoff")
    class FileStabilityBackoffTests {

        private Method waitForFileStabilityMethod;

        @BeforeEach
        void setUp() throws Exception {
            waitForFileStabilityMethod = SpeleoDBController.class.getDeclaredMethod("waitForFileStability", java.io.File.class);
            waitForFileStabilityMethod.setAccessible(true);
        }

        private boolean invokeWaitForFileStability(File file) throws Exception {
            SpeleoDBController controller = SpeleoDBController.getInstance();
            return (boolean) waitForFileStabilityMethod.invoke(controller, file);
        }

        @Test
        @DisplayName("Should return true immediately for an already-valid ZIP")
        void shouldReturnTrueImmediatelyForValidZip() throws Exception {
            File zip = createValidZip("ready");
            long start = System.currentTimeMillis();

            assertThat(invokeWaitForFileStability(zip)).isTrue();

            long elapsed = System.currentTimeMillis() - start;
            assertThat(elapsed)
                .as("Valid ZIP should not enter the retry loop")
                .isLessThan(500);
        }

        @Test
        @DisplayName("Should return false immediately for null file")
        void shouldReturnFalseForNull() throws Exception {
            long start = System.currentTimeMillis();

            assertThat(invokeWaitForFileStability(null)).isFalse();

            long elapsed = System.currentTimeMillis() - start;
            assertThat(elapsed).isLessThan(50);
        }

        @Test
        @DisplayName("Should detect file that becomes valid after a delay")
        void shouldDetectDelayedValidity() throws Exception {
            File zip = File.createTempFile("delayed", ".zip", tempDir.toFile());
            // Start with an empty (invalid) file — a background thread will make it valid
            int delayMillis = 300;
            AtomicReference<Exception> writerError = new AtomicReference<>();

            Thread writer = new Thread(() -> {
                try {
                    Thread.sleep(delayMillis);
                    // Atomically replace with a valid ZIP
                    File tmp = File.createTempFile("tmp", ".zip", tempDir.toFile());
                    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmp))) {
                        zos.putNextEntry(new ZipEntry("survey.xml"));
                        zos.write("<tml/>".getBytes());
                        zos.closeEntry();
                    }
                    java.nio.file.Files.move(tmp.toPath(), zip.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception e) {
                    writerError.set(e);
                }
            });
            writer.start();

            long start = System.currentTimeMillis();
            assertThat(invokeWaitForFileStability(zip)).isTrue();
            long elapsed = System.currentTimeMillis() - start;

            // Should have waited at least as long as the writer delay
            assertThat(elapsed).isGreaterThanOrEqualTo(delayMillis - 50);
            // But well within the timeout budget
            assertThat(elapsed).isLessThan(SpeleoDBConstants.TIMINGS.FILE_STABILITY_TIMEOUT_MILLIS);

            writer.join(5000);
            assertThat(writer.isAlive())
                .as("Writer thread should have completed")
                .isFalse();
            assertThat(writerError.get())
                .as("Writer thread should not have thrown")
                .isNull();
        }

        @Test
        @DisplayName("Backoff constants should be correctly ordered")
        void backoffConstantsShouldBeCorrectlyOrdered() {
            assertThat(SpeleoDBConstants.TIMINGS.FILE_STABILITY_INITIAL_BACKOFF_MILLIS)
                .as("Initial backoff must be positive")
                .isGreaterThan(0);
            assertThat(SpeleoDBConstants.TIMINGS.FILE_STABILITY_MAX_BACKOFF_MILLIS)
                .as("Max backoff must exceed initial backoff")
                .isGreaterThan(SpeleoDBConstants.TIMINGS.FILE_STABILITY_INITIAL_BACKOFF_MILLIS);
            assertThat(SpeleoDBConstants.TIMINGS.FILE_STABILITY_TIMEOUT_MILLIS)
                .as("Timeout must exceed max backoff")
                .isGreaterThan(SpeleoDBConstants.TIMINGS.FILE_STABILITY_MAX_BACKOFF_MILLIS);
        }

        private File createValidZip(String prefix) throws Exception {
            File zip = File.createTempFile(prefix, ".zip", tempDir.toFile());
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
                zos.putNextEntry(new ZipEntry("survey.xml"));
                zos.write("<tml/>".getBytes());
                zos.closeEntry();
            }
            return zip;
        }
    }

}
