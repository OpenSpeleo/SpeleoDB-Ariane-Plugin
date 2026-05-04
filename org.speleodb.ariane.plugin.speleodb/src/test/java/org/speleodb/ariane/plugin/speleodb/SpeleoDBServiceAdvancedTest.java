package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.PATHS;

import jakarta.json.JsonObject;

/**
 * Advanced tests for SpeleoDBService to improve test coverage.
 * Tests edge cases and utility methods.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpeleoDB Service Advanced Tests")
class SpeleoDBServiceAdvancedTest {

    private SpeleoDBService service;
    private TestableSpeleoDBController controller;

    @BeforeEach
    void setUp() {
        controller = new TestableSpeleoDBController();
        service = new SpeleoDBService(controller);
    }

    @Nested
    @DisplayName("Constants and File Operations")
    class ConstantsAndFileOperationsTests {


        @Test
        @DisplayName("Should generate correct file paths")
        void shouldGenerateCorrectFilePaths() {
            String projectId = "test-123";
            Path expectedPath = Paths.get(PATHS.SDB_PROJECT_DIR, projectId + PATHS.TML_FILE_EXTENSION);

            // This tests the internal path generation logic
            assertThat(expectedPath.toString()).endsWith(".ariane" + java.io.File.separator + "speleodb" + java.io.File.separator + "projects" + java.io.File.separator + "test-123.tml");
        }

    }

    @Nested
    @DisplayName("Authentication State Tests")
    class AuthenticationStateTests {

        @Test
        @DisplayName("Should handle initial unauthenticated state")
        void shouldHandleInitialUnauthenticatedState() {
            // Service starts unauthenticated
            assertThat(service.isAuthenticated()).isFalse();
        }

        @Test
        @DisplayName("Should throw IllegalStateException when getting instance while unauthenticated")
        void shouldThrowIllegalStateExceptionWhenGettingInstanceWhileUnauthenticated() {
            assertThat(service.isAuthenticated()).isFalse();

            assertThatThrownBy(() -> service.getSDBInstance())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not authenticated");
        }

        @Test
        @DisplayName("Should handle logout state clearing")
        void shouldHandleLogoutStateClearing() {
            // Even if not authenticated, logout should not throw
            assertThatCode(() -> service.logout())
                .doesNotThrowAnyException();

            // Should still be unauthenticated
            assertThat(service.isAuthenticated()).isFalse();
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle operations on unauthenticated service")
        void shouldHandleOperationsOnUnauthenticatedService() {
            assertThat(service.isAuthenticated()).isFalse();

            // All these operations should fail gracefully or throw appropriate exceptions
            assertThatThrownBy(() -> service.getSDBInstance())
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Should handle multiple logout calls")
        void shouldHandleMultipleLogoutCalls() {
            // Multiple logout calls should not cause issues
            assertThatCode(() -> {
                service.logout();
                service.logout();
                service.logout();
            }).doesNotThrowAnyException();

            assertThat(service.isAuthenticated()).isFalse();
        }
    }

    // Helper test class that exposes private methods for testing
    static class TestableSpeleoDBController extends SpeleoDBController {
        private JsonObject currentProject = null;

        // For testing purposes
        public String lastLoggedMessage = "";

        public TestableSpeleoDBController() {
            super(true); // Use protected constructor for testing
        }

        public void setCurrentProject(JsonObject project) {
            this.currentProject = project;
        }

        @Override
        public boolean hasActiveProjectLock() {
            return currentProject != null;
        }

        @Override
        public String getCurrentProjectName() {
            return currentProject != null ? currentProject.getString("name") : null;
        }

        @Override
        public void appendToUILog(String message) {
            lastLoggedMessage = message;
        }
    }

}
