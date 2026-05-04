package org.speleodb.ariane.plugin.speleodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.PREFERENCES;

/**
 * Tests to verify that the preference isolation system works correctly.
 * This ensures that tests don't affect user's actual preferences.
 *
 * Note: Test mode is controlled by a compile-time constant (SpeleoDBConstants.TEST_MODE)
 * that is set by the build system before compilation.
 */
class SpeleoDBPreferenceIsolationTest {

    private SpeleoDBController controller;

    @BeforeEach
    void setUp() {
        // Reset singleton instance before each test
        SpeleoDBController.resetInstance();
        // Get the singleton instance
        controller = SpeleoDBController.getInstance();
    }

    @AfterEach
    void tearDown() {
        // Reset singleton instance after each test to ensure clean state
        SpeleoDBController.resetInstance();
    }

    @Test
    void testPreferenceIsolationIsEnabled() {
        // Verify that TEST_MODE constant is true during test execution
        assertTrue(SpeleoDBConstants.TEST_MODE,
                  "TEST_MODE should be enabled during test execution");

        // Get preferences node
        Preferences testPrefs = controller.getPreferencesNode();

        // Verify we get a test-specific preferences node
        assertNotNull(testPrefs);

        // The test preferences should be isolated from production
        assertTrue(testPrefs.absolutePath().contains("test"),
                  "Test preferences should use test-specific path, got: " + testPrefs.absolutePath());
    }

    @Test
    void testPreferenceDataIsolation() {
        // Verify that we can write to test preferences without affecting user preferences
        Preferences testPrefs = controller.getPreferencesNode();

        String testKey = "test.isolation.verification";
        String testValue = "test-only-value-" + System.currentTimeMillis();

        // Write a test value
        testPrefs.put(testKey, testValue);

        // Verify we can read it back
        String readValue = testPrefs.get(testKey, "not-found");
        assertEquals(testValue, readValue, "Should be able to write and read test preferences");

        // Clean up
        testPrefs.remove(testKey);
    }

    @Test
    void testTestModePathConsistency() {
        // Get the singleton instance and verify consistent path usage
        SpeleoDBController controller1 = SpeleoDBController.getInstance();
        SpeleoDBController controller2 = SpeleoDBController.getInstance();

        // They should be the same instance
        assertEquals(controller1, controller2, "Should return the same singleton instance");

        Preferences prefs1 = controller1.getPreferencesNode();
        Preferences prefs2 = controller2.getPreferencesNode();

        assertEquals(prefs1.absolutePath(), prefs2.absolutePath(),
                    "All controllers should use the same test preferences path during tests");

        assertEquals(PREFERENCES.TEST_PREFERENCES_PATH, prefs1.absolutePath(),
                    "All controllers should use the test preferences path");
    }

    @Test
    void testPreferenceNodeStructure() {
        // Verify the structure of the test preferences node
        Preferences testPrefs = controller.getPreferencesNode();

        // The path should be under user root, not package-based
        String path = testPrefs.absolutePath();
        assertTrue(path.startsWith("/"), "Path should be absolute");
        assertEquals(PREFERENCES.TEST_PREFERENCES_PATH, path,
                    "Path should be exactly the test preferences path");
    }

    @Test
    void testPreferencesAreIsolatedFromProduction() throws Exception {
        // This test verifies isolation WITHOUT touching production preferences
        // We test that the test preferences namespace is completely separate

        Preferences testPrefs = controller.getPreferencesNode();

        // Clear test preferences to ensure clean state
        testPrefs.clear();

        // Verify test preferences start empty (regardless of production state)
        String[] keys = testPrefs.keys();
        assertEquals(0, keys.length, "Test preferences should start empty");

        // Write some test data
        testPrefs.put("test_isolation_key", "test_value");
        testPrefs.put("another_test_key", "another_value");

        // Verify we can read our test data
        assertEquals("test_value", testPrefs.get("test_isolation_key", null));
        assertEquals("another_value", testPrefs.get("another_test_key", null));

        // Clean up
        testPrefs.clear();

        // Verify cleanup worked
        assertEquals(0, testPrefs.keys().length, "Test preferences should be empty after clear");
    }

    @Test
    void testWritesToTestPreferences() throws Exception {
        // ONLY work with test preferences - never touch production
        Preferences testPrefs = Preferences.userRoot().node(PREFERENCES.TEST_PREFERENCES_NODE);

        // Clear test preferences to start fresh
        testPrefs.clear();

        // Get controller's preference node
        Preferences controllerPrefs = controller.getPreferencesNode();

        // Verify controller is using test preferences path
        assertEquals(PREFERENCES.TEST_PREFERENCES_PATH,
                    controllerPrefs.absolutePath(),
                    "Controller should use test preferences path during tests");

        // Write a test value through controller
        String testKey = "test_key_" + System.currentTimeMillis();
        String testValue = "test_value_" + System.currentTimeMillis();
        controllerPrefs.put(testKey, testValue);

        // Verify it was written to test preferences
        String readValue = testPrefs.get(testKey, null);
        assertEquals(testValue, readValue, "Value should be written to test preferences");

        // Test removing values
        controllerPrefs.remove(testKey);
        assertNull(testPrefs.get(testKey, null), "Value should be removed from test preferences");

        // Clean up
        testPrefs.clear();
    }

    @Test
    void testCleanupBetweenTests() throws Exception {
        // Clear any test preferences
        Preferences testPrefs = Preferences.userRoot().node(PREFERENCES.TEST_PREFERENCES_NODE);
        testPrefs.removeNode();

        Preferences prefs = controller.getPreferencesNode();

        // Verify we're using test preferences
        assertEquals(PREFERENCES.TEST_PREFERENCES_PATH,
                    prefs.absolutePath(),
                    "Should use test preferences");

        // Should have no values
        assertNull(prefs.get("SDB_EMAIL", null),
                  "Should have no email in fresh test preferences");
    }
}
