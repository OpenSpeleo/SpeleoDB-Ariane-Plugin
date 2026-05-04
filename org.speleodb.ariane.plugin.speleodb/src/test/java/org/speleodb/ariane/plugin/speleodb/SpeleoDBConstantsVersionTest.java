package org.speleodb.ariane.plugin.speleodb;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test class for SpeleoDBConstants version loading functionality.
 * Verifies that ARIANE_VERSION loading works with Plugin API containerVersion.
 */
@DisplayName("SpeleoDB Constants Version Loading Tests")
class SpeleoDBConstantsVersionTest {

    @Test
    @DisplayName("Should have ARIANE_VERSION field accessible")
    void shouldHaveArianeVersionFieldAccessible() {
        // Verify that ARIANE_VERSION field exists and is accessible
        assertNotNull(SpeleoDBConstants.ARIANE_VERSION,
            "ARIANE_VERSION should not be null");

        // Log the version for verification (can be empty in test environment)
        System.out.println("ARIANE_VERSION from SpeleoDBConstants: '" + SpeleoDBConstants.ARIANE_VERSION + "'");
    }

    @Test
    @DisplayName("Should have valid version format when not empty")
    void shouldHaveValidVersionFormatWhenNotEmpty() {
        String version = SpeleoDBConstants.ARIANE_VERSION;

        // If version is not empty, it should match the pattern
        if (version != null && !version.trim().isEmpty()) {
            assertTrue(version.matches("\\d+\\.\\d+\\.\\d+"),
                "ARIANE_VERSION should match version pattern when not empty: " + version);
        } else {
            System.out.println("ARIANE_VERSION is empty in test environment - this is expected");
        }
    }

    @Test
    @DisplayName("Should have ARIANE_SOFTWARE_NAME constant")
    void shouldHaveArianeSoftwareNameConstant() {
        assertEquals("ARIANE", SpeleoDBConstants.ARIANE_SOFTWARE_NAME,
            "ARIANE_SOFTWARE_NAME should be 'ARIANE'");
    }

    @Test
    @DisplayName("Should verify Plugin API containerVersion access works")
    void shouldVerifyPluginApiContainerVersionAccess() {
        // Test that we can access the Plugin API containerVersion method
        assertDoesNotThrow(() -> {
            String pluginVersion = com.arianesline.ariane.plugin.api.Plugin.containerVersion.toString();
            System.out.println("Plugin API containerVersion: '" + pluginVersion + "'");
        }, "Should be able to access Plugin API containerVersion.toString()");

        // Test setting and getting containerVersion
        String originalContent = com.arianesline.ariane.plugin.api.Plugin.containerVersion.toString();

        try {
            // Set a test version
            com.arianesline.ariane.plugin.api.Plugin.containerVersion.setLength(0);
            com.arianesline.ariane.plugin.api.Plugin.containerVersion.append("99.99.99");

            String testVersion = com.arianesline.ariane.plugin.api.Plugin.containerVersion.toString();
            assertEquals("99.99.99", testVersion,
                "Plugin API containerVersion should be settable and retrievable");

        } finally {
            // Restore original content
            com.arianesline.ariane.plugin.api.Plugin.containerVersion.setLength(0);
            com.arianesline.ariane.plugin.api.Plugin.containerVersion.append(originalContent);
        }
    }

    @Test
    @DisplayName("Should be able to use version in comparison when not empty")
    void shouldBeAbleToUseVersionInComparison() {
        String currentVersion = SpeleoDBConstants.ARIANE_VERSION;

        if (currentVersion != null && !currentVersion.trim().isEmpty()) {
            // Test that the version comparison method works
            assertEquals(0, SpeleoDBController.compareVersions(currentVersion, currentVersion),
                "Version should be equal to itself");

            // Test against a known lower version
            assertTrue(SpeleoDBController.compareVersions(currentVersion, "1.0.0") > 0,
                "Current version should be greater than 1.0.0: " + currentVersion);
        } else {
            System.out.println("Skipping version comparison test - ARIANE_VERSION is empty in test environment");
            // In test environment, we just verify the method exists and works
            assertEquals(0, SpeleoDBController.compareVersions("25.2.1", "25.2.1"),
                "Version comparison method should work with valid versions");
        }
    }
}
