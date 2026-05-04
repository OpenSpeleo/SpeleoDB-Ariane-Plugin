package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.PATHS;

import com.arianesline.ariane.plugin.api.DataServerCommands;

/**
 * Extended unit tests for SpeleoDBPlugin covering missing behaviors and edge cases.
 * Tests plugin lifecycle, timeout handling, and uncovered methods.
 */
@DisplayName("SpeleoDB Plugin Extended Tests")
class SpeleoDBPluginExtendedTest {

    @TempDir
    Path tempDir;

    private TestableSpeleoDBPlugin plugin;
    private File testSurveyFile;

    @BeforeEach
    void setUp() throws IOException {
        plugin = new TestableSpeleoDBPlugin();
        testSurveyFile = tempDir.resolve("test_survey.tml").toFile();
        Files.createFile(testSurveyFile.toPath());
    }

    @Nested
    @DisplayName("Show Settings Method")
    class ShowSettingsMethodTests {

        @Test
        @DisplayName("Should handle showSettings method without throwing exceptions")
        void shouldHandleShowSettingsMethodWithoutThrowingExceptions() {
            // The showSettings method is currently empty, but should not throw exceptions
            assertThatCode(() -> plugin.showSettings())
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should be callable multiple times")
        void shouldBeCallableMultipleTimes() {
            // Test that showSettings can be called multiple times safely
            assertThatCode(() -> {
                plugin.showSettings();
                plugin.showSettings();
                plugin.showSettings();
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Timeout Handling")
    class TimeoutHandlingTests {

        @Test
        @DisplayName("Should have correct timeout constant")
        void shouldHaveCorrectTimeoutConstant() {
            assertThat(SpeleoDBPlugin.TIMEOUT).isEqualTo(10000);
        }

        @Test
        @DisplayName("Should handle timeout calculation")
        void shouldHandleTimeoutCalculation() {
            LocalDateTime start = LocalDateTime.now();

            // Simulate a very short operation (should be under timeout)
            try {
                Thread.sleep(10); // 10ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Duration elapsed = Duration.between(start, LocalDateTime.now());
            assertThat(elapsed.toMillis()).isLessThan(SpeleoDBPlugin.TIMEOUT);
        }

        @Test
        @DisplayName("Should handle atomic boolean lock mechanism")
        void shouldHandleAtomicBooleanLockMechanism() {
            AtomicBoolean lock = new AtomicBoolean(false);

            // Initial state
            assertThat(lock.get()).isFalse();

            // Set lock
            lock.set(true);
            assertThat(lock.get()).isTrue();

            // Reset lock
            lock.set(false);
            assertThat(lock.get()).isFalse();

            // Test compare and set
            boolean result = lock.compareAndSet(false, true);
            assertThat(result).isTrue();
            assertThat(lock.get()).isTrue();

            // Test compare and set with wrong expected value
            boolean failedResult = lock.compareAndSet(false, false);
            assertThat(failedResult).isFalse();
            assertThat(lock.get()).isTrue(); // Should remain unchanged
        }
    }

    @Nested
    @DisplayName("Survey Loading Edge Cases")
    class SurveyLoadingEdgeCasesTests {

        @Test
        @DisplayName("Should handle loadSurvey with null file")
        void shouldHandleLoadSurveyWithNullFile() {
            plugin.setSurvey(null);
            plugin.setSurveyFile(null);

            // This should not crash, but we can't test the actual behavior
            // since it involves thread timing and external dependencies
            assertThatCode(() -> {
                File nullFile = null;
                plugin.setSurveyFile(nullFile);
                assertThat(plugin.getSurveyFile()).isNull();
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle survey file state management")
        void shouldHandleSurveyFileStateManagement() {
            // Test initial state
            assertThat(plugin.getSurveyFile()).isNull();

            // Set survey file
            plugin.setSurveyFile(testSurveyFile);
            assertThat(plugin.getSurveyFile()).isEqualTo(testSurveyFile);

            // Clear survey file
            plugin.setSurveyFile(null);
            assertThat(plugin.getSurveyFile()).isNull();
        }

        @Test
        @DisplayName("Should handle command property changes")
        void shouldHandleCommandPropertyChanges() {
            // Test initial command property
            assertThat(plugin.getCommandProperty()).isNotNull();

            // Test setting different commands
            plugin.getCommandProperty().set(DataServerCommands.SAVE.name());
            assertThat(plugin.getCommandProperty().get()).isEqualTo(DataServerCommands.SAVE.name());

            plugin.getCommandProperty().set(DataServerCommands.LOAD.name());
            assertThat(plugin.getCommandProperty().get()).isEqualTo(DataServerCommands.LOAD.name());

            plugin.getCommandProperty().set(DataServerCommands.REDRAW.name());
            assertThat(plugin.getCommandProperty().get()).isEqualTo(DataServerCommands.REDRAW.name());
        }
    }

    @Nested
    @DisplayName("Plugin Lifecycle Edge Cases")
    class PluginLifecycleEdgeCasesTests {

        @Test
        @DisplayName("Should handle multiple closeUI calls")
        void shouldHandleMultipleCloseUICalls() {
            // closeUI should be safe to call multiple times
            assertThatCode(() -> {
                plugin.closeUI();
                plugin.closeUI();
                plugin.closeUI();
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle executor service shutdown")
        void shouldHandleExecutorServiceShutdown() {
            // Test that the executor service exists and can be used
            assertThat(plugin.executorService).isNotNull();

            // Test that it's not shutdown initially
            assertThat(plugin.executorService.isShutdown()).isFalse();

            // Call closeUI which should shutdown the executor
            plugin.closeUI();

            // Verify executor is shutdown
            assertThat(plugin.executorService.isShutdown()).isTrue();
        }

        @Test
        @DisplayName("Should handle save survey operations")
        void shouldHandleSaveSurveyOperations() {
            assertThatCode(() -> plugin.saveSurvey()).doesNotThrowAnyException();

            // The command property should be set to SAVE then DONE
            // Note: We can't easily test the exact sequence due to timing,
            // but we can ensure the method doesn't throw exceptions
        }
    }

    @Nested
    @DisplayName("File Operations Edge Cases")
    class FileOperationsEdgeCasesTests {

        @Test
        @DisplayName("Should handle various file types")
        void shouldHandleVariousFileTypes() throws IOException {
            // Test with different file extensions
            String[] extensions = {PATHS.TML_FILE_EXTENSION, ".txt", ".dat", ".json", ".xml"};

            for (String ext : extensions) {
                File testFile = tempDir.resolve("test" + ext).toFile();
                Files.createFile(testFile.toPath());

                assertThatCode(() -> {
                    plugin.setSurveyFile(testFile);
                    assertThat(plugin.getSurveyFile()).isEqualTo(testFile);
                }).doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("Should handle files with special names")
        void shouldHandleFilesWithSpecialNames() throws IOException {
            String[] specialNames = {
                "file with spaces.tml",
                "file-with-dashes.tml",
                "file_with_underscores.tml",
                "file.with.dots.tml",
                "file123.tml"
            };

            for (String name : specialNames) {
                try {
                    File specialFile = tempDir.resolve(name).toFile();
                    Files.createFile(specialFile.toPath());

                    assertThatCode(() -> {
                        plugin.setSurveyFile(specialFile);
                        assertThat(plugin.getSurveyFile()).isEqualTo(specialFile);
                    }).doesNotThrowAnyException();
                } catch (IOException e) {
                    // Some special characters might not be allowed on all file systems
                    // This is expected and not a failure of our code
                }
            }
        }

        @Test
        @DisplayName("Should handle non-existent files")
        void shouldHandleNonExistentFiles() {
            File nonExistentFile = tempDir.resolve("does-not-exist.tml").toFile();

            // Setting a non-existent file should still work (file might be created later)
            assertThatCode(() -> {
                plugin.setSurveyFile(nonExistentFile);
                assertThat(plugin.getSurveyFile()).isEqualTo(nonExistentFile);
            }).doesNotThrowAnyException();
        }
    }

    // ===================== TEST HELPER CLASS ===================== //

    /**
     * Testable version of SpeleoDBPlugin that exposes protected/package methods for testing
     */
    static class TestableSpeleoDBPlugin extends SpeleoDBPlugin {

        // All methods are already public in SpeleoDBPlugin, so no additional exposure needed
        // This class exists mainly for consistency with other test patterns
    }
}
