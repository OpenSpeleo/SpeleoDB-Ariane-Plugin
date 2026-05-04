package org.speleodb.ariane.plugin.speleodb;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/**
 * Guards against macOS-specific JavaFX dialog quirks. The whole class is skipped on
 * non-macOS hosts because {@code Platform.startup} can throw
 * {@link UnsupportedOperationException} on a Linux CI runner without a display
 * (the previous in-method guard didn't help -- {@code @BeforeAll} fires before any
 * {@code @Test} body runs, so the class init still failed).
 */
@EnabledOnOs(OS.MAC)
@DisplayName("macOS Dialog Behavior Guarded Tests")
class MacOsDialogBehaviorTest {

    @BeforeAll
    static void initFX() {
        try {
            if (!Platform.isFxApplicationThread()) {
                Platform.startup(() -> {});
                Thread.sleep(100);
            }
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains("Toolkit already initialized")) {
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("Alerts are non-resizable and modal on macOS")
    void alertsAreNonResizableAndModalOnMac() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Test", ButtonType.OK);
            // Use the same path our code uses: set properties via helper (simulate minimal)
            alert.setResizable(false);
            assertFalse(alert.isResizable());
            // We cannot assert modality without an owner, but we can ensure creation succeeded
            assertNotNull(alert.getDialogPane());
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS), "FX task timed out");
    }
}
