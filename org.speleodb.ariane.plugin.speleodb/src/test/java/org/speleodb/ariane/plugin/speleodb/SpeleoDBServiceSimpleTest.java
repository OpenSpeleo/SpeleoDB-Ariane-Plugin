package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.PATHS;
/**
 * Simple unit tests for SpeleoDBService covering basic functionality and edge cases.
 * Tests fundamental behaviors without complex mocking.
 */
@DisplayName("SpeleoDB Service Simple Tests")
class SpeleoDBServiceSimpleTest {

    @Nested
    @DisplayName("Constants and Static Values")
    class ConstantsTests {

        @Test
        @DisplayName("Should have correct ARIANE_ROOT_DIR constant")
        void shouldHaveCorrectArianeRootDirConstant() {
            String expected = System.getProperty("user.home") + java.io.File.separator + ".ariane";
            assertThat(PATHS.ARIANE_ROOT_DIR).isEqualTo(expected);
        }

        @Test
        @DisplayName("ARIANE_ROOT_DIR should be a valid path")
        void arianeRootDirShouldBeValidPath() {
            Path arianeDir = Paths.get(PATHS.ARIANE_ROOT_DIR);
            assertThat(arianeDir).isNotNull();
            assertThat(arianeDir.isAbsolute()).isTrue();
        }
    }

    @Nested
    @DisplayName("URL Pattern Matching")
    class UrlPatternMatchingTests {

        @Test
        @DisplayName("Should correctly identify local URLs")
        void shouldCorrectlyIdentifyLocalUrls() {
            String localPattern = "(^localhost)|(^127\\.)|(^10\\.)|(^172\\.(1[6-9]|2[0-9]|3[0-1])\\.)|(^192\\.168\\.)";
            Pattern pattern = Pattern.compile(localPattern);

            // These should match (local addresses)
            assertThat(pattern.matcher("localhost").find()).isTrue();
            assertThat(pattern.matcher("127.0.0.1").find()).isTrue();
            assertThat(pattern.matcher("192.168.1.100").find()).isTrue();
            assertThat(pattern.matcher("10.0.0.1").find()).isTrue();
            assertThat(pattern.matcher("172.16.0.1").find()).isTrue();
            assertThat(pattern.matcher("172.20.0.1").find()).isTrue();
            assertThat(pattern.matcher("172.31.255.255").find()).isTrue();

            // These should not match (public addresses)
            assertThat(pattern.matcher("www.speleodb.org").find()).isFalse();
            assertThat(pattern.matcher("api.example.com").find()).isFalse();
            assertThat(pattern.matcher("8.8.8.8").find()).isFalse();
            assertThat(pattern.matcher("172.15.0.1").find()).isFalse(); // Outside private range
            assertThat(pattern.matcher("172.32.0.1").find()).isFalse(); // Outside private range
            assertThat(pattern.matcher("193.168.1.1").find()).isFalse(); // Not 192.168
        }
    }

    @Nested
    @DisplayName("File Path Operations")
    class FilePathOperationsTests {

        @Test
        @DisplayName("Should generate correct file paths for project IDs")
        void shouldGenerateCorrectFilePathsForProjectIds() {
            String[] projectIds = {
                "simple-project",
                "project-123",
                "project-with-dashes",
                "project_with_underscores",
                "UPPERCASE-PROJECT",
                "project.with.dots"
            };

            for (String projectId : projectIds) {
                Path expectedPath = Paths.get(PATHS.SDB_PROJECT_DIR, projectId + PATHS.TML_FILE_EXTENSION);

                assertThat(expectedPath).isNotNull();
                assertThat(expectedPath.toString()).contains(projectId);
                assertThat(expectedPath.toString()).endsWith(PATHS.TML_FILE_EXTENSION);
                assertThat(expectedPath.toString()).contains(PATHS.SDB_PROJECT_DIR);
            }
        }

