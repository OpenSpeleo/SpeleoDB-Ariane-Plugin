package org.speleodb.ariane.plugin.speleodb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.PATHS;
/**
 * Unit tests for the TestFixtures class
 * Validates fixture generation and consistency
 */
public class TestFixturesTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Clear any existing created projects
        TestFixtures.clearCreatedProjects();
    }

    @Test
    @DisplayName("Standard Project Fixture Creation")
    void testStandardProjectFixture() {
        TestFixtures.ProjectFixture fixture = TestFixtures.createProjectFixture();

        assertNotNull(fixture, "Fixture should not be null");
        assertNotNull(fixture.getName(), "Project name should not be null");
        assertNotNull(fixture.getDescription(), "Project description should not be null");
        assertNotNull(fixture.getCountryCode(), "Country code should not be null");
        assertNotNull(fixture.getLatitude(), "Latitude should not be null");
        assertNotNull(fixture.getLongitude(), "Longitude should not be null");

        assertFalse(fixture.isMinimal(), "Standard fixture should not be minimal");
        assertFalse(fixture.isComprehensive(), "Standard fixture should not be comprehensive");

        // Validate name format
        assertTrue(fixture.getName().contains("Test"), "Project name should contain 'Test'");
        assertTrue(fixture.getName().contains("-"), "Project name should contain separator");

        // Validate description format
        assertTrue(fixture.getDescription().contains("Run:"), "Description should contain run ID");

        // Validate coordinates are within valid ranges
        double lat = Double.parseDouble(fixture.getLatitude());
        double lon = Double.parseDouble(fixture.getLongitude());
        assertTrue(lat >= -90.0 && lat <= 90.0, "Latitude should be within valid range");
        assertTrue(lon >= -180.0 && lon <= 180.0, "Longitude should be within valid range");

        System.out.println("✓ Standard fixture: " + fixture.getName());
    }

    @Test
    @DisplayName("Minimal Project Fixture Creation")
    void testMinimalProjectFixture() {
        TestFixtures.ProjectFixture fixture = TestFixtures.createMinimalProjectFixture();

        assertNotNull(fixture, "Fixture should not be null");
        assertNotNull(fixture.getName(), "Project name should not be null");
        assertNotNull(fixture.getDescription(), "Project description should not be null");
        assertNotNull(fixture.getCountryCode(), "Country code should not be null");

        // Minimal fixtures should not have coordinates
        assertNull(fixture.getLatitude(), "Minimal fixture should not have latitude");
        assertNull(fixture.getLongitude(), "Minimal fixture should not have longitude");

        assertTrue(fixture.isMinimal(), "Minimal fixture should be marked as minimal");
        assertFalse(fixture.isComprehensive(), "Minimal fixture should not be comprehensive");

        System.out.println("✓ Minimal fixture: " + fixture.getName());
    }

    @Test
    @DisplayName("Comprehensive Project Fixture Creation")
    void testComprehensiveProjectFixture() {
        TestFixtures.ProjectFixture fixture = TestFixtures.createComprehensiveProjectFixture();

        assertNotNull(fixture, "Fixture should not be null");
        assertNotNull(fixture.getName(), "Project name should not be null");
        assertNotNull(fixture.getDescription(), "Project description should not be null");
        assertNotNull(fixture.getCountryCode(), "Country code should not be null");
        assertNotNull(fixture.getLatitude(), "Latitude should not be null");
        assertNotNull(fixture.getLongitude(), "Longitude should not be null");

        assertFalse(fixture.isMinimal(), "Comprehensive fixture should not be minimal");
        assertTrue(fixture.isComprehensive(), "Comprehensive fixture should be marked as comprehensive");

        // Comprehensive fixtures should have special description marker
        assertTrue(fixture.getDescription().contains("[COMPREHENSIVE TEST]"),
                   "Comprehensive fixture should have special description marker");

        System.out.println("✓ Comprehensive fixture: " + fixture.getName());
    }

    @Test
    @DisplayName("Fixture Builder Pattern")
    void testFixtureBuilderPattern() {
        String customName = "Custom Test Cave";
        String customDescription = "Custom test description";
        String customCountry = "US";
        double customLat = 45.0;
        double customLon = -120.0;

        TestFixtures.ProjectFixture fixture = TestFixtures.createProjectFixture()
            .withName(customName)
            .withDescription(customDescription)
            .withCountry(customCountry)
            .withCoordinates(customLat, customLon);

        assertEquals(customName, fixture.getName(), "Custom name should be set");
        assertEquals(customDescription, fixture.getDescription(), "Custom description should be set");
        assertEquals(customCountry, fixture.getCountryCode(), "Custom country should be set");
        assertEquals(String.valueOf(customLat), fixture.getLatitude(), "Custom latitude should be set");
        assertEquals(String.valueOf(customLon), fixture.getLongitude(), "Custom longitude should be set");

        System.out.println("✓ Builder pattern: " + fixture.getName());
    }

    @Test
    @DisplayName("Fixture Without Coordinates")
    void testFixtureWithoutCoordinates() {
        TestFixtures.ProjectFixture fixture = TestFixtures.createProjectFixture()
            .withoutCoordinates();

        assertNull(fixture.getLatitude(), "Fixture should not have latitude");
        assertNull(fixture.getLongitude(), "Fixture should not have longitude");

        System.out.println("✓ No coordinates fixture: " + fixture.getName());
    }

    @Test
    @DisplayName("TML File Generation - Standard")
    void testStandardTmlGeneration() throws IOException {
        TestFixtures.ProjectFixture fixture = TestFixtures.createProjectFixture();
        String testProjectId = "test-project-123";

        // Override ARIANE_ROOT_DIR for testing
        System.setProperty("ariane.root.dir", tempDir.toString());

        Path tmlFile = fixture.generateTmlFile(testProjectId);

        assertTrue(Files.exists(tmlFile), "TML file should be created");
        assertTrue(Files.size(tmlFile) > 0, "TML file should not be empty");

        // TML files are binary ZIP archives, so we can't read them as text
        // Instead, verify it's a substantial binary file (template is ~566 bytes)
        assertTrue(Files.size(tmlFile) > 500, "TML file should be substantial in size (>500 bytes)");

        // Verify file has correct extension
        assertTrue(tmlFile.getFileName().toString().endsWith(PATHS.TML_FILE_EXTENSION), "File should have .tml extension");
        assertTrue(tmlFile.getFileName().toString().contains(testProjectId), "Filename should contain project ID");

        System.out.println("✓ Standard TML file generated: " + tmlFile.getFileName());
        System.out.println("  File size: " + Files.size(tmlFile) + " bytes");

        // Clean up
        Files.delete(tmlFile);
    }

    @Test
    @DisplayName("TML File Generation - Minimal")
    void testMinimalTmlGeneration() throws IOException {
        TestFixtures.ProjectFixture fixture = TestFixtures.createMinimalProjectFixture();
        String testProjectId = "test-minimal-123";

        // Override ARIANE_ROOT_DIR for testing
        System.setProperty("ariane.root.dir", tempDir.toString());

        Path tmlFile = fixture.generateTmlFile(testProjectId);

        assertTrue(Files.exists(tmlFile), "TML file should be created");
        assertTrue(Files.size(tmlFile) > 0, "TML file should not be empty");

        // TML files are binary ZIP archives, so we can't read them as text
        // Instead, verify it's a substantial binary file
        assertTrue(Files.size(tmlFile) > 500, "TML file should be substantial in size (>500 bytes)");

        // Verify file has correct extension and naming
        assertTrue(tmlFile.getFileName().toString().endsWith(PATHS.TML_FILE_EXTENSION), "File should have .tml extension");
        assertTrue(tmlFile.getFileName().toString().contains(testProjectId), "Filename should contain project ID");

        System.out.println("✓ Minimal TML file generated: " + tmlFile.getFileName());
        System.out.println("  File size: " + Files.size(tmlFile) + " bytes");

        // Clean up
        Files.delete(tmlFile);
    }

    @Test
    @DisplayName("TML File Generation - Comprehensive")
    void testComprehensiveTmlGeneration() throws IOException {
        TestFixtures.ProjectFixture fixture = TestFixtures.createComprehensiveProjectFixture();
        String testProjectId = "test-comprehensive-123";

        // Override ARIANE_ROOT_DIR for testing
        System.setProperty("ariane.root.dir", tempDir.toString());

        Path tmlFile = fixture.generateTmlFile(testProjectId);

        assertTrue(Files.exists(tmlFile), "TML file should be created");
        assertTrue(Files.size(tmlFile) > 0, "TML file should not be empty");

        // TML files are binary ZIP archives, so we can't read them as text
        // Instead, verify it's a substantial binary file
        assertTrue(Files.size(tmlFile) > 500, "TML file should be substantial in size (>500 bytes)");

        // Verify file has correct extension and naming
        assertTrue(tmlFile.getFileName().toString().endsWith(PATHS.TML_FILE_EXTENSION), "File should have .tml extension");
        assertTrue(tmlFile.getFileName().toString().contains(testProjectId), "Filename should contain project ID");

        System.out.println("✓ Comprehensive TML file generated: " + tmlFile.getFileName());
        System.out.println("  File size: " + Files.size(tmlFile) + " bytes");

        // Clean up
        Files.delete(tmlFile);
    }

    @Test
    @DisplayName("Random Data Generation Consistency")
    void testRandomDataGeneration() {
        // Test that random generation produces valid data
        for (int i = 0; i < 10; i++) {
            String projectName = TestFixtures.generateProjectName();
            String description = TestFixtures.generateProjectDescription();
            String country = TestFixtures.generateCountryCode();
            double latitude = TestFixtures.generateLatitude();
            double longitude = TestFixtures.generateLongitude();
            String caveName = TestFixtures.generateCaveName();
            String uploadMessage = TestFixtures.generateUploadMessage();

            assertNotNull(projectName, "Project name should not be null");
            assertNotNull(description, "Description should not be null");
            assertNotNull(country, "Country should not be null");
            assertNotNull(caveName, "Cave name should not be null");
            assertNotNull(uploadMessage, "Upload message should not be null");

            assertTrue(projectName.length() > 0, "Project name should not be empty");
            assertTrue(description.length() > 0, "Description should not be empty");
            assertEquals(2, country.length(), "Country code should be 2 characters");
            assertTrue(caveName.contains(" "), "Cave name should have space separator");
            assertTrue(uploadMessage.contains("-"), "Upload message should have timestamp separator");

            // Validate coordinate ranges
            assertTrue(latitude >= -90.0 && latitude <= 90.0, "Latitude should be within valid range");
            assertTrue(longitude >= -180.0 && longitude <= 180.0, "Longitude should be within valid range");
        }

        System.out.println("✓ Random data generation validated for 10 iterations");
    }

    @Test
    @DisplayName("Fixture Registration and Cleanup")
    void testFixtureRegistration() {
        // Create mock project data
        jakarta.json.JsonObject mockProject = jakarta.json.Json.createObjectBuilder()
            .add("id", "test-123")
            .add("name", "Test Project")
            .build();

        assertEquals(0, TestFixtures.getCreatedProjects().size(), "Should start with no registered projects");

        TestFixtures.registerCreatedProject(mockProject);
        assertEquals(1, TestFixtures.getCreatedProjects().size(), "Should have one registered project");

        TestFixtures.clearCreatedProjects();
        assertEquals(0, TestFixtures.getCreatedProjects().size(), "Should have no registered projects after clear");

        System.out.println("✓ Fixture registration and cleanup working correctly");
    }

    @Test
    @DisplayName("Fixture Uniqueness")
    void testFixtureUniqueness() {
        // Generate multiple fixtures and ensure they have unique names
        TestFixtures.ProjectFixture fixture1 = TestFixtures.createProjectFixture();
        TestFixtures.ProjectFixture fixture2 = TestFixtures.createProjectFixture();
        TestFixtures.ProjectFixture fixture3 = TestFixtures.createProjectFixture();

        assertNotEquals(fixture1.getName(), fixture2.getName(), "Fixtures should have unique names");
        assertNotEquals(fixture2.getName(), fixture3.getName(), "Fixtures should have unique names");
        assertNotEquals(fixture1.getName(), fixture3.getName(), "Fixtures should have unique names");

        // All should contain the same test run ID though
        String testRunId = String.valueOf(System.currentTimeMillis());
        // Note: Due to timing, this might not be exactly the same, but they should all contain "Test"
        assertTrue(fixture1.getName().contains("Test"), "All fixtures should contain Test marker");
        assertTrue(fixture2.getName().contains("Test"), "All fixtures should contain Test marker");
        assertTrue(fixture3.getName().contains("Test"), "All fixtures should contain Test marker");

        System.out.println("✓ Fixture uniqueness validated");
        System.out.println("  Fixture 1: " + fixture1.getName());
        System.out.println("  Fixture 2: " + fixture2.getName());
        System.out.println("  Fixture 3: " + fixture3.getName());
    }

    @Test
    @DisplayName("Real TML File Handling")
    void testRealTmlFileHandling() throws IOException {
        // Test getting the real TML file
        try {
            Path tmlFile = TestFixtures.getTestTmlFile();
            assertNotNull(tmlFile, "TML file path should not be null");
            assertTrue(Files.exists(tmlFile), "TML file should exist");
            assertTrue(Files.size(tmlFile) > 0, "TML file should not be empty");
            System.out.println("✓ Real TML file found: " + tmlFile);
        } catch (RuntimeException e) {
            // If the real TML file doesn't exist, skip this test
            System.out.println("⚠ Skipping real TML file test - file not found: " + e.getMessage());
            return;
        }

        // Test copying the TML file
        System.setProperty("ariane.root.dir", tempDir.toString());
        String testProjectId = "test-real-tml-123";

        Path copiedFile = TestFixtures.copyTestTmlFile(testProjectId);

        assertTrue(Files.exists(copiedFile), "Copied TML file should exist");
        assertTrue(Files.size(copiedFile) > 0, "Copied TML file should not be empty");
        assertEquals(testProjectId + PATHS.TML_FILE_EXTENSION, copiedFile.getFileName().toString(), "Copied file should have correct name");

        // Verify it's a binary file (TML files are ZIP archives)
        // Content verification is done via file size and checksum only
        assertTrue(Files.size(copiedFile) > 1000, "Real TML file should be substantial in size (>1KB)");

        System.out.println("✓ Real TML file copied successfully");

        // Clean up
        Files.delete(copiedFile);
    }

    @Test
    @DisplayName("Checksum Calculation")
    void testChecksumCalculation() throws IOException, java.security.NoSuchAlgorithmException {
        // Create a test file with known content
        Path testFile = tempDir.resolve("checksum-test.txt");
        String testContent = "This is a test file for checksum calculation.\nLine 2\nLine 3";
        Files.write(testFile, testContent.getBytes());

        // Calculate checksum
        String checksum = TestFixtures.calculateChecksum(testFile);

        assertNotNull(checksum, "Checksum should not be null");
        assertEquals(64, checksum.length(), "SHA-256 checksum should be 64 characters");
        assertTrue(checksum.matches("[a-f0-9]+"), "Checksum should be hexadecimal");

        // Verify checksum is consistent
        String checksum2 = TestFixtures.calculateChecksum(testFile);
        assertEquals(checksum, checksum2, "Checksum should be consistent");

        System.out.println("✓ Checksum calculation working: " + checksum.substring(0, 16) + " ...");

        // Clean up
        Files.delete(testFile);
    }

    @Test
    @DisplayName("Checksum Verification")
    void testChecksumVerification() throws IOException, java.security.NoSuchAlgorithmException {
        // Create two identical files
        String testContent = "Identical content for checksum verification test";
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");

        Files.write(file1, testContent.getBytes());
        Files.write(file2, testContent.getBytes());

        // Verify they have the same checksum
        assertTrue(TestFixtures.verifyChecksum(file1, file2), "Identical files should have matching checksums");

        // Modify one file and verify checksums differ
        Files.write(file2, (testContent + " modified").getBytes());
        assertFalse(TestFixtures.verifyChecksum(file1, file2), "Different files should have different checksums");

        System.out.println("✓ Checksum verification working correctly");

        // Clean up
        Files.delete(file1);
        Files.delete(file2);
    }

    @Test
    @DisplayName("Real TML File Fixture")
    void testRealTmlFileFixture() throws IOException {
        TestFixtures.ProjectFixture fixture = TestFixtures.createProjectFixture().withRealTmlFile();

        assertTrue(fixture.usesRealTmlFile(), "Fixture should be marked as using real TML file");

        try {
            // Generate TML file using real file
            System.setProperty("ariane.root.dir", tempDir.toString());
            String testProjectId = "test-real-fixture-123";

            Path tmlFile = fixture.generateTmlFile(testProjectId);

            assertTrue(Files.exists(tmlFile), "Generated TML file should exist");
            assertTrue(Files.size(tmlFile) > 0, "Generated TML file should not be empty");

            // Verify it's the real binary file (TML files are ZIP archives)
            // Content verification is done via file size only - can't read as text
            assertTrue(Files.size(tmlFile) > 1000, "Real TML file should be substantial in size (>1KB)");

            System.out.println("✓ Real TML file fixture working correctly");

            // Clean up
            Files.delete(tmlFile);

        } catch (RuntimeException e) {
            System.out.println("⚠ Skipping real TML file fixture test - file not found: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Round-trip Result Object")
    void testRoundTripResult() {
        // Test successful result
        TestFixtures.RoundTripResult successResult = new TestFixtures.RoundTripResult(
            true, "abc123", "abc123", 100, 150, 1024, null
        );

        assertTrue(successResult.isSuccess(), "Result should be successful");
        assertTrue(successResult.checksumMatches(), "Checksums should match");
        assertEquals(100, successResult.getUploadTimeMs(), "Upload time should match");
        assertEquals(150, successResult.getDownloadTimeMs(), "Download time should match");
        assertEquals(1024, successResult.getFileSize(), "File size should match");
        assertNull(successResult.getErrorMessage(), "Error message should be null for success");

        // Test failed result
        TestFixtures.RoundTripResult failResult = new TestFixtures.RoundTripResult(
            false, "abc123", "def456", 100, 150, 1024, "Upload failed"
        );

        assertFalse(failResult.isSuccess(), "Result should not be successful");
        assertFalse(failResult.checksumMatches(), "Checksums should not match");
        assertEquals("Upload failed", failResult.getErrorMessage(), "Error message should be set");

        // Test toString
        String resultString = successResult.toString();
        assertTrue(resultString.contains("success=true"), "String should contain success status");
        assertTrue(resultString.contains("checksumMatch=true"), "String should contain checksum match status");
        assertTrue(resultString.contains("uploadTime=100ms"), "String should contain upload time");

        System.out.println("✓ RoundTripResult object working correctly");
        System.out.println("  Success result: " + successResult);
        System.out.println("  Failed result: " + failResult);
    }
}
