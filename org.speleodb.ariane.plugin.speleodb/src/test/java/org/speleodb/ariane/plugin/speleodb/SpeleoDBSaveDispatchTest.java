package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.arianesline.ariane.plugin.api.DataServerCommands;
import javafx.application.Platform;

@DisplayName("Single host save dispatch")
class SpeleoDBSaveDispatchTest {
    @Test
    @DisplayName("Accelerator takes precedence and never also fires button or command")
    void acceleratorOnly() {
        SpeleoDBPlugin plugin = new SpeleoDBPlugin();
        Runnable accelerator = mock(Runnable.class);
        Runnable button = mock(Runnable.class);
        try (var fx = mockStatic(Platform.class)) {
            fx.when(Platform::isFxApplicationThread).thenReturn(true);
            plugin.dispatchSaveActions(accelerator, button);
        }
        verify(accelerator).run();
        verify(button, never()).run();
        assertThat(plugin.getCommandProperty().get()).isNull();
    }

    @Test
    @DisplayName("Button is used only when the accelerator is unavailable")
    void buttonOnly() {
        SpeleoDBPlugin plugin = new SpeleoDBPlugin();
        Runnable button = mock(Runnable.class);
        try (var fx = mockStatic(Platform.class)) {
            fx.when(Platform::isFxApplicationThread).thenReturn(true);
            plugin.dispatchSaveActions(null, button);
        }
        verify(button).run();
        assertThat(plugin.getCommandProperty().get()).isNull();
    }

    @Test
    @DisplayName("Repeated command saves emit SAVE each time by rearming with DONE")
    void commandRearmed() {
        SpeleoDBPlugin plugin = new SpeleoDBPlugin();
        var commands = new ArrayList<String>();
        plugin.getCommandProperty().addListener((property, oldValue, value) -> commands.add(value));
        try (var fx = mockStatic(Platform.class)) {
            fx.when(Platform::isFxApplicationThread).thenReturn(true);
            plugin.dispatchSaveActions(null, null);
            plugin.dispatchSaveActions(null, null);
        }
        assertThat(commands).containsExactly(DataServerCommands.SAVE.name(),
                DataServerCommands.DONE.name(), DataServerCommands.SAVE.name());
    }

    @Test
    @DisplayName("A failed invoked accelerator never triggers a fallback save")
    void failedActionStops() {
        SpeleoDBPlugin plugin = new SpeleoDBPlugin();
        Runnable button = mock(Runnable.class);
        try (var fx = mockStatic(Platform.class)) {
            fx.when(Platform::isFxApplicationThread).thenReturn(true);
            assertThatThrownBy(() -> plugin.dispatchSaveActions(
                    () -> { throw new IllegalStateException("host failed"); }, button))
                    .isInstanceOf(IllegalStateException.class);
        }
        verify(button, never()).run();
        assertThat(plugin.getCommandProperty().get()).isNull();
    }

    @Test
    @DisplayName("Save actions cannot be invoked off the FX thread")
    void requiresFxThread() {
        try (var fx = mockStatic(Platform.class)) {
            fx.when(Platform::isFxApplicationThread).thenReturn(false);
            assertThatThrownBy(() -> new SpeleoDBPlugin().dispatchSaveActions(null, null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("Worker dispatch queues to FX and cancellation suppresses a queued save")
    void queuedCancellation() {
        SpeleoDBPlugin plugin = new SpeleoDBPlugin();
        AtomicReference<Runnable> queued = new AtomicReference<>();
        try (var fx = mockStatic(Platform.class)) {
            fx.when(Platform::isFxApplicationThread).thenReturn(false);
            fx.when(() -> Platform.runLater(any())).thenAnswer(call -> {
                queued.set(call.getArgument(0));
                return null;
            });
            var future = plugin.requestSurveySave();
            assertThat(future).isNotDone();
            assertThat(queued.get()).isNotNull();
            future.cancel(false);
            queued.get().run();
            assertThat(plugin.getCommandProperty().get()).isNull();
        }
    }

    @Test
    @DisplayName("Survey context changes while save is queued prevent host dispatch")
    void queuedContextChange() {
        SpeleoDBPlugin plugin = new SpeleoDBPlugin();
        AtomicBoolean valid = new AtomicBoolean(true);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        try (var fx = mockStatic(Platform.class)) {
            fx.when(Platform::isFxApplicationThread).thenReturn(false);
            fx.when(() -> Platform.runLater(any())).thenAnswer(call -> {
                queued.set(call.getArgument(0));
                return null;
            });
            var future = plugin.requestSurveySave(valid::get);
            valid.set(false);
            queued.get().run();
            assertThat(future).isCompletedExceptionally();
            assertThat(plugin.getCommandProperty().get()).isNull();
        }
    }

    @Test
    @DisplayName("Toolkit dispatch failure completes exceptionally without throwing from the API")
    void toolkitUnavailable() {
        try (var fx = mockStatic(Platform.class)) {
            fx.when(() -> Platform.runLater(any())).thenThrow(new IllegalStateException("toolkit stopped"));
            assertThat(new SpeleoDBPlugin().requestSurveySave()).isCompletedExceptionally();
        }
    }
}