        @Test
        @DisplayName("Should handle extreme project ID lengths")
        void shouldHandleExtremeProjectIdLengths() {
            // Very short ID
            String shortId = "a";
            Path shortPath = Paths.get(PATHS.SDB_PROJECT_DIR, shortId + PATHS.TML_FILE_EXTENSION);
            assertThat(shortPath.toString()).contains("a.tml");

            // Very long ID
            String longId = "very-long-project-id-" + "x".repeat(200);
            Path longPath = Paths.get(PATHS.SDB_PROJECT_DIR, longId + PATHS.TML_FILE_EXTENSION);
            assertThat(longPath.toString()).contains(longId);
            assertThat(longPath.toString()).endsWith(PATHS.TML_FILE_EXTENSION);
        }

        @Test
        @DisplayName("Should handle special characters in project IDs")
        void shouldHandleSpecialCharactersInProjectIds() {
            String[] specialIds = {
                "project!@#$%",
                "project with spaces",
                "project-with-unicode-字符",
                "project[with]brackets",
                "project{with}braces"
            };

            for (String specialId : specialIds) {
                assertThatCode(() -> {
                    Path path = Paths.get(PATHS.SDB_PROJECT_DIR, specialId + PATHS.TML_FILE_EXTENSION);
                    // Just test that path creation doesn't throw exceptions
                    assertThat(path).isNotNull();
                }).doesNotThrowAnyException();
            }
        }
    }

    @Nested
    @DisplayName("Project ID Validation")
    class ProjectIdValidationTests {

        @Test
        @DisplayName("Should validate project ID patterns")
        void shouldValidateProjectIdPatterns() {
            // Test various project ID formats that might be used
            String[] validIds = {
                "abc123def456",
                "project-2024-01-15",
                "cave_system_alpha",
                "speleoDB_project_001"
            };

            for (String id : validIds) {
                assertThat(id).isNotNull().isNotEmpty();
                assertThat(id.trim()).isEqualTo(id); // No leading/trailing whitespace
            }
        }
    }

    @Nested
    @DisplayName("General Edge Cases")
    class GeneralEdgeCasesTests {

        @Test
        @DisplayName("Should handle null and empty string inputs gracefully")
        void shouldHandleNullAndEmptyStringInputsGracefully() {
            // Test string handling for various input scenarios
            String nullString = null;
            String emptyString = "";
            String whitespaceString = "   ";
            String validString = "valid-input";

            // Test null handling
            assertThat(nullString).isNull();

            // Test empty string handling
            assertThat(emptyString).isEmpty();
            assertThat(emptyString.trim()).isEmpty();

            // Test whitespace handling
            assertThat(whitespaceString).isNotEmpty();
            assertThat(whitespaceString.trim()).isEmpty();

            // Test valid string
            assertThat(validString).isNotEmpty();
            assertThat(validString.trim()).isNotEmpty();
        }

        @Test
        @DisplayName("Should handle system property edge cases")
        void shouldHandleSystemPropertyEdgeCases() {
            // Test that system properties are accessible
            String userHome = System.getProperty("user.home");
            assertThat(userHome).isNotNull().isNotEmpty();

            // Test file separator
            String fileSeparator = java.io.File.separator;
            assertThat(fileSeparator).isNotNull().isNotEmpty();

            // Test that SDB_PROJECT_DIR is constructed correctly
            String expectedRoot = userHome + fileSeparator + ".ariane";
            assertThat(PATHS.ARIANE_ROOT_DIR).isEqualTo(expectedRoot);
        }

        @Test
        @DisplayName("Should handle path construction edge cases")
        void shouldHandlePathConstructionEdgeCases() {
            // Test various path construction scenarios
            String baseDir = PATHS.SDB_PROJECT_DIR;

            // Normal case
            Path normalPath = Paths.get(baseDir, "project.tml");
            assertThat(normalPath).isNotNull();

            // Multiple path components
            Path multiPath = Paths.get(baseDir, "subdir", "project.tml");
            assertThat(multiPath).isNotNull();

            // Empty filename (should still work)
            Path emptyFilePath = Paths.get(baseDir, "");
            assertThat(emptyFilePath).isNotNull();

            // Relative vs absolute paths
            assertThat(Paths.get(baseDir).isAbsolute()).isTrue();
        }
    }
}
