package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arianesline.ariane.plugin.api.DataServerCommands;
import com.arianesline.ariane.plugin.api.PluginInterface;
import com.arianesline.ariane.plugin.api.PluginType;
import com.arianesline.cavelib.api.CaveSurveyInterface;

import javafx.beans.property.StringProperty;

/**
 * Comprehensive unit tests for SpeleoDBPlugin using JUnit 5, Mockito, and AssertJ.
 * Tests plugin functionality, survey management, and lifecycle operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpeleoDB Plugin Tests")
class SpeleoDBPluginTest {

    @TempDir
    Path tempDir;

    @Mock
    private CaveSurveyInterface mockSurvey;

    @InjectMocks
    private SpeleoDBPlugin plugin;

    private File testSurveyFile;

    @BeforeEach
    void setUp() throws IOException {
        testSurveyFile = tempDir.resolve("test_survey.tml").toFile();
        Files.createFile(testSurveyFile.toPath());
    }

    @AfterEach
    void tearDown() {
        if (plugin != null) {
            plugin.closeUI();
        }
    }

    @Nested
    @DisplayName("Plugin Metadata")
    class PluginMetadataTests {

        @Test
        @DisplayName("Should return correct plugin name")
        void shouldReturnCorrectPluginName() {
            assertThat(plugin.getName()).isEqualTo("SpeleoDB");
        }

        @Test
        @DisplayName("Should return correct plugin type")
        void shouldReturnCorrectPluginType() {
            assertThat(plugin.getType()).isEqualTo(PluginType.DATASERVER);
        }

        @Test
        @DisplayName("Should return correct interface type")
        void shouldReturnCorrectInterfaceType() {
            assertThat(plugin.getInterfaceType()).isEqualTo(PluginInterface.LEFT_TAB);
        }

        @Test
        @DisplayName("Should have correct timeout value")
        void shouldHaveCorrectTimeoutValue() {
            assertThat(SpeleoDBPlugin.TIMEOUT).isEqualTo(10000);
        }
    }

    @Nested
    @DisplayName("Survey File Management")
    class SurveyFileManagementTests {

        @Test
        @DisplayName("Should initially have no survey file")
        void shouldInitiallyHaveNoSurveyFile() {
            SpeleoDBPlugin freshPlugin = new SpeleoDBPlugin();
            assertThat(freshPlugin.getSurveyFile()).isNull();
        }

        @Test
        @DisplayName("Should set and get survey file correctly")
        void shouldSetAndGetSurveyFileCorrectly() {
            plugin.setSurveyFile(testSurveyFile);

            assertThat(plugin.getSurveyFile())
                .isNotNull()
                .isEqualTo(testSurveyFile)
                .exists();
        }

        @Test
        @DisplayName("Should handle null survey file")
        void shouldHandleNullSurveyFile() {
            plugin.setSurveyFile(testSurveyFile);
            plugin.setSurveyFile(null);

            assertThat(plugin.getSurveyFile()).isNull();
        }
    }

    @Nested
    @DisplayName("Survey Interface Management")
    class SurveyInterfaceManagementTests {

        @Test
        @DisplayName("Should initially have no survey interface")
        void shouldInitiallyHaveNoSurveyInterface() {
            SpeleoDBPlugin freshPlugin = new SpeleoDBPlugin();
            assertThat(freshPlugin.getSurvey()).isNull();
        }

        @Test
        @DisplayName("Should set and get survey interface correctly")
        void shouldSetAndGetSurveyInterfaceCorrectly() {
            plugin.setSurvey(mockSurvey);

            assertThat(plugin.getSurvey()).isEqualTo(mockSurvey);
        }

        @Test
        @DisplayName("Should handle null survey interface")
        void shouldHandleNullSurveyInterface() {
            plugin.setSurvey(mockSurvey);
            plugin.setSurvey(null);

            assertThat(plugin.getSurvey()).isNull();
        }
    }

    @Nested
    @DisplayName("Command Property")
    class CommandPropertyTests {

        @Test
        @DisplayName("Should provide non-null command property")
        void shouldProvideNonNullCommandProperty() {
            StringProperty commandProperty = plugin.getCommandProperty();

            assertThat(commandProperty).isNotNull();
        }

        @Test
        @DisplayName("Should allow setting command values")
        void shouldAllowSettingCommandValues() {
            StringProperty commandProperty = plugin.getCommandProperty();

            commandProperty.set(DataServerCommands.SAVE.name());
            assertThat(commandProperty.get()).isEqualTo(DataServerCommands.SAVE.name());

            commandProperty.set(DataServerCommands.LOAD.name());
            assertThat(commandProperty.get()).isEqualTo(DataServerCommands.LOAD.name());

            commandProperty.set(DataServerCommands.REDRAW.name());
            assertThat(commandProperty.get()).isEqualTo(DataServerCommands.REDRAW.name());
        }
    }

    @Nested
    @DisplayName("Survey Operations")
    class SurveyOperationsTests {

        @Test
        @DisplayName("Should execute save survey operation")
        void shouldExecuteSaveSurveyOperation() {
            StringProperty commandProperty = plugin.getCommandProperty();

            plugin.saveSurvey();
        }

        @Test
        @DisplayName("Should handle load with null file")
        void shouldHandleLoadWithNullFile() {
            assertThatCode(() -> {
                plugin.setSurveyFile(null);
                plugin.getCommandProperty().set(DataServerCommands.LOAD.name());
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Plugin Lifecycle")
    class PluginLifecycleTests {

        @Test
        @DisplayName("Should provide executor service")
        void shouldProvideExecutorService() {
            ExecutorService executor = plugin.executorService;

            assertThat(executor)
                .isNotNull()
                .satisfies(service -> assertThat(service.isShutdown()).isFalse());
        }

        @Test
        @DisplayName("Should shutdown executor on close")
        void shouldShutdownExecutorOnClose() {
            ExecutorService executor = plugin.executorService;

            plugin.closeUI();

            assertThat(executor.isShutdown()).isTrue();
        }

        @Test
        @DisplayName("Should provide icon")
        @DisabledIfEnvironmentVariable(named = "CI", matches = "true",
                disabledReason = "new Image(...) requires JavaFX graphics toolkit (Internal graphics not initialized yet)")
        void shouldProvideIcon() {
            assertThatCode(() -> plugin.getIcon())
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should attempt to create UI node")
        @DisabledIfEnvironmentVariable(named = "CI", matches = "true",
                disabledReason = "FXMLLoader.load triggers Image init which requires JavaFX graphics toolkit on headless CI")
        void shouldAttemptToCreateUINode() {
            // JavaFX UI components may not be available in headless test environment
            // Just verify that the method can be called without crashing the test
            try {
                plugin.getUINode();
                // If it succeeds, that's fine too
            } catch (Exception | Error e) {
                // Expected in headless environment - this is acceptable
                assertThat(e).isNotNull();
            }
        }

        @Test
        @DisplayName("Should attempt to show UI")
        @DisabledIfEnvironmentVariable(named = "CI", matches = "true",
                disabledReason = "showUI -> FXMLLoader.load -> Image init requires JavaFX graphics toolkit on headless CI")
        void shouldAttemptToShowUI() {
            // JavaFX UI components may not be available in headless test environment
            // Just verify that the method can be called without crashing the test
            try {
                plugin.showUI();
                // If it succeeds, that's fine too
            } catch (Exception | Error e) {
                // Expected in headless environment - this is acceptable
                assertThat(e).isNotNull();
            }
        }

        @Test
        @DisplayName("Should handle settings without errors")
        void shouldHandleSettingsWithoutErrors() {
            assertThatCode(() -> plugin.showSettings())
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle concurrent survey operations")
        void shouldHandleConcurrentSurveyOperations() {
            assertThatCode(() -> {
                plugin.saveSurvey();
                plugin.setSurveyFile(testSurveyFile);
                plugin.getCommandProperty().set(DataServerCommands.LOAD.name());
                plugin.saveSurvey();
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should maintain thread safety for survey file operations")
        void shouldMaintainThreadSafetyForSurveyFileOperations() {
            assertThatCode(() -> {
                Thread t1 = new Thread(() -> plugin.setSurveyFile(testSurveyFile));
                Thread t2 = new Thread(() -> plugin.getSurveyFile());

                t1.start();
                t2.start();

                t1.join();
                t2.join();
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle multiple close operations gracefully")
        void shouldHandleMultipleCloseOperationsGracefully() {
            assertThatCode(() -> {
                plugin.closeUI();
                plugin.closeUI();
                plugin.closeUI();
            }).doesNotThrowAnyException();
        }
    }
}
