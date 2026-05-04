package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.PATHS;

/**
 * Pure unit tests for the on-disk concerns of {@link SpeleoDBService}: project file path
 * resolution and TML template extraction.
 *
 * <p>Other concerns are covered elsewhere:
 * <ul>
 *   <li>URL/hostname normalization → {@link SpeleoDBHostnameHandlingTest}</li>
 *   <li>v2 error envelope parsing → {@link SpeleoDBServiceErrorParsingTest}</li>
 *   <li>Every endpoint's HTTP success / failure paths → the {@code Speleo*ApiTest} WireMock suites</li>
 * </ul>
 */
class SpeleoDBServiceTest {

    private static final String TEST_ARIANE_DIR = System.getProperty("java.io.tmpdir") + File.separator + "test_ariane";

    @BeforeAll
    static void setupTestEnvironment() throws IOException {
        Path testDir = Paths.get(TEST_ARIANE_DIR);
        if (!Files.exists(testDir)) {
            Files.createDirectories(testDir);
        }
    }

    @AfterAll
    static void cleanupTestEnvironment() throws IOException {
        Path testDir = Paths.get(TEST_ARIANE_DIR);
        if (Files.exists(testDir)) {
            Files.walk(testDir)
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    @Test
    @DisplayName("Project path resolution and TML file lifecycle on disk")
    void testFileOperations() throws IOException {
        String projectId = "test-project-123";
        Path expectedPath = Paths.get(PATHS.SDB_PROJECT_DIR + File.separator + projectId + PATHS.TML_FILE_EXTENSION);
        Path actualPath = Paths.get(PATHS.SDB_PROJECT_DIR, projectId + PATHS.TML_FILE_EXTENSION);
        assertThat(actualPath).isEqualTo(expectedPath);

        Path testFile = Paths.get(TEST_ARIANE_DIR + File.separator + projectId + PATHS.TML_FILE_EXTENSION);
        Files.write(testFile, "test content".getBytes());
        assertThat(testFile).exists();

        Files.delete(testFile);
        assertThat(testFile).doesNotExist();
    }

    @Test
    @DisplayName("Empty TML template extraction copies the bundled resource to disk")
    void testEmptyTmlFileCreation() throws IOException {
        SpeleoDBService service = new SpeleoDBService(Mockito.mock(SpeleoDBController.class));

        String testProjectId = "test-422-project";
        String testProjectName = "Test HTTP 422 Project";

        Path createdFile = service.createEmptyTmlFileFromTemplate(testProjectId, testProjectName);
        try {
            assertThat(createdFile).exists();
            assertThat(Files.size(createdFile)).isPositive();
            assertThat(createdFile.getFileName().toString()).isEqualTo(testProjectId + PATHS.TML_FILE_EXTENSION);
            assertThat(createdFile.getParent().toString()).isEqualTo(PATHS.SDB_PROJECT_DIR);
        } finally {
            Files.deleteIfExists(createdFile);
        }
    }
}
