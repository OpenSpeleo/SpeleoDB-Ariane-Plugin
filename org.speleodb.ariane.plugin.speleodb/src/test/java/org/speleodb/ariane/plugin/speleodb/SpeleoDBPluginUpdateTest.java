package org.speleodb.ariane.plugin.speleodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

/**
 * Test class for SpeleoDB plugin update functionality.
 * Tests version comparison, release filtering, and update logic.
 */
@DisplayName("SpeleoDB Plugin Update Tests")
class SpeleoDBPluginUpdateTest {

    @Mock
    private SpeleoDBPlugin mockPlugin;

    private SpeleoDBController controller;

    @BeforeEach
    void setUp() {
        // Reset singleton instance before each test
        SpeleoDBController.resetInstance();
        controller = SpeleoDBController.getInstance();
        MockitoAnnotations.openMocks(this);
        controller.parentPlugin = mockPlugin;
    }

    @AfterEach
    void tearDown() {
        // Reset singleton instance after each test to ensure clean state
        SpeleoDBController.resetInstance();
    }

    @Nested
    @DisplayName("Version Comparison Tests")
    class VersionComparisonTests {

        @Test
        @DisplayName("Should compare CalVer versions correctly")
        void shouldCompareCalVerVersionsCorrectly() {
            // Now compareVersions is a static package-private method, so we can call it directly
            // Test equal versions
            assertEquals(0, SpeleoDBController.compareVersions("2025.06.23", "2025.06.23"));

            // Test newer version
            assertTrue(SpeleoDBController.compareVersions("2025.06.24", "2025.06.23") > 0);
            assertTrue(SpeleoDBController.compareVersions("2025.07.01", "2025.06.23") > 0);
            assertTrue(SpeleoDBController.compareVersions("2026.01.01", "2025.12.31") > 0);

            // Test older version
            assertTrue(SpeleoDBController.compareVersions("2025.06.22", "2025.06.23") < 0);
            assertTrue(SpeleoDBController.compareVersions("2025.05.23", "2025.06.23") < 0);
            assertTrue(SpeleoDBController.compareVersions("2024.12.31", "2025.01.01") < 0);

            // Test null handling
            assertEquals(0, SpeleoDBController.compareVersions(null, null));
            assertTrue(SpeleoDBController.compareVersions("2025.06.23", null) > 0);
            assertTrue(SpeleoDBController.compareVersions(null, "2025.06.23") < 0);
        }

        @Test
        @DisplayName("Should handle malformed version strings")
        void shouldHandleMalformedVersionStrings() {
            // Test with non-numeric parts
            assertEquals(0, SpeleoDBController.compareVersions("2025.06.23-beta", "2025.06.23-alpha"));
            assertTrue(SpeleoDBController.compareVersions("2025.06.24", "2025.06.23-beta") > 0);
        }
    }

    @Nested
    @DisplayName("Hash Verification Tests")
    class HashVerificationTests {

        @Test
        @DisplayName("Should verify SHA256 hash correctly")
        void shouldVerifySHA256HashCorrectly() {
            try {
                var method = SpeleoDBController.class.getDeclaredMethod("verifyFileHash", byte[].class, String.class);
                method.setAccessible(true);

                // Test with known data and hash
                String testData = "Hello, World!";
                byte[] testDataBytes = testData.getBytes();
                String expectedHash = "dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f";

                assertTrue((Boolean) method.invoke(controller, testDataBytes, expectedHash));
                assertFalse((Boolean) method.invoke(controller, testDataBytes, "wrong_hash"));

                // Test case insensitivity
                assertTrue((Boolean) method.invoke(controller, testDataBytes, expectedHash.toUpperCase()));

            } catch (Exception e) {
                throw new RuntimeException("Failed to test hash verification", e);
            }
        }

        @Test
        @DisplayName("Should handle invalid hash gracefully")
        void shouldHandleInvalidHashGracefully() {
            try {
                var method = SpeleoDBController.class.getDeclaredMethod("verifyFileHash", byte[].class, String.class);
                method.setAccessible(true);

                byte[] testData = "test".getBytes();

                // Test with null hash
                assertFalse((Boolean) method.invoke(controller, testData, null));

                // Test with empty hash
                assertFalse((Boolean) method.invoke(controller, testData, ""));

            } catch (IllegalAccessException | NoSuchMethodException | SecurityException | InvocationTargetException e) {
                throw new RuntimeException("Failed to test invalid hash handling", e);
            }
        }
    }

