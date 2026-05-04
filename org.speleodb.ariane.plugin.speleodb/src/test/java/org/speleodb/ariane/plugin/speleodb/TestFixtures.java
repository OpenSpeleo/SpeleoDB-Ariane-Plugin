package org.speleodb.ariane.plugin.speleodb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.PATHS;

import jakarta.json.JsonObject;

/**
 * Test fixtures for SpeleoDB API testing
 * Provides reusable test data and objects for consistent testing
 * Includes checksum verification for round-trip testing integrity
 */
public class TestFixtures {

    private static final Random random = new Random();
    private static final String testRunId = String.valueOf(System.currentTimeMillis());
    private static final java.util.concurrent.atomic.AtomicInteger uniqueCounter = new java.util.concurrent.atomic.AtomicInteger(0);

    // Track created projects for cleanup
    private static final List<JsonObject> createdProjects = new ArrayList<>();

    // Path to the test TML file
    private static final String TEST_TML_FILE = "project.tml";
    private static final String TEST_ARTIFACTS_PATH = "src/test/resources/artifacts";

    /**
     * Create a test project fixture with random but realistic data
     */
    public static ProjectFixture createProjectFixture() {
        return new ProjectFixture();
    }

    /**
     * Create a minimal test project fixture
     */
    public static ProjectFixture createMinimalProjectFixture() {
        return new ProjectFixture().minimal();
    }

    /**
     * Create a comprehensive test project fixture with full data
     */
    public static ProjectFixture createComprehensiveProjectFixture() {
        return new ProjectFixture().comprehensive();
    }

    /**
     * Register a created project for cleanup tracking
     */
    public static void registerCreatedProject(JsonObject project) {
        if (project != null && project.containsKey("id")) {
            createdProjects.add(project);
        }
    }

    /**
     * Get all created projects for cleanup
     */
    public static List<JsonObject> getCreatedProjects() {
        return new ArrayList<>(createdProjects);
    }

    /**
     * Clear the created projects list
     */
    public static void clearCreatedProjects() {
        createdProjects.clear();
    }

    /**
     * Calculate SHA-256 checksum of a file.
     * Delegates to SpeleoDBService.calculateSHA256 to avoid code duplication.
     */
    public static String calculateChecksum(Path filePath) throws IOException, NoSuchAlgorithmException {
        byte[] fileBytes = Files.readAllBytes(filePath);
        return SpeleoDBService.calculateSHA256(fileBytes);
    }

    /**
     * Get the path to the test TML file in test artifacts directory
     */
    public static Path getTestTmlFile() {
        // Try multiple possible locations for the test artifacts
        String[] possiblePaths = {
            TEST_ARTIFACTS_PATH + "/" + TEST_TML_FILE,  // From project root
            "org.speleodb.ariane.plugin.speleodb/" + TEST_ARTIFACTS_PATH + "/" + TEST_TML_FILE,  // From monorepo root
            "../" + TEST_ARTIFACTS_PATH + "/" + TEST_TML_FILE,  // From subdirectory
            "../../" + TEST_ARTIFACTS_PATH + "/" + TEST_TML_FILE  // From deeper subdirectory
        };

        for (String pathStr : possiblePaths) {
            Path tmlFile = Path.of(pathStr).toAbsolutePath().normalize();
            if (Files.exists(tmlFile)) {
                return tmlFile;
            }
        }

        // Also try using the class loader to find the resource
        try {
            ClassLoader classLoader = TestFixtures.class.getClassLoader();
            java.net.URL resource = classLoader.getResource("artifacts/" + TEST_TML_FILE);
            if (resource != null) {
                return Path.of(resource.toURI());
            }
        } catch (Exception e) {
            // Ignore and continue with file system search
        }

        throw new RuntimeException("Test TML file not found: " + TEST_TML_FILE +
                                 ". Please ensure " + TEST_TML_FILE + " exists in " + TEST_ARTIFACTS_PATH +
                                 " directory. Searched paths: " + String.join(", ", possiblePaths));
    }

