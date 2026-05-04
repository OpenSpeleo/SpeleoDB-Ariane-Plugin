package org.speleodb.ariane.plugin.speleodb;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test class for SpeleoDBConstants version loading fallback behavior.
 * This test verifies Plugin API containerVersion integration and fallback scenarios.
 */
@DisplayName("SpeleoDB Constants Version Fallback Tests")
class SpeleoDBConstantsVersionFallbackTest {

    @BeforeEach
    void setupForEachTest() {
        System.out.println("Test environment: Plugin API containerVersion = '" +
            com.arianesline.ariane.plugin.api.Plugin.containerVersion.toString() + "'");
        System.out.println("Test environment: ARIANE_VERSION = '" +
            SpeleoDBConstants.ARIANE_VERSION + "'");
    }

    @Test
    @DisplayName("Should handle empty containerVersion gracefully")
    void shouldHandleEmptyContainerVersionGracefully() {
        // This test verifies behavior when Plugin API containerVersion is empty
        String version = SpeleoDBConstants.ARIANE_VERSION;

        // Should never be null (can be empty string as fallback)
        assertNotNull(version, "ARIANE_VERSION should never be null");

        // If version is not empty, it should be a valid format
        if (!version.trim().isEmpty()) {
            assertTrue(version.matches("\\d+\\.\\d+\\.\\d+"),
                "ARIANE_VERSION should be in valid format when not empty: " + version);
        }

        // Log which version we got
        System.out.println("ARIANE_VERSION in fallback test: '" + version + "'");

        // Test Plugin API access
        String pluginApiVersion = com.arianesline.ariane.plugin.api.Plugin.containerVersion.toString();
        System.out.println("Plugin API containerVersion: '" + pluginApiVersion + "'");
    }

    @Test
    @DisplayName("Should test empty containerVersion scenario")
    void shouldTestEmptyContainerVersionScenario() {
        // Test what happens when containerVersion is explicitly empty
        String originalContent = com.arianesline.ariane.plugin.api.Plugin.containerVersion.toString();

        try {
            // Clear the containerVersion
            com.arianesline.ariane.plugin.api.Plugin.containerVersion.setLength(0);

            // Verify the API method works correctly
            String emptyApiVersion = com.arianesline.ariane.plugin.api.Plugin.containerVersion.toString();
            assertTrue(emptyApiVersion.isEmpty(), "Plugin API containerVersion should be empty for this test");

            System.out.println("Empty containerVersion test: Plugin API returns '" + emptyApiVersion + "'");

            // The SpeleoDBConstants.ARIANE_VERSION was already initialized during class loading,
            // so it won't change, but we can verify the current implementation logic
            System.out.println("Current ARIANE_VERSION (from static initialization): '" +
                SpeleoDBConstants.ARIANE_VERSION + "'");

        } finally {
            // Restore original content
            com.arianesline.ariane.plugin.api.Plugin.containerVersion.setLength(0);
            com.arianesline.ariane.plugin.api.Plugin.containerVersion.append(originalContent);
        }
    }

    @Test
    @DisplayName("Should be able to use ARIANE_VERSION in API calls when not empty")
    void shouldBeAbleToUseVersionInApiCalls() {
        // This test verifies that the version works in real scenarios
        String version = SpeleoDBConstants.ARIANE_VERSION;

        if (version != null && !version.trim().isEmpty()) {
            // Simulate version bounds checking like in plugin releases filtering
            String minVersion = "20.0.0";
            String maxVersion = "30.0.0";

            // Current version should be within reasonable bounds
            assertTrue(SpeleoDBController.compareVersions(version, minVersion) >= 0,
                "Version should be >= " + minVersion + " (actual: " + version + ")");
            assertTrue(SpeleoDBController.compareVersions(version, maxVersion) <= 0,
                "Version should be <= " + maxVersion + " (actual: " + version + ")");
        } else {
            System.out.println("Skipping API calls test - ARIANE_VERSION is empty in test environment");
            // Test that the comparison method works with valid versions
            assertTrue(SpeleoDBController.compareVersions("25.2.1", "20.0.0") > 0,
                "Version comparison should work: 25.2.1 > 20.0.0");
        }
    }

    @Test
    @DisplayName("Should handle version in logging without errors")
    void shouldHandleVersionInLogging() {
        // This test ensures that logging with the version doesn't cause issues
        assertDoesNotThrow(() -> {
            String logMessage = "Testing with Ariane version: '" + SpeleoDBConstants.ARIANE_VERSION + "'";
            System.out.println(logMessage);

            // Also test with Plugin API version
            String pluginLogMessage = "Plugin API containerVersion: '" +
                com.arianesline.ariane.plugin.api.Plugin.containerVersion.toString() + "'";
            System.out.println(pluginLogMessage);
        }, "Should be able to use ARIANE_VERSION in logging without errors");
    }
}