    @Nested
    @DisplayName("Release Filtering Tests")
    class ReleaseFilteringTests {

        @Test
        @DisplayName("Should filter releases correctly by version bounds")
        void shouldFilterReleasesCorrectly() {
            // Create test release data with version bounds
            JsonArray releases = Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                    .add(SpeleoDBConstants.JSON_FIELDS.SOFTWARE, "ARIANE")
                    .add(SpeleoDBConstants.JSON_FIELDS.MIN_SOFTWARE_VERSION, "25.2.0")
                    .add(SpeleoDBConstants.JSON_FIELDS.MAX_SOFTWARE_VERSION, "25.3.0")
                    .add(SpeleoDBConstants.JSON_FIELDS.PLUGIN_VERSION, "2025.06.23")
                    .add(SpeleoDBConstants.JSON_FIELDS.DOWNLOAD_URL, "https://example.com/plugin.jar")
                    .add(SpeleoDBConstants.JSON_FIELDS.SHA256_HASH, "abc123")
                    .add(SpeleoDBConstants.JSON_FIELDS.CHANGELOG, "Initial Release"))
                .add(Json.createObjectBuilder()
                    .add(SpeleoDBConstants.JSON_FIELDS.SOFTWARE, "OTHER_SOFTWARE")
                    .add(SpeleoDBConstants.JSON_FIELDS.MIN_SOFTWARE_VERSION, "25.2.1")
                    .add(SpeleoDBConstants.JSON_FIELDS.PLUGIN_VERSION, "2025.06.24")
                    .add(SpeleoDBConstants.JSON_FIELDS.DOWNLOAD_URL, "https://example.com/other.jar")
                    .add(SpeleoDBConstants.JSON_FIELDS.SHA256_HASH, "def456")
                    .add(SpeleoDBConstants.JSON_FIELDS.CHANGELOG, "Other software release"))
                .add(Json.createObjectBuilder()
                    .add(SpeleoDBConstants.JSON_FIELDS.SOFTWARE, "ARIANE")
                    .add(SpeleoDBConstants.JSON_FIELDS.MIN_SOFTWARE_VERSION, "25.0.0")
                    .add(SpeleoDBConstants.JSON_FIELDS.MAX_SOFTWARE_VERSION, "25.1.0")
                    .add(SpeleoDBConstants.JSON_FIELDS.PLUGIN_VERSION, "2025.06.25")
                    .add(SpeleoDBConstants.JSON_FIELDS.DOWNLOAD_URL, "https://example.com/old.jar")
                    .add(SpeleoDBConstants.JSON_FIELDS.SHA256_HASH, "ghi789")
                    .add(SpeleoDBConstants.JSON_FIELDS.CHANGELOG, "Old ARIANE version"))
                .build();

            // Count ARIANE releases compatible with test software version (25.2.1)
            // Use a test version instead of relying on SpeleoDBConstants.ARIANE_VERSION
            String testCurrentVersion = "25.2.1";
            int compatibleReleases = 0;

            for (int i = 0; i < releases.size(); i++) {
                JsonObject release = releases.getJsonObject(i);
                if (!"ARIANE".equals(release.getString(SpeleoDBConstants.JSON_FIELDS.SOFTWARE, ""))) {
                    continue;
                }

                String minVersion = release.getString(SpeleoDBConstants.JSON_FIELDS.MIN_SOFTWARE_VERSION, null);
                String maxVersion = release.getString(SpeleoDBConstants.JSON_FIELDS.MAX_SOFTWARE_VERSION, null);

                boolean isCompatible = true;

                // Check minimum version
                if (minVersion != null && SpeleoDBController.compareVersions(testCurrentVersion, minVersion) < 0) {
                    isCompatible = false;
                }

                // Check maximum version
                if (maxVersion != null && SpeleoDBController.compareVersions(testCurrentVersion, maxVersion) > 0) {
                    isCompatible = false;
                }

                if (isCompatible) {
                    compatibleReleases++;
                }
            }

            assertEquals(1, compatibleReleases, "Should find exactly 1 ARIANE release compatible with test version 25.2.1");
        }

