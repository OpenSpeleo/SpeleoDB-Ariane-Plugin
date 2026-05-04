package org.speleodb.ariane.plugin.speleodb;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import javafx.application.Platform;
import javafx.scene.control.DialogPane;

/**
 * Disabled on CI ({@code CI=true}) because instantiating {@link NewProjectDialog}
 * loads {@code javafx.scene.control.*} classes whose static initializers call
 * {@code Platform.runLater} and block on a {@link CountDownLatch} waiting for the
 * FX toolkit to start -- a toolkit that cannot start on a headless Linux runner
 * without DISPLAY/Monocle.
 */
@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
        disabledReason = "Headless CI cannot bootstrap JavaFX toolkit; NewProjectDialog instantiation requires it")
@DisplayName("NewProjectDialog FX Properties Tests")
class NewProjectDialogFXPropertiesTest {

    @BeforeAll
    static void initFX() throws InterruptedException {
        // Best-effort FX bootstrap for hosts where the toolkit IS available
        // (developer macOS/Windows). Class-level CI guard handles the headless case.
        try {
            if (!Platform.isFxApplicationThread()) {
                Platform.startup(() -> {});
                Thread.sleep(100);
            }
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains("Toolkit already initialized")) {
                throw e;
            }
        } catch (UnsupportedOperationException ignored) {
            // No DISPLAY -- already disabled on CI; this is a dev-machine edge case.
        }
    }

    @Test
    @DisplayName("Dialog should be non-resizable and size-capped")
    void dialogShouldBeNonResizableAndSizeCapped() throws InterruptedException {
        AtomicReference<NewProjectDialog> ref = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            ref.set(new NewProjectDialog());
            latch.countDown();
        });

        boolean started = latch.await(2, TimeUnit.SECONDS);
        assertEquals(true, started, "FX task timed out");

        NewProjectDialog dialog = ref.get();
        assertNotNull(dialog);
        assertFalse(dialog.isResizable(), "Dialog should be non-resizable");

        DialogPane pane = dialog.getDialogPane();
        assertNotNull(pane);
        assertEquals(SpeleoDBConstants.DIMENSIONS.DIALOG_PREF_WIDTH + 100, (int) pane.getMaxWidth());
        assertEquals(600, (int) pane.getMaxHeight());
    }
}
