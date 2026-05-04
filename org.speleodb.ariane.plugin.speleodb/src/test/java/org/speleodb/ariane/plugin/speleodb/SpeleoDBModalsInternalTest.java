package org.speleodb.ariane.plugin.speleodb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;

@DisplayName("SpeleoDBModals Internal Styling Tests")
class SpeleoDBModalsInternalTest {

    @BeforeAll
    static void initFX() throws InterruptedException {
        try {
            if (!Platform.isFxApplicationThread()) {
                Platform.startup(() -> {});
                Thread.sleep(100);
            }
        } catch (IllegalStateException e) {
            // "Toolkit already initialized" is the happy path -- FX is up from a prior class.
            if (!e.getMessage().contains("Toolkit already initialized")) {
                throw e;
            }
        } catch (UnsupportedOperationException e) {
            // Headless Linux CI without DISPLAY/Monocle: tests in this class are pure-Java
            // (Mockito reflection on private static methods), so a failed FX bootstrap is
            // non-fatal here.
        }
    }


    @Test
    @DisplayName("convertColorToRgba should convert hex and handle invalid input")
    void shouldConvertColorSafely() throws Exception {
        Method m = SpeleoDBModals.class.getDeclaredMethod("convertColorToRgba", String.class, double.class);
        m.setAccessible(true);

        String ok = (String) m.invoke(null, "#112233", 0.5);
        assertEquals("rgba(17,34,51,0.5)", ok);

        String bad = (String) m.invoke(null, "#ZZZZZZ", 0.5);
        assertEquals("rgba(0,0,0,0.5)", bad);

        String passthrough = (String) m.invoke(null, "blue", 0.5);
        assertEquals("blue", passthrough);
    }

    @Test
    @DisplayName("calculateTextWidth should scale with characters")
    void shouldCalculateTextWidth() throws Exception {
        Method m = SpeleoDBModals.class.getDeclaredMethod("calculateTextWidth", String.class, boolean.class);
        m.setAccessible(true);

        double width = (double) m.invoke(null, "Hello", true);
        // 5 * 11.0 = 55.0 for bold approximation
        assertEquals(55.0, width, 0.001);
    }
}