    /**
     * Copy the test TML file to the specified location for upload
     */
    public static Path copyTestTmlFile(String projectId) throws IOException {
        Path sourceTml = getTestTmlFile();

        String arianeRootDir = PATHS.SDB_PROJECT_DIR;
        Path arianeDir = Path.of(arianeRootDir);

        // Ensure directory exists
        if (!Files.exists(arianeDir)) {
            Files.createDirectories(arianeDir);
        }

        Path targetTml = arianeDir.resolve(projectId + PATHS.TML_FILE_EXTENSION);
        Files.copy(sourceTml, targetTml, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        return targetTml;
    }

    /**
     * Verify that two files have the same checksum
     */
    public static boolean verifyChecksum(Path file1, Path file2) throws IOException, NoSuchAlgorithmException {
        String checksum1 = calculateChecksum(file1);
        String checksum2 = calculateChecksum(file2);
        return checksum1.equals(checksum2);
    }

    /**
     * Generate a random project name
     */
    public static String generateProjectName() {
        String[] adjectives = {"Amazing", "Mysterious", "Deep", "Crystal", "Hidden", "Ancient", "Sacred", "Lost", "Wild", "Secret"};
        String[] nouns = {"Cave", "Cavern", "Grotto", "Chamber", "System", "Network", "Passage", "Tunnel", "Vault", "Labyrinth"};

        String adjective = adjectives[random.nextInt(adjectives.length)];
        String noun = nouns[random.nextInt(nouns.length)];
        int uniqueId = uniqueCounter.incrementAndGet();

        return adjective + " " + noun + " - Test " + testRunId + "-" + uniqueId;
    }

    /**
     * Generate a random project description
     */
    public static String generateProjectDescription() {
        String[] descriptions = {
            "Automated API test cave system with complex passages",
            "Test cave network for API validation and testing",
            "Randomly generated cave system for comprehensive testing",
            "API test project with simulated cave survey data",
            "Test cave complex for SpeleoDB API functionality verification"
        };

        return descriptions[random.nextInt(descriptions.length)] + " (Run: " + testRunId + ")";
    }

    /**
     * Generate a random country code
     */
    public static String generateCountryCode() {
        String[] countries = {"US", "FR", "ES", "IT", "DE", "GB", "CA", "AU", "NZ", "CH"};
        return countries[random.nextInt(countries.length)];
    }

    /**
     * Generate a random latitude (-90 to 90)
     */
    public static double generateLatitude() {
        return (random.nextDouble() * 180.0) - 90.0;
    }

    /**
     * Generate a random longitude (-180 to 180)
     */
    public static double generateLongitude() {
        return (random.nextDouble() * 360.0) - 180.0;
    }

    /**
     * Generate a random cave name for TML content
     */
    public static String generateCaveName() {
        String[] prefixes = {"Thunder", "Crystal", "Echo", "Shadow", "Mystic", "Dragon", "Silver", "Golden", "Frozen", "Hidden"};
        String[] suffixes = {"Falls", "Dome", "Hall", "Chamber", "Passage", "Gallery", "Room", "Corridor", "Pit", "Shaft"};

        String prefix = prefixes[random.nextInt(prefixes.length)];
        String suffix = suffixes[random.nextInt(suffixes.length)];

        return prefix + " " + suffix;
    }

    /**
     * Generate a random upload message
     */
    public static String generateUploadMessage() {
        String[] messages = {
            "API test upload",
            "Automated test file upload",
            "Test data upload for validation",
            "Random test file upload",
            "API testing file upload",
            "Round-trip test upload",
            "Fixture-based test upload",
            "Checksum verification upload"
        };

        return messages[random.nextInt(messages.length)] + " - " + Instant.now().toString();
    }

    /**
     * Project fixture builder class
     */
    public static class ProjectFixture {
        private String name;
        private String description;
        private String countryCode;
        private Double latitude;
        private Double longitude;
        private boolean isMinimal = false;
        private boolean isComprehensive = false;
        private boolean useRealTmlFile = false;

        public ProjectFixture() {
            // Default random values
            this.name = generateProjectName();
            this.description = generateProjectDescription();
            this.countryCode = generateCountryCode();
            this.latitude = generateLatitude();
            this.longitude = generateLongitude();
        }

        public ProjectFixture minimal() {
            this.isMinimal = true;
            this.latitude = null;
            this.longitude = null;
            return this;
        }

        public ProjectFixture comprehensive() {
            this.isComprehensive = true;
            // Add extra metadata for comprehensive testing
            this.description += " [COMPREHENSIVE TEST]";
            return this;
        }

        public ProjectFixture withRealTmlFile() {
            this.useRealTmlFile = true;
            return this;
        }

        public ProjectFixture withName(String name) {
            this.name = name;
            return this;
        }

        public ProjectFixture withDescription(String description) {
            this.description = description;
            return this;
        }

        public ProjectFixture withCountry(String countryCode) {
            this.countryCode = countryCode;
            return this;
        }

        public ProjectFixture withCoordinates(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
            return this;
        }

        public ProjectFixture withoutCoordinates() {
            this.latitude = null;
            this.longitude = null;
            return this;
        }

        // Getters
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getCountryCode() { return countryCode; }
        public String getLatitude() { return latitude != null ? String.valueOf(latitude) : null; }
        public String getLongitude() { return longitude != null ? String.valueOf(longitude) : null; }
        public boolean isMinimal() { return isMinimal; }
        public boolean isComprehensive() { return isComprehensive; }
        public boolean usesRealTmlFile() { return useRealTmlFile; }

        /**
         * Create the project using the SpeleoDB service
         */
        public JsonObject create(SpeleoDBService service) throws Exception {
            JsonObject project = service.createProject(name, description, countryCode, getLatitude(), getLongitude());
            registerCreatedProject(project);
            return project;
        }

        /**
         * Generate a TML file for this project
         * If useRealTmlFile is true, copies the real project.tml file
         * Otherwise generates synthetic TML content
         */
        public Path generateTmlFile(String projectId) throws IOException {
            if (useRealTmlFile) {
                return copyTestTmlFile(projectId);
            } else {
                return generateSyntheticTmlFile(projectId);
            }
        }

        /**
         * Generate synthetic TML content for testing (legacy method)
         */
        private Path generateSyntheticTmlFile(String projectId) throws IOException {
            String arianeRootDir = PATHS.SDB_PROJECT_DIR;
            Path arianeDir = Path.of(arianeRootDir);

            // Ensure directory exists
            if (!Files.exists(arianeDir)) {
                Files.createDirectories(arianeDir);
            }

            Path tmlFile = arianeDir.resolve(projectId + PATHS.TML_FILE_EXTENSION);

            // Create TML content based on fixture type
            String tmlContent = generateTmlContent();

            Files.write(tmlFile, tmlContent.getBytes());
            return tmlFile;
        }

        /**
         * Generate TML content based on fixture configuration
         * Now reads from the empty_project.tml template instead of generating synthetic content
         */
        private String generateTmlContent() {
            // Read the template file that the actual application uses
            try (var templateStream = TestFixtures.class.getResourceAsStream(PATHS.EMPTY_TML)) {
                if (templateStream == null) {
                    throw new RuntimeException("Template file `" + PATHS.EMPTY_TML + "` not found in resources");
                }

                // Read the template content
                return new String(templateStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

            } catch (Exception e) {
                throw new RuntimeException("Failed to read `" + PATHS.EMPTY_TML + "` template: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Result of a round-trip test with checksum verification
     */
    public static class RoundTripResult {
        private final boolean success;
        private final String originalChecksum;
        private final String downloadedChecksum;
        private final long uploadTimeMs;
        private final long downloadTimeMs;
        private final long fileSize;
        private final String errorMessage;

        public RoundTripResult(boolean success, String originalChecksum, String downloadedChecksum,
                              long uploadTimeMs, long downloadTimeMs, long fileSize, String errorMessage) {
            this.success = success;
            this.originalChecksum = originalChecksum;
            this.downloadedChecksum = downloadedChecksum;
            this.uploadTimeMs = uploadTimeMs;
            this.downloadTimeMs = downloadTimeMs;
            this.fileSize = fileSize;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public String getOriginalChecksum() { return originalChecksum; }
        public String getDownloadedChecksum() { return downloadedChecksum; }
        public long getUploadTimeMs() { return uploadTimeMs; }
        public long getDownloadTimeMs() { return downloadTimeMs; }
        public long getFileSize() { return fileSize; }
        public String getErrorMessage() { return errorMessage; }
        public boolean checksumMatches() {
            return originalChecksum != null && originalChecksum.equals(downloadedChecksum);
        }

        @Override
        public String toString() {
            return String.format("RoundTripResult{success=%s, checksumMatch=%s, uploadTime=%dms, downloadTime=%dms, fileSize=%d bytes%s}",
                success, checksumMatches(), uploadTimeMs, downloadTimeMs, fileSize,
                errorMessage != null ? ", error='" + errorMessage + "'" : "");
        }
    }
}