        @Test
        @DisplayName("Should find latest version from multiple releases")
        void shouldFindLatestVersionFromMultipleReleases() {
            // Create test releases with different plugin versions, all compatible with 25.2.1
            JsonArray releases = Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                    .add(SpeleoDBConstants.JSON_FIELDS.SOFTWARE, "ARIANE")
                    .add(SpeleoDBConstants.JSON_FIELDS.MIN_SOFTWARE_VERSION, "25.2.0")
                    .add(SpeleoDBConstants.JSON_FIELDS.MAX_SOFTWARE_VERSION, "25.3.0")
                    .add(SpeleoDBConstants.JSON_FIELDS.PLUGIN_VERSION, "2025.06.20")
                    .add(SpeleoDBConstants.JSON_FIELDS.DOWNLOAD_URL, "https://example.com/v1.jar")
                    .add(SpeleoDBConstants.JSON_FIELDS.SHA256_HASH, "hash1")
                    .add(SpeleoDBConstants.JSON_FIELDS.CHANGELOG, "Version 1"))
                .add(Json.createObjectBuilder()
                    .add(SpeleoDBConstants.JSON_FIELDS.SOFTWARE, "ARIANE")
                    .add(SpeleoDBConstants.JSON_FIELDS.MIN_SOFTWARE_VERSION, "25.2.1")
                    // No max version - compatible with 25.2.1 and above
                    .add(SpeleoDBConstants.JSON_FIELDS.PLUGIN_VERSION, "2025.06.25")
                    .add(SpeleoDBConstants.JSON_FIELDS.DOWNLOAD_URL, "https://example.com/v3.jar")
                    .add(SpeleoDBConstants.JSON_FIELDS.SHA256_HASH, "hash3")
                    .add(SpeleoDBConstants.JSON_FIELDS.CHANGELOG, "Version 3 - Latest"))
                .add(Json.createObjectBuilder()
                    .add(SpeleoDBConstants.JSON_FIELDS.SOFTWARE, "ARIANE")
                    .add(SpeleoDBConstants.JSON_FIELDS.MIN_SOFTWARE_VERSION, "25.1.0")
                    .add(SpeleoDBConstants.JSON_FIELDS.MAX_SOFTWARE_VERSION, "25.3.0")
                    .add(SpeleoDBConstants.JSON_FIELDS.PLUGIN_VERSION, "2025.06.22")
                    .add(SpeleoDBConstants.JSON_FIELDS.DOWNLOAD_URL, "https://example.com/v2.jar")
                    .add(SpeleoDBConstants.JSON_FIELDS.SHA256_HASH, "hash2")
                    .add(SpeleoDBConstants.JSON_FIELDS.CHANGELOG, "Version 2"))
                .build();

            // Find the latest version manually (simulating the controller logic)
            // Use a test version instead of relying on SpeleoDBConstants.ARIANE_VERSION
            String latestVersion = null;
            JsonObject latestRelease = null;
            String testCurrentVersion = "25.2.1";

            for (int i = 0; i < releases.size(); i++) {
                JsonObject release = releases.getJsonObject(i);
                if (!"ARIANE".equals(release.getString(SpeleoDBConstants.JSON_FIELDS.SOFTWARE, ""))) {
                    continue;
                }

                // Check version bounds
                String minVersion = release.getString(SpeleoDBConstants.JSON_FIELDS.MIN_SOFTWARE_VERSION, null);
                String maxVersion = release.getString(SpeleoDBConstants.JSON_FIELDS.MAX_SOFTWARE_VERSION, null);

                boolean isCompatible = true;

                if (minVersion != null && SpeleoDBController.compareVersions(testCurrentVersion, minVersion) < 0) {
                    isCompatible = false;
                }

                if (maxVersion != null && SpeleoDBController.compareVersions(testCurrentVersion, maxVersion) > 0) {
                    isCompatible = false;
                }

                if (isCompatible) {
                    String pluginVersion = release.getString(SpeleoDBConstants.JSON_FIELDS.PLUGIN_VERSION, null);
                    if (pluginVersion != null) {
                        if (latestVersion == null || SpeleoDBController.compareVersions(pluginVersion, latestVersion) > 0) {
                            latestVersion = pluginVersion;
                            latestRelease = release;
                        }
                    }
                }
            }

            assertNotNull(latestRelease, "Should find a latest release");
            assertEquals("2025.06.25", latestVersion, "Should identify the latest version");
            assertEquals("Version 3 - Latest", latestRelease.getString(SpeleoDBConstants.JSON_FIELDS.CHANGELOG, ""));
        }
    }


}
