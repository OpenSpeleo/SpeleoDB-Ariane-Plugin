package org.speleodb.ariane.plugin.speleodb;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

/**
 * Comprehensive API test suite for SpeleoDB endpoints
 * Tests all major API functionality including authentication, projects, and file operations
 * Uses fixtures for consistent test data and includes round-trip testing
 *
 * Requires .env file with:
 * - SPELEODB_INSTANCE_URL
 * - SPELEODB_OAUTH_TOKEN or SPELEODB_EMAIL/SPELEODB_PASSWORD
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SpeleoDBAPITest {

    private static SpeleoDBService service;
    private static SpeleoDBController testController;
    private static JsonObject testProject;
    private static JsonObject minimalProject;
    private static JsonObject comprehensiveProject;
    private static boolean skipTests = false;

    // Test configuration
    private static final int API_TIMEOUT_SECONDS = 30;
    private static final int RETRY_COUNT = 3;

    private static SpeleoDBPlugin testPlugin;

    private static String capturedURL;
    private static String capturedHeaders;
    private static String capturedBody;

    @BeforeAll
    static void setupEnvironment() {
        System.out.println("=== SpeleoDB API Test Suite ===");
        System.out.println("Using Fixtures and Round-trip Testing");

        // The lazy loader only throws RuntimeException at worst. Catching Exception
        // (rather than Throwable) keeps Errors -- OOM, LinkageError, StackOverflow --
        // visible instead of disguising them as "all tests skipped".
        try {
            TestEnvironmentConfig.printConfigStatus();
        } catch (Exception e) {
            System.out.println("SETUP WARNING: Cannot run SpeleoDB API tests!");
            System.out.println("   Reason: " + e.getMessage());
            System.out.println("   All API tests will be skipped.");
            skipTests = true;
            return;
        }

        if (!TestEnvironmentConfig.isApiTestEnabled()) {
            System.out.println("FAIL: API testing is disabled. Set API_TEST_ENABLED=true to enable.");
            skipTests = true;
            return;
        }

        if (!TestEnvironmentConfig.hasRequiredConfig()) {
            System.out.println("FAIL: Required configuration missing. Please set SPELEODB_INSTANCE_URL "
                + "and an auth method (token or email+password) via .env, env vars, or -D system properties.");
            skipTests = true;
            return;
        }

        System.out.println("OK: Environment setup complete. Running API tests...");
    }

    @BeforeEach
    void setUp() {
        // Reset singleton instance before each test
        SpeleoDBController.resetInstance();

        // Create real plugin instance (not a mock) since we need the final executorService field
        testPlugin = new SpeleoDBPlugin();

        // Get controller instance and set up
        testController = SpeleoDBController.getInstance();
        testController.parentPlugin = testPlugin;

        // Create real service instance without server communication
        service = new SpeleoDBService(testController);

        // Reset captured values
        capturedURL = null;
        capturedHeaders = null;
        capturedBody = null;
    }

    /**
     * Authenticate before each test using OAuth (preferred method)
     * Skip authentication for the email/password test since it handles its own auth
     */
    @BeforeEach
    void authenticateForTest(TestInfo testInfo) throws Exception {
        assumeFalse(skipTests, "API tests are disabled or misconfigured");

        // Skip auto-authentication for the email/password authentication test
        String testMethodName = testInfo.getTestMethod().get().getName();
        if ("testAuthenticationWithCredentials".equals(testMethodName)) {
            System.out.println("SKIP: Skipping auto-authentication for email/password test");
            return;
        }

        // Authenticate using OAuth for all other tests
        String oauthToken = TestEnvironmentConfig.get(TestEnvironmentConfig.SPELEODB_OAUTH_TOKEN);
        String instanceUrl = TestEnvironmentConfig.get(TestEnvironmentConfig.SPELEODB_INSTANCE_URL);

        if (oauthToken != null && !oauthToken.isEmpty()) {
            service.authenticate(null, null, oauthToken, instanceUrl);
            System.out.println("AUTH: Auto-authenticated with OAuth for test: " + testInfo.getDisplayName());
        } else {
            throw new RuntimeException("OAuth token not available for auto-authentication");
        }
    }

    /**
     * Logout after each test to ensure clean state
     * Always logout regardless of test type
     */
    @AfterEach
    void logoutAfterTest(TestInfo testInfo) {
        if (service != null) {
            service.logout();
            System.out.println("LOGOUT: Auto-logout completed for test: " + testInfo.getDisplayName());
        }
    }

    @Test
    @Order(1)
    @DisplayName("Authentication - OAuth Token")
    void testAuthenticationWithOAuth() throws Exception {
        assumeFalse(skipTests, "API tests are disabled or misconfigured");

        String oauthToken = TestEnvironmentConfig.get(TestEnvironmentConfig.SPELEODB_OAUTH_TOKEN);
        String instanceUrl = TestEnvironmentConfig.get(TestEnvironmentConfig.SPELEODB_INSTANCE_URL);

        if (oauthToken != null && !oauthToken.isEmpty()) {
            System.out.println("Testing OAuth authentication...");

            // Authentication is already handled by @BeforeEach
            // Just verify that we're authenticated
            assertTrue(service.isAuthenticated(), "Service should be authenticated via OAuth");
            assertNotNull(service.getSDBInstance(), "SDB instance should be set after authentication");

            System.out.println("OK: OAuth authentication successful");
        } else {
            System.out.println("No OAuth token provided, skipping OAuth test");
        }
    }

    @Test
    @Order(2)
    @DisplayName("Authentication - Email/Password")
    void testAuthenticationWithCredentials() throws Exception {
        assumeFalse(skipTests, "API tests are disabled or misconfigured");

        String email = TestEnvironmentConfig.get(TestEnvironmentConfig.SPELEODB_EMAIL);
        String password = TestEnvironmentConfig.get(TestEnvironmentConfig.SPELEODB_PASSWORD);
        String instanceUrl = TestEnvironmentConfig.get(TestEnvironmentConfig.SPELEODB_INSTANCE_URL);

        if (email != null && !email.isEmpty() && password != null && !password.isEmpty()) {
            System.out.println("Testing email/password authentication...");

            // Ensure we start with a clean state (no auto-authentication was done)
            assertFalse(service.isAuthenticated(), "Service should not be authenticated at start of email/password test");

            // Test email/password authentication
            assertDoesNotThrow(() -> {
                service.authenticate(email, password, null, instanceUrl);
            }, "Email/password authentication should not throw exception");

            assertTrue(service.isAuthenticated(), "Service should be authenticated after email/password login");
            assertNotNull(service.getSDBInstance(), "SDB instance should be set after authentication");

            System.out.println("OK: Email/password authentication successful");

            // Logout to clean state (AfterEach will also logout, but this is explicit)
            service.logout();
            assertFalse(service.isAuthenticated(), "Service should not be authenticated after explicit logout");

        } else {
            System.out.println("No email/password provided, skipping credential test");
        }
    }

    @Test
    @Order(3)
    @DisplayName("Authentication - Invalid Credentials")
    void testAuthenticationFailure() {
        assumeFalse(skipTests, "API tests are disabled or misconfigured");

        String instanceUrl = TestEnvironmentConfig.get(TestEnvironmentConfig.SPELEODB_INSTANCE_URL);

        System.out.println("Testing authentication with invalid credentials...");

        // Store current authentication state
        boolean wasAuthenticated = service.isAuthenticated();

        // Logout temporarily to test invalid credentials
        service.logout();

        // Test with invalid credentials
        Exception exception = assertThrows(Exception.class, () -> {
            service.authenticate("invalid@email.com", "wrongpassword", null, instanceUrl);
        }, "Authentication with invalid credentials should throw exception");

        assertFalse(service.isAuthenticated(), "Service should not be authenticated after failed login");
        assertTrue(exception.getMessage().contains("Authentication failed"),
                   "Exception message should indicate authentication failure");

        System.out.println("OK: Invalid credential handling works correctly");

        // Note: AfterEach will handle logout, and next test's BeforeEach will re-authenticate
    }

    @Test
    @Order(4)
    @DisplayName("Project Management - List Projects")
    void testListProjects() throws Exception {
        assumeFalse(skipTests, "API tests are disabled or misconfigured");
        // Authentication is handled by @BeforeEach

        System.out.println("Testing project listing...");

        JsonArray projects = retryOperation(() -> service.listProjects());

        assertNotNull(projects, "Projects list should not be null");
        System.out.println("OK: Successfully retrieved " + projects.size() + " projects");

        // Validate project structure if any projects exist
        if (projects.size() > 0) {
            JsonObject firstProject = projects.getJsonObject(0);
            assertTrue(firstProject.containsKey("id"), "Project should have ID field");
            assertTrue(firstProject.containsKey("name"), "Project should have name field");
            System.out.println("OK: Project structure validation passed");
        }
    }

    @Test
    @Order(5)
    @DisplayName("Fixtures - Create Standard Project")
    void testCreateStandardProject() throws Exception {
        assumeFalse(skipTests, "API tests are disabled or misconfigured");
        // Authentication is handled by @BeforeEach

        System.out.println("Testing standard project creation using fixtures...");

        // Create project using fixture
        TestFixtures.ProjectFixture fixture = TestFixtures.createProjectFixture();
        System.out.println("Creating project: " + fixture.getName());

        testProject = retryOperation(() -> fixture.create(service));

        assertNotNull(testProject, "Created project should not be null");
        assertTrue(testProject.containsKey("id"), "Created project should have ID");
        assertTrue(testProject.containsKey("name"), "Created project should have name");
        assertEquals(fixture.getName(), testProject.getString("name"), "Project name should match fixture");

        System.out.println("OK: Successfully created standard project: " + testProject.getString("name"));
        System.out.println("  Project ID: " + testProject.getString("id"));
    }

    @Test
    @Order(6)
    @DisplayName("Fixtures - Create Minimal Project")
    void testCreateMinimalProject() throws Exception {
        assumeFalse(skipTests, "API tests are disabled or misconfigured");
        // Authentication is handled by @BeforeEach

        System.out.println("Testing minimal project creation using fixtures...");

        // Create minimal project using fixture
        TestFixtures.ProjectFixture fixture = TestFixtures.createMinimalProjectFixture();
        System.out.println("Creating minimal project: " + fixture.getName());

        minimalProject = retryOperation(() -> fixture.create(service));

        assertNotNull(minimalProject, "Created minimal project should not be null");
        assertTrue(minimalProject.containsKey("id"), "Created project should have ID");
        assertTrue(minimalProject.containsKey("name"), "Created project should have name");
        assertEquals(fixture.getName(), minimalProject.getString("name"), "Project name should match fixture");

        System.out.println("OK: Successfully created minimal project: " + minimalProject.getString("name"));
        System.out.println("  Project ID: " + minimalProject.getString("id"));
    }

    @Test
    @Order(7)
    @DisplayName("Fixtures - Create Comprehensive Project")
    void testCreateComprehensiveProject() throws Exception {
        assumeFalse(skipTests, "API tests are disabled or misconfigured");
        // Authentication is handled by @BeforeEach

        System.out.println("Testing comprehensive project creation using fixtures...");

        // Create comprehensive project using fixture
        TestFixtures.ProjectFixture fixture = TestFixtures.createComprehensiveProjectFixture();
        System.out.println("Creating comprehensive project: " + fixture.getName());

        comprehensiveProject = retryOperation(() -> fixture.create(service));

        assertNotNull(comprehensiveProject, "Created comprehensive project should not be null");
        assertTrue(comprehensiveProject.containsKey("id"), "Created project should have ID");
        assertTrue(comprehensiveProject.containsKey("name"), "Created project should have name");
        assertEquals(fixture.getName(), comprehensiveProject.getString("name"), "Project name should match fixture");

        System.out.println("OK: Successfully created comprehensive project: " + comprehensiveProject.getString("name"));
        System.out.println("  Project ID: " + comprehensiveProject.getString("id"));
    }

    @Test
    @Order(8)
    @DisplayName("Project Management - Acquire Project Mutex")
    void testAcquireProjectMutex() throws Exception {
        assumeFalse(skipTests, "API tests are disabled or misconfigured");
        // Authentication is handled by @BeforeEach
        assumeTrue(testProject != null, "Test project must be created first");

        System.out.println("Testing project mutex acquisition...");

        boolean acquired = retryOperation(() -> service.acquireOrRefreshProjectMutex(testProject));

        assertTrue(acquired, "Should be able to acquire project mutex");
        System.out.println("OK: Successfully acquired project mutex");
    }

    @Test
    @Order(9)
    @DisplayName("Round-trip Testing - Standard Project Upload/Download")
    void testStandardProjectRoundTrip() throws Exception {
        assumeFalse(skipTests, "API tests are disabled or misconfigured");
        // Authentication is handled by @BeforeEach
        assumeTrue(testProject != null, "Test project must be created first");

        System.out.println("Testing standard project round-trip (upload -> download) with checksum verification...");

        // Acquire mutex first
        boolean acquired = retryOperation(() -> service.acquireOrRefreshProjectMutex(testProject));
        assertTrue(acquired, "Should be able to acquire test project mutex");

        // Create fixture and use real TML file
        TestFixtures.ProjectFixture fixture = TestFixtures.createProjectFixture().withRealTmlFile();
        Path originalTmlFile = fixture.generateTmlFile(testProject.getString("id"));

        try {
            // Calculate original checksum
            String originalChecksum = TestFixtures.calculateChecksum(originalTmlFile);
            System.out.println("  Original file checksum: " + originalChecksum.substring(0, 16) + "...");

            // Upload the file
            String uploadMessage = TestFixtures.generateUploadMessage();
            System.out.println("  Uploading real TML file...");
            long uploadStart = System.currentTimeMillis();

            assertDoesNotThrow(() -> {
                retryOperation(() -> {
                    service.uploadProject(uploadMessage, testProject);
                    return null;
                });
            }, "Project upload should not throw exception");

            long uploadTime = System.currentTimeMillis() - uploadStart;
            System.out.println("  OK: Upload successful (" + uploadTime + "ms)");

            // Download the file
            System.out.println("  Downloading TML file...");
            long downloadStart = System.currentTimeMillis();
            Path downloadedFile = retryOperation(() -> service.downloadProject(testProject));
            long downloadTime = System.currentTimeMillis() - downloadStart;

            assertNotNull(downloadedFile, "Downloaded file path should not be null");
            assertTrue(Files.exists(downloadedFile), "Downloaded file should exist");
            assertTrue(Files.size(downloadedFile) > 0, "Downloaded file should not be empty");

            System.out.println("  OK: Download successful (" + downloadTime + "ms)");

            // Verify checksum
            String downloadedChecksum = TestFixtures.calculateChecksum(downloadedFile);
            System.out.println("  Downloaded file checksum: " + downloadedChecksum.substring(0, 16) + "...");

            assertEquals(originalChecksum, downloadedChecksum, "Downloaded content checksum should match uploaded content");
            assertTrue(TestFixtures.verifyChecksum(originalTmlFile, downloadedFile), "Checksum verification should pass");

            System.out.println("  OK: Checksum verification passed");
            System.out.println("  File size: " + Files.size(downloadedFile) + " bytes");
            System.out.println("OK: Standard project round-trip test completed successfully");

            // Clean up downloaded file
            Files.delete(downloadedFile);

            // Release mutex
            service.releaseProjectMutex(testProject);

        } catch (Exception e) {
            System.err.println("Round-trip test failed: " + e.getMessage());
            throw e;
        } finally {
            // Clean up original test file
            if (Files.exists(originalTmlFile)) {
                Files.delete(originalTmlFile);
            }
        }
    }

    @Test
    @Order(10)
    @DisplayName("Round-trip Testing - Real TML File Upload/Download")
    void testRealTmlFileRoundTrip() throws Exception {
        assumeFalse(skipTests, "API tests are disabled or misconfigured");
        // Authentication is handled by @BeforeEach
        assumeTrue(minimalProject != null, "Minimal project must be created first");

        System.out.println("Testing real TML file round-trip with comprehensive verification...");

        // Acquire mutex first
        boolean acquired = retryOperation(() -> service.acquireOrRefreshProjectMutex(minimalProject));
        assertTrue(acquired, "Should be able to acquire minimal project mutex");

        // Use the real project.tml file
        Path originalTmlFile = TestFixtures.copyTestTmlFile(minimalProject.getString("id"));

        try {
            // Calculate original checksum and file info
            String originalChecksum = TestFixtures.calculateChecksum(originalTmlFile);
            long originalSize = Files.size(originalTmlFile);
            System.out.println("  Original TML file:");
            System.out.println("    Size: " + originalSize + " bytes");
            System.out.println("    Checksum: " + originalChecksum);

            // Upload the file
            String uploadMessage = "Real TML file upload test - " + Instant.now();
            System.out.println("  Uploading real project.tml file...");
            long uploadStart = System.currentTimeMillis();

            assertDoesNotThrow(() -> {
                retryOperation(() -> {
                    service.uploadProject(uploadMessage, minimalProject);
                    return null;
                });
            }, "Real TML file upload should not throw exception");

            long uploadTime = System.currentTimeMillis() - uploadStart;
            System.out.println("  OK: Upload successful (" + uploadTime + "ms)");

            // Download the file
            System.out.println("  Downloading TML file...");
            long downloadStart = System.currentTimeMillis();
            Path downloadedFile = retryOperation(() -> service.downloadProject(minimalProject));
            long downloadTime = System.currentTimeMillis() - downloadStart;

            assertNotNull(downloadedFile, "Downloaded file path should not be null");
            assertTrue(Files.exists(downloadedFile), "Downloaded file should exist");

            // Verify file properties
            long downloadedSize = Files.size(downloadedFile);
            String downloadedChecksum = TestFixtures.calculateChecksum(downloadedFile);

            System.out.println("  Downloaded TML file:");
            System.out.println("    Size: " + downloadedSize + " bytes");
            System.out.println("    Checksum: " + downloadedChecksum);

            // Comprehensive verification
            assertEquals(originalSize, downloadedSize, "Downloaded file size should match original");
            assertEquals(originalChecksum, downloadedChecksum, "Downloaded checksum should match original");
            assertTrue(TestFixtures.verifyChecksum(originalTmlFile, downloadedFile), "Binary comparison should pass");

            // Binary file verification (TML files are ZIP archives, not text)
            // Content verification is done via checksum comparison only
            System.out.println("  OK: Binary file integrity verified via checksum");

            System.out.println("  OK: Download successful (" + downloadTime + "ms)");
            System.out.println("  OK: Size verification passed");
            System.out.println("  OK: Checksum verification passed");
            System.out.println("OK: Real TML file round-trip test completed successfully");

            // Performance summary
            System.out.println("  Performance Summary:");
            System.out.println("    Upload time: " + uploadTime + "ms");
            System.out.println("    Download time: " + downloadTime + "ms");
            System.out.println("    Total round-trip time: " + (uploadTime + downloadTime) + "ms");

            // Clean up downloaded file
            Files.delete(downloadedFile);

            // Release mutex
            service.releaseProjectMutex(minimalProject);

        } catch (Exception e) {
            System.err.println("Real TML file round-trip test failed: " + e.getMessage());
            throw e;
        } finally {
            // Clean up original test file
            if (Files.exists(originalTmlFile)) {
                Files.delete(originalTmlFile);
            }
        }
    }

    @Test
    @Order(13)
    @DisplayName("Project Management - Release Project Mutex")
    void testReleaseProjectMutex() throws Exception {
        assumeFalse(skipTests, "API tests are disabled or misconfigured");
        // Authentication is handled by @BeforeEach
        assumeTrue(testProject != null, "Test project must be created first");

        System.out.println("Testing project mutex release...");

        boolean released = retryOperation(() -> service.releaseProjectMutex(testProject));

        assertTrue(released, "Should be able to release project mutex");
        System.out.println("OK: Successfully released project mutex");
    }

    @Test
    @Order(14)
    @DisplayName("Authentication - Logout")
    void testLogout() {
        assumeFalse(skipTests, "API tests are disabled or misconfigured");

        System.out.println("Testing logout...");

        service.logout();

        assertFalse(service.isAuthenticated(), "Service should not be authenticated after logout");

        // Test that operations fail after logout
        assertThrows(IllegalStateException.class, () -> {
            service.listProjects();
        }, "Operations should fail after logout");

        System.out.println("OK: Successfully logged out");
    }

    @Test
    @Order(15)
    @DisplayName("Performance - API Response Times")
    void testAPIPerformance() throws Exception {
        assumeFalse(skipTests, "API tests are disabled or misconfigured");

        System.out.println("Testing API performance...");

        // Re-authenticate for performance test
        reAuthenticate();

        // Test authentication performance
        long authStart = System.currentTimeMillis();
        reAuthenticate();
        long authTime = System.currentTimeMillis() - authStart;

        // Test list projects performance
        long listStart = System.currentTimeMillis();
        service.listProjects();
        long listTime = System.currentTimeMillis() - listStart;

        System.out.println("OK: Performance Results:");
        System.out.println("  Authentication: " + authTime + "ms");
        System.out.println("  List Projects: " + listTime + "ms");

        // Performance assertions (reasonable timeouts)
        int timeoutMs = TestEnvironmentConfig.getInt(TestEnvironmentConfig.API_TIMEOUT_MS, 10000);
        assertTrue(authTime < timeoutMs, "Authentication should complete within " + timeoutMs + "ms");
        assertTrue(listTime < timeoutMs, "List projects should complete within " + timeoutMs + "ms");
    }

    // ========================= UTILITY METHODS ========================= //

    /**
     * Re-authenticate using available credentials
     */
    private static void reAuthenticate() {
        try {
            String oauthToken = TestEnvironmentConfig.get(TestEnvironmentConfig.SPELEODB_OAUTH_TOKEN);
            String email = TestEnvironmentConfig.get(TestEnvironmentConfig.SPELEODB_EMAIL);
            String password = TestEnvironmentConfig.get(TestEnvironmentConfig.SPELEODB_PASSWORD);
            String instanceUrl = TestEnvironmentConfig.get(TestEnvironmentConfig.SPELEODB_INSTANCE_URL);

            if (oauthToken != null && !oauthToken.isEmpty()) {
                service.authenticate(null, null, oauthToken, instanceUrl);
            } else if (email != null && password != null) {
                service.authenticate(email, password, null, instanceUrl);
            } else {
                throw new IllegalStateException("No valid authentication credentials available");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to re-authenticate: " + e.getMessage(), e);
        }
    }

    /**
     * Retry operation with exponential backoff
     */
    private static <T> T retryOperation(ThrowingSupplier<T> operation) throws Exception {
        Exception lastException = null;
        int retryCount = TestEnvironmentConfig.getInt(TestEnvironmentConfig.API_RETRY_COUNT, RETRY_COUNT);

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt < retryCount) {
                    long delay = (long) Math.pow(2, attempt - 1) * 1000; // Exponential backoff
                    System.out.println("  Attempt " + attempt + " failed, retrying in " + delay + "ms: " + e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry delay", ie);
                    }
                }
            }
        }

        throw new Exception("Operation failed after " + retryCount + " retries", lastException);
    }

    /**
     * Functional interface for operations that can throw exceptions
     */
    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @AfterEach
    void tearDown() {
        // Reset singleton instance after each test to ensure clean state
        SpeleoDBController.resetInstance();
        // Shutdown the executor from the plugin
        if (testPlugin != null && testPlugin.executorService != null) {
            testPlugin.executorService.shutdownNow();
        }
    }

    @AfterAll
    static void cleanup() {
        if (service != null && service.isAuthenticated()) {
            System.out.println("Cleaning up test resources...");

            // Release mutexes for all created projects
            for (JsonObject project : TestFixtures.getCreatedProjects()) {
                try {
                    service.releaseProjectMutex(project);
                    System.out.println("OK: Released mutex for project: " + project.getString("name"));
                } catch (Exception e) {
                    System.err.println("Failed to release mutex for project " + project.getString("name") + ": " + e.getMessage());
                }
            }

            // Logout
            service.logout();
            System.out.println("OK: Logged out");
        }

        // Clear fixtures
        TestFixtures.clearCreatedProjects();

        System.out.println("=== API Test Suite Complete ===");
        System.out.println("Fixtures used: " + TestFixtures.getCreatedProjects().size() + " projects created and cleaned up");
    }
}
