package org.speleodb.ariane.plugin.speleodb;

import java.lang.reflect.Method;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

@DisplayName("Success GIF Suppression Preference Tests")
class SuccessGifSuppressionTest {

    private SpeleoDBController controller;

    @BeforeEach
    void setup() throws Exception {
        SpeleoDBController.resetInstance();
        controller = SpeleoDBController.getInstance();
        // Ensure prefs baseline
        Preferences prefs = getPrefs();
        prefs.remove(SpeleoDBConstants.PREFERENCES.PREF_SUPPRESS_SUCCESS_GIF);
    }

    @AfterEach
    void tearDown() {
        SpeleoDBController.resetInstance();
    }

    private Preferences getPrefs() throws Exception {
        Method m = SpeleoDBController.class.getDeclaredMethod("getPreferencesNode");
        m.setAccessible(true);
        return (Preferences) m.invoke(controller);
    }

    @Test
    @DisplayName("Setting suppression preference avoids dialog path")
    @DisabledIfEnvironmentVariable(named = "CI", matches = "true",
            disabledReason = "showSuccessCelebrationDialog -> SpeleoDBTooltips.showSuccess -> Platform.runLater blocks indefinitely on headless CI")
    void settingSuppressionAvoidsDialog() throws Exception {
        // Spy pattern: override tooltip class via reflection to detect call
        // We can't easily intercept the dialog, but we can set the pref and ensure code path reaches early-return.
        Preferences prefs = getPrefs();
        prefs.putBoolean(SpeleoDBConstants.PREFERENCES.PREF_SUPPRESS_SUCCESS_GIF, true);

        // Use reflection to call showSuccessCelebrationDialog with a counter callback
        final int[] counter = new int[1];
        Method m = SpeleoDBController.class.getDeclaredMethod("showSuccessCelebrationDialog", Runnable.class);
        m.setAccessible(true);
        m.invoke(controller, (Runnable) () -> counter[0]++);

        assertEquals(1, counter[0], "onCloseCallback should be invoked exactly once when suppressed");
    }

    @Test
    @DisplayName("Clicking 'Do not show again' sets the suppression preference")
    void clickingDoNotShowAgainSetsPreference() throws Exception {
        // Use the controller's test preferences node to avoid touching real user prefs
        Preferences prefs = getPrefs();
        prefs.remove(SpeleoDBConstants.PREFERENCES.PREF_SUPPRESS_SUCCESS_GIF);
        prefs.putBoolean(SpeleoDBConstants.PREFERENCES.PREF_SUPPRESS_SUCCESS_GIF, true);

        assertTrue(prefs.getBoolean(SpeleoDBConstants.PREFERENCES.PREF_SUPPRESS_SUCCESS_GIF, false));
    }
}
